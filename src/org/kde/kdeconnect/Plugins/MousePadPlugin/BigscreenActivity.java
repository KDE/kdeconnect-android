/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 * SPDX-FileCopyrightText: 2020 Sylvia van Os <sylvia@hackerchick.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PermissionsAlertDialogFragment;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityBigscreenBinding;

import java.util.ArrayList;
import java.util.Objects;

import kotlin.Lazy;
import kotlin.LazyKt;

public class BigscreenActivity extends BaseActivity<ActivityBigscreenBinding> {

    private static final int REQUEST_SPEECH = 100;

    private final Lazy<ActivityBigscreenBinding> lazyBinding = LazyKt.lazy(() -> ActivityBigscreenBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityBigscreenBinding getBinding() {
        return lazyBinding.getValue();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        final String deviceId = getIntent().getStringExtra("deviceId");

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            getBinding().micButton.setEnabled(false);
            getBinding().micButton.setVisibility(View.INVISIBLE);
        }

        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }

        getBinding().leftButton.setOnClickListener(v -> plugin.sendLeft());
        getBinding().rightButton.setOnClickListener(v -> plugin.sendRight());
        getBinding().upButton.setOnClickListener(v -> plugin.sendUp());
        getBinding().downButton.setOnClickListener(v -> plugin.sendDown());
        getBinding().selectButton.setOnClickListener(v -> plugin.sendSelect());
        getBinding().homeButton.setOnClickListener(v -> plugin.sendHome());
        getBinding().micButton.setOnClickListener(v -> {
            if (plugin.hasMicPermission()) {
                activateSTT();
            } else {
                new PermissionsAlertDialogFragment.Builder()
                        .setTitle(plugin.getDisplayName())
                        .setMessage(R.string.bigscreen_optional_permission_explanation)
                        .setPermissions(new String[]{Manifest.permission.RECORD_AUDIO})
                        .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                        .create().show(getSupportFragmentManager(), null);
            }
        });
    }

    public void activateSTT() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.bigscreen_speech_extra_prompt);
        startActivityForResult(intent, REQUEST_SPEECH);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_bigscreen, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_use_mouse_and_keyboard) {
            Intent intent = new Intent(this, MousePadActivity.class);
            intent.putExtra("deviceId", getIntent().getStringExtra("deviceId"));
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                ArrayList<String> result = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result.get(0) != null) {
                    final String deviceId = getIntent().getStringExtra("deviceId");
                    MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
                    if (plugin == null) {
                        finish();
                        return;
                    }
                    plugin.sendSTT(result.get(0));
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}

