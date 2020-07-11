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
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

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
                v.setBackgroundDrawable(ContextCompat.getDrawable(layoutInflater.getContext(), R.drawable.abc_list_selector_holo_dark));
            }
        }

        return v;
    }
}
