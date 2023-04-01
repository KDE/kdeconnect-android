package org.kde.kdeconnect.UserInterface;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;


/**
 * Utilities for working with android {@link android.content.res.Resources.Theme Themes}.
 */
public class ThemeUtil {

    public static final String LIGHT_MODE = "light";
    public static final String DARK_MODE = "dark";
    public static final String DEFAULT_MODE = "default";

    public static void applyTheme(@NonNull String themePref) {
        switch (themePref) {
            case LIGHT_MODE: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            }
            case DARK_MODE: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            }
            default: {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                }
                break;
            }
        }
    }

    /**
    * Called when an activity is created for the first time to reliably load correct theme.
    **/
    public static void setUserPreferredTheme(Application application) {
        String appTheme = PreferenceManager.getDefaultSharedPreferences(application)
                .getString("theme_pref", DEFAULT_MODE);
        DynamicColors.applyToActivitiesIfAvailable(application);
        applyTheme(appTheme);
    }
}
