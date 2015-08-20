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

package org.kde.kdeconnect.NewUserInterface.List;


import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NewUserInterface.NavigationDrawerFragment;
import org.kde.kdeconnect.UserInterface.DeviceActivity;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.PairActivity;
import org.kde.kdeconnect_tp.R;

public class MaterialDeviceItem implements ListAdapter.Item {

	private final Device device;
    private final Activity activity;
    private final NavigationDrawerFragment.NavigationDrawerCallbacks callback;
    private TextView titleView;
    private ImageView icon;
    private View rootView;

    public MaterialDeviceItem(Activity activity, Device device, NavigationDrawerFragment.NavigationDrawerCallbacks callback) {
		this.device = device;
        this.activity = activity;
        this.callback = callback;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        rootView = layoutInflater.inflate(R.layout.list_item_with_icon_entry, null);

        icon = (ImageView)rootView.findViewById(R.id.list_item_entry_icon);
        icon.setImageDrawable(device.getIcon());

        titleView = (TextView)rootView.findViewById(R.id.list_item_entry_title);
        titleView.setText(device.getName());

        if (device.compareProtocolVersion() != 0) {
            TextView summaryView = (TextView)rootView.findViewById(R.id.list_item_entry_summary);
            summaryView.setVisibility(View.VISIBLE);
            if (device.compareProtocolVersion() > 0) {
                summaryView.setText(R.string.protocol_version_newer);
            } else {
                summaryView.setText(R.string.protocol_version_older);
            }
        } else {
            rootView.findViewById(R.id.list_item_entry_summary).setVisibility(View.GONE);
        }

        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent;
                if (device.isPaired()) {
                    //intent = new Intent(activity, DeviceActivity.class);
                    //intent.putExtra("deviceId", device.getDeviceId());
                    //activity.startActivity(intent);
                    setSelected(true);
                    callback.onDeviceSelected(device);
                } else {
                    intent = new Intent(activity, PairActivity.class);
                    intent.putExtra("deviceId", device.getDeviceId());
                    activity.startActivity(intent);
                }


            }
        });

        return rootView;
    }

    void setSelected(boolean b) {
        Resources r = activity.getResources();
        if (b) {
            titleView.setTextColor(r.getColor(R.color.primaryDark));
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            titleView.setTextColor(r.getColor(android.R.color.black));
            titleView.setTypeface(Typeface.DEFAULT);
        }

        Drawable drawable = device.getIcon();
        drawable.setColorFilter(r.getColor(R.color.primaryDark), PorterDuff.Mode.SRC_ATOP);
        icon.setImageDrawable(drawable);
    }

}
