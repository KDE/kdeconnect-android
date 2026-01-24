/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.ui.list

import android.view.LayoutInflater
import android.view.View
import org.kde.kdeconnect_tp.databinding.ListItemCategoryBinding

class SectionItem : ListAdapter.Item {
    private val title: String

    constructor(title: String) {
        this.title = title
    }

    @JvmField
    var isEmpty: Boolean = true

    override fun inflateView(layoutInflater: LayoutInflater): View {
        val binding = ListItemCategoryBinding.inflate(layoutInflater)

        // Make it not selectable
        binding.root.setOnClickListener(null)
        binding.root.setOnLongClickListener(null)
        binding.root.isFocusable = false
        binding.root.isClickable = false

        binding.listItemCategoryText.text = title

        if (isEmpty) {
            binding.listItemCategoryEmptyPlaceholder.visibility = View.VISIBLE
        }

        return binding.root
    }
}
