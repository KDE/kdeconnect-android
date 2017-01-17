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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;


public class ShareActivity extends ActionBarActivity {

    private MenuItem menuProgress;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.refresh, menu);
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

                                Device device = devicesList.get(i - 1); //NOTE: -1 because of the title!

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

                                            SharePlugin.queuedSendUriList(getApplicationContext(), device, uriList);

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
                                        NetworkPackage np = new NetworkPackage(SharePlugin.PACKAGE_TYPE_SHARE_REQUEST);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);


        setContentView(R.layout.activity_list);
    }


    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.onNetworkChange();
                service.addDeviceListChangedCallback("ShareActivity", new BackgroundService.DeviceListChangedCallback() {
                    @Override
                    public void onDeviceListChanged() {
                        updateComputerList();
                    }
                });
            }
        });
        updateComputerList();
    }

    @Override
    protected void onStop() {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.removeDeviceListChangedCallback("ShareActivity");
            }
        });
        BackgroundService.removeGuiInUseCounter(this);
        super.onStop();
    }

}
