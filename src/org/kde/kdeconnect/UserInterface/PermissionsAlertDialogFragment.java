/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

public class PermissionsAlertDialogFragment extends AlertDialogFragment {
    private static final String KEY_PERMISSIONS = "Permissions";
    private static final String KEY_REQUEST_CODE = "RequestCode";

    private String[] permissions;
    private int requestCode;

    public PermissionsAlertDialogFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args == null || !args.containsKey(KEY_PERMISSIONS)) {
            throw new RuntimeException("You must call Builder.setPermission() to set the array of needed permissions");
        }

        permissions = args.getStringArray(KEY_PERMISSIONS);
        requestCode = args.getInt(KEY_REQUEST_CODE, 0);

        setCallback(new Callback() {
            @Override
            public void onPositiveButtonClicked() {
                ActivityCompat.requestPermissions(requireActivity(), permissions, requestCode);
            }
        });
    }

    public static class Builder extends AlertDialogFragment.AbstractBuilder<Builder, PermissionsAlertDialogFragment> {
        @Override
        public Builder getThis() {
            return this;
        }

        public Builder setPermissions(String[] permissions) {
            args.putStringArray(KEY_PERMISSIONS, permissions);

            return getThis();
        }

        public Builder setRequestCode(int requestCode) {
            args.putInt(KEY_REQUEST_CODE, requestCode);

            return getThis();
        }

        @Override
        protected PermissionsAlertDialogFragment createFragment() {
            return new PermissionsAlertDialogFragment();
        }
    }
}
