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

public class SectionItem implements ListAdapter.Item {

	private final String title;
    public boolean isSectionEmpty;

	public SectionItem(String title) {
		this.title = title;
        this.isSectionEmpty = false;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {

        View v = layoutInflater.inflate(R.layout.list_item_category, null);

        //Make it not selectable
        v.setOnClickListener(null);
        v.setOnLongClickListener(null);

        TextView sectionView = (TextView) v.findViewById(R.id.list_item_category_text);
        sectionView.setText(title);

        if (isSectionEmpty) {
            v.findViewById(R.id.list_item_category_empty_placeholder).setVisibility(View.VISIBLE);
        }

        return v;

    }
}
