/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import androidx.annotation.StringRes

class AboutPerson @JvmOverloads constructor(
    val name: String,
    @StringRes val task: Int? = null,
    val emailAddress: String? = null,
    val webAddress: String? = null
)
