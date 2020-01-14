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

public class AlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {
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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(titleString)
                .setPositiveButton(positiveButtonResId, this);
        if (negativeButtonResId != 0) {
                builder.setNegativeButton(negativeButtonResId, this);
        }
        if (customViewResId != 0) {
            builder.setView(customViewResId);
        } else {
            builder.setMessage(messageResId);
        }

        return builder.create();
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (callback == null) {
            return;
        }

        switch (which) {
            case AlertDialog.BUTTON_POSITIVE:
                callback.onPositiveButtonClicked();
                break;
            case AlertDialog.BUTTON_NEGATIVE:
                callback.onNegativeButtonClicked();
                break;
            default:
                break;
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);

        if (callback != null) {
            callback.onCancel();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        if (callback != null) {
            callback.onDismiss();
        }
    }

    public static abstract class AbstractBuilder<B extends AbstractBuilder<B, F>, F extends DialogFragment> {
        Bundle args;

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

    //TODO: Generify so the actual AlertDialogFragment subclass can be passed as an argument
    public static abstract class Callback {
        public void onPositiveButtonClicked() {}
        void onNegativeButtonClicked() {}
        public void onDismiss() {}
        void onCancel() {}
    }
}
