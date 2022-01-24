/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.tabs.TabLayoutMediator;

import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeFragment;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityMprisBinding;

import java.util.Objects;

public class MprisActivity extends AppCompatActivity {

    private ActivityMprisBinding activityMprisBinding;
    private MprisPagerAdapter mprisPagerAdapter;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (activityMprisBinding != null && mprisPagerAdapter != null) {
                    int pagePosition = activityMprisBinding.mprisTabs.getSelectedTabPosition();
                    mprisPagerAdapter.onVolumeUp(pagePosition);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (activityMprisBinding != null && mprisPagerAdapter != null) {
                    int pagePosition = activityMprisBinding.mprisTabs.getSelectedTabPosition();
                    mprisPagerAdapter.onVolumeDown(pagePosition);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        activityMprisBinding = ActivityMprisBinding.inflate(getLayoutInflater());

        setContentView(activityMprisBinding.getRoot());

        String deviceId = getIntent().getStringExtra(MprisPlugin.DEVICE_ID_KEY);

        mprisPagerAdapter = new MprisPagerAdapter(this, deviceId);
        activityMprisBinding.mprisPager.setAdapter(mprisPagerAdapter);

        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(
                activityMprisBinding.mprisTabs,
                activityMprisBinding.mprisPager,
                (tab, position) -> tab.setText(mprisPagerAdapter.getTitle(position))
        );

        activityMprisBinding.mprisTabs.getSelectedTabPosition();

        tabLayoutMediator.attach();

        setSupportActionBar(activityMprisBinding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    static class MprisPagerAdapter extends ExtendedFragmentAdapter {

        private final String deviceId;

        public MprisPagerAdapter(@NonNull FragmentActivity fragmentActivity, String deviceId) {
            super(fragmentActivity);
            this.deviceId = deviceId;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 1) {
                return SystemVolumeFragment.newInstance(deviceId);
            } else {
                return MprisNowPlayingFragment.newInstance(deviceId);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        @StringRes
        int getTitle(int position) {
            if (position == 1) {
                return R.string.devices;
            } else {
                return R.string.mpris_play;
            }
        }

        void onVolumeUp(int page) {
            Fragment requestedFragment = getFragment(page);
            if (requestedFragment == null) return;

            if (requestedFragment instanceof VolumeKeyListener) {
                ((VolumeKeyListener) requestedFragment).onVolumeUp();
            }
        }

        void onVolumeDown(int page) {
            Fragment requestedFragment = getFragment(page);
            if (requestedFragment == null) return;

            if (requestedFragment instanceof VolumeKeyListener) {
                ((VolumeKeyListener) requestedFragment).onVolumeDown();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}
