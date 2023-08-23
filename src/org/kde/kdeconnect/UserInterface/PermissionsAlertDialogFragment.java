/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.kde.kdeconnect_tp.R;

public class PermissionsAlertDialogFragment extends AlertDialogFragment {
    private static final String KEY_PERMANENTLY_DENIED_PREFERENCES = "permanently_denied_permissions";
    private static final String KEY_PERMISSIONS = "Permissions";
    private static final String KEY_REQUEST_CODE = "RequestCode";

    private String[] permissions;
    private int requestCode;

    public PermissionsAlertDialogFragment() {
    }

    public static void PermissionsDenied(Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                // The user selected "don't show again" or denied the permission twice, so the
                // system permission dialog won't show again. We want to remember this to open the
                // app preferences instead the next time
                SharedPreferences prefs = activity.getSharedPreferences(KEY_PERMANENTLY_DENIED_PREFERENCES, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(permission, true).apply();
            }
        }
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
            SharedPreferences prefs = getContext().getSharedPreferences(KEY_PERMANENTLY_DENIED_PREFERENCES, Context.MODE_PRIVATE);
            boolean permanentlyDenied = false;
            for (String permission : permissions) {
                if (prefs.getBoolean(permission, false)) {
                    permanentlyDenied = true;
                    break;
                }
            }
            if (permanentlyDenied) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } else {
                ActivityCompat.requestPermissions(requireActivity(), permissions, requestCode);
            }
            }
        });
    }

    public static class Builder extends AlertDialogFragment.AbstractBuilder<Builder, PermissionsAlertDialogFragment> {

        public Builder() {
            setPositiveButton(R.string.ok);
            setNegativeButton(R.string.cancel);
        }

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
