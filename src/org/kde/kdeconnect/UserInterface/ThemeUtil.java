package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.kde.kdeconnect_tp.R;

/**
 * Utilities for working with android {@link android.content.res.Resources.Theme Themes}.
 */
public class ThemeUtil {

    /**
     * This method should be called from the {@code activity}'s onCreate method, before
     * any calls to {@link Activity#setContentView} or
     * {@link android.preference.PreferenceActivity#setPreferenceScreen}.
     *
     * @param activity any Activity on screen
     */
    public static void setUserPreferredTheme(Activity activity) {
        boolean useDarkTheme = shouldUseDarkTheme(activity);

        // Only MainActivity sets its own Toolbar as the ActionBar.
        boolean usesOwnActionBar = activity instanceof MainActivity;

        if (useDarkTheme) {
            activity.setTheme(usesOwnActionBar ? R.style.KdeConnectTheme_Dark_NoActionBar : R.style.KdeConnectTheme_Dark);
        } else {
            activity.setTheme(usesOwnActionBar ? R.style.KdeConnectTheme_NoActionBar : R.style.KdeConnectTheme);
        }
    }

    /**
     * Checks {@link SharedPreferences} to figure out whether we should use the light
     * theme or the dark theme. The app defaults to light theme.
     *
     * @param context any active context (Activity, Service, Application, etc.)
     * @return true if the dark theme should be active, false otherwise
     */
    public static boolean shouldUseDarkTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("darkTheme", false);
    }
}
