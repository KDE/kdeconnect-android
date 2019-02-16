/*
 * Copyright 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
