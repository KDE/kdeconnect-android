/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp

import android.content.Context
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.view.View
import android.widget.CheckBox
import androidx.preference.DialogPreference
import androidx.preference.PreferenceViewHolder
import org.kde.kdeconnect_tp.R

class StoragePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    DialogPreference(
        context, attrs
    ) {
    var storageInfo: SftpPlugin.StorageInfo? = null
        private set
    private var onLongClickListener: OnLongClickListener? = null

    lateinit var checkbox: CheckBox
    var inSelectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    init {
        dialogLayoutResource = R.layout.fragment_storage_preference_dialog
        widgetLayoutResource = R.layout.preference_checkbox
        isPersistent = false
    }

    fun setOnLongClickListener(listener: OnLongClickListener?) {
        this.onLongClickListener = listener
    }

    fun setStorageInfo(storageInfo: SftpPlugin.StorageInfo) {
        if (this.storageInfo != null && (this.storageInfo == storageInfo)) {
            return
        }

        if (callChangeListener(storageInfo)) {
            setStorageInfoInternal(storageInfo)
        }
    }

    private fun setStorageInfoInternal(storageInfo: SftpPlugin.StorageInfo) {
        this.storageInfo = storageInfo

        title = storageInfo.displayName
        summary = DocumentsContract.getTreeDocumentId(storageInfo.uri)
    }

    override fun setDefaultValue(defaultValue: Any?) {
        require(defaultValue == null || defaultValue is SftpPlugin.StorageInfo) {
            "StoragePreference defaultValue must be null or an instance of StfpPlugin.StorageInfo"
        }
        super.setDefaultValue(defaultValue)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        if (defaultValue != null) {
            setStorageInfoInternal(defaultValue as SftpPlugin.StorageInfo)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        checkbox = holder.itemView.findViewById<View>(R.id.checkbox) as CheckBox
        checkbox.visibility = if (inSelectionMode) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnLongClickListener {
            onLongClickListener?.let {
                it.onLongClick(this@StoragePreference)
                true
            } ?: false
        }
    }

    override fun onClick() {
        if (inSelectionMode) {
            checkbox.isChecked = !checkbox.isChecked
            return
        }

        super.onClick()
    }

    interface OnLongClickListener {
        fun onLongClick(storagePreference: StoragePreference)
    }
}
