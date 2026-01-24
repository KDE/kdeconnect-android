/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AlertDialogFragment extends DialogFragment {
    private static final String KEY_TITLE_RES_ID = "TitleResId";
    private static final String KEY_TITLE = "Title";
    private static final String KEY_MESSAGE_RES_ID = "MessageResId";
    private static final String KEY_POSITIVE_BUTTON_TEXT_RES_ID = "PositiveButtonResId";
    private static final String KEY_NEGATIVE_BUTTON_TEXT_RES_ID = "NegativeButtonResId";
    private static final String KEY_CUSTOM_VIEW_RES_ID = "CustomViewResId";

    @StringRes private int titleResId;
    @Nullable private String title;
    @StringRes private int messageResId;
    @StringRes private int positiveButtonResId;
    @StringRes private int negativeButtonResId;
    @LayoutRes private int customViewResId;

    @Nullable private Callback callback;

    public AlertDialogFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args == null) {
            throw new RuntimeException("You need to instantiate a new AlertDialogFragment using AlertDialogFragment.Builder");
        }

        titleResId = args.getInt(KEY_TITLE_RES_ID);
        title = args.getString(KEY_TITLE);
        messageResId = args.getInt(KEY_MESSAGE_RES_ID);
        positiveButtonResId = args.getInt(KEY_POSITIVE_BUTTON_TEXT_RES_ID);
        negativeButtonResId = args.getInt(KEY_NEGATIVE_BUTTON_TEXT_RES_ID);
        customViewResId = args.getInt(KEY_CUSTOM_VIEW_RES_ID);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        @SuppressLint("ResourceType")
        String titleString = titleResId > 0 ? getString(titleResId) : title;

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleString)
                // Set listeners to null so dialog does not auto-dismiss
                .setPositiveButton(positiveButtonResId, null);
        if (negativeButtonResId != 0) {
            builder.setNegativeButton(negativeButtonResId, null);
        }
        if (customViewResId != 0) {
            builder.setView(customViewResId);
        } else {
            builder.setMessage(messageResId);
        }

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;
        // Set custom click listeners to prevent auto-dismiss
        if (positiveButtonResId != 0) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (callback == null || callback.onPositiveButtonClicked()) {
                    dismiss();
                }
            });
        }
        if (negativeButtonResId != 0) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (callback != null) {
                    callback.onNegativeButtonClicked();
                }
                dismiss();
            });
        }
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);

        if (callback != null) {
            callback.onCancel();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        if (callback != null) {
            callback.onDismiss();
        }
    }

    public static abstract class AbstractBuilder<B extends AbstractBuilder<B, F>, F extends DialogFragment> {
        final Bundle args;

        AbstractBuilder() {
            args = new Bundle();
        }

        public abstract B getThis();

        public B setTitle(@StringRes int titleResId) {
            args.putInt(KEY_TITLE_RES_ID, titleResId);
            return getThis();
        }

        public B setTitle(@NonNull String title) {
            args.putString(KEY_TITLE, title);
            return getThis();
        }

        public B setMessage(@StringRes int messageResId) {
            args.putInt(KEY_MESSAGE_RES_ID, messageResId);
            return getThis();
        }

        public B setPositiveButton(@StringRes int positiveButtonResId) {
            args.putInt(KEY_POSITIVE_BUTTON_TEXT_RES_ID, positiveButtonResId);
            return getThis();
        }

        public B setNegativeButton(@StringRes int negativeButtonResId) {
            args.putInt(KEY_NEGATIVE_BUTTON_TEXT_RES_ID, negativeButtonResId);
            return getThis();
        }

        public B setView(@LayoutRes int customViewResId) {
            args.putInt(KEY_CUSTOM_VIEW_RES_ID, customViewResId);
            return getThis();
        }

        protected abstract F createFragment();

        public F create() {
            F fragment = createFragment();
            fragment.setArguments(args);

            return fragment;
        }
    }

    public static class Builder extends AbstractBuilder<Builder, AlertDialogFragment> {
        @Override
        public Builder getThis() {
            return this;
        }

        @Override
        protected AlertDialogFragment createFragment() {
            return new AlertDialogFragment();
        }
    }

    public static abstract class Callback {
        // Return true to close the dialog, or false ot keep it open
        public boolean onPositiveButtonClicked() { return true; }
        public void onNegativeButtonClicked() { }
        public void onDismiss() {}
        public void onCancel() {}
    }
}
