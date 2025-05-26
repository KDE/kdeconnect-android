/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.WindowHelper;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.UserInterface.List.DeviceItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect.UserInterface.List.UnreachableDeviceItem;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;

public class ShareActivity extends BaseActivity<ActivityShareBinding> {
    private static final String KEY_UNREACHABLE_URL_LIST = "key_unreachable_url_list";

    private SharedPreferences mSharedPrefs;

    private final Lazy<ActivityShareBinding> lazyBinding = LazyKt.lazy(() -> ActivityShareBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    public ActivityShareBinding getBinding() {
        return lazyBinding.getValue();
    }

    @Override
    public boolean isScrollable() {
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.refresh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh) {
            refreshDevicesAction();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(this);

        getBinding().devicesListLayout.refreshListLayout.setRefreshing(true);
        getBinding().devicesListLayout.refreshListLayout.postDelayed(() -> {
            getBinding().devicesListLayout.refreshListLayout.setRefreshing(false);
        }, 1500);
    }

    private void updateDeviceList() {
        final Intent intent = getIntent();

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            finish();
            return;
        }

        Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
        final ArrayList<Device> devicesList = new ArrayList<>();
        final ArrayList<ListAdapter.Item> items = new ArrayList<>();

        boolean intentHasUrl = doesIntentContainUrl(intent);

        String sectionString = getString(R.string.share_to);
        if (intentHasUrl) {
            sectionString = getString(R.string.unreachable_device_url_share_text) + getString(R.string.share_to);
        }
        SectionItem section = new SectionItem(sectionString);
        items.add(section);

        for (Device d : devices) {
            // Show the paired devices only if they are unreachable and the shared intent has a URL
            if (d.isPaired() && (intentHasUrl || d.isReachable())) {
                devicesList.add(d);
                if (!d.isReachable()) {
                    items.add(new UnreachableDeviceItem(d));
                } else {
                    items.add(new DeviceItem(d));
                }
                section.isEmpty = false;
            }
        }

        getBinding().devicesListLayout.devicesList.setAdapter(new ListAdapter(ShareActivity.this, items));
        getBinding().devicesListLayout.devicesList.setOnItemClickListener((adapterView, view, i, l) -> {
            Device device = devicesList.get(i - 1); //NOTE: -1 because of the title!
            SharePlugin plugin = KdeConnect.getInstance().getDevicePlugin(device.getDeviceId(), SharePlugin.class);
            if (intentHasUrl && !device.isReachable()) {
                // Store the URL to be delivered once device becomes online
                storeUrlForFutureDelivery(device, intent.getStringExtra(Intent.EXTRA_TEXT));
            } else if (plugin != null) {
                plugin.share(intent);
            }
            finish();
        });
    }

    private boolean doesIntentContainUrl(Intent intent) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String url = extras.getString(Intent.EXTRA_TEXT);
                return URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url);
            }
        }
        return false;
    }

    private void storeUrlForFutureDelivery(Device device, String url) {
        Set<String> oldUrlSet = mSharedPrefs.getStringSet(KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), null);
        // According to the API docs, we should not directly modify the set returned above
        Set<String> newUrlSet = new HashSet<>();
        newUrlSet.add(url);
        if (oldUrlSet != null) {
            newUrlSet.addAll(oldUrlSet);
        }
        mSharedPrefs.edit().putStringSet(KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), newUrlSet).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences (this);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        ActionBar actionBar = getSupportActionBar();
        getBinding().devicesListLayout.refreshListLayout.setOnRefreshListener(this::refreshDevicesAction);
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        WindowHelper.setupBottomPadding(getBinding().devicesListLayout.devicesList);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final Intent intent = getIntent();
        String deviceId = intent.getStringExtra("deviceId");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID);
        }

        if (deviceId != null) {
            SharePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, SharePlugin.class);
            if (plugin != null) {
                plugin.share(intent);
            } else {
                Bundle extras = intent.getExtras();
                if (extras != null && extras.containsKey(Intent.EXTRA_TEXT)) {
                    final Device device = KdeConnect.getInstance().getDevice(deviceId);
                    if (doesIntentContainUrl(intent) && device != null && !device.isReachable()) {
                        final String text = extras.getString(Intent.EXTRA_TEXT);
                        storeUrlForFutureDelivery(device, text);
                    }
                }
            }
            finish();
        } else {
            KdeConnect.getInstance().addDeviceListChangedCallback("ShareActivity", () -> runOnUiThread(this::updateDeviceList));
            BackgroundService.ForceRefreshConnections(this); // force a network re-discover
            updateDeviceList();
        }
    }

    @Override
    protected void onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("ShareActivity");
        super.onStop();
    }
}
