package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

public class ImageListAdapter implements ListAdapter {

    static class ImageListElement {
        String text;
        Drawable icon;
    }

    private ArrayList<ImageListElement> localList = new ArrayList<ImageListElement>();

    public ImageListAdapter(ArrayList<ImageListElement> list) {
        super();
        localList = list;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
            convertView = inflater.inflate(R.layout.imagelist_element, null);
        }
        ImageListElement data = localList.get(position);
        ((TextView) convertView.findViewById(R.id.txt)).setText(data.text);
        ((ImageView) convertView.findViewById(R.id.img)).setImageDrawable(data.icon);
        return convertView;
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
