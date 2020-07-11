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
