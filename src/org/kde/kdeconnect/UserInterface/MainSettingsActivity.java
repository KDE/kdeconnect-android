/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.UserInterface;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect_tp.R;

public class MainSettingsActivity extends AppCompatPreferenceActivity {

    public static final String KEY_DEVICE_NAME_PREFERENCE = "device_name_preference";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDeviceName(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            addPreferencesOldApi();
        } else {
            getFragmentManager().beginTransaction().
                    replace(android.R.id.content, new GeneralPrefsFragment()).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //ActionBar's back button
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @SuppressWarnings("deprecation")
    private void addPreferencesOldApi() {
        addPreferencesFromResource(R.xml.general_preferences);
        initPreferences((EditTextPreference) findPreference(KEY_DEVICE_NAME_PREFERENCE));
    }

    private void initPreferences(final EditTextPreference deviceNamePref) {

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        deviceNamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newDeviceNameObject) {

                String newDeviceName = newDeviceNameObject == null ? "" : newDeviceNameObject.toString();

                if (newDeviceName.isEmpty()) {

                    Toast.makeText(
                            MainSettingsActivity.this,
                            getString(R.string.invalid_device_name),
                            Toast.LENGTH_SHORT).show();

                    return false;

                } else {

                    Log.i("MainSettingsActivity", "New device name: " + newDeviceName);
                    deviceNamePref.setSummary(newDeviceName);

                    //Broadcast the device information again since it has changed
                    BackgroundService.RunCommand(MainSettingsActivity.this, new BackgroundService.InstanceCallback() {
                        @Override
                        public void onServiceStart(BackgroundService service) {
                            service.onNetworkChange();
                        }
                    });

                    return true;
                }
            }
        });

        deviceNamePref.setSummary(sharedPreferences.getString(KEY_DEVICE_NAME_PREFERENCE,""));
    }

    /**
     * Until now it sets only the default deviceName (if not already set).
     * It's safe to call this multiple time because doesn't override any previous value.
     * @param context the application context
     */
    public static String initializeDeviceName(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        // Could use prefrences.contains but would need to check for empty String anyway.
        String deviceName = preferences.getString(KEY_DEVICE_NAME_PREFERENCE, "");
        if (deviceName.isEmpty()){
            deviceName = DeviceHelper.getDeviceName();
            Log.i("MainSettingsActivity", "New device name: " + deviceName);
            preferences.edit().putString(KEY_DEVICE_NAME_PREFERENCE, deviceName).apply();
        }
        return deviceName;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.general_preferences);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            if (getActivity() != null) {
                ((MainSettingsActivity)getActivity()).initPreferences(
                        (EditTextPreference) findPreference(KEY_DEVICE_NAME_PREFERENCE));
            }
        }
    }
}
