/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import java.util.List;

public class ListAdapter extends ArrayAdapter<ListAdapter.Item> {
    public interface Item {
        @NonNull
        View inflateView(@NonNull LayoutInflater layoutInflater);
    }

    private final List<? extends Item> items;
    private final boolean isEnabled;

    public ListAdapter(Context context, List<? extends Item> items) {
        this(context, items, true);
    }

    public ListAdapter(Context context, List<? extends Item> items, boolean isEnabled) {
        super(context, 0, (List<Item>) items);
        this.items = items;
        this.isEnabled = isEnabled;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        final Item i = items.get(position);
        return i.inflateView(LayoutInflater.from(parent.getContext()));
    }

    @Override
    public boolean isEnabled(int position) {
        return isEnabled;
    }
}
