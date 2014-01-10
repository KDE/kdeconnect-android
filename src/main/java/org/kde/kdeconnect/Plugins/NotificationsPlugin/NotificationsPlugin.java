package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;

import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.Helpers.ImagesHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class NotificationsPlugin extends Plugin implements NotificationReceiver.NotificationListener {

    /*static {
        PluginFactory.registerPlugin(NotificationsPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_notifications";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_notifications);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_notifications_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }




    static class NotificationId {
        String packageName;
        String tag;
        int id;

        public static NotificationId fromNotification(StatusBarNotification statusBarNotification) {
            NotificationId nid  = new NotificationId();
            nid.packageName = statusBarNotification.getPackageName();
            nid.tag = statusBarNotification.getTag();
            nid.id = statusBarNotification.getId();
            return nid;
        }
        public static NotificationId unserialize(String s) {
            NotificationId nid  = new NotificationId();
            int first = s.indexOf(':');
            int last = s.lastIndexOf(':');
            nid.packageName = s.substring(0, first);
            nid.tag = s.substring(first+1, last);
            if (nid.tag.length() == 0) nid.tag = null;
            String idString = s.substring(last+1);
            try {
                nid.id = Integer.parseInt(idString);
            } catch(Exception e) {
                nid.id = 0;
            }
            //Log.e("NotificationId","unserialize: " + nid.packageName+ ", "+nid.tag+ ", "+nid.id);
            return nid;
        }
        public String serialize() {
            //Log.e("NotificationId","serialize: " + packageName+ ", "+tag+ ", "+id);
            String safePackageName = (packageName == null)? "" : packageName;
            String safeTag = (tag == null)? "" : tag;
            return safePackageName+":"+safeTag+":"+id;
        }
        public String getPackageName() {
            return packageName;
        }
        public String getTag() {
            return tag;
        }
        public int getId() {
            return id;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NotificationId)) return false;
            NotificationId other = (NotificationId)o;
            return other.getTag().equals(tag) && other.getId() == id && other.getPackageName().equals(packageName);
        }
    }




    @Override
    public boolean onCreate() {

        if (Build.VERSION.SDK_INT < 18) return false;

        //Check for permissions
        String notificationListenerList = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (notificationListenerList != null && notificationListenerList.contains(context.getPackageName())) {
            NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                @Override
                public void onServiceStart(NotificationReceiver service) {
                    try {
                        service.addListener(NotificationsPlugin.this);
                        StatusBarNotification[] notifications = service.getActiveNotifications();
                        for (StatusBarNotification notification : notifications) {
                            sendNotification(notification, true);
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("NotificationsPlugin","Exception");
}
                }
            });
            return true;
        } else {
            return false;
        }

    }


    @Override
    public void onDestroy() {
        NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
            @Override
            public void onServiceStart(NotificationReceiver service) {
                service.removeListener(NotificationsPlugin.this);
            }
        });
    }





    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        NotificationId id = NotificationId.fromNotification(statusBarNotification);

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);
        np.set("id", id.serialize());
        np.set("isCancel", true);
        device.sendPackage(np);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        sendNotification(statusBarNotification, false);
    }

    public void sendNotification(StatusBarNotification statusBarNotification, boolean requestAnswer) {

        Notification notification = statusBarNotification.getNotification();

         if ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
             || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0 ) {
            //This is not a notification!
            return;
        }

        NotificationId id = NotificationId.fromNotification(statusBarNotification);

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);

        String packageName = statusBarNotification.getPackageName();
        String appName = AppsHelper.appNameLookup(context, packageName);

        //TODO: Add support for displaying app icons to desktop plasmoid and uncomment this piece of code
        /*
        try {
            //TODO: Scale down app icon if too big and compress as JPG
            Drawable drawableAppIcon = AppsHelper.appIconLookup(context, packageName);
            Bitmap appIcon = ImagesHelper.drawableToBitmap(drawableAppIcon);
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream);
            byte[] bitmapData = outStream.toByteArray();
            np.setPayload(bitmapData);
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("NotificationsPlugin","Error retrieving icon");
        }
        */

        np.set("id", id.serialize());
        np.set("appName", appName == null? packageName : appName);
        np.set("isClearable", statusBarNotification.isClearable());
        np.set("ticker", (notification != null && notification.tickerText != null)? notification.tickerText.toString() : "");
        np.set("time", Long.toString(statusBarNotification.getPostTime()));
        if (requestAnswer) np.set("requestAnswer", true);

        device.sendPackage(np);
    }





    @Override
    public boolean onPackageReceived(final NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_NOTIFICATION)) return false;

        if (np.getBoolean("request")) {

            NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                private void sendCurrentNotifications(NotificationReceiver service) {
                    StatusBarNotification[] notifications = service.getActiveNotifications();
                    for (StatusBarNotification notification : notifications) {
                        sendNotification(notification, true);
                    }
                }


                @Override
                public void onServiceStart(final NotificationReceiver service) {
                    try {
                        //If service just started, this call will throw an exception because the answer is not ready yet
                        sendCurrentNotifications(service);
                    } catch(Exception e) {
                        Log.e("onPackageReceived","Error when answering 'request': Service failed to start. Retrying in 100ms...");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(100);
                                    Log.e("onPackageReceived","Error when answering 'request': Service failed to start. Retrying...");
                                    sendCurrentNotifications(service);
                                } catch (Exception e) {
                                    Log.e("onPackageReceived","Error when answering 'request': Service failed to start twice!");
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                }
            });

        } else if (np.has("cancel")) {

            NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                @Override
                public void onServiceStart(NotificationReceiver service) {

                    NotificationId dismissedId = NotificationId.unserialize(np.getString("cancel"));
                    service.cancelNotification(dismissedId.getPackageName(), dismissedId.getTag(), dismissedId.getId());
                }
            });

        } else {

            Log.w("NotificationsPlugin","Nothing to do");

        }

        return true;
    }


    @Override
    public AlertDialog getErrorDialog(final Context baseContext) {

        if (Build.VERSION.SDK_INT < 18) {
            return new AlertDialog.Builder(baseContext)
                .setTitle(R.string.pref_plugin_notifications)
                .setMessage(R.string.plugin_not_available)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .create();
        } else {
            return new AlertDialog.Builder(baseContext)
                .setTitle(R.string.pref_plugin_notifications)
                .setMessage(R.string.no_permissions)
                .setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        baseContext.startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel,new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Do nothing
                    }
                })
                .create();
        }
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        return null;
    }

}
