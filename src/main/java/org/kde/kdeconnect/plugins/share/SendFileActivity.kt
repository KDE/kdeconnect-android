/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect_tp.R
import java.util.ArrayList

class SendFileActivity : AppCompatActivity() {

    private lateinit var mDeviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID)!!

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.send_files)),
                Activity.RESULT_FIRST_USER,
            )
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_file_browser, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Activity.RESULT_FIRST_USER -> {
                if (resultCode == RESULT_OK && data != null) {
                    val uris = ArrayList<Uri>()

                    val uri = data.data
                    if (uri != null) {
                        uris.add(uri)
                    }

                    val clipdata = data.clipData
                    if (clipdata != null) {
                        for (i in 0 until clipdata.itemCount) {
                            uris.add(clipdata.getItemAt(i).uri)
                        }
                    }

                    if (uris.isEmpty()) {
                        Log.w("SendFileActivity", "No files to send?")
                        finish()
                    } else {
                        ThreadHelper.execute {
                            try {
                                val plugin = KdeConnect.getInstance().getDevicePlugin(mDeviceId, SharePlugin::class.java)
                                plugin?.sendFiles(uris)
                            } finally {
                                // The read permissions ACTION_GET_CONTENT grants for these URIs are tied to
                                // this activity's lifetime, so we can't finish() until sendFiles() has opened
                                // all of them -- otherwise any URI not yet opened throws a SecurityException
                                // ("... requires that you obtain access using ACTION_OPEN_DOCUMENT").
                                runOnUiThread(this::finish)
                            }
                        }
                    }
                } else {
                    finish()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
    }
}
