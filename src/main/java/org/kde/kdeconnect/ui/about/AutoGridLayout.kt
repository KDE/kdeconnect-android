/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.content.Context
import android.util.AttributeSet
import androidx.gridlayout.widget.GridLayout
import org.kde.kdeconnect_tp.R
import kotlin.math.max

/**
* GridLayout that adjusts the number of columns and rows to fill all screen space
 */
class AutoGridLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : GridLayout(context, attrs, defStyleAttr) {
    private var defaultColumnCount = 0
    private var columnWidth = 0
    private var changeColumnCountIfTheyHaveOnlyOneElement = false

    init {
        var typedArray = context.obtainStyledAttributes(attrs, R.styleable.AutoGridLayout, 0, defStyleAttr)

        try {
            columnWidth = typedArray.getDimensionPixelSize(R.styleable.AutoGridLayout_columnWidth, 0)
            changeColumnCountIfTheyHaveOnlyOneElement = typedArray.getBoolean(R.styleable.AutoGridLayout_changeColumnCountIfTheyHaveOnlyOneElement, false)
            typedArray = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.columnCount), 0, defStyleAttr)
            defaultColumnCount = typedArray.getInt(0, 10)
        } finally {
            typedArray.recycle()
        }

        columnCount = 1
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        val width = MeasureSpec.getSize(widthSpec)

        if (columnWidth > 0 && width > 0) {
            val totalSpace = width - paddingRight - paddingLeft
            var calculatedColumnCount = max(1, totalSpace / columnWidth)

            if (calculatedColumnCount < childCount && changeColumnCountIfTheyHaveOnlyOneElement) {
                calculatedColumnCount = defaultColumnCount
            }

            columnCount = calculatedColumnCount
        } else {
            columnCount = defaultColumnCount
        }
    }
}
