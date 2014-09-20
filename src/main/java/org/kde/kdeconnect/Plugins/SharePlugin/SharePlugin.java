package org.kde.kdeconnect.Plugins.SharePlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class SharePlugin extends Plugin {

    @Override
    public String getPluginName() {
        return "plugin_share";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_sharereceiver);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_sharereceiver_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_SHARE)) {
            return false;
        }

        try {
            if (np.hasPayload()) {

                Log.e("SharePlugin", "hasPayload");

                final InputStream input = np.getPayload();
                final int fileLength = np.getPayloadSize();
                final String filename = np.getString("filename", new Long(System.currentTimeMillis()).toString());

                String deviceDir = FilesHelper.toFileSystemSafeName(device.getName());
                //Get the external storage and append "/kdeconnect/DEVICE_NAME/"
                String destinationDir = Environment.getExternalStorageDirectory().getPath();
                destinationDir = new File(destinationDir, "kdeconnect").getPath();
                destinationDir = new File(destinationDir, deviceDir).getPath();

                //Create directories if needed
                new File(destinationDir).mkdirs();

                //Append filename to the destination path
                final File destinationFullPath = new File(destinationDir, filename);

                Log.e("SharePlugin", "destinationFullPath:" + destinationFullPath);

                final int notificationId = (int)System.currentTimeMillis();
                Resources res = context.getResources();
                Notification noti = new NotificationCompat.Builder(context)
                        .setContentTitle(res.getString(R.string.incoming_file_title, device.getName()))
                        .setContentText(res.getString(R.string.incoming_file_text, filename))
                        .setTicker(res.getString(R.string.incoming_file_title, device.getName()))
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setAutoCancel(true)
                        .build();

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(notificationId, noti);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            OutputStream output = new FileOutputStream(destinationFullPath.getPath());

                            byte data[] = new byte[1024];
                            long total = 0;
                            int count;
                            while ((count = input.read(data)) >= 0) {
                                total += count;
                                output.write(data, 0, count);
                                if (fileLength > 0) {
                                    if (total >= fileLength) break;
                                    float progress = (total * 100 / fileLength);
                                }
                                //else Log.e("SharePlugin", "Infinite loop? :D");
                            }

                            output.flush();
                            output.close();
                            input.close();

                            Log.e("SharePlugin", "Transfer finished");

                            //Make sure it is added to the Android Gallery
                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(destinationFullPath));
                            context.sendBroadcast(mediaScanIntent);

                            //Update the notification and allow to open the file from it
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(destinationFullPath), FilesHelper.getMimeTypeFromFile(destinationFullPath.getPath()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                            stackBuilder.addNextIntent(intent);
                            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                                    0,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );

                            Resources res = context.getResources();

                            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                                    .setContentTitle(res.getString(R.string.received_file_title, device.getName()))
                                    .setContentText(res.getString(R.string.received_file_text, filename))
                                    .setContentIntent(resultPendingIntent)
                                    .setTicker(res.getString(R.string.received_file_title, device.getName()))
                                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                                    .setAutoCancel(true);


                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                            if (prefs.getBoolean("share_notification_preference", true)) {
                                builder.setDefaults(Notification.DEFAULT_ALL);
                            }

                            Notification noti = builder.build();

                            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                            notificationManager.notify(notificationId, noti);
                            
                        } catch (Exception e) {
                            Log.e("SharePlugin", "Receiver thread exception");
                            e.printStackTrace();

                        }
                    }
                }).start();

            } else if (np.has("text")) {
                Log.e("SharePlugin", "hasText");

                String text = np.getString("text");
                if(android.os.Build.VERSION.SDK_INT >= 11) {
                    ClipboardManager cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(text);
                } else {
                    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setText(text);
                }
                Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show();
            } else if (np.has("url")) {

                String url = np.getString("url");

                Log.e("SharePlugin", "hasUrl: "+url);

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                //Do not launch it directly, show a notification instead
                //context.startActivity(browserIntent);

                Resources res = context.getResources();
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                stackBuilder.addNextIntent(browserIntent);
                PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

                Notification noti = new NotificationCompat.Builder(context)
                        .setContentTitle(res.getString(R.string.received_url_title, device.getName()))
                        .setContentText(res.getString(R.string.received_url_text, url))
                        .setContentIntent(resultPendingIntent)
                        .setTicker(res.getString(R.string.received_url_title, device.getName()))
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .build();

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify((int)System.currentTimeMillis(), noti);

            } else {
                Log.e("SharePlugin", "Error: Nothing attached!");
            }


        } catch(Exception e) {
            Log.e("SharePlugin","Exception");
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public AlertDialog getErrorDialog(Activity deviceActivity) {
        return null;
    }

    @Override
    public Button getInterfaceButton(Activity activity) { return null; }

}
