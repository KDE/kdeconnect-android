/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect.extensions.viewBinding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityLicensesBinding
import java.nio.charset.Charset

class LicensesActivity : BaseActivity<ActivityLicensesBinding>() {

    override val binding: ActivityLicensesBinding by viewBinding(ActivityLicensesBinding::inflate)

    override val isScrollable: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.licensesText.setupBottomPadding()
        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        binding.licensesText.layoutManager = LinearLayoutManager(this)
        binding.licensesText.adapter = StringListAdapter(getLicenses().split("\n\n"))
    }

    private fun getLicenses(): String = resources.openRawResource(R.raw.license).use { inputStream -> IOUtils.toString(inputStream, Charset.defaultCharset()) }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_licenses, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun smoothScrollToPosition(position: Int) {
        val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(this) {
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float = 2.5F / displayMetrics.densityDpi
        }

        linearSmoothScroller.targetPosition = position
        binding.licensesText.layoutManager?.startSmoothScroll(linearSmoothScroller)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_rise_up -> {
            smoothScrollToPosition(0)
            true
        }
        R.id.menu_rise_down -> {
            smoothScrollToPosition(binding.licensesText.adapter!!.itemCount - 1)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onBackPressed()
        return true
    }
}
