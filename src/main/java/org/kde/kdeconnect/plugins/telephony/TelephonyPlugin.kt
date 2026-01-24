/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.telephony

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.preference.PreferenceManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.helpers.ContactsHelper
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect.ui.PluginSettingsFragment.Companion.newInstance
import org.kde.kdeconnect_tp.R
import java.util.Timer
import java.util.TimerTask

@LoadablePlugin
class TelephonyPlugin : Plugin() {
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var lastPacket: NetworkPacket? = null
    private var isMuted = false

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            //Log.e("TelephonyPlugin", "Telephony event: $action")
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED == intent.action) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val intState = when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                    else -> TelephonyManager.CALL_STATE_IDLE
                }

                // We will get a second broadcast with the phone number https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED
                if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                if (intState != lastState) {
                    lastState = intState
                    callBroadcastReceived(intState, number)
                }
            }
        }
    }

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_telephony)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_telephony_desc)

    private fun callBroadcastReceived(state: Int, phoneNumber: String?) {
        if (isNumberBlocked(phoneNumber)) return

        val np = NetworkPacket(PACKET_TYPE_TELEPHONY)

        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            val contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber)

            val name = contactInfo["name"]
            if (name != null) {
                np["contactName"] = name
            }

            if (contactInfo.containsKey("photoID")) {
                val photoUri = contactInfo["photoID"]
                if (photoUri != null) {
                    try {
                        val base64photo = ContactsHelper.photoId64Encoded(context, photoUri)
                        if (!base64photo.isNullOrEmpty()) {
                            np["phoneThumbnail"] = base64photo
                        }
                    } catch (e: Exception) {
                        Log.e("TelephonyPlugin", "Failed to get contact photo")
                    }
                }
            }
        } else if (phoneNumber != null) {
            np["contactName"] = phoneNumber
        }

        if (phoneNumber != null) {
            np["phoneNumber"] = phoneNumber
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                unmuteRinger()
                np["event"] = "ringing"
                device.sendPacket(np)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                np["event"] = "talking"
                device.sendPacket(np)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val lastPacket = lastPacket ?: return
                // Resend a cancel of the last event (can either be "ringing" or "talking")
                lastPacket["isCancel"] = "true"
                device.sendPacket(lastPacket)

                if (isMuted) {
                    val timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            unmuteRinger()
                        }
                    }, 500)
                }

                // Emit a missed call notification if needed
                if ("ringing" == lastPacket.getString("event")) {
                    np["event"] = "missedCall"
                    val phoneNumber = lastPacket.getStringOrNull("phoneNumber")
                    if (phoneNumber != null) {
                        np["phoneNumber"] = phoneNumber
                    }
                    val contactName = lastPacket.getStringOrNull("contactName")
                    if (contactName != null) {
                        np["contactName"] = contactName
                    }
                    device.sendPacket(np)
                }
            }
        }

        lastPacket = np
    }

    private fun unmuteRinger() {
        if (isMuted) {
            val am = ContextCompat.getSystemService(context, AudioManager::class.java) ?: return
            am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            isMuted = false
        }
    }

    private fun muteRinger() {
        if (!isMuted) {
            val am = ContextCompat.getSystemService(context, AudioManager::class.java) ?: return
            am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            isMuted = true
        }
    }

    override val permissionExplanation: Int = R.string.telephony_permission_explanation

    override val optionalPermissionExplanation: Int = R.string.telephony_optional_permission_explanation

    override fun onCreate(): Boolean {
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        filter.priority = 500
        context.registerReceiver(receiver, filter)
        return true
    }

    override fun onDestroy() {
        context.unregisterReceiver(receiver)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        when (np.type) {
            PACKET_TYPE_TELEPHONY_REQUEST_MUTE -> muteRinger()
        }
        return true
    }

    private fun isNumberBlocked(number: String?): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val blockedNumbers: List<String> = sharedPref.getString(KEY_PREF_BLOCKED_NUMBERS, "")!!.split("\n").dropLastWhile { it.isEmpty() }

        return blockedNumbers.any { s -> PhoneNumberUtils.compare(number, s) }
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_TELEPHONY_REQUEST_MUTE)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_TELEPHONY)

    override val requiredPermissions: Array<String> = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)

    override val optionalPermissions: Array<String> = arrayOf(Manifest.permission.READ_CONTACTS)

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment = newInstance(pluginKey, R.xml.telephonyplugin_preferences)

    companion object {
        /**
         * Packet used for simple call events
         *
         * It contains the key "event" which maps to a string indicating the type of event:
         * - "ringing" - A phone call is incoming
         * - "missedCall" - An incoming call was not answered
         * - "sms" - An incoming SMS message
         * - Note: As of this writing (15 May 2018) the SMS interface is being improved and this type of event
         * is no longer the preferred way of handling SMS. Use the packets defined by the SMS plugin instead.
         *
         * Depending on the event, other fields may be defined
         */
        const val PACKET_TYPE_TELEPHONY: String = "kdeconnect.telephony"

        /**
         * Packet sent to indicate the user has requested the device mute its ringer
         *
         * The body should be empty
         */
        private const val PACKET_TYPE_TELEPHONY_REQUEST_MUTE = "kdeconnect.telephony.request_mute"

        private const val KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers"
    }
}
