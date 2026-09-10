/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class AboutData(@StringRes val name: Int,
                     @DrawableRes val icon: Int,
                     val versionName: String,
                     val bugURL: String? = null,
                     val websiteURL: String? = null,
                     val sourceCodeURL: String? = null,
                     val donateURL: String? = null,
                     @StringRes val authorsFooterText: Int? = null,
                     val authors: List<AboutPerson> = mutableListOf()
)
