/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SafeTextChecker;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.List.EntryItemWithIcon;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivitySendkeystrokesBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SendKeystrokesToHostActivity extends AppCompatActivity {

    // text with these length and content can be send without user confirmation.
    // more or less chosen arbitrarily, so that we allow short PINS and TANS without interruption (if only one device is connected)
    // but also be on the safe side, so that apps cant send any harmful content
    public static final int MAX_SAFE_LENGTH = 8;
    public static final String SAFE_CHARS = "1234567890";


    private ActivitySendkeystrokesBinding binding;
    private boolean contentIsOkay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySendkeystrokesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1) // needed for this.getReferrer()
    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(getString(R.string.pref_sendkeystrokes_enabled), true)) {
            Toast.makeText(getApplicationContext(), R.string.sendkeystrokes_disabled_toast, Toast.LENGTH_LONG).show();
            finish();
        } else {
            final Intent intent = getIntent();
            String type = intent.getType();

            if ("text/x-keystrokes".equals(type)) {
                String toSend = intent.getStringExtra(Intent.EXTRA_TEXT);
                binding.textToSend.setText(toSend);

                // if the preference send_safe_text_immediately is true, we will check if exactly one
                // device is connected and send the text to it without user confirmation, to make sending of
                // short and safe text like PINs/TANs very fluent
                //
                // (contentIsOkay gets used in updateDeviceList again)
                if (prefs.getBoolean(getString(R.string.pref_send_safe_text_immediately), true)) {
                    SafeTextChecker safeTextChecker = new SafeTextChecker(SAFE_CHARS, MAX_SAFE_LENGTH);
                    contentIsOkay = safeTextChecker.isSafe(toSend);
                } else {
                    contentIsOkay = false;
                }

                // If we trust the sending app, check if there is only one device paired / reachable...
                if (contentIsOkay) {
                    List<Device> reachableDevices = KdeConnect.getInstance().getDevices().values().stream()
                            .filter(Device::isReachable)
                            .limit(2)  // we only need the first two; if its more than one, we need to show the user the device-selection
                            .collect(Collectors.toList());

                    // if its exactly one just send the text to it
                    if (reachableDevices.size() == 1) {
                        // send the text and close this activity
                        sendKeys(reachableDevices.get(0));
                        this.finish();
                        return;
                    }
                }

                KdeConnect.getInstance().addDeviceListChangedCallback("SendKeystrokesToHostActivity", () -> runOnUiThread(this::updateDeviceList));
                BackgroundService.ForceRefreshConnections(this); // force a network re-discover
                updateDeviceList();
            } else {
                Toast.makeText(getApplicationContext(), R.string.sendkeystrokes_wrong_data, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("SendKeystrokesToHostActivity");
        super.onStop();
    }

    private void sendKeys(Device deviceId) {
        String toSend;
        if (binding.textToSend.getText() != null && (toSend = binding.textToSend.getText().toString().trim()).length() > 0) {
            final NetworkPacket np = new NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST);
            np.set("key", toSend);
            MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId.getDeviceId(), MousePadPlugin.class);
            if (plugin == null) {
                finish();
                return;
            }
            plugin.sendKeyboardPacket(np);
            Toast.makeText(
                    getApplicationContext(),
                    getString(R.string.sendkeystrokes_sent_text, toSend, deviceId.getName()),
                    Toast.LENGTH_SHORT
            ).show();

        }
    }


    private void updateDeviceList() {
        Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
        final ArrayList<Device> devicesList = new ArrayList<>();
        final ArrayList<ListAdapter.Item> items = new ArrayList<>();

        SectionItem section = new SectionItem(getString(R.string.sendkeystrokes_send_to));
        items.add(section);

        for (Device d : devices) {
            if (d.isReachable() && d.isPaired()) {
                devicesList.add(d);
                items.add(new EntryItemWithIcon(d.getName(), d.getIcon()));
                section.isEmpty = false;
            }
        }

        binding.devicesList.setAdapter(new ListAdapter(SendKeystrokesToHostActivity.this, items));
        binding.devicesList.setOnItemClickListener((adapterView, view, i, l) -> {
            Device device = devicesList.get(i - 1); // NOTE: -1 because of the title!
            sendKeys(device);
            this.finish(); // close the activity
        });

        // only one device is connected and we trust the text to send -> send it and close the activity.
        // Usually we already check it in `onStart` - but if the BackgroundService was not started/connected to the host
        // it will not have the deviceList in memory. Use this callback as second chance (but it will flicker a bit, because the activity might
        // already been visible and get closed again quickly)
        if (devicesList.size() == 1 && contentIsOkay) {
            Device device = devicesList.get(0);
            sendKeys(device);
            this.finish(); // close the activity
        }
    }
}

