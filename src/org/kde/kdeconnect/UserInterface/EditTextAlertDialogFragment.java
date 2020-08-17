/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.app.Dialog;
import android.os.Bundle;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import butterknife.BindView;
import butterknife.ButterKnife;

public class EditTextAlertDialogFragment extends AlertDialogFragment {
    private static final String KEY_HINT_RES_ID = "HintResId";
    private static final String KEY_TEXT = "Text";

    @BindView(R.id.textInputLayout) TextInputLayout textInputLayout;
    @BindView(R.id.textInputEditText) TextInputEditText editText;
    private @StringRes int hintResId;
    private String text;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            dialog.setOnShowListener(null);
            ButterKnife.bind(EditTextAlertDialogFragment.this, dialog);

            textInputLayout.setHintEnabled(true);
            textInputLayout.setHint(getString(hintResId));
            editText.setText(text);
        });

        Bundle args = getArguments();

        if (args != null) {
            hintResId = args.getInt(KEY_HINT_RES_ID);
            text = args.getString(KEY_TEXT, "");
        }

        return dialog;
    }

    public static class Builder extends AlertDialogFragment.AbstractBuilder<Builder, EditTextAlertDialogFragment> {
        public Builder() {
            super();

            super.setView(R.layout.edit_text_alert_dialog_view);
        }

        @Override
        public Builder getThis() {
            return this;
        }

        @Override
        public Builder setView(int customViewResId) {
            throw new RuntimeException("You cannot set a custom view on an EditTextAlertDialogFragment");
        }

        public Builder setHint(@StringRes int hintResId) {
            args.putInt(KEY_HINT_RES_ID, hintResId);
            return getThis();
        }

        public Builder setText(@NonNull String text) {
            args.putString(KEY_TEXT, text);
            return getThis();
        }

        @Override
        protected EditTextAlertDialogFragment createFragment() {
            return new EditTextAlertDialogFragment();
        }
    }
}
