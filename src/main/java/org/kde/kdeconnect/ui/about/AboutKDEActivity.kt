/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect.extensions.viewBinding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityAboutKdeBinding

class AboutKDEActivity : BaseActivity<ActivityAboutKdeBinding>() {

    override val binding: ActivityAboutKdeBinding by viewBinding(ActivityAboutKdeBinding::inflate)

    override val isScrollable: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        binding.aboutTextView.text = fromHtml(resources.getString(R.string.about_kde_about))
        binding.reportBugsOrWishesTextView.text = fromHtml(resources.getString(R.string.about_kde_report_bugs_or_wishes))
        binding.joinKdeTextView.text = fromHtml(resources.getString(R.string.about_kde_join_kde))
        binding.supportKdeTextView.text = fromHtml(resources.getString(R.string.about_kde_support_kde))

        binding.aboutTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.reportBugsOrWishesTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.joinKdeTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.supportKdeTextView.movementMethod = LinkMovementMethod.getInstance()

        binding.scrollView.setupBottomPadding()
    }

    private fun fromHtml(html: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION") Html.fromHtml(html)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onBackPressed()
        return true
    }
}
