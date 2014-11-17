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

package org.kde.kdeconnect.UserInterface.List;


import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.DeviceActivity;
import org.kde.kdeconnect.UserInterface.PairActivity;
import org.kde.kdeconnect_tp.R;

public class DeviceItem implements ListAdapter.Item {

	private final Device device;
    private final Activity activity;

	public DeviceItem(Activity activity, Device device) {
		this.device = device;
        this.activity = activity;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        final View v = layoutInflater.inflate(R.layout.list_item_entry, null);

        //Highlight when selected effect
        v.setBackgroundDrawable(layoutInflater.getContext().getResources().getDrawable(R.drawable.kitkatcompatselector_list_selector_holo_dark));

        TextView titleView = (TextView)v.findViewById(R.id.list_item_entry_title);
        if (titleView != null) titleView.setText(device.getName());
        if (device.compareProtocolVersion() != 0) {
            TextView summaryView = (TextView)v.findViewById(R.id.list_item_entry_summary);
            summaryView.setVisibility(View.VISIBLE);
            if (device.compareProtocolVersion() > 0) {
                summaryView.setText(R.string.protocol_version_newer);
            } else {
                summaryView.setText(R.string.protocol_version_older);
            }
        } else {
            v.findViewById(R.id.list_item_entry_summary).setVisibility(View.GONE);
        }

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent;
                if (device.isPaired()) {
                    intent = new Intent(activity, DeviceActivity.class);
                } else {
                    intent = new Intent(activity, PairActivity.class);
                }
                intent.putExtra("deviceId", device.getDeviceId());
                activity.startActivity(intent);
            }
        });

        return v;
    }

}
