/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Plugins.TelepathyPlugin;

import android.Manifest;
import android.telephony.SmsManager;
import android.util.Log;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;


public class TelepathyPlugin extends Plugin {

    private final static String PACKET_TYPE_SMS_REQUEST = "kdeconnect.sms.request";

    @Override
    public boolean onCreate() {
        permissionExplanation = R.string.telepathy_permission_explanation;
        return true;
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telepathy);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_telepathy_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (!np.getType().equals(PACKET_TYPE_SMS_REQUEST)) {
            return false;
        }

        if (np.getBoolean("sendSms")) {
            String phoneNo = np.getString("phoneNumber");
            String sms = np.getString("messageBody");

            try {
                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> parts = smsManager.divideMessage(sms);

                // If this message turns out to fit in a single SMS, sendMultipartTextMessage
                // properly handles that case
                smsManager.sendMultipartTextMessage(phoneNo, null, parts, null, null);

                //TODO: Notify other end
            } catch (Exception e) {
                //TODO: Notify other end
                Log.e("TelepathyPlugin", e.getMessage());
                e.printStackTrace();
            }
        }

        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_SMS_REQUEST, TelephonyPlugin.PACKET_TYPE_TELEPHONY_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{};
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.SEND_SMS};
    }
}
