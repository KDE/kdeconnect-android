/*
 * SPDX-FileCopyrightText: 2020 Anjani Kumar <anjanik012@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.ClibpoardPlugin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

/*
    An activity to access the clipboard on Android 10 and later by raising over other apps.
    This is invisible and doesn't require any interaction from the user.
    This should be called when a change in clipboard is detected. This can be done by manually
    when user wants to send the clipboard or by reading system log files which requires a special
    privileged permission android.permission.READ_LOGS.
    https://developer.android.com/reference/android/Manifest.permission#READ_LOGS
    This permission can be gained by only from the adb by the user.
    https://www.reddit.com/r/AndroidBusters/comments/fh60lt/how_to_solve_a_problem_with_the_clipboard_on/

    Currently this activity is bering triggered from a button in Foreground Notification.
* */
public class ClipboardFloatingActivity extends AppCompatActivity {

    private ArrayList<Device> connectedDevices = new ArrayList<>();

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // We are now sure that clipboard can be accessed from here.
            ClipboardManager clipboardManager = ContextCompat.getSystemService(this,
                    ClipboardManager.class);
            ClipData.Item item;
            if (clipboardManager.hasPrimaryClip()) {
                item = clipboardManager.getPrimaryClip().getItemAt(0);
                String content = item.coerceToText(this).toString();
                for (Device device : connectedDevices) {
                    ClipboardPlugin clipboardPlugin = (ClipboardPlugin) device.getPlugin("ClipboardPlugin");
                    if (clipboardPlugin != null) {
                        clipboardPlugin.propagateClipboard(content);
                    }
                }
                Toast.makeText(this, R.string.pref_plugin_clipboard_sent, Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clipboard_floating);
        WindowManager.LayoutParams wlp = getWindow().getAttributes();
        wlp.dimAmount = 0;
        wlp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        getWindow().setAttributes(wlp);
        ArrayList<String> connectedDeviceIds = getIntent().getStringArrayListExtra("connectedDeviceIds");
        if (connectedDeviceIds != null) {
            for (String deviceId : connectedDeviceIds) {
                connectedDevices.add(BackgroundService.getInstance().getDevice(deviceId));
            }
        }
    }
}

