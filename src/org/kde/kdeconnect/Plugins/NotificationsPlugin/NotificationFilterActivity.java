/*
 * Copyright 2015 Vineet Garg <grg.vineet@gmail.com>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.StringsHelper;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class NotificationFilterActivity extends AppCompatActivity {

    private AppDatabase appDatabase;

    static class AppListInfo {
        String pkg;
        String name;
        Drawable icon;
        boolean isEnabled;
    }

    private AppListInfo[] apps;

    class AppListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return apps.length;
        }

        @Override
        public AppListInfo getItem(int position) {
            return apps[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = getLayoutInflater();
                view = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null, true);
            }
            CheckedTextView checkedTextView = (CheckedTextView) view;
            checkedTextView.setText(apps[position].name);
            checkedTextView.setCompoundDrawablesWithIntrinsicBounds(apps[position].icon, null, null, null);
            checkedTextView.setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));

            return view;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);
        setContentView(R.layout.activity_notification_filter);
        appDatabase = new AppDatabase(NotificationFilterActivity.this, false);

        new Thread(new Runnable() {
            @Override
            public void run() {

                PackageManager packageManager = getPackageManager();
                List<ApplicationInfo> appList = packageManager.getInstalledApplications(0);
                int count = appList.size();

                apps = new AppListInfo[count];
                for (int i = 0; i < count; i++) {
                    ApplicationInfo appInfo = appList.get(i);
                    apps[i] = new AppListInfo();
                    apps[i].pkg = appInfo.packageName;
                    apps[i].name = appInfo.loadLabel(packageManager).toString();
                    apps[i].icon = resizeIcon(appInfo.loadIcon(packageManager), 48);
                    apps[i].isEnabled = appDatabase.isEnabled(appInfo.packageName);
                }

                Arrays.sort(apps, new Comparator<AppListInfo>() {
                    @Override
                    public int compare(AppListInfo lhs, AppListInfo rhs) {
                        return StringsHelper.compare(lhs.name, rhs.name);
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayAppList();
                    }
                });
            }
        }).start();

    }

    private void displayAppList() {

        final ListView listView = (ListView) findViewById(R.id.lvFilterApps);
        AppListAdapter adapter = new AppListAdapter();
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                boolean checked = listView.isItemChecked(i);
                appDatabase.setEnabled(apps[i].pkg, checked);
                apps[i].isEnabled = checked;
            }
        });

        for (int i = 0; i < apps.length; i++) {
            listView.setItemChecked(i, apps[i].isEnabled);
        }

        listView.setVisibility(View.VISIBLE);
        findViewById(R.id.spinner).setVisibility(View.GONE);

    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackgroundService.removeGuiInUseCounter(this);
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
}
