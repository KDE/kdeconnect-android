package org.kde.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.kde.connect.PackageInterfaces.PingPackageInterface;
import org.kde.kdeconnect.R;

public class MainActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
            }
        });

        findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        service.restart();
                    }
                });
            }
        });

        findViewById(R.id.button2).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        PingPackageInterface pi = (PingPackageInterface) service.getPackageInterface(PingPackageInterface.class);
                        pi.sendPing();
                    }
                });

            }
        });

        findViewById(R.id.button3).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Intent intent = new Intent(MainActivity.this, MprisActivity.class);
                        startActivity(intent);
                    }
                });

            }
        });

        findViewById(R.id.button4).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(final BackgroundService service) {
                          runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String[] listContent = service.getVisibleDevices().toArray(new String[0]);
                                ListView list = (ListView)findViewById(R.id.listView1);
                                list.setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, listContent));
                            }
                        });
                    }
                });

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()){
            case R.id.action_settings:
                Intent intent = new Intent(this,SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onMenuItemSelected(featureId, item);
        }
    }
}
