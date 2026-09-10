/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

/**
* Add authors and credits here
 */
val applicationAboutData = AboutData(
        name = R.string.kde_connect,
        icon = R.drawable.icon,
        versionName = BuildConfig.VERSION_NAME,
        bugURL = "https://bugs.kde.org/enter_bug.cgi?product=kdeconnect&amp;component=android-application",
        websiteURL = "https://kdeconnect.kde.org/",
        sourceCodeURL = "https://invent.kde.org/network/kdeconnect-android/",
        donateURL = "https://kde.org/community/donations/?app=kdeconnect-android",
        authorsFooterText = R.string.everyone_else,
        authors = listOf(
            // Have you made some contributions and think your name should be here? Open a MR to add yourself to the list :)
            AboutPerson("Albert Vaca Cintora", R.string.maintainer_and_developer, "albertvaka+kde@gmail.com"),
            AboutPerson("Aleix Pol", R.string.developer, "aleixpol@kde.org"),
            AboutPerson("Inoki Shaw", R.string.apple_support, "veyx.shaw@gmail.com"),
            AboutPerson("Matthijs Tijink", R.string.developer, "matthijstijink@gmail.com"),
            AboutPerson("Nicolas Fella", R.string.developer, "nicolas.fella@gmx.de"),
            AboutPerson("Philip Cohn-Cort", R.string.developer, "cliabhach@gmail.com"),
            AboutPerson("Piyush Aggarwal", R.string.developer, "piyushaggarwal002@gmail.com"),
            AboutPerson("Simon Redman", R.string.developer, "simon@ergotech.com"),
            AboutPerson("Erik Duisters", R.string.developer, "e.duisters1@gmail.com"),
            AboutPerson("Isira Seneviratne", R.string.developer, "isirasen96@gmail.com"),
            AboutPerson("Vineet Garg", R.string.developer, "grg.vineet@gmail.com"),
            AboutPerson("Anjani Kumar", R.string.bug_fixes_and_general_improvements, "anjanik012@gmail.com"),
            AboutPerson("Samoilenko Yuri", R.string.samoilenko_yuri_task, "kinnalru@gmail.com"),
            AboutPerson("Aniket Kumar", R.string.aniket_kumar_task, "anikketkumar786@gmail.com"),
            AboutPerson("Àlex Fiestas", R.string.alex_fiestas_task, "afiestas@kde.org"),
            AboutPerson("Daniel Tang", R.string.bug_fixes_and_general_improvements, "danielzgtg.opensource@gmail.com"),
            AboutPerson("Maxim Leshchenko", R.string.maxim_leshchenko_task, "cnmaks90@gmail.com"),
            AboutPerson("Holger Kaelberer", R.string.holger_kaelberer_task, "holger.k@elberer.de"),
            AboutPerson("Saikrishna Arcot", R.string.saikrishna_arcot_task, "saiarcot895@gmail.com"),
            AboutPerson("ShellWen Chen", R.string.shellwen_chen_task, "me@shellwen.com"),
        ),
    )