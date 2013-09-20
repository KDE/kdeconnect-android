package org.kde.kdeconnect.Plugins.FileTransferPlugin;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.apache.mina.util.Base64;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.List.DeviceItem;
import org.kde.kdeconnect.UserInterface.List.EntryItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;


public class ShareToReceiver extends Activity {

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

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

                                Device device = devicesList.get(i-1); //-1 because of the title!

                                NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_FILETRANSFER);

                                Bundle extras = intent.getExtras();
                                if (extras.containsKey(Intent.EXTRA_STREAM)) {
                                    try {
                                        Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);

                                        ContentResolver cr = getContentResolver();
                                        Cursor c = cr.query(intent.getData(), null, null, null, null);
                                        c.moveToFirst();
                                        final int fileNameColumnId = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                                        if (fileNameColumnId >= 0) {
                                            np.set("filename", c.getString(fileNameColumnId));
                                        } else {
                                            Log.e("ShareToReceiver", "No filename found");
                                        }

                                        InputStream inputStream = cr.openInputStream(uri);

                                        //TODO: Figure out a way to find the file size, so we can show a progress bar in KDE
                                        np.setPayload(inputStream, -1);

                                    } catch (Exception e) {
                                        Log.e(this.getClass().getName(), e.toString());
                                    }
                                } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                                    np.set("content",extras.getString(Intent.EXTRA_TEXT));
                                }

                                device.sendPackage(np);

                            }
                        });
                    }
                });

            }
        });


    }
}
