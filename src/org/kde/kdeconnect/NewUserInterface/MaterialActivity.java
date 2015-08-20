package org.kde.kdeconnect.NewUserInterface;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.MainSettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.util.Collection;
import java.util.HashMap;

public class MaterialActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_DEVICE = "selected_device";

    private NavigationView mNavigationView;
    private DrawerLayout mDrawerLayout;

    private String mCurrentDevice;

    HashMap<MenuItem, String> mMapMenuToDeviceId = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_material);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.navigation_drawer);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_drawer);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        String deviceName = MainSettingsActivity.initializeDeviceName(this);
        TextView nameView = (TextView) mDrawerLayout.findViewById(R.id.device_name);
        nameView.setText(deviceName);

        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {

                String deviceId = mMapMenuToDeviceId.get(menuItem);
                onDeviceSelected(deviceId);

                mDrawerLayout.closeDrawer(mNavigationView);

                return true;
            }
        });

        if (savedInstanceState != null) {
            String savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE);
            onDeviceSelected(savedDevice);
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

        BackgroundService.RunCommand(MaterialActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                Menu menu = mNavigationView.getMenu();

                menu.clear();
                mMapMenuToDeviceId.clear();

                int id = 0;
                Collection<Device> devices = service.getDevices().values();
                for (Device device : devices) {
                    if (device.isReachable() && device.isPaired()) {
                        MenuItem item = menu.add(0,id++,0,device.getName());
                        item.setIcon(device.getIcon());
                        item.setCheckable(true);
                        mMapMenuToDeviceId.put(item, device.getDeviceId());
                    }
                }

                MenuItem item = menu.add(99, id++, 0, "Pair new device");
                item.setIcon(R.drawable.ic_action_content_add_circle_outline);
                item.setCheckable(true);
                mMapMenuToDeviceId.put(item, null);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.RunCommand(MaterialActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.onNetworkChange();
                service.setDeviceListChangedCallback(new BackgroundService.DeviceListChangedCallback() {
                    @Override
                    public void onDeviceListChanged() {
                        updateComputerList();
                    }
                });
            }
        });
    }

    @Override
    protected void onStop() {
        BackgroundService.RunCommand(MaterialActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.setDeviceListChangedCallback(null);
            }
        });
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateComputerList();
    }

    public void onDeviceSelected(String deviceId) {

        mCurrentDevice = deviceId;

        for(HashMap.Entry<MenuItem, String> entry : mMapMenuToDeviceId.entrySet()) {
            boolean selected = TextUtils.equals(entry.getValue(), deviceId); //null-safe
            entry.getKey().setChecked(selected);
        }

        Fragment fragment;
        if (deviceId == null) {
            fragment = new PairingFragment();
        } else {
            fragment = new DeviceFragment(deviceId);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
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

}
