/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.helpers

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

// the default Preference Category only shows a one-line summary
class LongSummaryPreferenceCategory : PreferenceCategory {
    constructor(ctx: Context, attrs: AttributeSet?, defStyle: Int) : super(ctx, attrs, defStyle)

    constructor(ctx: Context, attrs: AttributeSet?) : super(ctx, attrs)

    constructor(ctx: Context) : super(ctx)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as? TextView
        summary?.maxLines = 3
        summary?.isSingleLine = false
    }
}
