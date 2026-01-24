/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView

class StringListAdapter(private val stringList: List<String>) : RecyclerView.Adapter<StringListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context)
        textView.setPadding(8)
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.itemView as TextView).text = stringList[position]
    }

    override fun getItemCount(): Int = stringList.size
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
