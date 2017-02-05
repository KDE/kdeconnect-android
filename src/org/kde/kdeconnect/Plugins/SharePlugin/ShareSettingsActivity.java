package org.kde.kdeconnect.Plugins.SharePlugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;

import java.io.File;

public class ShareSettingsActivity extends PluginSettingsActivity {

    private final static String PREFERENCE_CUSTOMIZE_DESTINATION = "share_destination_custom";
    private final static String PREFERENCE_DESTINATION = "share_destination_folder_uri";

    private static final int RESULT_PICKER = Activity.RESULT_FIRST_USER;

    private Preference filePicker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final CheckBoxPreference customDownloads = (CheckBoxPreference) findPreference("share_destination_custom");
        filePicker = findPreference("share_destination_folder_preference");

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            customDownloads.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateFilePickerStatus((Boolean) newValue);
                    return true;
                }
            });
            filePicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, RESULT_PICKER);
                    return true;
                }
            });
        } else {
            customDownloads.setEnabled(false);
            filePicker.setEnabled(false);
        }

        boolean customized = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false);
        updateFilePickerStatus(customized);
    }

    void updateFilePickerStatus(boolean enabled) {
        filePicker.setEnabled(enabled);
        String path = PreferenceManager.getDefaultSharedPreferences(this).getString(PREFERENCE_DESTINATION, null);
        if (enabled && path != null) {
            filePicker.setSummary(Uri.parse(path).getPath());
        } else {
            filePicker.setSummary(getDefaultDestinationDirectory().getAbsolutePath());
        }
    }

    public static File getDefaultDestinationDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static boolean isCustomDestinationEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false);
    }

    //Will return the appropriate directory, whether it is customized or not
    public static DocumentFile getDestinationDirectory(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false)) {
            String path = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFERENCE_DESTINATION, null);
            if (path != null) {
                //There should be no way to enter here on api level < kitkat
                DocumentFile treeDocumentFile = DocumentFile.fromTreeUri(context, Uri.parse(path));
                if (treeDocumentFile.canWrite()) { //Checks for FLAG_DIR_SUPPORTS_CREATE on directories
                    return treeDocumentFile;
                } else {
                    //Maybe permission was revoked
                    Log.w("SharePlugin", "Share destination is not writable, falling back to default path.");
                }
            }
        }
        return DocumentFile.fromFile(getDefaultDestinationDirectory());
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {

        if (requestCode == RESULT_PICKER
                && resultCode == Activity.RESULT_OK
                && resultData != null) {

            Uri uri = resultData.getData();

            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            Preference filePicker = findPreference("share_destination_folder_preference");
            filePicker.setSummary(uri.getPath());

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(PREFERENCE_DESTINATION, uri.toString()).apply();
        }
    }

}
