package org.kde.kdeconnect

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

/**
 * Mock implementation of shared preference, which just saves data in memory using map.
 *
 * It DOES NOT support transactions, changes are immediate
 *
 * From https://gist.github.com/amardeshbd/354173d00b988574ee5019c4ba0c8a0b
 */
internal class MockSharedPreference : SharedPreferences {
    private val preferenceMap = mutableMapOf<String, Any?>()
    private val preferenceEditor = MockSharedPreferenceEditor(preferenceMap)

    override fun getAll(): Map<String, *> = preferenceMap
    override fun getString(s: String, def: String?): String? = preferenceMap.getOrDefault(s, def) as String?
    override fun getBoolean(s: String, def: Boolean): Boolean = preferenceMap.getOrDefault(s, def) as Boolean
    override fun getInt(s: String, def: Int): Int = preferenceMap.getOrDefault(s, def) as Int
    override fun getLong(s: String, def: Long): Long = preferenceMap.getOrDefault(s, def) as Long
    override fun getFloat(s: String, def: Float): Float = preferenceMap.getOrDefault(s, def) as Float
    override fun getStringSet(s: String, def: Set<String>?): Set<String>? = preferenceMap.getOrDefault(s, def) as Set<String>?
    override fun contains(s: String): Boolean = preferenceMap.containsKey(s)
    override fun edit(): SharedPreferences.Editor = preferenceEditor

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}

    internal class MockSharedPreferenceEditor(private val preferenceMap: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(s: String, s1: String?): SharedPreferences.Editor {
            preferenceMap[s] = s1
            return this
        }

        override fun putStringSet(s: String, set: Set<String>?): SharedPreferences.Editor {
            preferenceMap[s] = set
            return this
        }

        override fun putInt(s: String, i: Int): SharedPreferences.Editor {
            preferenceMap[s] = i
            return this
        }

        override fun putLong(s: String, l: Long): SharedPreferences.Editor {
            preferenceMap[s] = l
            return this
        }

        override fun putFloat(s: String, v: Float): SharedPreferences.Editor {
            preferenceMap[s] = v
            return this
        }

        override fun putBoolean(s: String, b: Boolean): SharedPreferences.Editor {
            preferenceMap[s] = b
            return this
        }

        override fun remove(s: String): SharedPreferences.Editor {
            preferenceMap.remove(s)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            preferenceMap.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            // Nothing to do, everything is saved in memory.
        }
    }
}
