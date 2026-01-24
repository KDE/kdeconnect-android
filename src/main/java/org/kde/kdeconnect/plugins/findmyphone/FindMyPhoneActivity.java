/* SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.findmyphone;

import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.databinding.ActivityFindMyPhoneBinding;

import java.util.Objects;

import kotlin.Lazy;
import kotlin.LazyKt;

public class FindMyPhoneActivity extends BaseActivity<ActivityFindMyPhoneBinding> {
    static final String EXTRA_DEVICE_ID = "deviceId";

    String deviceId;

    private final Lazy<ActivityFindMyPhoneBinding> lazyBinding = LazyKt.lazy(() -> ActivityFindMyPhoneBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityFindMyPhoneBinding getBinding() {
        return lazyBinding.getValue();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (!getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            Log.e("FindMyPhoneActivity", "You must include the deviceId for which this activity is started as an intent EXTRA");
            finish();
        }

        deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);

        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        getBinding().bFindMyPhone.setOnClickListener(view -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FindMyPhonePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, FindMyPhonePlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.startPlaying();
        plugin.hideNotification();
    }

    @Override
    protected void onStop() {
        super.onStop();
        FindMyPhonePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, FindMyPhonePlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.stopPlaying();
    }
}
