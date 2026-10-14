/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.text.Html
import android.util.Log

/** Runs as Shizuku's shell user so Android permits background clipboard reads. */
class EcosystemClipboardUserService() : IClipboardWatcher.Stub() {
    @Volatile private var callback: IClipboardSyncCallback? = null
    private var serviceContext: Context? = null
    private var clipboardManager: ClipboardManager? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    constructor(context: Context) : this() {
        serviceContext = context
    }

    override fun setCallback(callback: IClipboardSyncCallback?) {
        this.callback = callback
    }

    override fun start(): Boolean {
        if (listener != null) return true
        try {
            val context = serviceContext
                ?: throw IllegalStateException("Shizuku v13 or later is required for the user-service context")
            // Shizuku runs this process as shell (uid 2000), but its injected Context uses the
            // Ecosystem package name. Android 16 validates that package against the caller UID
            // when dispatching clipboard listeners, so attribute this manager to the shell package.
            val shellContext = object : ContextWrapper(context) {
                override fun getOpPackageName(): String = SHELL_PACKAGE
                override fun getPackageName(): String = SHELL_PACKAGE
            }
            val constructor = ClipboardManager::class.java.getDeclaredConstructor(Context::class.java, Handler::class.java)
            constructor.isAccessible = true
            val manager = constructor.newInstance(shellContext, Handler(Looper.getMainLooper())) as ClipboardManager
            val newListener = ClipboardManager.OnPrimaryClipChangedListener { reportCurrentClip(manager) }
            clipboardManager = manager
            listener = newListener
            manager.addPrimaryClipChangedListener(newListener)
            Log.i(TAG, "Clipboard watcher started as Shizuku user service")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start clipboard watcher", e)
            listener = null
            clipboardManager = null
            return false
        }
    }

    override fun stop() {
        val oldManager = clipboardManager
        val oldListener = listener
        listener = null
        clipboardManager = null
        callback = null
        if (oldManager != null && oldListener != null) {
            runCatching { oldManager.removePrimaryClipChangedListener(oldListener) }
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // Shizuku sends this reserved transaction when removing a UserService.
        if (code == 16777114) {
            stop()
            android.os.Process.killProcess(android.os.Process.myPid())
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun reportCurrentClip(manager: ClipboardManager) {
        try {
            val clip = manager.primaryClip ?: return
            val description = clip.description
            val isPlainText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            val isHtmlText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            if (!isPlainText && !isHtmlText) return
            val item = clip.getItemAt(0)
            val text = item.text?.toString()
                ?: item.htmlText?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                ?: return
            val sensitive = description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            val remoteDevice = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                description.extras?.getBoolean(ClipDescription.EXTRA_IS_REMOTE_DEVICE, false) == true
            callback?.onClipboardChanged(text, sensitive, isPlainText, remoteDevice)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read clipboard change", e)
        }
    }

    companion object {
        private const val TAG = "EcosystemClipboard"
        private const val SHELL_PACKAGE = "com.android.shell"
    }
}
