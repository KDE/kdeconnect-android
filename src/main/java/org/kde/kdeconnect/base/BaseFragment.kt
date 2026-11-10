/*
 * SPDX-FileCopyrightText: 2025 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<VB: ViewBinding> : Fragment() {

    private var _binding: VB? = null

    protected val binding get() = _binding!!

    val hasBinding : Boolean get() = _binding != null

    abstract fun onInflateBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): VB

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setActionBarText(null, null) // Clear text from previous screen
        _binding = onInflateBinding(inflater, container, savedInstanceState)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    protected fun setActionBarText(title: CharSequence?, subtitle: CharSequence? = null) {
        (activity  as? AppCompatActivity?)?.supportActionBar?.apply {
            this.title = title
            this.subtitle = subtitle
        }
    }

    protected fun setActionBarSubtitle(subtitle: CharSequence? = null) {
        (activity  as? AppCompatActivity?)?.supportActionBar?.subtitle = subtitle
    }
}