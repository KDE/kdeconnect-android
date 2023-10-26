/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
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
//        binding.listItemEntryTitle.setText(device.getName() + " " + device.getConnectivityType());
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
