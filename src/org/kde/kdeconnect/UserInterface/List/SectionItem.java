/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import org.kde.kdeconnect_tp.databinding.ListItemCategoryBinding;

public class SectionItem implements ListAdapter.Item {
    private final String title;
    public boolean isEmpty;

    public SectionItem(String title) {
        this.title = title;
        this.isEmpty = true;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        final ListItemCategoryBinding binding = ListItemCategoryBinding.inflate(layoutInflater);

        //Make it not selectable
        binding.getRoot().setOnClickListener(null);
        binding.getRoot().setOnLongClickListener(null);
        binding.getRoot().setFocusable(false);
        binding.getRoot().setClickable(false);

        binding.listItemCategoryText.setText(title);

        if (isEmpty) {
            binding.listItemCategoryEmptyPlaceholder.setVisibility(View.VISIBLE);
        }

        return binding.getRoot();
    }
}
