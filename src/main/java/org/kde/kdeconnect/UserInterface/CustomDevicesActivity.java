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

    ArrayList<String> ipAddressList = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDeviceList(this);
        setContentView(R.layout.custom_ip_list);
        setListAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, ipAddressList));


        EditText ipEntryBox = (EditText)findViewById(R.id.ip_edittext);
        ipEntryBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    addNewIp(v);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onListItemClick(ListView l, View v, final int position, final long id) {
        Log.i(LOG_ID, "Item clicked pos: "+position+" id: "+id);
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

    public void addNewIp(View v) {
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

        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
    }

    void saveList() {
        // add entry to list and save to preferences (unless empty)
        String serialized = "";
        if (!ipAddressList.isEmpty()) {
            serialized = serializeIpList(ipAddressList);
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(
                KEY_CUSTOM_DEVLIST_PREFERENCE, serialized).commit();
        ((ArrayAdapter)getListAdapter()).notifyDataSetChanged();

    }

    static String serializeIpList(ArrayList<String> iplist) {
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
        ArrayList<String> iplist = new ArrayList<String>();
        Log.d(LOG_ID, serialized);
        for (String ipaddr : serialized.split(IP_DELIM)) {
            iplist.add(ipaddr);
            Log.d(LOG_ID, ipaddr);
        }
        return iplist;
    }

    void initializeDeviceList(Context context){
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
