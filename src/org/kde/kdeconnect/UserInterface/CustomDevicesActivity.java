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

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collections;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class CustomDevicesActivity extends AppCompatActivity {

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE = "device_list_preference";
    private static final String IP_DELIM = ",";

    private ListView list;

    private ArrayList<String> ipAddressList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDeviceList(this);
        ThemeUtil.setUserPreferredTheme(this);
        setContentView(R.layout.custom_ip_list);

        list = findViewById(android.R.id.list);
        list.setOnItemClickListener(onClickListener);

        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ipAddressList));

        findViewById(android.R.id.button1).setOnClickListener(v -> addNewDevice());

        EditText ipEntryBox = findViewById(R.id.ip_edittext);
        ipEntryBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                addNewDevice();
                return true;
            }
            return false;
        });
    }

    private boolean dialogAlreadyShown = false;
    private final AdapterView.OnItemClickListener onClickListener = (parent, view, position, id) -> {

        if (dialogAlreadyShown) {
            return;
        }

        // remove touched item after confirmation
        DialogInterface.OnClickListener confirmationListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    ipAddressList.remove(position);
                    saveList();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(CustomDevicesActivity.this);
        builder.setMessage(getString(R.string.delete_custom_device, ipAddressList.get(position)));
        builder.setPositiveButton(R.string.ok, confirmationListener);
        builder.setNegativeButton(R.string.cancel, confirmationListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) { //DismissListener
            dialogAlreadyShown = true;
            builder.setOnDismissListener(dialog -> dialogAlreadyShown = false);
        }

        builder.show();
    };

    private void addNewDevice() {
        EditText ipEntryBox = findViewById(R.id.ip_edittext);
        String enteredText = ipEntryBox.getText().toString().trim();
        if (!enteredText.isEmpty()) {
            // don't add empty string (after trimming)
            ipAddressList.add(enteredText);
        }

        saveList();
        // clear entry box
        ipEntryBox.setText("");
        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = getCurrentFocus();
        if (focus != null && inputManager != null) {
            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void saveList() {
        String serialized = TextUtils.join(IP_DELIM, ipAddressList);
        PreferenceManager.getDefaultSharedPreferences(CustomDevicesActivity.this).edit().putString(
                KEY_CUSTOM_DEVLIST_PREFERENCE, serialized).apply();
        ((ArrayAdapter) list.getAdapter()).notifyDataSetChanged();

    }

    public static ArrayList<String> deserializeIpList(String serialized) {
        ArrayList<String> ipList = new ArrayList<>();
        Collections.addAll(ipList, serialized.split(IP_DELIM));
        return ipList;
    }

    private void initializeDeviceList(Context context) {
        String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_CUSTOM_DEVLIST_PREFERENCE, "");
        if (deviceListPrefs.isEmpty()) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                    KEY_CUSTOM_DEVLIST_PREFERENCE,
                    deviceListPrefs).apply();
        } else {
            ipAddressList = deserializeIpList(deviceListPrefs);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackgroundService.removeGuiInUseCounter(this);
    }

}
