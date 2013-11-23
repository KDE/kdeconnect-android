package org.kde.kdeconnect.UserInterface;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.List.ButtonItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

public class DeviceActivity extends ActionBarActivity {

    static private String deviceId; //Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.
    private Device device;

    private Device.PluginsChangedListener pluginsChangedListener = new Device.PluginsChangedListener() {
        @Override
        public void onPluginsChanged(final Device device) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    //Errors list
                    final HashMap<String, Plugin> failedPlugins = device.getFailedPlugins();
                    final String[] ids = failedPlugins.keySet().toArray(new String[failedPlugins.size()]);
                    String[] names = new String[failedPlugins.size()];
                    for(int i = 0; i < ids.length; i++) {
                        Plugin p = failedPlugins.get(ids[i]);
                        names[i] = p.getDisplayName();
                    }
                    ListView errorList = (ListView)findViewById(R.id.errors_list);
                    if (!failedPlugins.isEmpty() && errorList.getHeaderViewsCount() == 0) {
                        TextView header = new TextView(DeviceActivity.this);
                        header.setPadding(0,24,0,0);
                        header.setText(getResources().getString(R.string.plugins_failed_to_load));
                        errorList.addHeaderView(header);
                    }
                    errorList.setAdapter(new ArrayAdapter<String>(DeviceActivity.this, android.R.layout.simple_list_item_1, names));
                    errorList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                            Plugin p = failedPlugins.get(ids[position - 1]); //Header is position 0, so we have to subtract one
                            p.getErrorDialog(DeviceActivity.this).show();
                        }
                    });

                    try {
                        //Buttons list
                        ArrayList<ListAdapter.Item> items = new ArrayList<ListAdapter.Item>();
                        final Collection<Plugin> plugins = device.getLoadedPlugins().values();
                        for (Plugin p : plugins) {
                            Button b = p.getInterfaceButton(DeviceActivity.this);
                            if (b != null) {
                                items.add(new SectionItem(p.getDisplayName()));
                                items.add(new ButtonItem(b));
                            }
                        }

                        ListView buttonsList = (ListView)findViewById(R.id.buttons_list);
                        buttonsList.setAdapter(new ListAdapter(DeviceActivity.this, items));

                    } catch(ConcurrentModificationException e) {
                        Log.e("DeviceActivity", "ConcurrentModificationException");
                        this.run(); //Try again
                    }

                }
            });

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device = service.getDevice(deviceId);
                if (device == null) return;
                setTitle(device.getName());
                device.addPluginsChangedListener(pluginsChangedListener);
                pluginsChangedListener.onPluginsChanged(device);
            }
        });

    }

    @Override
    protected void onDestroy() {
        BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                device.removePluginsChangedListener(pluginsChangedListener);
            }
        });
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        if (device.isPaired()) {
            menu.add(R.string.device_menu_plugins).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    Intent intent = new Intent(DeviceActivity.this, SettingsActivity.class);
                    intent.putExtra("deviceId", deviceId);
                    startActivity(intent);
                    return true;
                }
            });
            menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    device.unpair();
                    finish();
                    return true;
                }
            });
            return true;
        } else {
            return false;
        }

    }
}
