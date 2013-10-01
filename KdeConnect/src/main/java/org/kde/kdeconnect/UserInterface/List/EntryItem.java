package org.kde.kdeconnect.UserInterface.List;


import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.DeviceActivity;
import org.kde.kdeconnect.UserInterface.PairActivity;
import org.kde.kdeconnect_tp.R;

public class EntryItem implements ListAdapter.Item {

	private final String title;

	public EntryItem(String title) {
		this.title = title;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        View v = layoutInflater.inflate(R.layout.list_item_entry, null);

        TextView titleView = (TextView)v.findViewById(R.id.list_item_entry_title);
        if (titleView != null) titleView.setText(title);

        return v;
    }

}
