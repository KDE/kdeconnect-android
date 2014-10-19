package org.kde.kdeconnect.UserInterface;

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

import java.io.File;
import java.io.InputStream;
import java.net.URL;
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

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
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

                                Bundle extras = intent.getExtras();
                                if (extras.containsKey(Intent.EXTRA_STREAM)) {

                                    try {

                                        ArrayList<Uri> uriList;
                                        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
                                            uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                                        } else {
                                            Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
                                            uriList = new ArrayList<Uri>();
                                            uriList.add(uri);
                                        }

                                        queuedSendUriList(device, uriList);

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.e("ShareToReceiver", "Exception");
                                    }

                                } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                                    String text = extras.getString(Intent.EXTRA_TEXT);
                                    boolean isUrl;
                                    try {
                                        new URL(text);
                                        isUrl = true;
                                    } catch(Exception e) {
                                        isUrl = false;
                                    }
                                    NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_SHARE);
                                    if (isUrl) {
                                        np.set("url", text);
                                    } else {
                                        np.set("text", text);
                                    }
                                    device.sendPackage(np);
                                }



                                finish();
                            }
                        });
                    }
                });

            }
        });
    }

    private void queuedSendUriList(final Device device, final ArrayList<Uri> uriList) {
        try {
            Uri uri = uriList.remove(0);
            ContentResolver cr = getContentResolver();
            InputStream inputStream = cr.openInputStream(uri);

            NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_SHARE);
            int size = -1;

            if (uri.getScheme().equals("file")) {
                // file:// is a non media uri, so we cannot query the ContentProvider

                np.set("filename", uri.getLastPathSegment());

                try {
                    size = (int)new File(uri.getPath()).length();
                    np.setPayload(inputStream, size);
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("ShareToReceiver", "Could not obtain file size");
                }

            }else{
                // Probably a content:// uri, so we query the Media content provider

                Cursor cursor = null;
                try {
                    String[] proj = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME };
                    cursor = getContentResolver().query(uri, proj, null, null, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    cursor.moveToFirst();
                    String path = cursor.getString(column_index);
                    np.set("filename", Uri.parse(path).getLastPathSegment());
                    np.set("size", (int)new File(path).length());
                } catch(Exception unused) {

                    Log.e("ShareToReceiver", "Could not resolve media to a file, trying to get info as media");

                    try {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                        cursor.moveToFirst();
                        String name = cursor.getString(column_index);
                        np.set("filename", name);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("ShareToReceiver", "Could not obtain file name");
                    }

                    try {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                        cursor.moveToFirst();
                        //For some reason this size can differ from the actual file size!
                        size = cursor.getInt(column_index);
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("ShareToReceiver", "Could not obtain file size");
                    }
                } finally {
                    cursor.close();
                }

                np.setPayload(inputStream, size);

            }

            device.sendPackage(np, new Device.SendPackageFinishedCallback() {
                @Override
                public void sendSuccessful() {
                    if (!uriList.isEmpty()) queuedSendUriList(device, uriList);
                    else Log.e("ShareToReceiver", "All files sent");
                }

                @Override
                public void sendFailed() {
                    Log.e("ShareToReceiver", "Failed to send file");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("ShareToReceiver", "Exception sending files");
        }

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
