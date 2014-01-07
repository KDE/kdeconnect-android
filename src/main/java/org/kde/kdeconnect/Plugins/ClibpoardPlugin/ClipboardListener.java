package org.kde.kdeconnect.Plugins.ClibpoardPlugin;

import android.content.ClipData;
import android.content.Context;
import android.content.ClipboardManager;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;

public class ClipboardListener {


    private Context context;
    private String currentContent;

    private ClipboardManager cm = null;
    ClipboardManager.OnPrimaryClipChangedListener listener;

    ClipboardListener(final Context ctx, final Device device) {
        context = ctx;
        if(android.os.Build.VERSION.SDK_INT < 11) {
            return;
        }

        cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        listener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                try {

                    ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                    String content = item.coerceToText(context).toString();

                    if (!content.equals(currentContent)) {
                        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_CLIPBOARD);
                        np.set("content", content);
                        device.sendPackage(np);
                        currentContent = content;
                    }

                } catch(Exception e) {
                    //Probably clipboard was not text
                }
            }
        };
        cm.addPrimaryClipChangedListener(listener);
    }

    public void stop() {
        if(android.os.Build.VERSION.SDK_INT < 11) {
            return;
        }

        cm.removePrimaryClipChangedListener(listener);
    }

    @SuppressWarnings("deprecation")
    public void setText(String text) {
        currentContent = text;
        if(android.os.Build.VERSION.SDK_INT < 11) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
        }
        else
        {
            cm.setText(text);
        }
    }

}
