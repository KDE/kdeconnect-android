/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo906@mail.ru>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mpris

import android.util.Log
import androidx.collection.LongSparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Provides access to adapter fragments
 */
abstract class ExtendedFragmentAdapter : FragmentStateAdapter {
    constructor(fragmentActivity: FragmentActivity) : super(fragmentActivity)

    @Suppress("unused")
    constructor(fragment: Fragment) : super(fragment)

    @Suppress("unused")
    constructor(fragmentManager: FragmentManager, lifecycle: Lifecycle) : super(fragmentManager, lifecycle)

    private fun getReflectionFragments(): LongSparseArray<Fragment>? {
        try {
            val fragmentsField = FragmentStateAdapter::class.java.getDeclaredField("mFragments")
            fragmentsField.isAccessible = true
            val fieldData = fragmentsField[this]
            if (fieldData is LongSparseArray<*>) {
                @Suppress("UNCHECKED_CAST")
                return fieldData as LongSparseArray<Fragment>
            }
            fragmentsField.isAccessible = false
        } catch (e: Throwable) {
            when (e) {
                is NoSuchFieldException, is IllegalAccessException -> {
                    Log.e("ExtendedFragmentAdapter", "Failed to get mFragments field", e)
                }
                else -> throw e
            }
        }

        return null
    }

    protected fun getFragment(position: Int): Fragment? {
        val adapterFragments = getReflectionFragments() ?: return null

        return adapterFragments[position.toLong()]
    }
}
