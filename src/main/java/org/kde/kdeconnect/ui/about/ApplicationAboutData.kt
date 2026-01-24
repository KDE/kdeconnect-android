/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.content.Context
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

/**
* Add authors and credits here
 */
fun getApplicationAboutData(context: Context): AboutData {
    val aboutData = AboutData(context.getString(R.string.kde_connect), R.drawable.icon, BuildConfig.VERSION_NAME,
                              context.getString(R.string.report_bug_url), context.getString(R.string.website_url), context.getString(R.string.source_code_url), context.getString(R.string.donate_url),
                              R.string.everyone_else)

    aboutData.authors += AboutPerson("Albert Vaca Cintora", R.string.maintainer_and_developer, "albertvaka+kde@gmail.com")
    aboutData.authors += AboutPerson("Aleix Pol", R.string.developer, "aleixpol@kde.org")
    aboutData.authors += AboutPerson("Inoki Shaw", R.string.apple_support, "veyx.shaw@gmail.com")
    aboutData.authors += AboutPerson("Matthijs Tijink", R.string.developer, "matthijstijink@gmail.com")
    aboutData.authors += AboutPerson("Nicolas Fella", R.string.developer, "nicolas.fella@gmx.de")
    aboutData.authors += AboutPerson("Philip Cohn-Cort", R.string.developer, "cliabhach@gmail.com")
    aboutData.authors += AboutPerson("Piyush Aggarwal", R.string.developer, "piyushaggarwal002@gmail.com")
    aboutData.authors += AboutPerson("Simon Redman", R.string.developer, "simon@ergotech.com")
    aboutData.authors += AboutPerson("Erik Duisters", R.string.developer, "e.duisters1@gmail.com")
    aboutData.authors += AboutPerson("Isira Seneviratne", R.string.developer, "isirasen96@gmail.com")
    aboutData.authors += AboutPerson("Vineet Garg", R.string.developer, "grg.vineet@gmail.com")
    aboutData.authors += AboutPerson("Anjani Kumar", R.string.bug_fixes_and_general_improvements, "anjanik012@gmail.com")
    aboutData.authors += AboutPerson("Samoilenko Yuri", R.string.samoilenko_yuri_task, "kinnalru@gmail.com")
    aboutData.authors += AboutPerson("Aniket Kumar", R.string.aniket_kumar_task, "anikketkumar786@gmail.com")
    aboutData.authors += AboutPerson("Àlex Fiestas", R.string.alex_fiestas_task, "afiestas@kde.org")
    aboutData.authors += AboutPerson("Daniel Tang", R.string.bug_fixes_and_general_improvements, "danielzgtg.opensource@gmail.com")
    aboutData.authors += AboutPerson("Maxim Leshchenko", R.string.maxim_leshchenko_task, "cnmaks90@gmail.com")
    aboutData.authors += AboutPerson("Holger Kaelberer", R.string.holger_kaelberer_task, "holger.k@elberer.de")
    aboutData.authors += AboutPerson("Saikrishna Arcot", R.string.saikrishna_arcot_task, "saiarcot895@gmail.com")
    aboutData.authors += AboutPerson("ShellWen Chen", R.string.shellwen_chen_task, "me@shellwen.com")

    // Have you made some contributions and think your name should be here? Open a MR to add yourself to the list :)

    return aboutData
}
