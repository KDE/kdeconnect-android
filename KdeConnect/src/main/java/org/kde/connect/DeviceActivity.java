package org.kde.connect;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;

import org.kde.connect.Plugins.PingPlugin;
import org.kde.connect.Plugins.Plugin;
import org.kde.kdeconnect.R;

import java.util.HashMap;
import java.util.Map;

public class DeviceActivity extends Activity {

    private String deviceId;
    private Device.PluginsChangedListener pluginsChangedListener = new Device.PluginsChangedListener() {
        @Override
        public void onPluginsChanged(final Device device) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("MainActivity", "updateComputerList");

                    final HashMap<String, Plugin> plugins = device.getFailedPlugins();
                    final String[] ids = plugins.keySet().toArray(new String[plugins.size()]);
                    String[] names = new String[plugins.size()];
                    for(int i = 0; i < ids.length; i++) {
                        Plugin p = plugins.get(ids[i]);
                        names[i] = p.getDisplayName();
                    }

                    ListView list = (ListView)findViewById(R.id.listView1);

                    list.setAdapter(new ArrayAdapter<String>(DeviceActivity.this, android.R.layout.simple_list_item_1, names));

                    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                            Plugin p = plugins.get(ids[position]);
                            p.getErrorDialog(DeviceActivity.this).show();
                        }
                    });

                   findViewById(R.id.textView).setVisibility(plugins.size() > 0? View.VISIBLE : View.GONE);

                }
            });



        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.device, menu);

        MenuItem item = menu.findItem(R.id.menu_trusted);
        final Switch toggle = (Switch)item.getActionView();
        BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                final Device device = service.getDevice(deviceId);

                toggle.setChecked(device.isTrusted());
                toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, final boolean b) {
                            device.setTrusted(b);
                    }
                });
            }
        });

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

/*
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setDisplayHomeAsUpEnabled(true);
        //actionBar.setIcon()
*/

        deviceId = getIntent().getStringExtra("deviceId");

        BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                setTitle(device.getName());
                device.addPluginsChangedListener(pluginsChangedListener);
                pluginsChangedListener.onPluginsChanged(device);
            }
        });

        findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DeviceActivity.this, SettingsActivity.class);
                intent.putExtra("deviceId", deviceId);
                startActivity(intent);
            }
        });


        findViewById(R.id.button2).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        device.sendPackage(new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PING));
                    }
                });

            }
        });

        findViewById(R.id.button3).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Intent intent = new Intent(DeviceActivity.this, MprisActivity.class);
                        intent.putExtra("deviceId", deviceId);
                        startActivity(intent);
                    }
                });
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
}
