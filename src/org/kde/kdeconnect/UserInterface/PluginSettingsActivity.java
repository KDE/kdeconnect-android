/*
 * Copyright 2014 Ronny Yabar Aizcorbe <ronnycontacto@gmail.com>
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

import android.os.Bundle;
import android.view.MenuItem;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect_tp.R;

import java.util.Locale;

public class PluginSettingsActivity extends AppCompatPreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String pluginDisplayName = getIntent().getStringExtra("plugin_display_name");
        setTitle(getString(R.string.plugin_settings_with_name, pluginDisplayName));

        String pluginKey = getIntent().getStringExtra("plugin_key");
        int resFile = getResources().getIdentifier(pluginKey.toLowerCase(Locale.ENGLISH) + "_preferences", "xml", getPackageName());
        addPreferencesFromResource(resFile);
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

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackgroundService.removeGuiInUseCounter(this);
    }

}
