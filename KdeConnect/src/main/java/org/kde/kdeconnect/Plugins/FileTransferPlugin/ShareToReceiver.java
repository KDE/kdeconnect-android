package org.kde.kdeconnect.Plugins.FileTransferPlugin;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.apache.mina.util.Base64;

import java.io.InputStream;


public class ShareToReceiver extends Activity {
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String action = intent.getAction();

        // if this is from the share menu
        if (Intent.ACTION_SEND.equals(action))
        {
            if (extras.containsKey(Intent.EXTRA_STREAM))
            {
                try
                {
                    // Get resource path from intent callee
                    Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

                    // Query gallery for camera picture via
                    // Android ContentResolver interface
                    ContentResolver cr = getContentResolver();
                    InputStream inputStream = cr.openInputStream(uri);

                    //TODO: Send inputStream as payload

                    return;
                } catch (Exception e)
                {
                    Log.e(this.getClass().getName(), e.toString());
                }

            } else if (extras.containsKey(Intent.EXTRA_TEXT))
            {
                //TODO: Send text
                return;
            }
        }

    }
}
