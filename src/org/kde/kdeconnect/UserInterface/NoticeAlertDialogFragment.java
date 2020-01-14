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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect_tp.R;

public class NoticeAlertDialogFragment extends AlertDialogFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setCallback(new Callback() {
            @Override
            public void onPositiveButtonClicked() {
                //TODO: Find a way to pass this callback from the Builder. For now, this is only used in one place and this is the callback needed.
                MainActivity mainActivity = (MainActivity)requireActivity();
                mainActivity.reloadCurrentDevicePlugins();
            }
        });
    }

    public static class Builder extends AbstractBuilder<Builder, NoticeAlertDialogFragment> {

        public Builder() {
            super();
            setTitle(R.string.pref_plugin_clipboard);
            setMessage(R.string.clipboard_android_x_incompat);
            setPositiveButton(R.string.sad_ok);
        }

        @Override
        public Builder getThis() {
            return this;
        }

        @Override
        protected NoticeAlertDialogFragment createFragment() {
            return new NoticeAlertDialogFragment();
        }
    }
}
