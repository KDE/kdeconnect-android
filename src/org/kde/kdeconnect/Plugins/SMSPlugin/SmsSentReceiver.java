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

import com.klinker.android.send_message.SentReceiver;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

public class SmsSentReceiver extends SentReceiver {

    @Override
    public void updateInInternalDatabase(Context context, Intent intent, int receiverResultCode) {
        super.updateInInternalDatabase(context, intent, receiverResultCode);

        if (Utils.isDefaultSmsApp(context)) {
            // Notify messageUpdateReceiver about the successful sending of the sms message
            Intent refreshIntent = new Intent(Transaction.REFRESH);
            context.sendBroadcast(refreshIntent);
        }
    }

    @Override
    public void onMessageStatusUpdated(Context context, Intent intent, int receiverResultCode) {
    }
}
