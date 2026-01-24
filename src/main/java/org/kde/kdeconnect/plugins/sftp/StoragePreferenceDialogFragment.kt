/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.sftp

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.TextViewCompat
import androidx.preference.PreferenceDialogFragmentCompat
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.helpers.StorageHelper
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.StorageInfo.Companion.fromJSON
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.FragmentStoragePreferenceDialogBinding

class StoragePreferenceDialogFragment : PreferenceDialogFragmentCompat(), TextWatcher {
    private var binding: FragmentStoragePreferenceDialogBinding? = null

    var callback: Callback? = null
    private var arrowDropDownDrawable: Drawable? = null
    private var positiveButton: Button? = null
    private var stateRestored = false
    private var enablePositiveButton = false
    private var storageInfo: SftpPlugin.StorageInfo? = null
    private var takeFlags = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stateRestored = false
        enablePositiveButton = true

        if (savedInstanceState != null) {
            stateRestored = true
            enablePositiveButton = savedInstanceState.getBoolean(KEY_POSITIVE_BUTTON_ENABLED)
            takeFlags = savedInstanceState.getInt(KEY_TAKE_FLAGS, 0)
            try {
                val jsonObject = JSONObject(savedInstanceState.getString(KEY_STORAGE_INFO, "{}"))
                storageInfo = fromJSON(jsonObject)
            } catch (ignored: JSONException) {
            }
        }

