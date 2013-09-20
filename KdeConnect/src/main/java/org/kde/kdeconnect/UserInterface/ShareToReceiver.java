package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.List.EntryItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;


public class ShareToReceiver extends ActionBarActivity {


    //
    // Action bar
    //

    private MenuItem menuProgress;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        menuProgress = menu.findItem(R.id.menu_progress);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                updateComputerList();
                BackgroundService.RunCommand(ShareToReceiver.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        service.onNetworkChange();
                    }
                });
                item.setVisible(false);
                menuProgress.setVisible(true);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try { Thread.sleep(1500); } catch (InterruptedException e) { }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                menuProgress.setVisible(false);
                                item.setVisible(true);
                            }
                        });
                    }
                }).start();
                break;
            default:
                break;
        }
        return true;
    }

    private void updateComputerList() {

        final Intent intent = getIntent();

        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                Collection<Device> devices = service.getDevices().values();
                final ArrayList<Device> devicesList = new ArrayList<Device>();
                final ArrayList<ListAdapter.Item> items = new ArrayList<ListAdapter.Item>();

                items.add(new SectionItem(getString(R.string.share_to)));

                for (Device d : devices) {
                    if (d.isReachable() && d.isPaired()) {
                        devicesList.add(d);
                        items.add(new EntryItem(d.getName()));
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ListView list = (ListView) findViewById(R.id.listView1);
                        list.setAdapter(new ListAdapter(ShareToReceiver.this, items));
                        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                                Device device = devicesList.get(i-1); //NOTE: -1 because of the title!

                                NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_FILETRANSFER);

                                Bundle extras = intent.getExtras();
                                if (extras.containsKey(Intent.EXTRA_STREAM)) {
                                    try {
                                        Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);

                                        ContentResolver cr = getContentResolver();

                                        InputStream inputStream = cr.openInputStream(uri);

                                        //TODO: Figure out a way to find the file size, so we can show a progress bar in KDE
                                        np.setPayload(inputStream, -1);

                                    } catch (Exception e) {
                                        Log.e(this.getClass().getName(), e.toString());
                                    }
                                } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                                    np.set("text",extras.getString(Intent.EXTRA_TEXT));
                                }

                                device.sendPackage(np);

                                finish();
                            }
                        });
                    }
                });

            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);

        setContentView(R.layout.activity_main);
    }



    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
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
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
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
}
