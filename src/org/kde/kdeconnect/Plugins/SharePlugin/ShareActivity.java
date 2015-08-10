/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
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


public class ShareActivity extends ActionBarActivity {

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
                BackgroundService.RunCommand(ShareActivity.this, new BackgroundService.InstanceCallback() {
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
                final ArrayList<Device> devicesList = new ArrayList<>();
                final ArrayList<ListAdapter.Item> items = new ArrayList<>();

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
                        list.setAdapter(new ListAdapter(ShareActivity.this, items));
                        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                                Device device = devicesList.get(i-1); //NOTE: -1 because of the title!

                                Bundle extras = intent.getExtras();
                                if (extras != null) {
                                    if (extras.containsKey(Intent.EXTRA_STREAM)) {

                                        try {

                                            ArrayList<Uri> uriList;
                                            if (!Intent.ACTION_SEND.equals(intent.getAction())) {
                                                uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                                            } else {
                                                Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
                                                uriList = new ArrayList<>();
                                                uriList.add(uri);
                                            }

                                            queuedSendUriList(device, uriList);

                                        } catch (Exception e) {
                                            Log.e("ShareActivity", "Exception");
                                            e.printStackTrace();
                                        }

                                    } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                                        String text = extras.getString(Intent.EXTRA_TEXT);
                                        String subject = extras.getString(Intent.EXTRA_SUBJECT);

                                        //Hack: Detect shared youtube videos, so we can open them in the browser instead of as text
                                        if (subject != null && subject.endsWith("YouTube")) {
                                            int index = text.indexOf(": http://youtu.be/");
                                            if (index > 0) {
                                                text = text.substring(index + 2); //Skip ": "
                                            }
                                        }

                                        boolean isUrl;
                                        try {
                                            new URL(text);
                                            isUrl = true;
                                        } catch (Exception e) {
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
            long size = -1;

            final NotificationManager notificationManager = (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            final int notificationId = (int)System.currentTimeMillis();
            final NotificationCompat.Builder builder ;
            Resources res = getApplicationContext().getResources();
            builder = new NotificationCompat.Builder(getApplicationContext())
                    .setContentTitle(res.getString(R.string.outgoing_file_title, device.getName()))
                    .setTicker(res.getString(R.string.outgoing_file_title, device.getName()))
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setProgress(100,0,true);

            try {
                notificationManager.notify(notificationId,builder.build());
            } catch(Exception e) {
                //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
            }

            final Handler progressBarHandler = new Handler(Looper.getMainLooper());

            if (uri.getScheme().equals("file")) {
                // file:// is a non media uri, so we cannot query the ContentProvider

                np.set("filename", uri.getLastPathSegment());

                try {
                    size = new File(uri.getPath()).length();
                } catch(Exception e) {
                    Log.e("ShareActivity", "Could not obtain file size");
                    e.printStackTrace();
                }

                np.setPayload(inputStream, size);

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
                    size = new File(path).length();
                } catch(Exception unused) {

                    Log.e("ShareActivity", "Could not resolve media to a file, trying to get info as media");

                    try {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                        cursor.moveToFirst();
                        String name = cursor.getString(column_index);
                        np.set("filename", name);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("ShareActivity", "Could not obtain file name");
                    }

                    try {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                        cursor.moveToFirst();
                        //For some reason this size can differ from the actual file size!
                        size = cursor.getInt(column_index);
                    } catch(Exception e) {
                        Log.e("ShareActivity", "Could not obtain file size");
                        e.printStackTrace();
                    }
                } finally {
                    cursor.close();
                }

                np.setPayload(inputStream, size);

            }

            final String filename = np.getString("filename");

            builder.setContentText(res.getString(R.string.outgoing_file_text,filename));
            try {
                notificationManager.notify(notificationId,builder.build());
            } catch(Exception e) {
                //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
            }

            device.sendPackage(np, new Device.SendPackageStatusCallback() {

                int prevProgress = 0;

                @Override
                public void onProgressChanged(final int progress) {
                    if (progress != prevProgress) {
                        prevProgress = progress;
                        progressBarHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                builder.setProgress(100, progress, false);
                                try {
                                    notificationManager.notify(notificationId,builder.build());
                                } catch(Exception e) {
                                    //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                                    //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
                                }
                            }
                        });
                    }
                }

                @Override
                public void onSuccess() {
                    progressBarHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Resources res = getApplicationContext().getResources();
                            NotificationCompat.Builder anotherBuilder = new NotificationCompat.Builder(getApplicationContext())
                                    .setContentTitle(res.getString(R.string.sent_file_title, device.getName()))
                                    .setContentText(res.getString(R.string.sent_file_text, filename))
                                    .setTicker(res.getString(R.string.sent_file_title, device.getName()))
                                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                                    .setOngoing(false)
                                    .setAutoCancel(true);

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            if (prefs.getBoolean("share_notification_preference", true)) {
                                anotherBuilder.setDefaults(Notification.DEFAULT_ALL);
                            }
                            try {
                                notificationManager.notify(notificationId,anotherBuilder.build());
                            } catch(Exception e) {
                                //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                                //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
                            }
                        }
                    });

                    if (!uriList.isEmpty()) queuedSendUriList(device, uriList);
                    else Log.i("ShareActivity", "All files sent");
                }

                @Override
                public void onFailure(Throwable e) {
                    progressBarHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Resources res = getApplicationContext().getResources();
                            NotificationCompat.Builder anotherBuilder = new NotificationCompat.Builder(getApplicationContext())
                                    .setContentTitle(res.getString(R.string.sent_file_failed_title, device.getName()))
                                    .setContentText(res.getString(R.string.sent_file_failed_text, filename))
                                    .setTicker(res.getString(R.string.sent_file_title, device.getName()))
                                    .setSmallIcon(android.R.drawable.stat_notify_error)
                                    .setOngoing(false)
                                    .setAutoCancel(true);

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            if (prefs.getBoolean("share_notification_preference", true)) {
                                anotherBuilder.setDefaults(Notification.DEFAULT_ALL);
                            }
                            try {
                                notificationManager.notify(notificationId,anotherBuilder.build());
                            } catch(Exception e) {
                                //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                                //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
                            }
                        }
                    });

                    Log.e("ShareActivity", "Failed to send file");
                }
            });

        } catch (Exception e) {
            Log.e("ShareActivity", "Exception sending files");
            e.printStackTrace();
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
