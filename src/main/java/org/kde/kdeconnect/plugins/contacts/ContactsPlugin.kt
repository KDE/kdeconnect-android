/*
 * ContactsPlugin.java - This file is part of KDE Connect's Android App
 * Implement a way to request and send contact information
 *
 * SPDX-FileCopyrightText: 2018 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.contacts

import android.Manifest
import android.util.Log
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.helpers.ContactsHelper
import org.kde.kdeconnect.helpers.ContactsHelper.ContactNotFoundException
import org.kde.kdeconnect.helpers.ContactsHelper.VCardBuilder
import org.kde.kdeconnect.helpers.ContactsHelper.uID
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.AlertDialogFragment
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class ContactsPlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_contacts)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_contacts_desc)

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS, PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS, PACKET_TYPE_CONTACTS_RESPONSE_VCARDS)

    override val permissionExplanation: Int = R.string.contacts_permission_explanation

    override val isEnabledByDefault: Boolean = true

    // One day maybe we will also support WRITE_CONTACTS, but not yet
    override val requiredPermissions: Array<String> = arrayOf(Manifest.permission.READ_CONTACTS)

    override fun checkRequiredPermissions(): Boolean {
        if (!arePermissionsGranted(requiredPermissions)) {
            return false
        }
        return preferences!!.getBoolean("acceptedToTransferContacts", false)
    }

    override fun supportsDeviceSpecificSettings(): Boolean = true

    override val permissionExplanationDialog: DialogFragment
        get() {
            if (!arePermissionsGranted(requiredPermissions)) {
                return super.permissionExplanationDialog
            }
            return AlertDialogFragment.Builder()
                .setTitle(displayName)
                .setMessage(R.string.contacts_per_device_confirmation)
                .setPositiveButton(R.string.ok)
                .setNegativeButton(R.string.cancel)
                .create()
                .apply {
                    setCallback(object : AlertDialogFragment.Callback() {
                        override fun onPositiveButtonClicked(): Boolean {
                            preferences!!.edit { putBoolean("acceptedToTransferContacts", true) }
                            device.launchBackgroundReloadPluginsFromSettings()
                            return true
                        }
                    })
                }
        }

    /**
     * Add custom fields to the vcard to keep track of KDE Connect-specific fields
     *
     *
     * These include the local device's uID as well as last-changed timestamp
     *
     *
     * This might be extended in the future to include more fields
     *
     * @param vcard vcard to apply metadata to
     * @param uID   uID to which the vcard corresponds
     * @throws ContactNotFoundException If the given ID for some reason does not match a contact
     * @return The same VCard as was passed in, but now with KDE Connect-specific fields
     */
    @Throws(ContactNotFoundException::class)
    private fun addVCardMetadata(vcard: VCardBuilder, uID: uID): VCardBuilder {
        // Append the device ID line
        // Unclear if the deviceID forms a valid name per the vcard spec. Worry about that later..
        vcard.appendLine("X-KDECONNECT-ID-DEV-${device.deviceId}", uID.toString())

        val timestamp: Long = ContactsHelper.getContactTimestamp(context, uID)
        vcard.appendLine("REV", timestamp.toString())

        return vcard
    }

    /**
     * Return a unique identifier (Contacts.LOOKUP_KEY) for all contacts in the Contacts database
     *
     *
     * The identifiers returned can be used in future requests to get more information about the contact
     *
     * @param np The packet containing the request
     * @return true if successfully handled, false otherwise
     */
    private fun handleRequestAllUIDsTimestamps(@Suppress("unused") np: NetworkPacket): Boolean {
        val uIDsToTimestamps: Map<uID, Long> = ContactsHelper.getAllContactTimestamps(context)
        val reply = NetworkPacket(PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS).apply {
            val uIDsAsString = mutableListOf<String>()
            for ((contactID: uID, timestamp: Long) in uIDsToTimestamps) {
                set(contactID.toString(), timestamp.toString())
                uIDsAsString.add(contactID.toString())
            }
            set(PACKET_UIDS_KEY, uIDsAsString)
        }

        device.sendPacket(reply)

        return true
    }

    private fun handleRequestVCardsByUIDs(np: NetworkPacket): Boolean {
        if (PACKET_UIDS_KEY !in np) {
            Log.e("ContactsPlugin", "handleRequestNamesByUIDs received a malformed packet with no uids key")
            return false
        }

        val storedUIDs: List<uID>? = np.getStringList("uids")?.distinct()?.map { uID(it) }
        if (storedUIDs == null) {
            Log.e("ContactsPlugin", "handleRequestNamesByUIDs received a malformed packet with no uids")
            return false
        }

        val uIDsToVCards: Map<uID, VCardBuilder> = ContactsHelper.getVCardsForContactIDs(context, storedUIDs)

        val reply = NetworkPacket(PACKET_TYPE_CONTACTS_RESPONSE_VCARDS).apply {
            // ContactsHelper.getVCardsForContactIDs(..) is allowed to reply without some of the requested uIDs if they were not in the database, so update our list
            val uIDsAsStrings = mutableListOf<String>()
            for ((uID: uID, vcard: VCardBuilder) in uIDsToVCards) {
                try {
                    val vcardWithMetadata = addVCardMetadata(vcard, uID)
                    // Store this as a valid uID
                    uIDsAsStrings.add(uID.toString())
                    // Add the uid -> vcard pairing to the packet
                    set(uID.toString(), vcardWithMetadata.toString())
                } catch (e: ContactNotFoundException) {
                    Log.e("ContactsPlugin", "handleRequestVCardsByUIDs failed to find contact with uID $uID")
                }
            }
            set(PACKET_UIDS_KEY, uIDsAsStrings)
        }

        device.sendPacket(reply)

        return true
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean = when (np.type) {
        PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS -> this.handleRequestAllUIDsTimestamps(np)
        PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS -> this.handleRequestVCardsByUIDs(np)
        else -> {
            Log.e("ContactsPlugin", "Contacts plugin received an unexpected packet!")
            false
        }
    }

    companion object {
        private const val PACKET_UIDS_KEY: String = "uids"

        /**
         * Used to request the device send the unique ID of every contact
         */
        private const val PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS: String = "kdeconnect.contacts.request_all_uids_timestamps"

        /**
         * Used to request the names for the contacts corresponding to a list of UIDs
         *
         *
         * It shall contain the key "uids", which will have a list of uIDs (long int, as string)
         */
        private const val PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS: String = "kdeconnect.contacts.request_vcards_by_uid"

        /**
         * Response indicating the packet contains a list of contact uIDs
         *
         *
         * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
         * The returned IDs can be used in future requests for more information about the contact
         */
        private const val PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS: String = "kdeconnect.contacts.response_uids_timestamps"

        /**
         * Response indicating the packet contains a list of contact names
         *
         *
         * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
         * then, for each UID, there shall be a field with the key of that UID and the value of the name of the contact
         *
         *
         * For example:
         * { 'uids' : ['1', '3', '15'],
         * '1'  : 'John Smith',
         * '3'  : 'Abe Lincoln',
         * '15' : 'Mom'
         * }
         */
        private const val PACKET_TYPE_CONTACTS_RESPONSE_VCARDS: String = "kdeconnect.contacts.response_vcards"
    }
}
