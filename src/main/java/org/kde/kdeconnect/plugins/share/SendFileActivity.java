/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.helpers.ThreadHelper;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;


public class SendFileActivity extends AppCompatActivity {

    private String mDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDeviceId = getIntent().getStringExtra("deviceId");

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
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

                    ClipData clipdata = data.getClipData();
                    if (clipdata != null) {
                        for (int i = 0; i < clipdata.getItemCount(); i++) {
                            uris.add(clipdata.getItemAt(i).getUri());
                        }
                    }

                    if (uris.isEmpty()) {
                        Log.w("SendFileActivity", "No files to send?");
                    } else {
                        ThreadHelper.execute(() -> {
                            SharePlugin plugin = KdeConnect.getInstance().getDevicePlugin(mDeviceId, SharePlugin.class);
                            if (plugin == null) {
                                finish();
                                return;
                            }
                            plugin.sendUriList(uris);
                        });
                    }
                }
                finish();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

}
