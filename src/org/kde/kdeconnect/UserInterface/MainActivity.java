package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect_tp.R;

import java.util.Collection;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_DEVICE = "selected_device";

    public static final int RESULT_NEEDS_RELOAD = Activity.RESULT_FIRST_USER;

    public static final String PAIR_REQUEST_STATUS = "pair_req_status";
    public static final String PAIRING_ACCEPTED = "accepted";
    public static final String PAIRING_REJECTED = "rejected";

    private NavigationView mNavigationView;
    private DrawerLayout mDrawerLayout;

    private String mCurrentDevice;

    private SharedPreferences preferences;

    private final HashMap<MenuItem, String> mMapMenuToDeviceId = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We need to set this theme before the call to 'setContentView' below
        ThemeUtil.setUserPreferredTheme(this);

        setContentView(R.layout.activity_main);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.navigation_drawer);
        View mDrawerHeader = mNavigationView.getHeaderView(0);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
                mDrawerLayout, /* DrawerLayout object */
                R.string.open, /* "open drawer" description */
                R.string.close /* "close drawer" description */
        );

        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerToggle.syncState();

        String deviceName = DeviceHelper.getDeviceName(this);
        TextView nameView = (TextView) mDrawerHeader.findViewById(R.id.device_name);
        nameView.setText(deviceName);

        View.OnClickListener renameListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renameDevice();
            }
        };
        mDrawerHeader.findViewById(R.id.kdeconnect_label).setOnClickListener(renameListener);
        mDrawerHeader.findViewById(R.id.device_name).setOnClickListener(renameListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            addDarkModeSwitch((ViewGroup) mDrawerHeader);
        }

        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {

                String deviceId = mMapMenuToDeviceId.get(menuItem);
                onDeviceSelected(deviceId);

                mDrawerLayout.closeDrawer(mNavigationView);

                return true;
            }
        });

        preferences = getSharedPreferences(STATE_SELECTED_DEVICE, Context.MODE_PRIVATE);

        String savedDevice;
        String pairStatus = "";
        if (getIntent().hasExtra("forceOverview")) {
            Log.i("MainActivity", "Requested to start main overview");
            savedDevice = null;
        } else if (getIntent().hasExtra("deviceId")) {
            Log.i("MainActivity", "Loading selected device from parameter");
            savedDevice = getIntent().getStringExtra("deviceId");
            if (getIntent().hasExtra(PAIR_REQUEST_STATUS)) {
                pairStatus = getIntent().getStringExtra(PAIR_REQUEST_STATUS);
            }
        } else if (savedInstanceState != null) {
            Log.i("MainActivity", "Loading selected device from saved activity state");
            savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE);
        } else {
            Log.i("MainActivity", "Loading selected device from persistent storage");
            savedDevice = preferences.getString(STATE_SELECTED_DEVICE, null);
        }
        //if pairStatus is not empty, then the decision has been made...
        if (!pairStatus.equals("")) {
            Log.i("MainActivity", "pair status is " + pairStatus);
            onNewDeviceSelected(savedDevice, pairStatus);
        }
        onDeviceSelected(savedDevice);
    }

    /**
     * Adds a {@link SwitchCompat} to the bottom of the navigation header for
     * toggling dark mode on and off. Call from {@link #onCreate(Bundle)}.
     * <p>
     *     Only supports android ICS and higher because {@link SwitchCompat}
     *     requires that.
     * </p>
     *
     * @param drawerHeader the layout which should contain the switch
     */
    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addDarkModeSwitch(ViewGroup drawerHeader) {
        getLayoutInflater().inflate(R.layout.nav_dark_mode_switch, drawerHeader);

        SwitchCompat darkThemeSwitch = (SwitchCompat) drawerHeader.findViewById(R.id.dark_theme);
        darkThemeSwitch.setChecked(ThemeUtil.shouldUseDarkTheme(this));
        darkThemeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onCheckedChanged(CompoundButton darkThemeSwitch, boolean isChecked) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                boolean isDarkAlready = prefs.getBoolean("darkTheme", false);
                if (isDarkAlready != isChecked) {
                    prefs.edit().putBoolean("darkTheme", isChecked).apply();
                    MainActivity.this.recreate();
                }
            }
        });
    }

    //like onNewDeviceSelected but assumes that the new device is simply requesting to be paired
    //and can't be null
    private void onNewDeviceSelected(String deviceId, String pairStatus) {
        mCurrentDevice = deviceId;

        preferences.edit().putString(STATE_SELECTED_DEVICE, mCurrentDevice).apply();

        for (HashMap.Entry<MenuItem, String> entry : mMapMenuToDeviceId.entrySet()) {
            boolean selected = TextUtils.equals(entry.getValue(), deviceId); //null-safe
            entry.getKey().setChecked(selected);
        }

        if (pairStatus.equals(PAIRING_ACCEPTED)) {
            DeviceFragment.acceptPairing(deviceId, this);
        } else {
            DeviceFragment.rejectPairing(deviceId, this);
        }
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

    private void updateComputerList() {

        //Log.e("MainActivity", "UpdateComputerList");

        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                Menu menu = mNavigationView.getMenu();

                menu.clear();
                mMapMenuToDeviceId.clear();

                int id = 0;
                Collection<Device> devices = service.getDevices().values();
                for (Device device : devices) {
                    if (device.isReachable() && device.isPaired()) {
                        MenuItem item = menu.add(0, id++, 0, device.getName());
                        item.setIcon(device.getIcon());
                        item.setCheckable(true);
                        item.setChecked(device.getDeviceId().equals(mCurrentDevice));
                        mMapMenuToDeviceId.put(item, device.getDeviceId());
                    }
                }

                MenuItem item = menu.add(99, id++, 0, R.string.pair_new_device);
                item.setIcon(R.drawable.ic_action_content_add_circle_outline);
                item.setCheckable(true);
                item.setChecked(mCurrentDevice == null);
                mMapMenuToDeviceId.put(item, null);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this, true);
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.addDeviceListChangedCallback("MainActivity", new BackgroundService.DeviceListChangedCallback() {
                    @Override
                    public void onDeviceListChanged() {
                        updateComputerList();
                    }
                });
            }
        });
        updateComputerList();
    }

    @Override
    protected void onStop() {
        BackgroundService.removeGuiInUseCounter(this);
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.removeDeviceListChangedCallback("MainActivity");
            }
        });
        super.onStop();
    }

    //TODO: Make it accept two parameters, a constant with the type of screen and the device id in
    //case the screen is for a device, or even three parameters and the third one be the plugin id?
    //This way we can keep adding more options with null device id (eg: about, help...)
    public void onDeviceSelected(String deviceId, boolean fromDeviceList) {

        mCurrentDevice = deviceId;

        preferences.edit().putString(STATE_SELECTED_DEVICE, mCurrentDevice).apply();

        for (HashMap.Entry<MenuItem, String> entry : mMapMenuToDeviceId.entrySet()) {
            boolean selected = TextUtils.equals(entry.getValue(), deviceId); //null-safe
            entry.getKey().setChecked(selected);
        }

        Fragment fragment;
        if (deviceId == null) {
            fragment = new PairingFragment();
        } else {
            fragment = new DeviceFragment(deviceId, fromDeviceList);
        }

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
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE);
        onDeviceSelected(savedDevice);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_NEEDS_RELOAD:
                BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(mCurrentDevice);
                        device.reloadPluginsFromSettings();
                    }
                });
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                //New permission granted, reload plugins
                BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(mCurrentDevice);
                        device.reloadPluginsFromSettings();
                    }
                });
            }
        }
    }

    public void renameDevice() {
        final TextView nameView = (TextView) mNavigationView.findViewById(R.id.device_name);
        final EditText deviceNameEdit = new EditText(MainActivity.this);
        String deviceName = DeviceHelper.getDeviceName(MainActivity.this);
        deviceNameEdit.setText(deviceName);
        deviceNameEdit.setPadding(
                ((int) (18 * getResources().getDisplayMetrics().density)),
                ((int) (16 * getResources().getDisplayMetrics().density)),
                ((int) (18 * getResources().getDisplayMetrics().density)),
                ((int) (12 * getResources().getDisplayMetrics().density))
        );
        new AlertDialog.Builder(MainActivity.this)
                .setView(deviceNameEdit)
                .setPositiveButton(R.string.device_rename_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String deviceName = deviceNameEdit.getText().toString();
                        DeviceHelper.setDeviceName(MainActivity.this, deviceName);
                        nameView.setText(deviceName);
                        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                            @Override
                            public void onServiceStart(final BackgroundService service) {
                                service.onNetworkChange();
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setTitle(R.string.device_rename_title)
                .show();
    }
}