        var drawable =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_drop_down_24px)
        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable)
            DrawableCompat.setTint(
                drawable, ContextCompat.getColor(
                    requireContext(),
                    android.R.color.darker_gray
                )
            )
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            arrowDropDownDrawable = drawable
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        dialog.setOnShowListener { alertDialog: DialogInterface ->
            positiveButton = (alertDialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = enablePositiveButton
            }
        }

        return dialog
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val binding = FragmentStoragePreferenceDialogBinding.bind(view).also {
            this.binding = it
        }

        binding.storageLocation.setOnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            // For API >= 26 we can also set Extra: DocumentsContract.EXTRA_INITIAL_URI
            startActivityForResult(intent, REQUEST_CODE_DOCUMENT_TREE)
        }

        binding.storageDisplayName.filters = arrayOf<InputFilter>(FileSeparatorCharFilter())
        binding.storageDisplayName.addTextChangedListener(this)

        if (preference.key == getString(R.string.sftp_preference_key_add_storage)) {
            if (!stateRestored) {
                enablePositiveButton = false
                binding.storageLocation.setText(requireContext().getString(R.string.sftp_storage_preference_click_to_select))
            }

            val isClickToSelect = TextUtils.equals(
                binding.storageLocation.text,
                getString(R.string.sftp_storage_preference_click_to_select)
            )

            TextViewCompat.setCompoundDrawablesRelative(
                binding.storageLocation, null, null,
                if (isClickToSelect) arrowDropDownDrawable else null, null
            )
            binding.storageLocation.isEnabled = isClickToSelect
            binding.storageLocation.isFocusable = false
            binding.storageLocation.isFocusableInTouchMode = false

            binding.storageDisplayName.isEnabled = !isClickToSelect
        } else {
            if (!stateRestored) {
                val preference = preference as StoragePreference
                val info = preference.storageInfo
                    ?: throw RuntimeException("Cannot edit a StoragePreference that does not have its storageInfo set")

                storageInfo = info.copy()

                binding.storageLocation.setText(DocumentsContract.getTreeDocumentId(storageInfo!!.uri))

                binding.storageDisplayName.setText(storageInfo!!.displayName)
            }

            TextViewCompat.setCompoundDrawablesRelative(
                binding.storageLocation,
                null,
                null,
                null,
                null
            )
            binding.storageLocation.isEnabled = false
            binding.storageLocation.isFocusable = false
            binding.storageLocation.isFocusableInTouchMode = false

            binding.storageDisplayName.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        binding = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {
            return
        }

        when (requestCode) {
            REQUEST_CODE_DOCUMENT_TREE -> {
                val uri = data!!.data
                takeFlags =
                    data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                if (uri == null) {
                    return
                }

                val result = callback!!.isUriAllowed(uri)

                if (result.isAllowed) {
                    val documentId = DocumentsContract.getTreeDocumentId(uri)
                    val displayName = StorageHelper.getDisplayName(uri)

                    storageInfo = SftpPlugin.StorageInfo(displayName, uri)

                    binding!!.storageLocation.setText(documentId)
                    TextViewCompat.setCompoundDrawablesRelative(
                        binding!!.storageLocation,
                        null,
                        null,
                        null,
                        null
                    )
                    binding!!.storageLocation.error = null
                    binding!!.storageLocation.isEnabled = false

                    // TODO: Show name as used in android's picker app but I don't think it's possible to get that, everything I tried throws PermissionDeniedException
                    binding!!.storageDisplayName.setText(displayName)
                    binding!!.storageDisplayName.isEnabled = true
                } else {
                    binding!!.storageLocation.error = result.errorMessage
                    setPositiveButtonEnabled(false)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(KEY_POSITIVE_BUTTON_ENABLED, positiveButton!!.isEnabled)
        outState.putInt(KEY_TAKE_FLAGS, takeFlags)

        if (storageInfo != null) {
            try {
                outState.putString(KEY_STORAGE_INFO, storageInfo!!.toJSON().toString())
            } catch (ignored: JSONException) {
            }
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return

        storageInfo!!.displayName = binding!!.storageDisplayName.text.toString()

        if (preference.key == getString(R.string.sftp_preference_key_add_storage)) {
            callback!!.addNewStoragePreference(storageInfo!!, takeFlags)
        } else {
            (preference as StoragePreference).setStorageInfo(storageInfo!!)
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // Don't care
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // Don't care
    }

    override fun afterTextChanged(s: Editable) {
        val displayName = s.toString()

        val storagePreference = preference as StoragePreference
        val storageInfo = storagePreference.storageInfo

        if (storageInfo != null && storageInfo.displayName == displayName) return

        val result = callback!!.isDisplayNameAllowed(displayName)

        if (result.isAllowed) {
            setPositiveButtonEnabled(true)
        } else {
            setPositiveButtonEnabled(false)
            binding!!.storageDisplayName.error = result.errorMessage
        }
    }

    private fun setPositiveButtonEnabled(enabled: Boolean) {
        if (positiveButton != null) {
            positiveButton!!.isEnabled = enabled
        } else {
            enablePositiveButton = enabled
        }
    }

    private class FileSeparatorCharFilter : InputFilter {
        // TODO: Add more chars to refuse?
        // https://www.cyberciti.biz/faq/linuxunix-rules-for-naming-file-and-directory-names/
        var notAllowed: String = "/\\><|:&?*"

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            var keepOriginal = true
            val sb = StringBuilder(end - start)
            for (i in start until end) {
                val c = source[i]

                if (notAllowed.indexOf(c) < 0) {
                    sb.append(c)
                } else {
                    keepOriginal = false
                    sb.append("_")
                }
            }

            if (keepOriginal) {
                return null
            } else {
                if (source is Spanned) {
                    val sp = SpannableString(sb)
                    TextUtils.copySpansFrom(source, start, sb.length, null, sp, 0)
                    return sp
                } else {
                    return sb
                }
            }
        }
    }

    class CallbackResult {
        @JvmField
        var isAllowed: Boolean = false
        @JvmField
        var errorMessage: String? = null
    }

    interface Callback {
        fun isDisplayNameAllowed(displayName: String): CallbackResult
        fun isUriAllowed(uri: Uri): CallbackResult
        fun addNewStoragePreference(storageInfo: SftpPlugin.StorageInfo, takeFlags: Int)
    }

    companion object {
        private const val REQUEST_CODE_DOCUMENT_TREE = 1001

        // When state is restored I cannot determine if an error is going to be displayed on one of the TextInputEditText's or not so I have to remember if the dialog's positive button was enabled or not
        private const val KEY_POSITIVE_BUTTON_ENABLED = "PositiveButtonEnabled"
        private const val KEY_STORAGE_INFO = "StorageInfo"
        private const val KEY_TAKE_FLAGS = "TakeFlags"

        @JvmStatic
        fun newInstance(key: String): StoragePreferenceDialogFragment {
            return StoragePreferenceDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_KEY, key)
                }
            }
        }
    }
}
