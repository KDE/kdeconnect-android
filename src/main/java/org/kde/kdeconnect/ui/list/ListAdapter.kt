/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.list

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter

class ListAdapter(
    context: Context,
    private val items: List<Item>,
    private val isEnabled: Boolean = true
) : ArrayAdapter<ListAdapter.Item>(context, 0, items) {

    interface Item {
        fun inflateView(layoutInflater: LayoutInflater): View
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val i = items[position]
        return i.inflateView(LayoutInflater.from(parent.context))
    }

    override fun isEnabled(position: Int): Boolean {
        return isEnabled
    }
}
