/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.UserInterface.List

import android.R
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

class SmallEntryItem : ListAdapter.Item {
    private val title: String
    private val clickListener: View.OnClickListener?

    constructor(title: String, clickListener: View.OnClickListener?) {
        this.title = title
        this.clickListener = clickListener
    }

    override fun inflateView(layoutInflater: LayoutInflater): View {
        val v = layoutInflater.inflate(R.layout.simple_list_item_1, null)
        val padding = (28 * layoutInflater.context.resources.displayMetrics.density).toInt()
        v.setPadding(padding, 0, padding, 0)

        val titleView = v.findViewById<TextView>(R.id.text1)
        if (titleView != null) {
            titleView.text = title
            if (clickListener != null) {
                titleView.setOnClickListener(clickListener)
                val outValue = TypedValue()
                layoutInflater.context.theme.resolveAttribute(
                    R.attr.selectableItemBackground,
                    outValue,
                    true
                )
                v.setBackgroundResource(outValue.resourceId)
            }
        }

        return v
    }
}
