/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.notifications

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

internal class AppDatabase private constructor(context: Context) {

    private val ourHelper: DbHelper = DbHelper(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)

    protected fun finalize() {
        ourHelper.close()
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(DATABASE_CREATE_ENABLED)
            db.execSQL(DATABASE_CREATE_PRIVACY_OPTS)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 5) {
                db.execSQL(DATABASE_CREATE_PRIVACY_OPTS)
            }
        }
    }

    fun setEnabled(packageName: String, isEnabled: Boolean) {
        val columns = arrayOf(KEY_IS_ENABLED)
        val ourDatabase = ourHelper.writableDatabase
        ourDatabase.query(TABLE_ENABLED, columns, "$KEY_PACKAGE_NAME =? ", arrayOf(packageName), null, null, null).use { res ->
            val cv = ContentValues().apply {
                put(KEY_IS_ENABLED, if (isEnabled) 1 else 0)
            }
            if (res.count > 0) {
                ourDatabase.update(TABLE_ENABLED, cv, "$KEY_PACKAGE_NAME=?", arrayOf(packageName))
            } else {
                cv.put(KEY_PACKAGE_NAME, packageName)
                val retVal = ourDatabase.insert(TABLE_ENABLED, null, cv)
                Log.i("AppDatabase", "SetEnabled retval = $retVal")
            }
        }
    }

    var allEnabled: Boolean
        get() = prefs.getBoolean(SETTINGS_KEY_ALL_ENABLED, true)
        set(enabled) {
            prefs.edit().putBoolean(SETTINGS_KEY_ALL_ENABLED, enabled).apply()
            val ourDatabase = ourHelper.writableDatabase
            ourDatabase.execSQL("UPDATE $TABLE_ENABLED SET $KEY_IS_ENABLED=" + if (enabled) "1" else "0")
        }

    fun isEnabled(packageName: String): Boolean {
        val columns = arrayOf(KEY_IS_ENABLED)
        val ourDatabase = ourHelper.readableDatabase
        return ourDatabase.query(TABLE_ENABLED, columns, "$KEY_PACKAGE_NAME =? ", arrayOf(packageName), null, null, null).use { res ->
            if (res.count > 0) {
                res.moveToFirst()
                res.getInt(res.getColumnIndexOrThrow(KEY_IS_ENABLED)) != 0
            } else {
                getDefaultStatus(packageName)
            }
        }
    }

    private fun getDefaultStatus(packageName: String): Boolean {
        if (disabledByDefault.contains(packageName)) {
            return false
        }
        return allEnabled
    }

    enum class PrivacyOptions {
        BLOCK_CONTENTS,
        BLOCK_IMAGES,
    }

    private fun getPrivacyOptionsValue(packageName: String): Int {
        val columns = arrayOf(KEY_PRIVACY_OPTIONS)
        val ourDatabase = ourHelper.readableDatabase
        return ourDatabase.query(TABLE_PRIVACY, columns, "$KEY_PACKAGE_NAME =? ", arrayOf(packageName), null, null, null).use { res ->
            if (res.count > 0) {
                res.moveToFirst()
                res.getInt(res.getColumnIndexOrThrow(KEY_PRIVACY_OPTIONS))
            } else {
                0
            }
        }
    }

    private fun setPrivacyOptionsValue(packageName: String, value: Int) {
        val columns = arrayOf(KEY_PRIVACY_OPTIONS)
        val ourDatabase = ourHelper.writableDatabase
        ourDatabase.query(TABLE_PRIVACY, columns, "$KEY_PACKAGE_NAME =? ", arrayOf(packageName), null, null, null).use { res ->
            val cv = ContentValues().apply {
                put(KEY_PRIVACY_OPTIONS, value)
            }
            if (res.count > 0) {
                ourDatabase.update(TABLE_PRIVACY, cv, "$KEY_PACKAGE_NAME=?", arrayOf(packageName))
            } else {
                cv.put(KEY_PACKAGE_NAME, packageName)
                val retVal = ourDatabase.insert(TABLE_PRIVACY, null, cv)
                Log.i("AppDatabase", "SetPrivacyOptions retval = $retVal")
            }
        }
    }

    /**
     * Set privacy option of an app.
     * @param packageName name of the app
     * @param option option of PrivacyOptions enum, that we set the value of.
     * @param isBlocked boolean, if user wants to block an option.
     */
    fun setPrivacy(packageName: String, option: PrivacyOptions, isBlocked: Boolean) {
        // Bit, that we want to change
        val curBit = option.ordinal
        // Current value of privacy options
        var value = getPrivacyOptionsValue(packageName)
        // Make the selected bit '1'
        value = value or (1 shl curBit)
        // If we want to block an option, we set the selected bit to '0'.
        value = value xor if (isBlocked) 0 else (1 shl curBit)
        // Update the value
        setPrivacyOptionsValue(packageName, value)
    }

    /**
     * Get privacy option of an app.
     * @param packageName name of the app
     * @param option option of PrivacyOptions enum, that we set the value of.
     * @return returns true if the option is blocking.
     */
    fun getPrivacy(packageName: String, option: PrivacyOptions): Boolean {
        // Bit, that we want to change
        val curBit = option.ordinal
        // Current value of privacy options
        val value = getPrivacyOptionsValue(packageName)
        // Read the bit
        val bit = value and (1 shl curBit)
        // If that bit was 0, the bit variable is 0. If not, it's some power of 2.
        return bit != 0
    }

    companion object {
        private val disabledByDefault: HashSet<String> = hashSetOf(
            "com.google.android.googlequicksearchbox", //Google Now notifications re-spawn every few minutes
        )

        private const val SETTINGS_NAME = "app_database"
        private const val SETTINGS_KEY_ALL_ENABLED = "all_enabled"

        private const val DATABASE_VERSION = 5
        private const val DATABASE_NAME = "Applications"
        private const val TABLE_ENABLED = "Applications"
        private const val TABLE_PRIVACY = "PrivacyOpts"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_IS_ENABLED = "isEnabled"
        private const val KEY_PRIVACY_OPTIONS = "privacyOptions"

        private const val DATABASE_CREATE_ENABLED = "CREATE TABLE " +
            TABLE_ENABLED + "(" + KEY_PACKAGE_NAME + " TEXT PRIMARY KEY NOT NULL, " +
            KEY_IS_ENABLED + " INTEGER NOT NULL ); "
        private const val DATABASE_CREATE_PRIVACY_OPTS = "CREATE TABLE " +
            TABLE_PRIVACY + "(" + KEY_PACKAGE_NAME + " TEXT PRIMARY KEY NOT NULL, " +
            KEY_PRIVACY_OPTIONS + " INTEGER NOT NULL); "

        private var instance: AppDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return instance ?: AppDatabase(context.applicationContext).also { instance = it }
        }
    }
}
