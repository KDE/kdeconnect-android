/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.screen.about.AboutScreen
import org.kde.kdeconnect_tp.R

class AboutFragment : Fragment() {
    companion object {
        @JvmStatic
        fun newInstance(): Fragment = AboutFragment()
    }

    private lateinit var aboutData: AboutData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aboutData = applicationAboutData
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KdeTheme(context) {
                    AboutScreen(
                        aboutData = aboutData,
                        onEasterEggTriggered = {
                            startActivity(Intent(context, EasterEggActivity::class.java))
                        },
                        onReportBugClicked = { openUrl(aboutData.bugURL) },
                        onDonateClicked = { openUrl(aboutData.donateURL) },
                        onSourceCodeClicked = { openUrl(aboutData.sourceCodeURL) },
                        onLicensesClicked = {
                            startActivity(Intent(context, LicensesActivity::class.java))
                        },
                        onAboutKdeClicked = {
                            startActivity(Intent(context, AboutKDEActivity::class.java))
                        },
                        onWebsiteClicked = { openUrl(aboutData.websiteURL) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.let { actionBar ->
            actionBar.title = getString(R.string.about)
            actionBar.subtitle = null
        }
    }

    private fun openUrl(url: String?) {
        url?.let { startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
    }

}
