/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.widget.Adapter
import android.widget.LinearLayout

class AdapterLinearLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : LinearLayout(context, attrs, defStyle) {
    var adapter: Adapter? = null
        set(adapter) {
            if (field !== adapter) {
                field = adapter
                field?.registerDataSetObserver(dataSetObserver)
                reloadChildViews()
            }
        }

    private val dataSetObserver: DataSetObserver = object : DataSetObserver() {
        override fun onChanged() {
            super.onChanged()
            reloadChildViews()
        }
    }

    init {
        orientation = VERTICAL
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        adapter?.unregisterDataSetObserver(dataSetObserver)
    }

    private fun reloadChildViews() {
        removeAllViews()

        if (adapter != null) {
            for (position in 0 until adapter!!.count) {
                adapter!!.getView(position, null, this)?.let { addView(it) }
            }

            requestLayout()
        }
    }
}
