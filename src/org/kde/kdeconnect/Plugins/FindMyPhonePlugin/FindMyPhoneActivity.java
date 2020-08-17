/* SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;

import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.databinding.ActivityFindMyPhoneBinding;

public class FindMyPhoneActivity extends AppCompatActivity {
    static final String EXTRA_DEVICE_ID = "deviceId";

    private FindMyPhonePlugin plugin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        final ActivityFindMyPhoneBinding binding = ActivityFindMyPhoneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            Log.e("FindMyPhoneActivity", "You must include the deviceId for which this activity is started as an intent EXTRA");
            finish();
        }

        String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        plugin = BackgroundService.getInstance().getDevice(deviceId).getPlugin(FindMyPhonePlugin.class);

        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        binding.bFindMyPhone.setOnClickListener(view -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        /*
           For whatever reason when Android launches this activity as a SystemAlertWindow it calls:
           onCreate(), onStart(), onResume(), onStop(), onStart(), onResume().
           When using BackgroundService.RunWithPlugin we get into concurrency problems and sometimes no sound will be played
        */
        plugin.startPlaying();
        plugin.hideNotification();
    }

    @Override
    protected void onStop() {
        super.onStop();

        plugin.stopPlaying();
    }
}
