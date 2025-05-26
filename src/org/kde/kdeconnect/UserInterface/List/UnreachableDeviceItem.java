/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

public class UnreachableDeviceItem extends DeviceItem {

    public UnreachableDeviceItem(Device device, Callback callback) {
        super(device, callback);
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        View ret = super.inflateView(layoutInflater);
        binding.listItemEntryTitle.setText(device.getName());
        binding.listItemEntrySummary.setText(R.string.runcommand_notreachable);
        binding.listItemEntrySummary.setVisibility(View.VISIBLE);
        return ret;
    }

}
