package org.kde.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.Switch;

import org.kde.connect.Plugins.PingPlugin;
import org.kde.kdeconnect.R;

public class DeviceActivity extends Activity {

    private String deviceId;

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
                        PingPlugin pi = (PingPlugin) device.getPlugin("plugin_ping");
                        if (pi != null) pi.sendPing();
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

}
