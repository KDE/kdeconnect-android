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

import androidx.annotation.NonNull;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ListItemWithIconEntryBinding;

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

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        final ListItemWithIconEntryBinding binding = ListItemWithIconEntryBinding.inflate(layoutInflater);

        binding.listItemEntryIcon.setImageDrawable(device.getIcon());
        binding.listItemEntryTitle.setText(device.getName());

        if (device.compareProtocolVersion() != 0) {
            if (device.compareProtocolVersion() > 0) {
                binding.listItemEntrySummary.setText(R.string.protocol_version_newer);
                binding.listItemEntrySummary.setVisibility(View.VISIBLE);
            } else {
                //FIXME: Uncoment when we decide old versions are old enough to notify the user.
                binding.listItemEntrySummary.setVisibility(View.GONE);
                /*
                summaryView.setText(R.string.protocol_version_older);
                summaryView.setVisibility(View.VISIBLE);
                */
            }
        } else {
            binding.listItemEntrySummary.setVisibility(View.GONE);
        }

        binding.getRoot().setOnClickListener(v1 -> callback.pairingClicked(device));

        return binding.getRoot();
    }

}
