/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.List;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import org.kde.kdeconnect_tp.databinding.ListItemWithIconEntryBinding;

public class EntryItemWithIcon implements ListAdapter.Item {
    protected final String title;
    protected final String subtitle;
    protected final Drawable icon;

    public EntryItemWithIcon(String title, Drawable icon) {
        this(title, null, icon);
    }

    protected EntryItemWithIcon(String title, String subtitle, Drawable icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        final ListItemWithIconEntryBinding binding = ListItemWithIconEntryBinding.inflate(layoutInflater);

        binding.listItemEntryTitle.setText(title);
        binding.listItemEntryIcon.setImageDrawable(icon);

        if (subtitle != null) {
            binding.listItemEntrySummary.setVisibility(View.VISIBLE);
            binding.listItemEntrySummary.setText(subtitle);
        }

        return binding.getRoot();
    }
}
