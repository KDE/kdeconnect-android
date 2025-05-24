/*
 * SPDX-FileCopyrightText: 2025 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.extensions

import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat


inline fun <reified T> Intent.getParcelableCompat(key: String): T? {
    return IntentCompat.getParcelableExtra(this, key, T::class.java)
}

inline fun <reified T> Intent.getParcelableArrayListCompat(key: String): ArrayList<T>? {
    return IntentCompat.getParcelableArrayListExtra(this, key, T::class.java)
}

inline fun <reified T: Parcelable> Intent.getParcelableArrayCompat(key: String): Array<Parcelable>? {
    return IntentCompat.getParcelableArrayExtra(this, key, T::class.java)
}