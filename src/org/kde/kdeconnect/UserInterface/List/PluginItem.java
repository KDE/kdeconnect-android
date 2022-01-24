/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ListItemWithIconEntryBinding;

public class PluginItem extends EntryItemWithIcon {
    private final View.OnClickListener clickListener;

    public PluginItem(Plugin p, View.OnClickListener clickListener) {
        super(p.getActionName(), p.getIcon());
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        final View root = super.inflateView(layoutInflater);
        root.setOnClickListener(clickListener);
        return root;
    }
}
