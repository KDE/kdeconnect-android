/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;


public class SendFileActivity extends AppCompatActivity {

    private String mDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        mDeviceId = getIntent().getStringExtra("deviceId");

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.send_files)), Activity.RESULT_FIRST_USER);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.no_file_browser, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Activity.RESULT_FIRST_USER:
                if (resultCode == RESULT_OK) {

                    final ArrayList<Uri> uris = new ArrayList<>();

                    Uri uri = data.getData();
                    if (uri != null) {
                        uris.add(uri);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        ClipData clipdata = data.getClipData();
                        if (clipdata != null) {
                            for (int i = 0; i < clipdata.getItemCount(); i++) {
                                uris.add(clipdata.getItemAt(i).getUri());
                            }
                        }
                    }

                    if (uris.isEmpty()) {
                        Log.w("SendFileActivity", "No files to send?");
                    } else {
                        BackgroundService.RunWithPlugin(this, mDeviceId, SharePlugin.class, plugin -> plugin.sendUriList(uris));
                    }
                }
                finish();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

}
