/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.kde.kdeconnect.helpers.ThreadHelper;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityNotificationFilterBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;

//TODO: Turn this into a PluginSettingsFragment
public class NotificationFilterActivity extends BaseActivity<ActivityNotificationFilterBinding> {

    private AppDatabase appDatabase;
    private String prefKey;

    static class AppListInfo {

        String pkg;
        String name;
        Drawable icon;
        boolean isEnabled;
    }

    // This variable stores all app information and serves as a data source for filtering.
    private List<AppListInfo> mAllApps;
    private List<AppListInfo> apps; // Filtered data.

    private final Lazy<ActivityNotificationFilterBinding> lazyBinding = LazyKt.lazy(() -> ActivityNotificationFilterBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityNotificationFilterBinding getBinding() {
        return lazyBinding.getValue();
    }

    class AppListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return apps.size() + 1;
        }

        @Override
        public AppListInfo getItem(int position) {
            return apps.get(position - 1);
        }

        @Override
        public long getItemId(int position) {
            return position - 1;
        }

        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = getLayoutInflater();
                view = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null, true);
            }
            CheckedTextView checkedTextView = (CheckedTextView) view;
            if (position == 0) {
                checkedTextView.setText(R.string.all);
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(checkedTextView, null, null, null, null);
                getBinding().lvFilterApps.setItemChecked(position, appDatabase.getAllEnabled());
            } else {
                final AppListInfo info = apps.get(position - 1);
                checkedTextView.setText(info.name);
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(checkedTextView, info.icon, null, null, null);
                checkedTextView.setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));
                getBinding().lvFilterApps.setItemChecked(position, info.isEnabled);
            }

            return view;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appDatabase = AppDatabase.getInstance(NotificationFilterActivity.this);
        if (getIntent()!= null){
            prefKey = getIntent().getStringExtra(NotificationsPlugin.getPrefKey());
        }

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        SharedPreferences preferences = this.getSharedPreferences(prefKey, Context.MODE_PRIVATE);

        configureSwitch(preferences);

        ThreadHelper.execute(() -> {
            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> appList = packageManager.getInstalledApplications(0);
            int count = appList.size();

            final Set<String> allPackageNames = new HashSet<>(count);
            final List<AppListInfo> allApps = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final ApplicationInfo appInfo = appList.get(i);
                AppListInfo appListInfo = new AppListInfo();
                appListInfo.pkg = appInfo.packageName;
                appListInfo.name = appInfo.loadLabel(packageManager).toString();
                appListInfo.icon = resizeIcon(appInfo.loadIcon(packageManager), 48);
                appListInfo.isEnabled = appDatabase.isEnabled(appInfo.packageName);

                allApps.add(appListInfo);

                allPackageNames.add(appInfo.packageName);
            }

            // Find apps from work profile
            try {
                final UserHandle currentUser = Process.myUserHandle();
                final LauncherApps launcher = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                final UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
                final List<UserHandle> userProfiles = um.getUserProfiles();
                for (final UserHandle userProfile : userProfiles) {
                    if (userProfile.equals(currentUser)) {
                        continue;
                    }

                    final List<LauncherActivityInfo> userActivityList = launcher.getActivityList(null, userProfile);
                    for (final LauncherActivityInfo app : userActivityList) {
                        if (allPackageNames.contains(app.getApplicationInfo().packageName)) {
                            continue;
                        }

                        final ApplicationInfo appInfo = app.getApplicationInfo();
                        AppListInfo appListInfo = new AppListInfo();
                        appListInfo.pkg = appInfo.packageName;
                        appListInfo.name = appInfo.loadLabel(packageManager).toString();
                        appListInfo.icon = resizeIcon(appInfo.loadIcon(packageManager), 48);
                        appListInfo.isEnabled = appDatabase.isEnabled(appInfo.packageName);

                        allApps.add(appListInfo);

                        allPackageNames.add(app.getApplicationInfo().packageName);
                    }
                }
            } catch (final Exception e) {
                Log.e("NotificationFilterActiv", "Failed to get apps from work profile", e);
            }

            allApps.sort((lhs, rhs) -> lhs.name.compareToIgnoreCase(rhs.name));
            mAllApps = allApps;
            apps = new ArrayList<>(mAllApps);
            runOnUiThread(this::displayAppList);
        });

    }

    private void configureSwitch(SharedPreferences sharedPreferences) {
        MaterialSwitch smScreenOffNotification = findViewById(R.id.smScreenOffNotification);
        smScreenOffNotification.setChecked(
                sharedPreferences.getBoolean(getString(NotificationsPlugin.PREF_NOTIFICATION_SCREEN_OFF),false)
        );
        smScreenOffNotification.setOnCheckedChangeListener((buttonView, isChecked) ->
                sharedPreferences.edit().putBoolean(getString(NotificationsPlugin.PREF_NOTIFICATION_SCREEN_OFF),isChecked).apply()
        );
    }

    private void displayAppList() {
        final ListView listView = getBinding().lvFilterApps;
        AppListAdapter adapter = new AppListAdapter();
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setLongClickable(true);
        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (i == 0) {
                boolean enabled = listView.isItemChecked(0);
                for (int j = 0; j < mAllApps.size(); j++) {
                    mAllApps.get(j).isEnabled = enabled;
                }
                appDatabase.setAllEnabled(enabled);
                ((AppListAdapter) adapterView.getAdapter()).notifyDataSetChanged();
            } else {
                boolean checked = listView.isItemChecked(i);
                apps.get(i - 1).isEnabled = checked;
                appDatabase.setEnabled(apps.get(i - 1).pkg, checked);
                ((AppListAdapter) adapterView.getAdapter()).notifyDataSetChanged();
            }
        });
        listView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            if(i == 0)
                return true;
            Context context = this;
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View mView = getLayoutInflater().inflate(R.layout.popup_notificationsfilter, null);
            builder.setMessage(context.getResources().getString(R.string.extra_options));

            ListView lv = mView.findViewById(R.id.extra_options_list);
            final String[] options = new String[] {
                    context.getResources().getString(R.string.privacy_options)
            };
            ArrayAdapter<String> extra_options_adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, options);
            lv.setAdapter(extra_options_adapter);
            builder.setView(mView);

            AlertDialog ad = builder.create();

            lv.setOnItemClickListener((new_adapterView, new_view, new_i, new_l) -> {
                switch (new_i){
                    case 0:
                        AlertDialog.Builder myBuilder = new AlertDialog.Builder(context);
                        String packageName = apps.get(i - 1).pkg;

                        View myView = getLayoutInflater().inflate(R.layout.privacy_options, null);
                        CheckBox checkbox_contents = myView.findViewById(R.id.checkbox_contents);
                        checkbox_contents.setChecked(appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS));
                        checkbox_contents.setText(context.getResources().getString(R.string.block_contents));
                        CheckBox checkbox_images = myView.findViewById(R.id.checkbox_images);
                        checkbox_images.setChecked(appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES));
                        checkbox_images.setText(context.getResources().getString(R.string.block_images));

                        myBuilder.setView(myView);
                        myBuilder.setTitle(context.getResources().getString(R.string.privacy_options));
                        myBuilder.setPositiveButton(context.getResources().getString(R.string.ok), (dialog, id) -> dialog.dismiss());
                        myBuilder.setMessage(context.getResources().getString(R.string.set_privacy_options));

                        checkbox_contents.setOnCheckedChangeListener((compoundButton, b) ->
                                appDatabase.setPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS,
                                        compoundButton.isChecked()));
                        checkbox_images.setOnCheckedChangeListener((compoundButton, b) ->
                                appDatabase.setPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES,
                                        compoundButton.isChecked()));

                        ad.cancel();
                        myBuilder.show();
                        break;
                }
            });

            ad.show();
            return true;
        });

        listView.setItemChecked(0, appDatabase.getAllEnabled()); //"Select all" button
        for (int i = 0; i < apps.size(); i++) {
            listView.setItemChecked(i + 1, apps.get(i).isEnabled);
        }

        listView.setVisibility(View.VISIBLE);
        getBinding().spinner.setVisibility(View.GONE);
    }

    private Drawable resizeIcon(Drawable icon, int maxSize) {
        Resources res = getResources();

        //Convert to display pixels
        maxSize = (int) (maxSize * res.getDisplayMetrics().density);

        Bitmap bitmap = Bitmap.createBitmap(maxSize, maxSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        icon.draw(canvas);

        return new BitmapDrawable(res, bitmap);

    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem mitem = menu.add(android.R.string.search_go);
        mitem.setIcon(R.drawable.ic_search_24);
        mitem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        SearchView searchView = new SearchView(this);
        mitem.setActionView(searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return  true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if(mAllApps == null) return false;
                apps.clear();
                if(newText.isEmpty()){
                    apps.addAll(mAllApps);
                } else {
                    for (AppListInfo s : mAllApps) {
                        if (s.name.toLowerCase().contains(newText.toLowerCase().trim()))
                            apps.add(s);
                    }
                }

                ((AppListAdapter) getBinding().lvFilterApps.getAdapter()).notifyDataSetChanged();
                return true;
            }
        });


        return true;
    }
}
