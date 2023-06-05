/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect_tp.R;

public class RunCommandUrlActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getAction() != null) {
            try {
                Uri uri = getIntent().getData();
                String deviceId = uri.getPathSegments().get(0);

                final Device device = KdeConnect.getInstance().getDevice(deviceId);

                if(device == null) {
                    error(R.string.runcommand_nosuchdevice);
                    return;
                }

                if (!device.isPaired()) {
                    error(R.string.runcommand_notpaired);
                    return;
                }

                if (!device.isReachable()) {
                    error(R.string.runcommand_notreachable);
                    return;
                }

                final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
                if (plugin == null) {
                    error(R.string.runcommand_noruncommandplugin);
                    return;
                }

                plugin.runCommand(uri.getPathSegments().get(1));
                RunCommandUrlActivity.this.finish();

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    Vibrator vibrator = getSystemService(Vibrator.class);
                    if(vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(100);
                    }
                }
            } catch (Exception e) {
                Log.e("RuncommandPlugin", "Exception", e);
            }
        }
    }

    private void error(int message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setGravity(Gravity.CENTER);
        setContentView(view);
    }

}
