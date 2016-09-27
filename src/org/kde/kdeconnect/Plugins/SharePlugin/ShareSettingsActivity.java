package org.kde.kdeconnect.Plugins.SharePlugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.provider.DocumentFile;
import android.view.View;
import android.widget.Toast;
import android.preference.PreferenceManager;

import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.util.Locale;

import static android.R.attr.id;


public class ShareSettingsActivity extends PluginSettingsActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //addPreferencesFromResource(R.xml.fw_preferences); //deprecated
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }


    private static final int READ_REQUEST_CODE = 42;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            // Sorry, only KitKat and up!
            return;
        }
        if (key.equals("share_destination_folder_preference"))
            if (sharedPreferences.getBoolean("share_destination_folder_preference", false)) {
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                // browser.
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                //intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Filter to show only images, using the image MIME data type.
                // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
                // To search for all documents available via installed storage providers,
                // it would be "*/*".
                //intent.setType("*/*");

                startActivityForResult(intent, READ_REQUEST_CODE);
            } else {
                prefs.edit().remove("share_destination_folder_preference").apply();
            }
      //  }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();


                // Check for the freshest data.
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                DocumentFile df = DocumentFile.fromTreeUri(getApplicationContext(),uri);
                df.listFiles();

                prefs.edit().putString("share_destination_folder_uri", uri.toString()).apply();
                prefs.contains("share_destination_folder_uri");


                //owImage(uri);
            }
        }
    }

}
