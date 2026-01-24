/*
 * SPDX-FileCopyrightText: 2019 Juan David Vega <jdvr.93@hotmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.extensions.viewBinding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.TrustedNetworkListBinding

class TrustedNetworksActivity : BaseActivity<TrustedNetworkListBinding>() {
    override val binding: TrustedNetworkListBinding by viewBinding(TrustedNetworkListBinding::inflate)
    private val trustedNetworks: MutableList<String> = mutableListOf()

    private val trustedNetworksView: ListView
        get() = binding.list
    private val allowAllCheckBox: CheckBox
        get() = binding.trustAllNetworksCheckBox
    private val trustedNetworkHelper: TrustedNetworkHelper by lazy {
        TrustedNetworkHelper(applicationContext) // Lazy to avoid creating it before onCreate, because it needs the context
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PackageManager.PERMISSION_GRANTED in grantResults) {
            allowAllCheckBox.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        trustedNetworks.addAll(trustedNetworkHelper.trustedNetworks)

        allowAllCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (trustedNetworkHelper.hasPermissions) {
                trustedNetworkHelper.allNetworksAllowed = isChecked
                updateTrustedNetworkListView()
                addNetworkButton()
            } else {
                allowAllCheckBox.isChecked = true // Disable unchecking it
                PermissionsAlertDialogFragment.Builder()
                    .setTitle(R.string.location_permission_needed_title)
                    .setMessage(R.string.location_permission_needed_desc)
                    .setPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    .setRequestCode(0)
                    .create().show(supportFragmentManager, null)
            }
        }
        allowAllCheckBox.isChecked = trustedNetworkHelper.allNetworksAllowed

        updateTrustedNetworkListView()
    }

    private fun updateEmptyListMessage() {
        val isVisible = trustedNetworks.isEmpty() && !trustedNetworkHelper.allNetworksAllowed
        binding.trustedNetworkListEmpty.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun updateTrustedNetworkListView() {
        val allAllowed = trustedNetworkHelper.allNetworksAllowed
        updateEmptyListMessage()
        trustedNetworksView.visibility = if (allAllowed) View.GONE else View.VISIBLE
        if (allAllowed) {
            return
        }
        trustedNetworksView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, trustedNetworks)
        trustedNetworksView.onItemClickListener =
            OnItemClickListener { _, _, position, _ ->
                val targetItem = trustedNetworks[position]
                AlertDialog.Builder(this@TrustedNetworksActivity)
                    .setMessage("Delete $targetItem ?")
                    .setPositiveButton("Yes") { _, _ ->
                        trustedNetworks.removeAt(position)
                        trustedNetworkHelper.trustedNetworks = trustedNetworks
                        (trustedNetworksView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                        addNetworkButton()
                        updateEmptyListMessage()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        addNetworkButton()
    }

    private fun addNetworkButton() {
        val addButton = binding.button1
        if (trustedNetworkHelper.allNetworksAllowed) {
            addButton.visibility = View.GONE
            return
        }
        val currentSSID = trustedNetworkHelper.currentSSID
        if (currentSSID != null && currentSSID !in trustedNetworks) {
            addButton.text = getString(R.string.add_trusted_network, currentSSID)
            addButton.setOnClickListener { v ->
                if (trustedNetworks.contains(currentSSID)) {
                    return@setOnClickListener
                }
                trustedNetworks.add(currentSSID)
                trustedNetworkHelper.trustedNetworks = trustedNetworks
                (trustedNetworksView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                v.visibility = View.GONE
                updateEmptyListMessage()
            }
            addButton.visibility = View.VISIBLE
        } else {
            addButton.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        this.onBackPressedDispatcher.onBackPressed()
        return true
    }
}
