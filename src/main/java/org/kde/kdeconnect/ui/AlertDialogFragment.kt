/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class AlertDialogFragment : DialogFragment() {

    @StringRes
    private var titleResId = 0
    private var title: String? = null
    @StringRes
    private var messageResId = 0
    @StringRes
    private var positiveButtonResId = 0
    @StringRes
    private var negativeButtonResId = 0
    @LayoutRes
    private var customViewResId = 0

    var callback: Callback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
            ?: throw RuntimeException("You need to instantiate a new AlertDialogFragment using AlertDialogFragment.Builder")

        titleResId = args.getInt(KEY_TITLE_RES_ID)
        title = args.getString(KEY_TITLE)
        messageResId = args.getInt(KEY_MESSAGE_RES_ID)
        positiveButtonResId = args.getInt(KEY_POSITIVE_BUTTON_TEXT_RES_ID)
        negativeButtonResId = args.getInt(KEY_NEGATIVE_BUTTON_TEXT_RES_ID)
        customViewResId = args.getInt(KEY_CUSTOM_VIEW_RES_ID)
    }

    @SuppressLint("ResourceType")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleString = if (titleResId > 0) getString(titleResId) else title

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleString)
            // Set listeners to null so dialog does not auto-dismiss
            .setPositiveButton(positiveButtonResId, null)

        if (negativeButtonResId != 0) {
            builder.setNegativeButton(negativeButtonResId, null)
        }
        if (customViewResId != 0) {
            builder.setView(customViewResId)
        } else {
            builder.setMessage(messageResId)
        }

        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? AlertDialog ?: return
        // Set custom click listeners to prevent auto-dismiss
        if (positiveButtonResId != 0) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (callback?.onPositiveButtonClicked() != false) {
                    dismiss()
                }
            }
        }
        if (negativeButtonResId != 0) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                callback?.onNegativeButtonClicked()
                dismiss()
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        callback?.onCancel()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        callback?.onDismiss()
    }

    abstract class AbstractBuilder<B : AbstractBuilder<B, F>, F : DialogFragment> {
        @JvmField
        protected val args = Bundle()

        abstract fun getThis(): B

        fun setTitle(@StringRes titleResId: Int): B {
            args.putInt(KEY_TITLE_RES_ID, titleResId)
            return getThis()
        }

        fun setTitle(title: String): B {
            args.putString(KEY_TITLE, title)
            return getThis()
        }

        fun setMessage(@StringRes messageResId: Int): B {
            args.putInt(KEY_MESSAGE_RES_ID, messageResId)
            return getThis()
        }

        fun setPositiveButton(@StringRes positiveButtonResId: Int): B {
            args.putInt(KEY_POSITIVE_BUTTON_TEXT_RES_ID, positiveButtonResId)
            return getThis()
        }

        fun setNegativeButton(@StringRes negativeButtonResId: Int): B {
            args.putInt(KEY_NEGATIVE_BUTTON_TEXT_RES_ID, negativeButtonResId)
            return getThis()
        }

        fun setView(@LayoutRes customViewResId: Int): B {
            args.putInt(KEY_CUSTOM_VIEW_RES_ID, customViewResId)
            return getThis()
        }

        protected abstract fun createFragment(): F

        fun create(): F {
            val fragment = createFragment()
            fragment.arguments = args
            return fragment
        }
    }

    open class Builder : AbstractBuilder<Builder, AlertDialogFragment>() {
        override fun getThis(): Builder = this

        override fun createFragment(): AlertDialogFragment = AlertDialogFragment()
    }

    abstract class Callback {
        // Return true to close the dialog, or false to keep it open
        open fun onPositiveButtonClicked(): Boolean = true
        open fun onNegativeButtonClicked() {}
        open fun onDismiss() {}
        open fun onCancel() {}
    }

    companion object {
        private const val KEY_TITLE_RES_ID = "TitleResId"
        private const val KEY_TITLE = "Title"
        private const val KEY_MESSAGE_RES_ID = "MessageResId"
        private const val KEY_POSITIVE_BUTTON_TEXT_RES_ID = "PositiveButtonResId"
        private const val KEY_NEGATIVE_BUTTON_TEXT_RES_ID = "NegativeButtonResId"
        private const val KEY_CUSTOM_VIEW_RES_ID = "CustomViewResId"
    }
}
