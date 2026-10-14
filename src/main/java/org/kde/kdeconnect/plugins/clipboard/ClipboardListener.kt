/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.Html
import androidx.core.content.getSystemService

class ClipboardListener {
    enum class ClipboardContentType {
        Text,
        Password,
    }

    interface ClipboardObserver {
        fun clipboardChanged(content: String, contentType: ClipboardContentType, plainText: Boolean)
    }

    private val observers: HashSet<ClipboardObserver> = HashSet()

    private val context: Context
    var currentContent: String? = null
        private set
    var currentContentType: ClipboardContentType = ClipboardContentType.Text
        private set
    var updateTimestamp: Long = 0
        private set

    private lateinit var cm: ClipboardManager

    private constructor(ctx: Context) {
        context = ctx.applicationContext
        Handler(Looper.getMainLooper()).post {
            cm = context.getSystemService<ClipboardManager>()!!
            cm.addPrimaryClipChangedListener { this.onClipboardChanged() }
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
            val clip = cm.primaryClip!!
            val plainText = clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            val htmlText = clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            if (!plainText && !htmlText) return
            val item = clip.getItemAt(0)
            val content = item.text?.toString()
                ?: item.htmlText?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                ?: return
            val contentType = detectContentType(clip)

            if (content == currentContent && contentType == currentContentType) {
                return
            }
            updateTimestamp = System.currentTimeMillis()
            currentContent = content
            currentContentType = contentType

            for (observer in observers) {
                observer.clipboardChanged(content, contentType, plainText)
            }
        } catch (_: Exception) {
            //Probably clipboard was not text
        }
    }

    fun setText(text: String?) {
        setText(text, remoteDevice = false)
    }

    fun setText(text: String?, remoteDevice: Boolean) {
        updateTimestamp = System.currentTimeMillis()
        currentContent = text
        currentContentType = ClipboardContentType.Text
        val applyClip = Runnable {
            if (this::cm.isInitialized) {
                val clip = ClipData.newPlainText(null, text)
                if (remoteDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_REMOTE_DEVICE, true)
                    }
                }
                cm.setPrimaryClip(clip)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) applyClip.run()
        else Handler(Looper.getMainLooper()).post(applyClip)
    }

    companion object {
        private var _instance: ClipboardListener? = null

        fun instance(context: Context): ClipboardListener {
            // FIXME: The _instance we return won't be completely initialized yet since initialization happens on a new thread (why?)
            return _instance ?: ClipboardListener(context).also { _instance = it }
        }

        fun detectContentType(clip: ClipData?): ClipboardContentType {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return ClipboardContentType.Text
            }
            if (clip?.description?.extras
                    ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            ) {
                return ClipboardContentType.Password
            }
            return ClipboardContentType.Text
        }
    }
}
