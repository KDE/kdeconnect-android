/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

public class DefaultSmsAppAlertDialogFragment extends AlertDialogFragment {
    private static final String KEY_PERMISSIONS = "Permissions";
    private static final String KEY_REQUEST_CODE = "RequestCode";

    private String[] permissions;
    private int requestCode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args == null) {
            return;
        }

        permissions = args.getStringArray(KEY_PERMISSIONS);
        requestCode = args.getInt(KEY_REQUEST_CODE, 0);

        setCallback(new Callback() {
            @Override
            public void onPositiveButtonClicked() {
                Activity host = requireActivity();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    RoleManager roleManager = host.getSystemService(RoleManager.class);

                    if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                            Intent roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                            host.startActivityForResult(roleRequestIntent, requestCode);
                        }
                    }
                } else {
                    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getActivity().getPackageName());
                    host.startActivityForResult(intent, requestCode);
                }

                ActivityCompat.requestPermissions(requireActivity(), permissions, requestCode);
            }
        });
    }

    public static class Builder extends AlertDialogFragment.AbstractBuilder<DefaultSmsAppAlertDialogFragment.Builder, DefaultSmsAppAlertDialogFragment> {

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
        protected DefaultSmsAppAlertDialogFragment createFragment() {
            return new DefaultSmsAppAlertDialogFragment();
        }
    }
}
