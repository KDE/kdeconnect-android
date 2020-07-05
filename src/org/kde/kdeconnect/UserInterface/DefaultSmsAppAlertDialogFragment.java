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
