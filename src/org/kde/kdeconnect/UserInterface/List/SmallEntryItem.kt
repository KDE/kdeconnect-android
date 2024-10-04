/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface.List;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class SmallEntryItem implements ListAdapter.Item {
    private final String title;
    private final View.OnClickListener clickListener;

    SmallEntryItem(String title, View.OnClickListener clickListener) {
        this.title = title;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public View inflateView(@NonNull LayoutInflater layoutInflater) {
        View v = layoutInflater.inflate(android.R.layout.simple_list_item_1, null);
        final int padding = (int) (28 * layoutInflater.getContext().getResources().getDisplayMetrics().density);
        v.setPadding(padding, 0, padding, 0);

        TextView titleView = v.findViewById(android.R.id.text1);
        if (titleView != null) {
            titleView.setText(title);
            if (clickListener != null) {
                titleView.setOnClickListener(clickListener);
                TypedValue outValue = new TypedValue();
                layoutInflater.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                v.setBackgroundResource(outValue.resourceId);
            }
        }

        return v;
    }
}
