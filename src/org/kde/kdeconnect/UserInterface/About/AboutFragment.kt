/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.About

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import org.kde.kdeconnect.UserInterface.List.ListAdapter
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect.extensions.getParcelableCompat
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.FragmentAboutBinding

class AboutFragment : BaseFragment<FragmentAboutBinding>() {

    companion object {
        private const val KEY_ABOUT_DATA = "about_data"

        @JvmStatic
        fun newInstance(aboutData: AboutData): Fragment {
            val fragment = AboutFragment()

            val args = Bundle(1)
            args.putParcelable(KEY_ABOUT_DATA, aboutData)
            fragment.arguments = args

            return fragment
        }
    }

    override fun getActionBarTitle() = getString(R.string.about)

    private lateinit var aboutData: AboutData
    private var tapCount = 0
    private var firstTapMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aboutData = arguments?.getParcelableCompat(KEY_ABOUT_DATA) ?: throw IllegalArgumentException("AboutData is null")
    }

    override fun onInflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentAboutBinding {
        return FragmentAboutBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scrollView.setupBottomPadding()
        updateData()
    }

    @SuppressLint("SetTextI18n")
    fun updateData() {
        // Update general info

        binding.appName.text = aboutData.name
        binding.appIcon.setImageDrawable(context?.let { ContextCompat.getDrawable(it, aboutData.icon) })
        binding.appVersion.text = context?.getString(R.string.version, aboutData.versionName)

        // Setup Easter Egg onClickListener

        binding.generalInfoCard.setOnClickListener {
            if (firstTapMillis == null) {
                firstTapMillis = System.currentTimeMillis()
            }

            if (++tapCount == 3) {
                tapCount = 0

                if (firstTapMillis!! >= (System.currentTimeMillis() - 500)) {
                    startActivity(Intent(context, EasterEggActivity::class.java))
                }

                firstTapMillis = null
            }
        }

        // Update button onClickListeners

        setupInfoButton(aboutData.bugURL, binding.reportBugButton)
        setupInfoButton(aboutData.donateURL, binding.donateButton)
        setupInfoButton(aboutData.sourceCodeURL, binding.sourceCodeButton)

        binding.licensesButton.setOnClickListener {
            startActivity(Intent(context, LicensesActivity::class.java))
        }

        binding.aboutKdeButton.setOnClickListener {
            startActivity(Intent(context, AboutKDEActivity::class.java))
        }

        setupInfoButton(aboutData.websiteURL, binding.websiteButton)

        // Update authors
        binding.authorsList.adapter = ListAdapter(requireContext(), aboutData.authors.map { AboutPersonEntryItem(it) }, false)
        if (aboutData.authorsFooterText != null) {
            binding.authorsFooterText.text = context?.getString(aboutData.authorsFooterText!!)
        }
    }

    override fun onDestroyView() {
        binding.authorsList.adapter = null
        super.onDestroyView()
    }

    private fun setupInfoButton(url: String?, button: FrameLayout) {
        if (url == null) {
            button.visibility = View.GONE
        } else {
            button.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
        }
    }

}