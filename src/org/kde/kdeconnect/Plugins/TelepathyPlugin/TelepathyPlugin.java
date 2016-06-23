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

import android.telephony.SmsManager;
import android.util.Log;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin;
import org.kde.kdeconnect_tp.R;

public class TelepathyPlugin extends Plugin {


    public final static String PACKAGE_TYPE_SMS_REQUEST = "kdeconnect.sms.request";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telepathy);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_telepathy_desc);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        if (!np.getType().equals(PACKAGE_TYPE_SMS_REQUEST)) {
            return false;
        }

        if (np.getBoolean("sendSms")) {
            String phoneNo = np.getString("phoneNumber");
            String sms = np.getString("messageBody");
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNo, null, sms, null, null);
                //TODO: Notify other end
            } catch (Exception e) {
                //TODO: Notify other end
                Log.e("TelepathyPlugin", e.getMessage());
                e.printStackTrace();
            }
        }

/*
        if (np.getBoolean("requestContacts")) {

            ArrayList<String> vCards = new ArrayList<String>();

            Cursor cursor = context.getContentResolver().query(
                    Contacts.CONTENT_URI,
                    null,
                    Contacts.HAS_PHONE_NUMBER + " > 0 ",
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                try {
                    do {
                        String lookupKey = cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));
                        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
                        AssetFileDescriptor fd = null;
                        try {
                            fd = context.getContentResolver()
                                    .openAssetFileDescriptor(uri, "r");
                            FileInputStream fis = fd.createInputStream();
                            byte[] b = new byte[(int) fd.getDeclaredLength()];
                            fis.read(b);
                            String vCard = new String(b);
                            vCards.add(vCard);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            Log.e("RequestContacts", e.getMessage());
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("RequestContacts", e.getMessage());
                        } finally {
                            if (fd != null) fd.close();
                        }
                    } while (cursor.moveToNext());
                    Log.e("Contacts","Size: "+vCards.size());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("RequestContacts", e.getMessage());
                } finally {
                    cursor.close();
                }
            }

            NetworkPackage answer = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_TELEPHONY);
            answer.set("contacts",vCards);
            device.sendPackage(answer);

        }
*/
/*
        if (np.getBoolean("requestNumbers")) {

            ArrayList<String> numbers = new ArrayList<String>();

            Cursor cursor = context.getContentResolver().query(
                    CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            CommonDataKinds.Phone.DISPLAY_NAME,
                            CommonDataKinds.Phone.NUMBER
                    },
                    Contacts.HAS_PHONE_NUMBER + " > 0 ",
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                try {
                    do {
                        String number = cursor.getString(cursor.getColumnIndex(CommonDataKinds.Phone.NUMBER));
                        String name = cursor.getString(cursor.getColumnIndex(CommonDataKinds.Phone.DISPLAY_NAME));
                        numbers.add(number);
                    } while (cursor.moveToNext());
                    Log.e("Numbers","Size: "+numbers.size());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("RequestContacts", e.getMessage());
                } finally {
                    cursor.close();
                }
            }

            NetworkPackage answer = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_TELEPHONY);
            answer.set("numbers",numbers);
            device.sendPackage(answer);
        }
*/

        return true;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_SMS_REQUEST, TelephonyPlugin.PACKAGE_TYPE_TELEPHONY_REQUEST};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{};
    }

}
