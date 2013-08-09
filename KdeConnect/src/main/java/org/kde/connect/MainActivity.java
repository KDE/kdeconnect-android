package org.kde.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.PackageInterfaces.PingPackageInterface;
import org.kde.kdeconnect.R;

import java.util.ArrayList;

public class MainActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.onNetworkChange();
            }
        });

        findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        /*service.stopDiscovery();
                        service.startDiscovery();*/
                        service.onNetworkChange();
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
                        if (pi != null) pi.sendPing();
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

        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                final Runnable updateComputerList = new Runnable() {
                    @Override
                    public void run() {
                        Log.e("MainActivity","updateComputerList");
                        ArrayList<String> devices = service.getVisibleDevices();
                        String[] listContent = devices.toArray(new String[devices.size()]);
                        ListView list = (ListView)findViewById(R.id.listView1);
                        list.setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, listContent));
                    }
                };

                service.addConnectionListener(new BaseLinkProvider.ConnectionReceiver() {
                    @Override
                    public void onConnectionAccepted(NetworkPackage identityPackage, BaseComputerLink link) {
                        runOnUiThread(updateComputerList);
                    }

                    @Override
                    public void onConnectionLost(BaseComputerLink link) {
                        Log.e("MainActivity","onConnectionLost");
                        runOnUiThread(updateComputerList);
                    }
                });

                updateComputerList.run();

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
                if (Build.VERSION.SDK_INT > 10) {
                    Intent intent = new Intent(this,SettingsActivity.class);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this,CompatSettingsActivity.class);
                    startActivity(intent);
                }
                return true;
            default:
                return super.onMenuItemSelected(featureId, item);
        }
    }
}
