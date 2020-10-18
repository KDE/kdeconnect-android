/*
 * SPDX-FileCopyrightText: 2020 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.PowerPlugin;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.databinding.ActivityPowerBinding;

public class PowerActivity extends AppCompatActivity {
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        ActivityPowerBinding binding = ActivityPowerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        deviceId = getIntent().getStringExtra("deviceId");

        binding.shutdown.setOnClickListener(
            v -> BackgroundService.RunWithPlugin(PowerActivity.this, deviceId, PowerPlugin.class, plugin ->
                {
                    plugin.sendCommand("shutdown");
                }
            )
        );

        binding.suspend.setOnClickListener(
            v -> BackgroundService.RunWithPlugin(PowerActivity.this, deviceId, PowerPlugin.class, plugin ->
                {
                    plugin.sendCommand("suspend");
                }
            )
        );

        binding.lock.setOnClickListener(
            v -> BackgroundService.RunWithPlugin(PowerActivity.this, deviceId, PowerPlugin.class, plugin ->
                {
                    plugin.sendCommand("lock");
                }
            )
        );
    }
}
