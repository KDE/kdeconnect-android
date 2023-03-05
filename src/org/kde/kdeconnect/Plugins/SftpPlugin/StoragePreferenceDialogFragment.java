package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.PreferenceDialogFragmentCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.StorageHelper;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.FragmentStoragePreferenceDialogBinding;

public class StoragePreferenceDialogFragment extends PreferenceDialogFragmentCompat implements TextWatcher {
    private static final int REQUEST_CODE_DOCUMENT_TREE = 1001;

    //When state is restored I cannot determine if an error is going to be displayed on one of the TextInputEditText's or not so I have to remember if the dialog's positive button was enabled or not
    private static final String KEY_POSITIVE_BUTTON_ENABLED = "PositiveButtonEnabled";
    private static final String KEY_STORAGE_INFO = "StorageInfo";
    private static final String KEY_TAKE_FLAGS = "TakeFlags";

    private FragmentStoragePreferenceDialogBinding binding;

    private Callback callback;
    private Drawable arrowDropDownDrawable;
    private Button positiveButton;
    private boolean stateRestored;
    private boolean enablePositiveButton;
    private SftpPlugin.StorageInfo storageInfo;
    private int takeFlags;

    public static StoragePreferenceDialogFragment newInstance(String key) {
        StoragePreferenceDialogFragment fragment = new StoragePreferenceDialogFragment();

        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        stateRestored = false;
        enablePositiveButton = true;

        if (savedInstanceState != null) {
            stateRestored = true;
            enablePositiveButton = savedInstanceState.getBoolean(KEY_POSITIVE_BUTTON_ENABLED);
            takeFlags = savedInstanceState.getInt(KEY_TAKE_FLAGS, 0);
            try {
                JSONObject jsonObject = new JSONObject(savedInstanceState.getString(KEY_STORAGE_INFO, "{}"));
                storageInfo = SftpPlugin.StorageInfo.fromJSON(jsonObject);
            } catch (JSONException ignored) {}
        }

        Drawable drawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_drop_down_24px);
        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(drawable, ContextCompat.getColor(requireContext(),
                    android.R.color.darker_gray));
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            arrowDropDownDrawable = drawable;
        }
    }

    void setCallback(Callback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog dialog = (AlertDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialog1 -> {
            AlertDialog alertDialog = (AlertDialog) dialog1;
            positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(enablePositiveButton);
        });

        return dialog;
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        binding = FragmentStoragePreferenceDialogBinding.bind(view);

        binding.storageLocation.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            //For API >= 26 we can also set Extra: DocumentsContract.EXTRA_INITIAL_URI
            startActivityForResult(intent, REQUEST_CODE_DOCUMENT_TREE);
        });

        binding.storageDisplayName.setFilters(new InputFilter[]{new FileSeparatorCharFilter()});
        binding.storageDisplayName.addTextChangedListener(this);

        if (getPreference().getKey().equals(getString(R.string.sftp_preference_key_add_storage))) {
            if (!stateRestored) {
                enablePositiveButton = false;
                binding.storageLocation.setText(requireContext().getString(R.string.sftp_storage_preference_click_to_select));
            }

            boolean isClickToSelect = TextUtils.equals(binding.storageLocation.getText(),
                    getString(R.string.sftp_storage_preference_click_to_select));

            TextViewCompat.setCompoundDrawablesRelative(binding.storageLocation, null, null,
                    isClickToSelect ? arrowDropDownDrawable : null, null);
            binding.storageLocation.setEnabled(isClickToSelect);
            binding.storageLocation.setFocusable(false);
            binding.storageLocation.setFocusableInTouchMode(false);

            binding.storageDisplayName.setEnabled(!isClickToSelect);
        } else {
            if (!stateRestored) {
                StoragePreference preference = (StoragePreference) getPreference();
                SftpPlugin.StorageInfo info = preference.getStorageInfo();

                if (info == null) {
                    throw new RuntimeException("Cannot edit a StoragePreference that does not have its storageInfo set");
                }

                storageInfo = SftpPlugin.StorageInfo.copy(info);

                binding.storageLocation.setText(DocumentsContract.getTreeDocumentId(storageInfo.uri));

                binding.storageDisplayName.setText(storageInfo.displayName);
            }

            TextViewCompat.setCompoundDrawablesRelative(binding.storageLocation, null, null, null, null);
            binding.storageLocation.setEnabled(false);
            binding.storageLocation.setFocusable(false);
            binding.storageLocation.setFocusableInTouchMode(false);

            binding.storageDisplayName.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_DOCUMENT_TREE:
                Uri uri = data.getData();
                takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                if (uri == null) {
                    return;
                }

                CallbackResult result = callback.isUriAllowed(uri);

                if (result.isAllowed) {
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    String displayName = StorageHelper.getDisplayName(requireContext(), uri);

                    storageInfo = new SftpPlugin.StorageInfo(displayName, uri);

                    binding.storageLocation.setText(documentId);
                    TextViewCompat.setCompoundDrawablesRelative(binding.storageLocation, null, null, null, null);
                    binding.storageLocation.setError(null);
                    binding.storageLocation.setEnabled(false);

                    // TODO: Show name as used in android's picker app but I don't think it's possible to get that, everything I tried throws PermissionDeniedException
                    binding.storageDisplayName.setText(displayName);
                    binding.storageDisplayName.setEnabled(true);
                } else {
                    binding.storageLocation.setError(result.errorMessage);
                    setPositiveButtonEnabled(false);
                }
                break;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_POSITIVE_BUTTON_ENABLED, positiveButton.isEnabled());
        outState.putInt(KEY_TAKE_FLAGS, takeFlags);

        if (storageInfo != null) {
            try {
                outState.putString(KEY_STORAGE_INFO, storageInfo.toJSON().toString());
            } catch (JSONException ignored) {}
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            storageInfo.displayName = binding.storageDisplayName.getText().toString();

            if (getPreference().getKey().equals(getString(R.string.sftp_preference_key_add_storage))) {
                callback.addNewStoragePreference(storageInfo, takeFlags);
            } else {
                ((StoragePreference)getPreference()).setStorageInfo(storageInfo);
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        //Don't care
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        //Don't care
    }

    @Override
    public void afterTextChanged(Editable s) {
        String displayName = s.toString();

        StoragePreference storagePreference = (StoragePreference) getPreference();
        SftpPlugin.StorageInfo storageInfo = storagePreference.getStorageInfo();

        if (storageInfo == null || !storageInfo.displayName.equals(displayName)) {
            CallbackResult result = callback.isDisplayNameAllowed(displayName);

            if (result.isAllowed) {
                setPositiveButtonEnabled(true);
            } else {
                setPositiveButtonEnabled(false);
                binding.storageDisplayName.setError(result.errorMessage);
            }
        }
    }

    private void setPositiveButtonEnabled(boolean enabled) {
        if (positiveButton != null) {
            positiveButton.setEnabled(enabled);
        } else {
            enablePositiveButton = enabled;
        }
    }

    private static class FileSeparatorCharFilter implements InputFilter {
        //TODO: Add more chars to refuse?
        //https://www.cyberciti.biz/faq/linuxunix-rules-for-naming-file-and-directory-names/
        String notAllowed = "/\\><|:&?*";

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            boolean keepOriginal = true;
            StringBuilder sb = new StringBuilder(end - start);
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);

                if (notAllowed.indexOf(c) < 0) {
                    sb.append(c);
                } else {
                    keepOriginal = false;
                    sb.append("_");
                }
            }

            if (keepOriginal) {
                return null;
            } else {
                if (source instanceof Spanned) {
                    SpannableString sp = new SpannableString(sb);
                    TextUtils.copySpansFrom((Spanned) source, start, sb.length(), null, sp, 0);
                    return sp;
                } else {
                    return sb;
                }
            }
        }
    }

    static class CallbackResult {
        boolean isAllowed;
        String errorMessage;
    }

    interface Callback {
        @NonNull CallbackResult isDisplayNameAllowed(@NonNull String displayName);
        @NonNull CallbackResult isUriAllowed(@NonNull Uri uri);
        void addNewStoragePreference(@NonNull SftpPlugin.StorageInfo storageInfo, int takeFlags);
    }
}
