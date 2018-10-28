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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashSet;

class AppDatabase {

    static final private HashSet<String> disabledByDefault = new HashSet<>();

    static {
        disabledByDefault.add("com.google.android.googlequicksearchbox"); //Google Now notifications re-spawn every few minutes
    }

    private static final String KEY_PACKAGE_NAME = "packageName";
    private static final String KEY_IS_ENABLED = "isEnabled";

    private static final String DATABASE_NAME = "Applications";
    private static final String DATABASE_TABLE = "Applications";
    private static final int DATABASE_VERSION = 2;

    private SQLiteDatabase ourDatabase;
    private DbHelper ourHelper;

    AppDatabase(Context context, boolean readonly) {
        ourHelper = new DbHelper(context);
        if (readonly) {
            ourDatabase = ourHelper.getReadableDatabase();
        } else {
            ourDatabase = ourHelper.getWritableDatabase();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        ourHelper.close();
        super.finalize();
    }

    private static class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DATABASE_TABLE + "(" + KEY_PACKAGE_NAME + " TEXT PRIMARY KEY NOT NULL, " + KEY_IS_ENABLED + " TEXT NOT NULL); ");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int i, int i2) {
            db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
            onCreate(db);
        }

    }

    void setEnabled(String packageName, boolean isEnabled) {
        String[] columns = new String[]{KEY_IS_ENABLED};
        Cursor res = ourDatabase.query(DATABASE_TABLE, columns, KEY_PACKAGE_NAME + " =? ", new String[]{packageName}, null, null, null);
        try {
            ContentValues cv = new ContentValues();
            cv.put(KEY_IS_ENABLED, isEnabled ? "true" : "false");
            if (res.getCount() > 0) {
                ourDatabase.update(DATABASE_TABLE, cv, KEY_PACKAGE_NAME + "=?", new String[]{packageName});
            } else {
                cv.put(KEY_PACKAGE_NAME, packageName);
                ourDatabase.insert(DATABASE_TABLE, null, cv);
            }
        } finally {
            res.close();
        }
    }

    void setAllEnabled(boolean enabled) {
        ourDatabase.execSQL("UPDATE " + DATABASE_TABLE + " SET " + KEY_IS_ENABLED + "='" + enabled + "'");
    }

    boolean isEnabled(String packageName) {
        String[] columns = new String[]{KEY_IS_ENABLED};
        Cursor res = ourDatabase.query(DATABASE_TABLE, columns, KEY_PACKAGE_NAME + " =? ", new String[]{packageName}, null, null, null);
        try {
            boolean result;
            if (res.getCount() > 0) {
                res.moveToFirst();
                result = (res.getString(res.getColumnIndex(KEY_IS_ENABLED))).equals("true");
            } else {
                result = getDefaultStatus(packageName);
            }
            return result;
        } finally {
            res.close();
        }
    }

    private boolean getDefaultStatus(String packageName) {
        return !disabledByDefault.contains(packageName);
    }

}
