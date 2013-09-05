package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

public class PreferenceListAdapter extends ArrayAdapter<Preference> {


    private ArrayList<Preference> localList;

    public PreferenceListAdapter(Context context, ArrayList<Preference> items) {
        super(context,0, items);
        localList = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Preference preference = localList.get(position);
        return preference.getView(convertView, parent);
    }

}
