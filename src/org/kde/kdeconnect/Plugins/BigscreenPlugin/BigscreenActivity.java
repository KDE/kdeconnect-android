/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 * SPDX-FileCopyrightText: 2020 Sylvia van Os <sylvia@hackerchick.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.BigscreenPlugin;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PermissionsAlertDialogFragment;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityBigscreenBinding;

import java.util.ArrayList;
import java.util.Objects;

public class BigscreenActivity extends AppCompatActivity {

    private static final int REQUEST_SPEECH = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityBigscreenBinding binding = ActivityBigscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        final String deviceId = getIntent().getStringExtra("deviceId");

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.micButton.setEnabled(false);
            binding.micButton.setVisibility(View.INVISIBLE);
        }

        BigscreenPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, BigscreenPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }

        binding.leftButton.setOnClickListener(v -> plugin.sendLeft());
        binding.rightButton.setOnClickListener(v -> plugin.sendRight());
        binding.upButton.setOnClickListener(v -> plugin.sendUp());
        binding.downButton.setOnClickListener(v -> plugin.sendDown());
        binding.selectButton.setOnClickListener(v -> plugin.sendSelect());
        binding.homeButton.setOnClickListener(v -> plugin.sendHome());
        binding.micButton.setOnClickListener(v -> {
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                ArrayList<String> result = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result.get(0) != null) {
                    final String deviceId = getIntent().getStringExtra("deviceId");
                    BigscreenPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, BigscreenPlugin.class);
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

