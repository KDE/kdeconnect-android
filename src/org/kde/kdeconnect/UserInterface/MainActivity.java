package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityMainBinding;

import java.util.Collection;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int MENU_ENTRY_ADD_DEVICE = 1; //0 means no-selection
    private static final int MENU_ENTRY_SETTINGS = 2;
    private static final int MENU_ENTRY_DEVICE_FIRST_ID = 1000; //All subsequent ids are devices in the menu
    private static final int MENU_ENTRY_DEVICE_UNKNOWN = 9999; //It's still a device, but we don't know which one yet

    private static final String STATE_SELECTED_MENU_ENTRY = "selected_entry"; //Saved only in onSaveInstanceState
    private static final String STATE_SELECTED_DEVICE = "selected_device"; //Saved persistently in preferences

    public static final int RESULT_NEEDS_RELOAD = Activity.RESULT_FIRST_USER;

    public static final String PAIR_REQUEST_STATUS = "pair_req_status";
    public static final String PAIRING_ACCEPTED = "accepted";
    public static final String PAIRING_REJECTED = "rejected";
    public static final String PAIRING_PENDING = "pending";

    public static final String EXTRA_DEVICE_ID = "deviceId";

    private NavigationView mNavigationView;
    private DrawerLayout mDrawerLayout;
    private TextView mNavViewDeviceName;

    private String mCurrentDevice;
    private int mCurrentMenuEntry;

    private SharedPreferences preferences;

    private final HashMap<MenuItem, String> mMapMenuToDeviceId = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setUserPreferredTheme(this);
        super.onCreate(savedInstanceState);

        final ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mNavigationView = binding.navigationDrawer;
        mDrawerLayout = binding.drawerLayout;
        final Toolbar mToolbar = binding.toolbar;

        View mDrawerHeader = mNavigationView.getHeaderView(0);
        mNavViewDeviceName = mDrawerHeader.findViewById(R.id.device_name);

        setSupportActionBar(mToolbar);

        ActionBar actionBar = getSupportActionBar();

        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
                mDrawerLayout, /* DrawerLayout object */
                R.string.open, /* "open drawer" description */
                R.string.close /* "close drawer" description */
        );

        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerToggle.syncState();

        preferences = getSharedPreferences("stored_menu_selection", Context.MODE_PRIVATE);

        // Note: The preference changed listener should be registered before getting the name, because getting
        // it can trigger a background fetch from the internet that will eventually update the preference
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        String deviceName = DeviceHelper.getDeviceName(this);
        mNavViewDeviceName.setText(deviceName);

        mNavigationView.setNavigationItemSelectedListener(menuItem -> {
            mCurrentMenuEntry = menuItem.getItemId();
            switch (mCurrentMenuEntry) {
                case MENU_ENTRY_ADD_DEVICE:
                    mCurrentDevice = null;
                    preferences.edit().putString(STATE_SELECTED_DEVICE, null).apply();
                    setContentFragment(new PairingFragment());
                    break;
                case MENU_ENTRY_SETTINGS:
                    mCurrentDevice = null;
                    preferences.edit().putString(STATE_SELECTED_DEVICE, null).apply();
                    setContentFragment(new SettingsFragment());
                    break;
                default:
                    String deviceId = mMapMenuToDeviceId.get(menuItem);
                    onDeviceSelected(deviceId);
                    break;
            }

            mDrawerLayout.closeDrawer(mNavigationView);
            return true;
        });

        // Decide which menu entry should be selected at start
        String savedDevice;
        int savedMenuEntry;
        if (getIntent().hasExtra("forceOverview")) {
            Log.i("MainActivity", "Requested to start main overview");
            savedDevice = null;
            savedMenuEntry = MENU_ENTRY_ADD_DEVICE;
        } else if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            Log.i("MainActivity", "Loading selected device from parameter");
            savedDevice = getIntent().getStringExtra(EXTRA_DEVICE_ID);
            savedMenuEntry = MENU_ENTRY_DEVICE_UNKNOWN;
            // If pairStatus is not empty, then the user has accepted/reject the pairing from the notification
            String pairStatus = getIntent().getStringExtra(PAIR_REQUEST_STATUS);
            if (pairStatus != null) {
                Log.i("MainActivity", "pair status is " + pairStatus);
                savedDevice = onPairResultFromNotification(savedDevice, pairStatus);
                if (savedDevice == null) {
                    savedMenuEntry = MENU_ENTRY_ADD_DEVICE;
                }
            }
        } else if (savedInstanceState != null) {
            Log.i("MainActivity", "Loading selected device from saved activity state");
            savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE);
            savedMenuEntry = savedInstanceState.getInt(STATE_SELECTED_MENU_ENTRY, MENU_ENTRY_ADD_DEVICE);
        } else {
            Log.i("MainActivity", "Loading selected device from persistent storage");
            savedDevice = preferences.getString(STATE_SELECTED_DEVICE, null);
            savedMenuEntry = (savedDevice != null)? MENU_ENTRY_DEVICE_UNKNOWN : MENU_ENTRY_ADD_DEVICE;
        }

        mCurrentMenuEntry = savedMenuEntry;
        mCurrentDevice = savedDevice;
        mNavigationView.setCheckedItem(savedMenuEntry);

        //FragmentManager will restore whatever fragment was there
        if (savedInstanceState != null) {
            Fragment frag = getSupportFragmentManager().findFragmentById(R.id.container);
            if (!(frag instanceof DeviceFragment) || ((DeviceFragment)frag).getDeviceId().equals(savedDevice)) {
                return;
            }
        }

        // Activate the chosen fragment and select the entry in the menu
        if (savedMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID && savedDevice != null) {
            onDeviceSelected(savedDevice);
        } else {
            if (mCurrentMenuEntry == MENU_ENTRY_SETTINGS) {
                setContentFragment(new SettingsFragment());
            } else {
                setContentFragment(new PairingFragment());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    private String onPairResultFromNotification(String deviceId, String pairStatus) {
        assert(deviceId != null);

        if (!pairStatus.equals(PAIRING_PENDING)) {
            BackgroundService.RunCommand(this, service -> {
                Device device = service.getDevice(deviceId);
                if (device == null) {
                    Log.w("rejectPairing", "Device no longer exists: " + deviceId);
                    return;
                }

                if (pairStatus.equals(PAIRING_ACCEPTED)) {
                    device.acceptPairing();
                } else if (pairStatus.equals(PAIRING_REJECTED)) {
                    device.rejectPairing();
                }
            });
        }

        if (pairStatus.equals(PAIRING_ACCEPTED) || pairStatus.equals(PAIRING_PENDING)) {
            return deviceId;
        } else {
            return null;
        }
    }

    private int deviceIdToMenuEntryId(String deviceId) {
        for (HashMap.Entry<MenuItem, String> entry : mMapMenuToDeviceId.entrySet()) {
            if (TextUtils.equals(entry.getValue(), deviceId)) { //null-safe
                return entry.getKey().getItemId();
            }
        }
        return MENU_ENTRY_DEVICE_UNKNOWN;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawer(mNavigationView);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            mDrawerLayout.openDrawer(mNavigationView);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void updateDeviceList() {
        BackgroundService.RunCommand(MainActivity.this, service -> {

            Menu menu = mNavigationView.getMenu();
            menu.clear();
            mMapMenuToDeviceId.clear();

            SubMenu devicesMenu = menu.addSubMenu(R.string.devices);

            int id = MENU_ENTRY_DEVICE_FIRST_ID;
            Collection<Device> devices = service.getDevices().values();
            for (Device device : devices) {
                if (device.isReachable() && device.isPaired()) {
                    MenuItem item = devicesMenu.add(Menu.FIRST, id++, 1, device.getName());
                    item.setIcon(device.getIcon());
                    item.setCheckable(true);
                    mMapMenuToDeviceId.put(item, device.getDeviceId());
                }
            }

            MenuItem addDeviceItem = devicesMenu.add(Menu.FIRST, MENU_ENTRY_ADD_DEVICE, 1000, R.string.pair_new_device);
            addDeviceItem.setIcon(R.drawable.ic_action_content_add_circle_outline_32dp);
            addDeviceItem.setCheckable(true);

            MenuItem settingsItem = menu.add(Menu.FIRST, MENU_ENTRY_SETTINGS, 1000, R.string.settings);
            settingsItem.setIcon(R.drawable.ic_settings_white_32dp);
            settingsItem.setCheckable(true);

            //Ids might have changed
            if (mCurrentMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID) {
                mCurrentMenuEntry = deviceIdToMenuEntryId(mCurrentDevice);
            }
            mNavigationView.setCheckedItem(mCurrentMenuEntry);
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.RunCommand(this, service -> {
            service.onNetworkChange();
            service.addDeviceListChangedCallback("MainActivity", this::updateDeviceList);
        });
        updateDeviceList();
    }

    @Override
    protected void onStop() {
        BackgroundService.RunCommand(this, service -> service.removeDeviceListChangedCallback("MainActivity"));
        super.onStop();
    }

    private static void uncheckAllMenuItems(Menu menu) {
        int size = menu.size();
        for (int i = 0; i < size; i++) {
            MenuItem item = menu.getItem(i);
            if(item.hasSubMenu()) {
                uncheckAllMenuItems(item.getSubMenu());
            } else {
                item.setChecked(false);
            }
        }
    }

    public void onDeviceSelected(String deviceId, boolean fromDeviceList) {
        mCurrentDevice = deviceId;
        preferences.edit().putString(STATE_SELECTED_DEVICE, deviceId).apply();

        if (mCurrentDevice != null) {
            mCurrentMenuEntry = deviceIdToMenuEntryId(deviceId);
            if (mCurrentMenuEntry == MENU_ENTRY_DEVICE_UNKNOWN) {
                uncheckAllMenuItems(mNavigationView.getMenu());
            } else {
                mNavigationView.setCheckedItem(mCurrentMenuEntry);
            }
            setContentFragment(DeviceFragment.newInstance(deviceId, fromDeviceList));
        } else {
            mCurrentMenuEntry = MENU_ENTRY_ADD_DEVICE;
            mNavigationView.setCheckedItem(mCurrentMenuEntry);
            setContentFragment(new PairingFragment());
        }
    }

    private void setContentFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    public void onDeviceSelected(String deviceId) {
        onDeviceSelected(deviceId, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_DEVICE, mCurrentDevice);
        outState.putInt(STATE_SELECTED_MENU_ENTRY, mCurrentMenuEntry);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_NEEDS_RELOAD) {
            BackgroundService.RunCommand(this, service -> {
                Device device = service.getDevice(mCurrentDevice);
                device.reloadPluginsFromSettings();
            });
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (ArrayUtils.contains(grantResults, PackageManager.PERMISSION_GRANTED)) {
            //New permission granted, reload plugins
            BackgroundService.RunCommand(this, service -> {
                Device device = service.getDevice(mCurrentDevice);
                device.reloadPluginsFromSettings();
            });
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DeviceHelper.KEY_DEVICE_NAME_PREFERENCE.equals(key)) {
            mNavViewDeviceName.setText(DeviceHelper.getDeviceName(this));
            BackgroundService.RunCommand(this, BackgroundService::onNetworkChange); //Re-send our identity packet
        }
    }
}
