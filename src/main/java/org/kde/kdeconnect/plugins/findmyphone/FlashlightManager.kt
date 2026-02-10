/*
 * SPDX-FileCopyrightText: 2025 Łukasz Żarnowiecki <lukasz@zarnowiecki.pl>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.findmyphone

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class FlashlightManager(context: Context) {

    companion object {
        private const val TAG = "FlashlightManager"
        private const val FLASH_INTERVAL_MS = 500L
    }

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val handler = Handler(Looper.getMainLooper())

    private var cameraId: String? = findFlashCameraId()

    private var isFlashing = false
    private var isFlashOn = false

    private val flashRunnable = object : Runnable {
        override fun run() {
            // This guards agains potential race condition.
            // Its probably not an issue since this is all running in the same thread,
            // but let's have it just in case.
            if (!isFlashing) {
                return
            }
            toggleFlash()
            handler.postDelayed(this, FLASH_INTERVAL_MS)
        }
    }

    private fun findFlashCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access camera", e)
            null
        }
    }

    fun startFlashing() {
        cameraId ?: run {
            Log.w(TAG, "Flashlight not available on this device")
            return
        }

        if (isFlashing) return

        isFlashing = true
        handler.post(flashRunnable)
    }

    fun stopFlashing() {
        if (!isFlashing) return

        isFlashing = false
        handler.removeCallbacks(flashRunnable)

        setFlashlight(false)
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        setFlashlight(isFlashOn)
    }

    private fun setFlashlight(enabled: Boolean) {
        try {
            cameraManager.setTorchMode(cameraId!!, enabled)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to set flashlight mode", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid camera ID or flashlight not available", e)
        }
    }
}
