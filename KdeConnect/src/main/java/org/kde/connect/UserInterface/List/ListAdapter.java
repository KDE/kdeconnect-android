package org.kde.connect.UserInterface.List;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class ListAdapter extends ArrayAdapter<ListAdapter.Item> {

    public interface Item {
        public View inflateView(LayoutInflater layoutInflater);
    }

	private ArrayList<Item> items;
	private LayoutInflater layoutInflater;

	public ListAdapter(Context context, ArrayList<Item> items) {
		super(context, 0, items);
        this.items = items;
		layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}


	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final Item i = items.get(position);
        return i.inflateView(layoutInflater);
    }

}
