/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mpris

import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.tabs.TabLayoutMediator
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumeFragment
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.extensions.viewBinding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMprisBinding

class MprisActivity : BaseActivity<ActivityMprisBinding>() {

    override val binding: ActivityMprisBinding by viewBinding(ActivityMprisBinding::inflate)

    private lateinit var mprisPagerAdapter: MprisPagerAdapter

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val pagePosition = binding.mprisTabs.selectedTabPosition
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    mprisPagerAdapter.onVolumeUp(pagePosition)
                } else {
                    mprisPagerAdapter.onVolumeDown(pagePosition)
                }
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra(MprisPlugin.DEVICE_ID_KEY)

        mprisPagerAdapter = MprisPagerAdapter(this, deviceId)
        binding.mprisPager.adapter = mprisPagerAdapter

        val tabLayoutMediator = TabLayoutMediator(
            binding.mprisTabs, binding.mprisPager
        ) { tab, position ->
            tab.setText(
                mprisPagerAdapter.getTitle(position)
            )
        }

        tabLayoutMediator.attach()

        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    internal class MprisPagerAdapter(fragmentActivity: FragmentActivity, private val deviceId: String?) :
        ExtendedFragmentAdapter(fragmentActivity) {
        override fun createFragment(position: Int): Fragment = if (position == 1) {
            SystemVolumeFragment.newInstance(deviceId)
        } else {
            MprisNowPlayingFragment.newInstance(deviceId)
        }

        override fun getItemCount(): Int = 2

        @StringRes
        fun getTitle(position: Int): Int = if (position == 1) {
            R.string.devices
        } else {
            R.string.mpris_play
        }

        fun onVolumeUp(page: Int) {
            val requestedFragment = getFragment(page) ?: return

            if (requestedFragment is VolumeKeyListener) {
                requestedFragment.onVolumeUp()
            }
        }

        fun onVolumeDown(page: Int) {
            val requestedFragment = getFragment(page) ?: return

            if (requestedFragment is VolumeKeyListener) {
                requestedFragment.onVolumeDown()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onBackPressedDispatcher.onBackPressed()
        return true
    }
}
