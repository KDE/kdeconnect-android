package org.kde.connect.Plugins.ClibpoardPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.Button;

import org.kde.connect.NetworkPackage;
import org.kde.connect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

public class ClipboardPlugin extends Plugin {

    private String currentContent;

    /*static {
        PluginFactory.registerPlugin(ClipboardPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_clipboard";
    }
    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_clipboard);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_clipboard_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean isEnabledByDefault() {
        return (Build.VERSION.SDK_INT >= 11);
    }

    private ClipboardManager cm;
    private ClipboardManager.OnPrimaryClipChangedListener listener = new ClipboardManager.OnPrimaryClipChangedListener() {
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

    @Override
    public boolean onCreate() {

        cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);

        if (Build.VERSION.SDK_INT < 11) {
            return false;
        }

        cm.addPrimaryClipChangedListener(listener);

        return true;

    }

    @Override
    public void onDestroy() {

        cm.removePrimaryClipChangedListener(listener);

    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_CLIPBOARD)) {
            return false;
        }

        currentContent = np.getString("content");
        cm.setText(currentContent);
        return true;

    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {
        return new AlertDialog.Builder(baseContext)
                .setTitle(R.string.pref_plugin_clipboard)
                .setMessage(R.string.plugin_not_available)
                .setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .create();
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        return null;
    }
}
