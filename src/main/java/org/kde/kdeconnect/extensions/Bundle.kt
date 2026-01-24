/*
 * SPDX-FileCopyrightText: 2025 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.extensions

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat


inline fun <reified T> Bundle.getParcelableCompat(key: String): T? {
    return BundleCompat.getParcelable(this, key, T::class.java)
}

inline fun <reified T> Bundle.getParcelableArrayListCompat(key: String): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList(this, key, T::class.java)
}

inline fun <reified T: Parcelable> Bundle.getParcelableArrayCompat(key: String): Array<Parcelable>? {
    return BundleCompat.getParcelableArray(this, key, T::class.java)
}