/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.MprisPlugin

import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.tabs.TabLayoutMediator
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeFragment
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMprisBinding

class MprisActivity : AppCompatActivity() {
    private lateinit var activityMprisBinding: ActivityMprisBinding
    private lateinit var mprisPagerAdapter: MprisPagerAdapter

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val pagePosition = activityMprisBinding.mprisTabs.selectedTabPosition
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

        activityMprisBinding = ActivityMprisBinding.inflate(layoutInflater)

        setContentView(activityMprisBinding.root)

        val deviceId = intent.getStringExtra(MprisPlugin.DEVICE_ID_KEY)

        mprisPagerAdapter = MprisPagerAdapter(this, deviceId)
        activityMprisBinding.mprisPager.adapter = mprisPagerAdapter

        val tabLayoutMediator = TabLayoutMediator(
            activityMprisBinding.mprisTabs, activityMprisBinding.mprisPager
        ) { tab, position ->
            tab.setText(
                mprisPagerAdapter.getTitle(position)
            )
        }

        tabLayoutMediator.attach()

        setSupportActionBar(activityMprisBinding.toolbar)
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
