/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo906@mail.ru>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.SystemVolumePlugin;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

public class SinkItemCallback extends DiffUtil.ItemCallback<Sink> {

    @Override
    public boolean areItemsTheSame(@NonNull Sink oldItem, @NonNull Sink newItem) {
        return oldItem.getName().equals(newItem.getName());
    }

    @Override
    public boolean areContentsTheSame(@NonNull Sink oldItem, @NonNull Sink newItem) {
        return oldItem.getVolume() == newItem.getVolume()
                && oldItem.isMute() == newItem.isMute()
                && oldItem.isDefault() == newItem.isDefault()
                && oldItem.getMaxVolume() == newItem.getMaxVolume() // should this be checked?
                && oldItem.getDescription().equals(newItem.getDescription()); // should this be checked?
    }
}
