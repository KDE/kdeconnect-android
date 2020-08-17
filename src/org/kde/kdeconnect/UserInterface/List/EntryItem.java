/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import org.kde.kdeconnect_tp.databinding.ListItemEntryBinding;

public class EntryItem implements ListAdapter.Item {
    protected final String title;
    protected final String subtitle;

    public EntryItem(String title) {
        this(title, null);
    }

    protected EntryItem(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        final ListItemEntryBinding binding = ListItemEntryBinding.inflate(layoutInflater);

        binding.listItemEntryTitle.setText(title);

        if (subtitle != null) {
            binding.listItemEntrySummary.setVisibility(View.VISIBLE);
            binding.listItemEntrySummary.setText(subtitle);
        }

        return binding.getRoot();
    }
}
