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

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

public class PairingDeviceItem implements ListAdapter.Item {

    public interface Callback {
        void pairingClicked(Device d);
    }

    private final Callback callback;
    private final Device device;

    public PairingDeviceItem(Device device, Callback callback) {
        this.device = device;
        this.callback = callback;
    }

    public Device getDevice() {
        return this.device;
    }

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        final View v = layoutInflater.inflate(R.layout.list_item_with_icon_entry, null);

        ImageView icon = (ImageView) v.findViewById(R.id.list_item_entry_icon);
        icon.setImageDrawable(device.getIcon());

        TextView titleView = (TextView) v.findViewById(R.id.list_item_entry_title);
        titleView.setText(device.getName());

        if (device.compareProtocolVersion() != 0) {
            TextView summaryView = (TextView)v.findViewById(R.id.list_item_entry_summary);

            if (device.compareProtocolVersion() > 0) {
                summaryView.setText(R.string.protocol_version_newer);
                summaryView.setVisibility(View.VISIBLE);
            } else {
                //FIXME: Uncoment when we decide old versions are old enough to notify the user.
                summaryView.setVisibility(View.GONE);
                /*
                summaryView.setText(R.string.protocol_version_older);
                summaryView.setVisibility(View.VISIBLE);
                */
            }
        } else {
            v.findViewById(R.id.list_item_entry_summary).setVisibility(View.GONE);
        }

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.pairingClicked(device);
            }
        });

        return v;
    }

}
