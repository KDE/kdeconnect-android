package org.kde.connect;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

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
                Log.i("MainActivity","Button1");
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
                Log.i("MainActivity","Button2");
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        service.sendPing();
                    }
                });

            }
        });

        final ListView list = (ListView)findViewById(R.id.listView1);


        findViewById(R.id.button3).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("MainActivity","Button3");
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(final BackgroundService service) {
                          runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String[] listContent = service.getVisibleDevices().toArray(new String[0]);
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

}
