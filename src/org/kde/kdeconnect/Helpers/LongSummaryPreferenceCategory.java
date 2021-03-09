/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

// the default Preference Category only shows a one-line summary
public class LongSummaryPreferenceCategory extends PreferenceCategory {
    public LongSummaryPreferenceCategory(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
    }

    public LongSummaryPreferenceCategory(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxLines(3);
        summary.setSingleLine(false);
    }

}