/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.kde.kdeconnect_tp.databinding.ListItemPluginHeaderBinding;

public class PluginListHeaderItem implements ListAdapter.Item {
    private final int text;

    public PluginListHeaderItem(int text) {
        this.text = text;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        TextView textView = ListItemPluginHeaderBinding.inflate(layoutInflater).getRoot();
        textView.setText(text);
        textView.setOnClickListener(null);
        textView.setOnLongClickListener(null);
        return textView;
    }
}
