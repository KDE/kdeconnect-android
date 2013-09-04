package org.kde.kdeconnect.Plugins;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Button;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;

public abstract class Plugin {

    protected Device device;
    protected Context context;

    public void setContext(Context context, Device device) {
        this.device = device;
        this.context = context;
    }

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it. This function can not access
     * this.context nor this.device.
     */
    public abstract String getPluginName();

    /**
     * Return the human-readable plugin name. This function can
     * access this.context to provide translated text.
     */
    public abstract String getDisplayName();

    /**
     * Return the human-readable description of this plugin. This
     * function can access this.context to provide translated text.
     */
    public abstract String getDescription();

    /**
     * Return an icon associated to this plugin. This function can
     * access this.context to load the image from resources.
     */
    public abstract Drawable getIcon();

    /**
     * Return true if this plugin should be enabled on new devices.
     * This function can access this.context and perform compatibility
     * checks with the Android version, but can not access this.device.
     */
    public abstract boolean isEnabledByDefault();

    /**
     * Initialize the listeners and structures in your plugin.
     * Should return true if initialization was successful.
     */
    public abstract boolean onCreate();

    /**
     * Finish any ongoing operations, remove listeners... so
     * this object could be garbage collected.
     */
    public abstract void onDestroy();

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    public abstract boolean onPackageReceived(NetworkPackage np);

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    public abstract AlertDialog getErrorDialog(Context baseContext);

    /**
     * Creates a button that will be displayed in the user interface
     * It can open an activity or perform any other action that the
     * plugin would wants to expose to the user. Return null if no
     * button should be displayed.
     */
    public abstract Button getInterfaceButton(Activity activity);


}
