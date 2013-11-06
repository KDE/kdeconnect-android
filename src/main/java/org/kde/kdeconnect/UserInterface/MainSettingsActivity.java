package org.kde.kdeconnect.UserInterface;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect_tp.R;

public class MainSettingsActivity extends PreferenceActivity{

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

    @SuppressWarnings("deprecation")
    private void addPreferencesOldApi() {
        addPreferencesFromResource(R.xml.general_preferences);
        initPreferences((EditTextPreference) findPreference(KEY_DEVICE_NAME_PREFERENCE));
    }

    private void initPreferences(final EditTextPreference deviceNamePref) {
        final SharedPreferences sharedPreferences=PreferenceManager.getDefaultSharedPreferences(this);
        deviceNamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newDeviceName) {
                if (newDeviceName.toString().isEmpty()) {
                    Toast.makeText(
                            MainSettingsActivity.this,
                            getString(R.string.invalid_device_name),
                            Toast.LENGTH_SHORT).show();
                    return false;
                }else{
                    Log.i("MainSettingsActivity", "New device name: " + newDeviceName);
                    deviceNamePref.setSummary(getString(
                            R.string.device_name_preference_summary,
                            newDeviceName.toString()));
                    return true;
                }
            }
        });
        deviceNamePref.setSummary(getString(
                R.string.device_name_preference_summary,
                sharedPreferences.getString(KEY_DEVICE_NAME_PREFERENCE,"")));
    }

    /**
     * Until now it sets only the default deviceName (if not already set).
     * It's safe to call this multiple time because doesn't override any previous value.
     * @param context
     */
    public static void initializeDeviceName(Context context){
        // I could have used getDefaultSharedPreferences(context).contains but we need to check
        // to checkAgainst empty String also.
        String deviceName=PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_DEVICE_NAME_PREFERENCE,
                "");
        if(deviceName.isEmpty()){
            Log.i("MainSettingsActivity", "New device name: " + deviceName);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                    KEY_DEVICE_NAME_PREFERENCE,
                    DeviceHelper.getDeviceName()).commit();
        }
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
