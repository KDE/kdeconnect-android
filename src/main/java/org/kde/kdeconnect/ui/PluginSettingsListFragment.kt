/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.DeviceStats
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect_tp.R

class PluginSettingsListFragment : PreferenceFragmentCompat() {
    private var callback: PluginPreference.PluginPreferenceCallback? = null
    private var recyclerViewLayoutManagerState: Parcelable? = null

    /*
        https://bricolsoftconsulting.com/state-preservation-in-backstack-fragments/
        When adding a fragment to the backstack the fragments onDestroyView is called (which releases
        the RecyclerView) but the fragments onSaveInstanceState is not called. When the fragment is destroyed later
        on, its onSaveInstanceState() is called but I don't have access to the RecyclerView or it's LayoutManager any more
     */
    private var stateSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val activity = requireActivity()
        if (activity is PluginPreference.PluginPreferenceCallback) {
            callback = activity
        } else {
            throw RuntimeException("${activity.javaClass.simpleName} must implement PluginPreference.PluginPreferenceCallback")
        }

        super.onCreate(savedInstanceState)

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_RECYCLERVIEW_LAYOUTMANAGER_STATE)) {
            @Suppress("DEPRECATION")
            recyclerViewLayoutManagerState = savedInstanceState.getParcelable(KEY_RECYCLERVIEW_LAYOUTMANAGER_STATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callback = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceScreen = preferenceManager.createPreferenceScreen(preferenceManager.context)
        setPreferenceScreen(preferenceScreen)

        val deviceId = requireArguments().getString(ARG_DEVICE_ID) ?: return
        val cb = callback ?: return

        val device = KdeConnect.getInstance().getDevice(deviceId)
        if (device == null) {
            val activity = requireActivity()
            activity.runOnUiThread(activity::finish)
            return
        }

        val plugins = PluginFactory.sortPluginList(device.supportedPlugins)
        for (pluginKey in plugins) {
            val pref = PluginPreference(preferenceManager.context, pluginKey, device, cb)
            preferenceScreen.addPreference(pref)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            return // PacketStats not working in API < 24
        }
        val deviceId = requireArguments().getString(ARG_DEVICE_ID) ?: return

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(R.string.plugin_stats)
                    .setOnMenuItemClickListener {
                        val stats = DeviceStats.getStatsForDevice(deviceId)
                        val alertDialog = MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.plugin_stats)
                            .setPositiveButton(R.string.ok) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setMessage(stats)
                            .show()
                        val messageView = alertDialog.findViewById<View?>(android.R.id.message)
                        if (messageView is TextView) {
                            messageView.setTextIsSelectable(true)
                        }
                        true
                    }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        val adapter = super.onCreateAdapter(preferenceScreen)

        /*
            The layoutmanager's state (e.g. scroll position) can only be restored when the recyclerView's
            adapter has been re-populated with data.
         */
        if (recyclerViewLayoutManagerState != null) {
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    listView?.layoutManager?.onRestoreInstanceState(recyclerViewLayoutManagerState)
                    recyclerViewLayoutManagerState = null
                    adapter.unregisterAdapterDataObserver(this)
                }
            })
        }

        return adapter
    }

    override fun onPause() {
        super.onPause()
        stateSaved = false
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.device_menu_plugins)
    }

    override fun onDestroyView() {
        if (!stateSaved) {
            val listView = listView
            if (listView?.layoutManager != null) {
                recyclerViewLayoutManagerState = listView.layoutManager?.onSaveInstanceState()
            }
        }

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        var layoutManagerState = recyclerViewLayoutManagerState

        val listView = listView
        if (listView?.layoutManager != null) {
            layoutManagerState = listView.layoutManager?.onSaveInstanceState()
        }

        if (layoutManagerState != null) {
            outState.putParcelable(KEY_RECYCLERVIEW_LAYOUTMANAGER_STATE, layoutManagerState)
        }

        stateSaved = true
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val KEY_RECYCLERVIEW_LAYOUTMANAGER_STATE = "RecyclerViewLayoutmanagerState"

        fun newInstance(deviceId: String): PluginSettingsListFragment {
            return PluginSettingsListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }
}
