package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class MediaStoreHelper {

    //Maybe this class could batch successive calls together

    public static void indexFile(Context context, Uri path) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(path);
        context.sendBroadcast(mediaScanIntent);
    }

}
