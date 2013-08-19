package org.kde.connect;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.kde.kdeconnect.R;

import java.util.ArrayList;

public class PreferenceListAdapter implements ListAdapter {


    private ArrayList<Preference> localList;

    public PreferenceListAdapter(ArrayList<Preference> list) {
        super();
        Log.e("PreferenceListAdapter", ""+list.size());
        localList = list;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = localList.get(position).getView(convertView, parent);
        v.setEnabled(true);
        v.setFocusable(true);
        return v;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
    } // Empty

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
    } // Empty

    @Override
    public boolean isEmpty() {
        return localList.size() == 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public int getViewTypeCount() {
        return localList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    } // Empty

    @Override
    public Object getItem(int position) {
        return localList.get(position);
    } // Empty

    @Override
    public int getCount() {
        return localList.size();
    }

    @Override
    public boolean isEnabled(int i) {
        return false;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }
 }
