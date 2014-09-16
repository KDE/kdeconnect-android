package org.kde.kdeconnect.UserInterface;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PluginSettingsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String resource_name = getIntent().getStringExtra(Intent.EXTRA_INTENT);
        int resource_file = getResources().getIdentifier(resource_name, "xml", getPackageName());
        addPreferencesFromResource(resource_file);
    }
}