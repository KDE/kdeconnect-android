/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.HashSet;

class AppDatabase {

    static final private HashSet<String> disabledByDefault = new HashSet<>();
    static {
        disabledByDefault.add("com.google.android.googlequicksearchbox"); //Google Now notifications re-spawn every few minutes
    }

    private static final String SETTINGS_NAME = "app_database";
    private static final String SETTINGS_KEY_ALL_ENABLED = "all_enabled";

    private static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "Applications";
    private static final String TABLE_ENABLED = "Applications";
    private static final String TABLE_PRIVACY = "PrivacyOpts";
    private static final String KEY_PACKAGE_NAME = "packageName";
    private static final String KEY_IS_ENABLED = "isEnabled";
    private static final String KEY_PRIVACY_OPTIONS = "privacyOptions";


    private static final String DATABASE_CREATE_ENABLED = "CREATE TABLE "
            + TABLE_ENABLED + "(" + KEY_PACKAGE_NAME + " TEXT PRIMARY KEY NOT NULL, "
            + KEY_IS_ENABLED + " INTEGER NOT NULL ); ";
    private static final String DATABASE_CREATE_PRIVACY_OPTS = "CREATE TABLE "
            + TABLE_PRIVACY + "(" + KEY_PACKAGE_NAME + " TEXT PRIMARY KEY NOT NULL, "
            + KEY_PRIVACY_OPTIONS + " INTEGER NOT NULL); ";


    private final DbHelper ourHelper;
    private final SharedPreferences prefs;

    static private AppDatabase _instance = null;

    static public AppDatabase getInstance(Context context) {
        if (_instance == null) {
            _instance = new AppDatabase(context.getApplicationContext());
        }
        return _instance;
    }

    private AppDatabase(Context context) {
        ourHelper = new DbHelper(context);
        prefs = context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
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
            db.execSQL(DATABASE_CREATE_ENABLED);
            db.execSQL(DATABASE_CREATE_PRIVACY_OPTS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 5) {
                db.execSQL(DATABASE_CREATE_PRIVACY_OPTS);
            }
        }

    }

    void setEnabled(String packageName, boolean isEnabled) {
        String[] columns = new String[]{KEY_IS_ENABLED};
        SQLiteDatabase ourDatabase = ourHelper.getWritableDatabase();
        try (Cursor res = ourDatabase.query(TABLE_ENABLED, columns, KEY_PACKAGE_NAME + " =? ", new String[]{packageName}, null, null, null)) {
            ContentValues cv = new ContentValues();
            cv.put(KEY_IS_ENABLED, isEnabled ? 1 : 0);
            if (res.getCount() > 0) {
                ourDatabase.update(TABLE_ENABLED, cv, KEY_PACKAGE_NAME + "=?", new String[]{packageName});
            } else {
                cv.put(KEY_PACKAGE_NAME, packageName);
                long retVal = ourDatabase.insert(TABLE_ENABLED, null, cv);
                Log.i("AppDatabase", "SetEnabled retval = " + retVal);
            }
        }
    }

    boolean getAllEnabled() {
        return prefs.getBoolean(SETTINGS_KEY_ALL_ENABLED, true);
    }

    void setAllEnabled(boolean enabled) {
        prefs.edit().putBoolean(SETTINGS_KEY_ALL_ENABLED, enabled).apply();
        SQLiteDatabase ourDatabase = ourHelper.getWritableDatabase();
        ourDatabase.execSQL("UPDATE " + TABLE_ENABLED + " SET " + KEY_IS_ENABLED + "=" + (enabled? "1" : "0"));
    }

    boolean isEnabled(String packageName) {
        String[] columns = new String[]{KEY_IS_ENABLED};
        SQLiteDatabase ourDatabase = ourHelper.getReadableDatabase();
        try (Cursor res = ourDatabase.query(TABLE_ENABLED, columns, KEY_PACKAGE_NAME + " =? ", new String[]{packageName}, null, null, null)) {
            boolean result;
            if (res.getCount() > 0) {
                res.moveToFirst();
                result = (res.getInt(res.getColumnIndex(KEY_IS_ENABLED)) != 0);
            } else {
                result = getDefaultStatus(packageName);
            }
            return result;
        }
    }

    private boolean getDefaultStatus(String packageName) {
        if (disabledByDefault.contains(packageName)) {
            return false;
        }
        return getAllEnabled();
    }

    public enum PrivacyOptions {
        BLOCK_CONTENTS,
        BLOCK_IMAGES
        // Just add new enum to add a new privacy option.
    }

    private int getPrivacyOptionsValue(String packageName)
    {
        String[] columns = new String[]{KEY_PRIVACY_OPTIONS};
        SQLiteDatabase ourDatabase = ourHelper.getReadableDatabase();
        try (Cursor res = ourDatabase.query(TABLE_PRIVACY, columns, KEY_PACKAGE_NAME + " =? ", new String[]{packageName}, null, null, null)) {
            int result;
            if (res.getCount() > 0) {
                res.moveToFirst();
                result = res.getInt(res.getColumnIndex(KEY_PRIVACY_OPTIONS));
            } else {
                result = 0;
            }
            return result;
        }
    }

    private void setPrivacyOptionsValue(String packageName, int value) {
        String[] columns = new String[]{KEY_PRIVACY_OPTIONS};
        SQLiteDatabase ourDatabase = ourHelper.getWritableDatabase();
        try (Cursor res = ourDatabase.query(TABLE_PRIVACY, columns, KEY_PACKAGE_NAME + " =? ", new String[]{packageName}, null, null, null)) {
            ContentValues cv = new ContentValues();
            cv.put(KEY_PRIVACY_OPTIONS, value);
            if (res.getCount() > 0) {
                ourDatabase.update(TABLE_PRIVACY, cv, KEY_PACKAGE_NAME + "=?", new String[]{packageName});
            } else {
                cv.put(KEY_PACKAGE_NAME, packageName);
                long retVal = ourDatabase.insert(TABLE_PRIVACY, null, cv);
                Log.i("AppDatabase", "SetPrivacyOptions retval = " + retVal);
            }
        }
    }

    /**
     * Set privacy option of an app.
     * @param packageName name of the app
     * @param option option of PrivacyOptions enum, that we set the value of.
     * @param isBlocked boolean, if user wants to block an option.
     */
    public void setPrivacy(String packageName, PrivacyOptions option, boolean isBlocked) {
        // Bit, that we want to change
        int curBit = option.ordinal();
        // Current value of privacy options
        int value = getPrivacyOptionsValue(packageName);
        // Make the selected bit '1'
        value |= (1 << curBit);
        // If we want to block an option, we set the selected bit to '0'.
        value ^= isBlocked ? 0 : (1 << curBit);
        // Update the value
        setPrivacyOptionsValue(packageName, value);
    }

    /**
     * Get privacy option of an app.
     * @param packageName name of the app
     * @param option option of PrivacyOptions enum, that we set the value of.
     * @return returns true if the option is blocking.
     */
    public boolean getPrivacy(String packageName, PrivacyOptions option) {
        // Bit, that we want to change
        int curBit = option.ordinal();
        // Current value of privacy options
        int value = getPrivacyOptionsValue(packageName);
        // Read the bit
        int bit = value & (1 << curBit);
        // If that bit was 0, the bit variable is 0. If not, it's some power of 2.
        return bit != 0;
    }
}
