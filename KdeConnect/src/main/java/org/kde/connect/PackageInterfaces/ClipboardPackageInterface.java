package org.kde.connect.PackageInterfaces;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public class ClipboardPackageInterface extends BasePackageInterface {

    ClipboardManager cm;
    boolean ignore_next_clipboard_change = false;

    public ClipboardPackageInterface(final Context ctx) {

        cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);

        cm.addPrimaryClipChangedListener(new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                try {
                    if (ignore_next_clipboard_change) {
                        ignore_next_clipboard_change = false;
                        return;
                    }
                    NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_CLIPBOARD);
                    ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                    np.set("content",item.coerceToText(ctx).toString());
                    sendPackage(np);
                } catch(Exception e) {
                    //Probably clipboard was not text
                }
            }
        });

    }

    @Override
    public void onPackageReceived(Device d, NetworkPackage np) {
        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_CLIPBOARD)) {
            ignore_next_clipboard_change = true;
            cm.setText(np.getString("content"));
        }
    }
}
