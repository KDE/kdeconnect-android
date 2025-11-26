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

    protected val mActivity
        get() = activity as? AppCompatActivity?

    protected abstract fun getActionBarTitle(): String?

    abstract fun onInflateBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = onInflateBinding(inflater, container, savedInstanceState)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setActionBarText()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setActionBarText(){
        mActivity?.supportActionBar?.title = getActionBarTitle()
        mActivity?.supportActionBar?.subtitle = null
    }
}