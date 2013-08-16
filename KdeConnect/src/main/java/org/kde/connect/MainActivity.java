package org.kde.connect;


import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.kdeconnect.R;

import java.util.HashMap;

public class MainActivity extends Activity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    private MenuItem menuItem;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_load:
                BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        service.onNetworkChange();
                    }
                });
                if (Build.VERSION.SDK_INT >= 11) {
                    menuItem = item;
                    menuItem.setActionView(R.layout.progressbar);
                    if (Build.VERSION.SDK_INT >= 14) {
                        menuItem.expandActionView();
                    }
                    TestTask task = new TestTask();
                    task.execute();
                }
                break;
            default:
                break;
        }
        return true;
    }

    private class TestTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            if (Build.VERSION.SDK_INT >= 14)
                menuItem.collapseActionView();
            menuItem.setActionView(null);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
/*
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
*/

        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.onNetworkChange();
            }
        });

        BackgroundService.RunCommand(MainActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                final Runnable updateComputerList = new Runnable() {
                    @Override
                    public void run() {
                        Log.e("MainActivity","updateComputerList");

                        HashMap<String, Device> devices = service.getDevices();
                        final String[] ids = devices.keySet().toArray(new String[devices.size()]);
                        String[] names = new String[devices.size()];
                        for(int i = 0; i < ids.length; i++) {
                            Device d = devices.get(ids[i]);
                            names[i] = d.getName() + " " + d.isTrusted() + " " + d.isReachable();
                        }

                        ListView list = (ListView)findViewById(R.id.listView1);

                        list.setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, names));

                        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                                Intent intent = new Intent(MainActivity.this, DeviceActivity.class);
                                intent.putExtra("deviceId", ids[position]);
                                startActivity(intent);
                            }
                        });
                    }
                };

                service.addConnectionListener(new BaseLinkProvider.ConnectionReceiver() {
                    @Override
                    public void onConnectionReceived(NetworkPackage identityPackage, BaseComputerLink link) {
                        runOnUiThread(updateComputerList);
                    }

                    @Override
                    public void onConnectionLost(BaseComputerLink link) {
                        runOnUiThread(updateComputerList);
                    }
                });

                updateComputerList.run();

            }
        });
    }

}
