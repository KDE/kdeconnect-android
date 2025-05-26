/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.databinding.ListItemDeviceEntryBinding;

public class DeviceItem implements ListAdapter.Item {

    public interface Callback {
        void pairingClicked(Device d);
    }

    private final @Nullable Callback callback;
    protected final @NonNull Device device;
    protected ListItemDeviceEntryBinding binding;

    public DeviceItem(@NonNull Device device, @Nullable Callback callback) {
        this.device = device;
        this.callback = callback;
    }

    public @NonNull Device getDevice() {
        return this.device;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        binding = ListItemDeviceEntryBinding.inflate(layoutInflater);

        binding.listItemEntryIcon.setImageDrawable(device.getIcon());
        binding.listItemEntryTitle.setText(device.getName());

        if (callback != null) {
            binding.getRoot().setOnClickListener(v1 -> callback.pairingClicked(device));
        }

        return binding.getRoot();
    }

}
