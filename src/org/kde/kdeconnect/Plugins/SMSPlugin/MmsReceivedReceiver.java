/*
 * Copyright 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.klinker.android.send_message.Transaction;

import org.kde.kdeconnect_tp.R;

/**
 * Receiver for notifying user when a new MMS has been received by the device. By default it will
 * persist the message to the internal database and notification service to notify the users will be
 * implemented later.
 */
public class MmsReceivedReceiver extends com.klinker.android.send_message.MmsReceivedReceiver {

    private String mmscUrl = null;
    private String mmsProxy = null;
    private  String mmsPort = null;

    @Override
    public void onMessageReceived(Context context, Uri messageUri) {
        Log.v("MmsReceived", "message received: " + messageUri.toString());

        // Notify messageUpdateReceiver about the arrival of the new MMS message
        Intent refreshIntent = new Intent(Transaction.REFRESH);
        context.sendBroadcast(refreshIntent);
    }

    @Override
    public void onError(Context context, String error) {
        Log.v("MmsReceived", "error: " + error);
    }

    public void loadFromPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mmscUrl = prefs.getString(context.getString(R.string.sms_pref_set_mmsc), "");
        mmsProxy = prefs.getString(context.getString(R.string.sms_pref_set_mms_proxy), "");
        mmsPort = prefs.getString(context.getString(R.string.sms_pref_set_mms_port), "");
    }

    /**
     * some carriers will download duplicate MMS messages without this ACK. When using the
     * system sending method, apparently Android does not do this for us. Not sure why.
     * We might have to have users manually enter their APN settings if we cannot get them
     * from the system somehow.
     */
    @Override
    public MmscInformation getMmscInfoForReceptionAck() {

        if (mmscUrl != null || mmsProxy != null || mmsPort != null) {
            try {
                return new MmscInformation(mmscUrl, mmsProxy, Integer.parseInt(mmsPort));
            } catch (Exception e) {
                Log.e("MmsReceivedReceiver", "Exception", e);
                return null;
            }
        } else {
            return null;
        }
    }
}
