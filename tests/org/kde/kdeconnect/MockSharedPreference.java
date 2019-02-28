package org.kde.kdeconnect;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;


/**
 * Mock implementation of shared preference, which just saves data in memory using map.
 *
 * It DOES NOT support transactions, changes are immediate
 *
 * From https://gist.github.com/amardeshbd/354173d00b988574ee5019c4ba0c8a0b
 */
class MockSharedPreference implements SharedPreferences {

    private final HashMap<String, Object> preferenceMap;
    private final MockSharedPreferenceEditor preferenceEditor;

    public MockSharedPreference() {
        preferenceMap = new HashMap<>();
        preferenceEditor = new MockSharedPreferenceEditor(preferenceMap);
    }

    @Override
    public Map<String, ?> getAll() {
        return preferenceMap;
    }

    @Nullable
    @Override
    public String getString(final String s, @Nullable final String def) {
        if (!preferenceMap.containsKey(s)) return def;
        return (String) preferenceMap.get(s);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(final String s, @Nullable final Set<String> def) {
        if (!preferenceMap.containsKey(s)) return def;
        return (Set<String>) preferenceMap.get(s);
    }

    @Override
    public int getInt(final String s, final int def) {
        if (!preferenceMap.containsKey(s)) return def;
        return (int) preferenceMap.get(s);
    }

    @Override
    public long getLong(final String s, final long def) {
        if (!preferenceMap.containsKey(s)) return def;
        return (long) preferenceMap.get(s);
    }

    @Override
    public float getFloat(final String s, final float def) {
        if (!preferenceMap.containsKey(s)) return def;
        return (float) preferenceMap.get(s);
    }

    @Override
    public boolean getBoolean(final String s, final boolean def) {
        if (!preferenceMap.containsKey(s)) return def;
        return (boolean) preferenceMap.get(s);
    }

    @Override
    public boolean contains(final String s) {
        return preferenceMap.containsKey(s);
    }

    @Override
    public Editor edit() {
        return preferenceEditor;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {

    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {

    }

    static class MockSharedPreferenceEditor implements Editor {

        private final HashMap<String, Object> preferenceMap;

        MockSharedPreferenceEditor(final HashMap<String, Object> preferenceMap) {
            this.preferenceMap = preferenceMap;
        }

        @Override
        public Editor putString(final String s, @Nullable final String s1) {
            preferenceMap.put(s, s1);
            return this;
        }

        @Override
        public Editor putStringSet(final String s, @Nullable final Set<String> set) {
            preferenceMap.put(s, set);
            return this;
        }

        @Override
        public Editor putInt(final String s, final int i) {
            preferenceMap.put(s, i);
            return this;
        }

        @Override
        public Editor putLong(final String s, final long l) {
            preferenceMap.put(s, l);
            return this;
        }

        @Override
        public Editor putFloat(final String s, final float v) {
            preferenceMap.put(s, v);
            return this;
        }

        @Override
        public Editor putBoolean(final String s, final boolean b) {
            preferenceMap.put(s, b);
            return this;
        }

        @Override
        public Editor remove(final String s) {
            preferenceMap.remove(s);
            return this;
        }

        @Override
        public Editor clear() {
            preferenceMap.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return true;
        }

        @Override
        public void apply() {
            // Nothing to do, everything is saved in memory.
        }
    }

}
