package org.kde.connect.UserInterface;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.kde.kdeconnect.R;

import java.util.ArrayList;

public class PreferenceListAdapter extends ArrayAdapter<Preference> {


    private ArrayList<Preference> localList;

    public PreferenceListAdapter(Context context, ArrayList<Preference> items) {
        super(context,0, items);
        Log.e("PreferenceListAdapter", ""+items.size());
        localList = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Preference preference = localList.get(position);
        return preference.getView(convertView, parent);
    }

}
