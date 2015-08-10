/*
 * Copyright 2014 Achilleas Koutsou <achilleas.k@gmail.com>
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

package org.kde.kdeconnect.UserInterface;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

public class CustomDevicesActivity extends ListActivity {

    private static final String LOG_ID = "CustomDevicesActivity";
    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private static final String IP_DELIM = ",";

    private ArrayList<String> ipAddressList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDeviceList(this);
        setContentView(R.layout.custom_ip_list);
        setListAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ipAddressList));


        EditText ipEntryBox = (EditText)findViewById(R.id.ip_edittext);
        ipEntryBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    addNewIp();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onListItemClick(ListView l, View v, final int position, final long id) {
        Log.i(LOG_ID, "Item clicked pos: " + position + " id: " + id);
        // remove touched item after confirmation
        DialogInterface.OnClickListener confirmationListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        ipAddressList.remove(position);
                        Log.i(LOG_ID, "Removed item pos: "+position+" id: "+id);
                        saveList();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Delete "+ipAddressList.get(position)+" ?");
        builder.setPositiveButton("Yes", confirmationListener);
        builder.setNegativeButton("No", confirmationListener);
        builder.show();
        ((ArrayAdapter)getListAdapter()).notifyDataSetChanged();
    }

    private void addNewIp() {
        EditText ipEntryBox = (EditText)findViewById(R.id.ip_edittext);
        String enteredText = ipEntryBox.getText().toString().trim();
        if (!enteredText.equals("")) {
            // don't add empty string (after trimming)
            ipAddressList.add(enteredText);
        }

        saveList();
        // clear entry box
        ipEntryBox.setText("");
        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = getCurrentFocus();
        if (focus != null) {
            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void saveList() {
        // add entry to list and save to preferences (unless empty)
        String serialized = "";
        if (!ipAddressList.isEmpty()) {
            serialized = serializeIpList(ipAddressList);
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(
                KEY_CUSTOM_DEVLIST_PREFERENCE, serialized).commit();
        ((ArrayAdapter)getListAdapter()).notifyDataSetChanged();

    }

    public static String serializeIpList(ArrayList<String> iplist) {
        String serialized = "";
        for (String ipaddr : iplist) {
            serialized += IP_DELIM+ipaddr;
        }
        // remove first delimiter
        serialized = serialized.substring(IP_DELIM.length());
        Log.d(LOG_ID, serialized);
        return serialized;
    }

    public static ArrayList<String> deserializeIpList(String serialized) {
        ArrayList<String> iplist = new ArrayList<>();
        Log.d(LOG_ID, serialized);
        for (String ipaddr : serialized.split(IP_DELIM)) {
            iplist.add(ipaddr);
            Log.d(LOG_ID, ipaddr);
        }
        return iplist;
    }

    private void initializeDeviceList(Context context){
        String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_CUSTOM_DEVLIST_PREFERENCE,
                "");
        if(deviceListPrefs.isEmpty()){
            Log.i(LOG_ID, "Initialising empty custom device list");
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                    KEY_CUSTOM_DEVLIST_PREFERENCE,
                    deviceListPrefs).commit();
        } else {
            Log.i(LOG_ID, "Populating device list");
            ipAddressList = deserializeIpList(deviceListPrefs);
        }
    }
}
