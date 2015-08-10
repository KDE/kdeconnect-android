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
import android.database.Cursor;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.kde.kdeconnect_tp.R;

import java.util.List;

public class NotificationFilterActivity extends ActionBarActivity {

    private AppDatabase appDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_filter);
        final ListView listView = (ListView)findViewById(R.id.lvFilterApps);
        appDatabase = new AppDatabase(this);

        deleteUninstalledApps();
        addNewlyInstalledApps();

        appDatabase.open();
        Cursor res = appDatabase.getAllApplications();
        res.moveToFirst();

        String[] appName = new String[res.getCount()];
        final String[] pkgName = new String[res.getCount()];
        Boolean[] isFiltered = new Boolean[res.getCount()];

        int i = 0;
        while(!res.isAfterLast()){
            appName[i] = res.getString(res.getColumnIndex(AppDatabase.KEY_NAME));
            pkgName[i] = res.getString(res.getColumnIndex(AppDatabase.KEY_PACKAGE_NAME));
            isFiltered[i] = res.getString(res.getColumnIndex(AppDatabase.KEY_IS_ENABLED)).equals("true");
            res.moveToNext();
            i++;
        }
        res.close();
        appDatabase.close();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, android.R.id.text1, appName);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        for (i = 0 ; i < isFiltered.length; i++){
            if (isFiltered[i]) {
                listView.setItemChecked(i, true);
            }
        }
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                boolean checked = listView.isItemChecked(i);
                //Log.e("NotificationFilterActivity", pkgName[i] + ":" + checked);
                appDatabase.open();
                appDatabase.update(pkgName[i], checked);
                appDatabase.close();
            }
        });

    }

    // Delete apps from database which are uninstalled
    private void deleteUninstalledApps(){
        Cursor res;
        appDatabase.open();
        res = appDatabase.getAllApplications();
        if (res != null) {
            res.moveToFirst();
            while (!res.isAfterLast()) {
                String packageName = res.getString(res.getColumnIndex(AppDatabase.KEY_PACKAGE_NAME));
                if (!isPackageInstalled(packageName)) {
                    appDatabase.delete(packageName);
                }
                res.moveToNext();
            }
            res.close();
        }
        appDatabase.close();

    }

    // Adding newly installed apps in database
    private void addNewlyInstalledApps() {

        List<ApplicationInfo> PackList = getPackageManager().getInstalledApplications(0);
        appDatabase.open();

        for (int i=0; i < PackList.size(); i++)
        {
            ApplicationInfo PackInfo = PackList.get(i);

            String appName = PackInfo.loadLabel(getPackageManager()).toString();
            String packageName = PackInfo.packageName;

            if ( (PackInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ) {

                if (!appDatabase.exists(packageName)) {
                    appDatabase.create(appName, packageName, true);
                }
                //Log.e("App FLAG_UPDATED_SYSTEM_APP: " + Integer.toString(i), appName);

            } else if ( (PackInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {

                //ignore these apps

            } else {

                if (!appDatabase.exists(packageName)) {
                    appDatabase.create(appName, packageName, true);
                }
                //Log.e("App : " + Integer.toString(i), appName);

            }
        }

        appDatabase.close();

    }

    private boolean isPackageInstalled(String packageName){
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_META_DATA);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}
