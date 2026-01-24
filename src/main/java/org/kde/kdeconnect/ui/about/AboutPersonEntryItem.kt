/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.net.toUri
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.AboutPersonListItemEntryBinding

class AboutPersonEntryItem(val person: AboutPerson) : ListAdapter.Item {
    override fun inflateView(layoutInflater: LayoutInflater): View {
        val binding = AboutPersonListItemEntryBinding.inflate(layoutInflater)

        binding.aboutPersonListItemEntryName.text = person.name

        if (person.task != null) {
            binding.aboutPersonListItemEntryTask.visibility = View.VISIBLE
            binding.aboutPersonListItemEntryTask.text = layoutInflater.context.getString(person.task)
        }

        if (person.webAddress != null) {
            binding.aboutPersonListItemEntryVisitHomepageButton.visibility = View.VISIBLE
            TooltipCompat.setTooltipText(binding.aboutPersonListItemEntryVisitHomepageButton, layoutInflater.context.resources.getString(R.string.visit_contributors_homepage, person.webAddress))
            binding.aboutPersonListItemEntryVisitHomepageButton.setOnClickListener {
                layoutInflater.context.startActivity(Intent(Intent.ACTION_VIEW,
                    person.webAddress.toUri()))
            }
        }

        return binding.root
    }
}
