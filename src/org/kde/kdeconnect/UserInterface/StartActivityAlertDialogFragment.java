/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class StartActivityAlertDialogFragment extends AlertDialogFragment {
    private static final String KEY_INTENT_ACTION = "IntentAction";
    private static final String KEY_REQUEST_CODE = "RequestCode";
    private static final String KEY_START_FOR_RESULT = "StartForResult";

    private String intentAction;
    private int requestCode;
    private boolean startForResult;

    public StartActivityAlertDialogFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args == null || !args.containsKey(KEY_INTENT_ACTION)) {
            throw new RuntimeException("You must call Builder.setIntentAction() to set the intent action");
        }

        intentAction = args.getString(KEY_INTENT_ACTION);
        requestCode = args.getInt(KEY_REQUEST_CODE, 0);
        startForResult = args.getBoolean(KEY_START_FOR_RESULT);

        if (startForResult && !args.containsKey(KEY_REQUEST_CODE)) {
            throw new RuntimeException("You requested startForResult but you did not set the requestCode");
        }

        setCallback(new Callback() {
            @Override
            public void onPositiveButtonClicked() {
                Intent intent = new Intent(intentAction);

                if (startForResult) {
                    requireActivity().startActivityForResult(intent, requestCode);
                } else {
                    requireActivity().startActivity(intent);
                }
            }
        });
    }

    public static class Builder extends AlertDialogFragment.AbstractBuilder<StartActivityAlertDialogFragment.Builder, StartActivityAlertDialogFragment> {
        @Override
        public StartActivityAlertDialogFragment.Builder getThis() {
            return this;
        }

        public StartActivityAlertDialogFragment.Builder setIntentAction(@NonNull String intentAction) {
            args.putString(KEY_INTENT_ACTION, intentAction);

            return getThis();
        }

        public StartActivityAlertDialogFragment.Builder setRequestCode(int requestCode) {
            args.putInt(KEY_REQUEST_CODE, requestCode);

            return getThis();
        }

        public StartActivityAlertDialogFragment.Builder setStartForResult(boolean startForResult) {
            args.putBoolean(KEY_START_FOR_RESULT, startForResult);

            return getThis();
        }

        @Override
        protected StartActivityAlertDialogFragment createFragment() {
            return new StartActivityAlertDialogFragment();
        }
    }
}
