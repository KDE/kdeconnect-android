/*
 * ContactsPlugin.java - This file is part of KDE Connect's Android App
 * Implement a way to request and send contact information
 *
 * SPDX-FileCopyrightText: 2018 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.ContactsPlugin;

import android.Manifest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.Helpers.ContactsHelper.ContactNotFoundException;
import org.kde.kdeconnect.Helpers.ContactsHelper.VCardBuilder;
import org.kde.kdeconnect.Helpers.ContactsHelper.uID;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.AlertDialogFragment;
import org.kde.kdeconnect_tp.R;

import java.util.*;

@PluginFactory.LoadablePlugin
public class ContactsPlugin extends Plugin {

    /**
     * Used to request the device send the unique ID of every contact
     */
    private static final String PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS = "kdeconnect.contacts.request_all_uids_timestamps";

    /**
     * Used to request the names for the contacts corresponding to a list of UIDs
     * <p>
     * It shall contain the key "uids", which will have a list of uIDs (long int, as string)
     */
    private static final String PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS = "kdeconnect.contacts.request_vcards_by_uid";

    /**
     * Response indicating the packet contains a list of contact uIDs
     * <p>
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * The returned IDs can be used in future requests for more information about the contact
     */
    private static final String PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS = "kdeconnect.contacts.response_uids_timestamps";

    /**
     * Response indicating the packet contains a list of contact names
     * <p>
     * It shall contain the key "uids", which will mark a list of uIDs (long int, as string)
     * then, for each UID, there shall be a field with the key of that UID and the value of the name of the contact
     * <p>
     * For example:
     * { 'uids' : ['1', '3', '15'],
     *   '1'  : 'John Smith',
     *   '3'  : 'Abe Lincoln',
     *   '15' : 'Mom'
     * }
     */
    private static final String PACKET_TYPE_CONTACTS_RESPONSE_VCARDS = "kdeconnect.contacts.response_vcards";

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_contacts);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_contacts_desc);
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{
                PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS,
                PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS
        };
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{
                PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS,
                PACKET_TYPE_CONTACTS_RESPONSE_VCARDS
        };
    }

    @Override
    protected int getPermissionExplanation() {
        return R.string.contacts_permission_explanation;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public @NonNull String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.READ_CONTACTS};
        // One day maybe we will also support WRITE_CONTACTS, but not yet
    }

    @Override
    public boolean checkRequiredPermissions() {
        if (!arePermissionsGranted(getRequiredPermissions())) {
            return false;
        }
        return getPreferences().getBoolean("acceptedToTransferContacts", false);
    }

    @Override
    public boolean supportsDeviceSpecificSettings() {
        return true;
    }

    public @NonNull DialogFragment getPermissionExplanationDialog() {
        if (!arePermissionsGranted(getRequiredPermissions())) {
            return super.getPermissionExplanationDialog();
        }
        AlertDialogFragment dialog = new AlertDialogFragment.Builder()
                .setTitle(getDisplayName())
                .setMessage(R.string.contacts_per_device_confirmation)
                .setPositiveButton(R.string.ok)
                .setNegativeButton(R.string.cancel)
                .create();
        dialog.setCallback(new AlertDialogFragment.Callback() {
            @Override
            public void onPositiveButtonClicked() {
                Objects.requireNonNull(getPreferences()).edit().putBoolean("acceptedToTransferContacts", true).apply();
                Objects.requireNonNull(getDevice()).reloadPluginsFromSettings();
            }
        });
        return dialog;
    }

    /**
     * Add custom fields to the vcard to keep track of KDE Connect-specific fields
     * <p>
     * These include the local device's uID as well as last-changed timestamp
     * <p>
     * This might be extended in the future to include more fields
     *
     * @param vcard vcard to apply metadata to
     * @param uID   uID to which the vcard corresponds
     * @throws ContactNotFoundException If the given ID for some reason does not match a contact
     * @return The same VCard as was passed in, but now with KDE Connect-specific fields
     */
    private VCardBuilder addVCardMetadata(VCardBuilder vcard, uID uID) throws ContactNotFoundException {
        // Append the device ID line
        // Unclear if the deviceID forms a valid name per the vcard spec. Worry about that later..
        vcard.appendLine("X-KDECONNECT-ID-DEV-" + getDevice().getDeviceId(),
                uID.toString());

        // Build the timestamp line
        // Maybe one day this should be changed into the vcard-standard REV key
        Long timestamp = ContactsHelper.getContactTimestamp(context, uID);
        vcard.appendLine("X-KDECONNECT-TIMESTAMP",
                timestamp.toString());

        return vcard;
    }

    /**
     * Return a unique identifier (Contacts.LOOKUP_KEY) for all contacts in the Contacts database
     * <p>
     * The identifiers returned can be used in future requests to get more information
     * about the contact
     *
     * @param np The packet containing the request
     * @return true if successfully handled, false otherwise
     */
    @SuppressWarnings("SameReturnValue")
    private boolean handleRequestAllUIDsTimestamps(@SuppressWarnings("unused") NetworkPacket np) {
        NetworkPacket reply = new NetworkPacket(PACKET_TYPE_CONTACTS_RESPONSE_UIDS_TIMESTAMPS);

        Map<uID, Long> uIDsToTimestamps = ContactsHelper.getAllContactTimestamps(context);

        int contactCount = uIDsToTimestamps.size();

        List<String> uIDs = new ArrayList<>(contactCount);

        for (uID contactID : uIDsToTimestamps.keySet()) {
            Long timestamp = uIDsToTimestamps.get(contactID);
            reply.set(contactID.toString(), timestamp);
            uIDs.add(contactID.toString());
        }

        reply.set("uids", uIDs);

        getDevice().sendPacket(reply);

        return true;
    }

    private boolean handleRequestVCardsByUIDs(NetworkPacket np) {
        if (!np.has("uids")) {
            Log.e("ContactsPlugin", "handleRequestNamesByUIDs received a malformed packet with no uids key");
            return false;
        }

        List<String> uIDsAsStrings = np.getStringList("uids");

        // Convert to Collection<uIDs> to call getVCardsForContactIDs
        Set<uID> uIDs = new HashSet<>(uIDsAsStrings.size());
        for (String uID : uIDsAsStrings) {
            uIDs.add(new uID(uID));
        }

        Map<uID, VCardBuilder> uIDsToVCards = ContactsHelper.getVCardsForContactIDs(context, uIDs);

        // ContactsHelper.getVCardsForContactIDs(..) is allowed to reply without
        // some of the requested uIDs if they were not in the database, so update our list
        uIDsAsStrings = new ArrayList<>(uIDsToVCards.size());

        NetworkPacket reply = new NetworkPacket(PACKET_TYPE_CONTACTS_RESPONSE_VCARDS);

        // Add the vcards to the packet
        for (uID uID : uIDsToVCards.keySet()) {
            VCardBuilder vcard = uIDsToVCards.get(uID);

            try {
                vcard = this.addVCardMetadata(vcard, uID);

                // Store this as a valid uID
                uIDsAsStrings.add(uID.toString());
                // Add the uid -> vcard pairing to the packet
                reply.set(uID.toString(), vcard.toString());

            } catch (ContactsHelper.ContactNotFoundException e) {
                e.printStackTrace();
            }
        }

        // Add the valid uIDs to the packet
        reply.set("uids", uIDsAsStrings);

        getDevice().sendPacket(reply);

        return true;
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        switch (np.getType()) {
            case PACKET_TYPE_CONTACTS_REQUEST_ALL_UIDS_TIMESTAMPS:
                return this.handleRequestAllUIDsTimestamps(np);
            case PACKET_TYPE_CONTACTS_REQUEST_VCARDS_BY_UIDS:
                return this.handleRequestVCardsByUIDs(np);
            default:
                Log.e("ContactsPlugin", "Contacts plugin received an unexpected packet!");
                return false;
        }
    }
}
