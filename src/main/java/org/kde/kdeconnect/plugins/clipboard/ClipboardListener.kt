/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.clipboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import org.kde.kdeconnect_tp.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardListener {
    interface ClipboardObserver {
        fun clipboardChanged(content: String)
    }

    private val observers: HashSet<ClipboardObserver> = HashSet()

    private val context: Context
    var currentContent: String? = null
        private set
    var updateTimestamp: Long = 0
        private set

    private lateinit var cm: ClipboardManager

    private constructor(ctx: Context) {
        context = ctx.applicationContext
        Handler(Looper.getMainLooper()).post {
            cm = ContextCompat.getSystemService<ClipboardManager>(context, ClipboardManager::class.java)!!
            cm.addPrimaryClipChangedListener { this.onClipboardChanged() }
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED) {
            execute {
                try {
                    val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                    // Listen only ClipboardService errors after now
                    val logcatFilter = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) { "E ClipboardService" } else { "ClipboardService:E" }
                    val process = Runtime.getRuntime().exec(arrayOf<String>("logcat", "-T", timeStamp, logcatFilter, "*:S"))
                    val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                    bufferedReader.forEachLine { line ->
                        if (line.contains(BuildConfig.APPLICATION_ID)) {
                            context.startActivity(ClipboardFloatingActivity.getIntent(context, false))
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun registerObserver(observer: ClipboardObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: ClipboardObserver) {
        observers.remove(observer)
    }

    fun onClipboardChanged() {
        try {
            val item = cm.primaryClip!!.getItemAt(0)
            val content = item.coerceToText(context).toString()

            if (content == currentContent) {
                return
            }
            updateTimestamp = System.currentTimeMillis()
            currentContent = content

            for (observer in observers) {
                observer.clipboardChanged(content)
            }
        } catch (_: Exception) {
            //Probably clipboard was not text
        }
    }

    @Suppress("deprecation")
    fun setText(text: String?) {
        if (this::cm.isInitialized) {
            updateTimestamp = System.currentTimeMillis()
            currentContent = text
            cm.text = text
        }
    }

    companion object {
        private var _instance: ClipboardListener? = null

        @JvmStatic
        fun instance(context: Context): ClipboardListener {
            // FIXME: The _instance we return won't be completely initialized yet since initialization happens on a new thread (why?)
            return _instance ?: ClipboardListener(context).also { _instance = it }
        }
    }
}
