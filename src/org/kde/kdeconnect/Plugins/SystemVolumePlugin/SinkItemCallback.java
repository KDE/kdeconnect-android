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
