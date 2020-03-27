/*
 * Copyright 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 * Copyright 2020 Sylvia van Os <sylvia@hackerchick.me>
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

package org.kde.kdeconnect.Plugins.BigscreenPlugin;

import android.os.Bundle;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import androidx.appcompat.app.AppCompatActivity;

public class BigscreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        setContentView(R.layout.activity_bigscreen);

        final String deviceId = getIntent().getStringExtra("deviceId");

        BackgroundService.RunWithPlugin(this, deviceId, org.kde.kdeconnect.Plugins.BigscreenPlugin.BigscreenPlugin.class, plugin -> runOnUiThread(() -> {
            findViewById(R.id.left_button).setOnClickListener(v -> plugin.sendLeft());
            findViewById(R.id.right_button).setOnClickListener(v -> plugin.sendRight());
            findViewById(R.id.up_button).setOnClickListener(v -> plugin.sendUp());
            findViewById(R.id.down_button).setOnClickListener(v -> plugin.sendDown());
            findViewById(R.id.select_button).setOnClickListener(v -> plugin.sendSelect());
            findViewById(R.id.home_button).setOnClickListener(v -> plugin.sendHome());
        }));
    }
}

