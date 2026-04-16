/*
 * SPDX-FileCopyrightText: 2026 GMechaSoft <gmechasoft@example.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class ClipboardAccessibilityService : AccessibilityService() {

    private val TAG = "ClipboardAccessibility"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SISTEMA: ClipboardAccessibilityService conectado y activo")

        // Sincronizamos al activar el servicio (útil para el botón flotante de accesibilidad)
        triggerClipboardSync()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Metodo requerido por la clase base AccessibilityService.
        // Dejamos el cuerpo vacio ya que no queremos reaccionar a eventos automaticos.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    /**
     * Este método se llama cuando el servicio se desvincula del sistema.
     * Útil para realizar una última sincronización antes de que el servicio quede inactivo.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        Log.i(TAG, "SISTEMA: ClipboardAccessibilityService desconectándose. Sincronización final...")
        triggerClipboardSync()
        return super.onUnbind(intent)
    }

    /**
     * This method can be called by the system or used as a target for
     * accessibility shortcuts.
     */
    fun triggerClipboardSync() {
        Log.d(TAG, "Triggering clipboard sync via accessibility service")
        val intent = ClipboardFloatingActivity.getIntent(this, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
