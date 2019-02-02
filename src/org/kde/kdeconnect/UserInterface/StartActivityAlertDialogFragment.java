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
