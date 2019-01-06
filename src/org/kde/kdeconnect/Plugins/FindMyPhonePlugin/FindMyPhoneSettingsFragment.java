/*
 * Copyright 2018 Erik Duisters <e.duisters1@gmail.com>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

public class FindMyPhoneSettingsFragment extends PluginSettingsFragment {
    private static final int REQUEST_CODE_SELECT_RINGTONE = 1000;

    private String preferenceKeyRingtone;
    private SharedPreferences sharedPreferences;
    private Preference ringtonePreference;

    public static FindMyPhoneSettingsFragment newInstance(@NonNull String pluginKey) {
        FindMyPhoneSettingsFragment fragment = new FindMyPhoneSettingsFragment();
        fragment.setArguments(pluginKey);

        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        preferenceKeyRingtone = getString(R.string.findmyphone_preference_key_ringtone);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        ringtonePreference = getPreferenceScreen().findPreference(preferenceKeyRingtone);

        setRingtoneSummary();
    }

    private void setRingtoneSummary() {
        String ringtone = sharedPreferences.getString(preferenceKeyRingtone, Settings.System.DEFAULT_RINGTONE_URI.toString());

        Uri ringtoneUri = Uri.parse(ringtone);

        ringtonePreference.setSummary(RingtoneManager.getRingtone(requireContext(), ringtoneUri).getTitle(requireContext()));
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        /*
         * There is no RingtonePreference in support library nor androidx, this is the workaround proposed here:
         * https://issuetracker.google.com/issues/37057453
         */

        if (preference.hasKey() && preference.getKey().equals(preferenceKeyRingtone)) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

            String existingValue = sharedPreferences.getString(preferenceKeyRingtone, null);
            if (existingValue != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue));
            } else {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_RINGTONE_URI);
            }

            startActivityForResult(intent, REQUEST_CODE_SELECT_RINGTONE);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SELECT_RINGTONE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

            if (uri != null) {
                sharedPreferences.edit()
                        .putString(preferenceKeyRingtone, uri.toString())
                        .apply();

                setRingtoneSummary();
            }
        }
    }
}
