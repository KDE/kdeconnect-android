/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.AlertDialogFragment;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PermissionsAlertDialogFragment;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

public abstract class Plugin {
    protected Device device;
    protected Context context;
    @Nullable
    protected SharedPreferences preferences;

    public final void setContext(@NonNull Context context, @Nullable Device device) {
        this.device = device;
        this.context = context;

        if (device != null) {
            this.preferences = this.context.getSharedPreferences(this.getSharedPreferencesName(), Context.MODE_PRIVATE);
        }
    }

    public @NotNull String getSharedPreferencesName() {
        if (device == null) {
            throw new RuntimeException("You have to call setContext() before you can call getSharedPreferencesName()");
        }

        if (this.supportsDeviceSpecificSettings())
            return this.device.getDeviceId() + "_" + this.getPluginKey() + "_preferences";
        else
            return this.getPluginKey() + "_preferences";
    }

    public @Nullable SharedPreferences getPreferences() {
        return this.preferences;
    }

    /**
     * To receive the network packet from the unpaired device, override
     * listensToUnpairedDevices to return true and this method.
     */
    public boolean onUnpairedDevicePacketReceived(@NonNull NetworkPacket np) {
        return false;
    }

    /**
     * Returns whether this plugin should be loaded or not, to listen to NetworkPackets
     * from the unpaired devices. By default, returns false.
     */
    public boolean listensToUnpairedDevices() {
        return false;
    }

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it. Use the class name as key.
     */
    public final @NonNull String getPluginKey() {
        return getPluginKey(this.getClass());
    }

    public static @NonNull String getPluginKey(Class<? extends Plugin> p) {
        return p.getSimpleName();
    }

    /**
     * Return the human-readable plugin name. This function can
     * access this.context to provide translated text.
     */
    public abstract @NonNull String getDisplayName();

    /**
     * Return the human-readable description of this plugin. This
     * function can access this.context to provide translated text.
     */
    public abstract @NonNull String getDescription();

    /**
     * Return the action name displayed in the main activity, that
     * will call startMainActivity when clicked
     */
    public @NonNull String getActionName() {
        return getDisplayName();
    }

    /**
     * Return an icon associated to this plugin. Only needed if hasMainActivity() returns true and displayInContextMenu() returns false
     */
    public @DrawableRes int getIcon() {
        return -1;
    }

    /**
     * Return true if this plugin should be enabled on new devices.
     * This function can access this.context and perform compatibility
     * checks with the Android version, but can not access this.device.
     */
    public boolean isEnabledByDefault() {
        return true;
    }

    /**
     * Return true if this plugin needs an specific UI settings.
     */
    public boolean hasSettings() {
        return false;
    }

    /**
     * Called to find out if a plugin supports device specific settings.
     * If you return true your PluginSettingsFragment will use the device
     * specific SharedPreferences to store the settings.
     *
     * @return true if this plugin supports device specific settings
     */
    public boolean supportsDeviceSpecificSettings() { return false; }

    /**
     * If hasSettings returns true, this will be called when the user
     * wants to access this plugin's preferences. The default implementation
     * will return a PluginSettingsFragment with content from "yourplugin"_preferences.xml
     *
     * @return The PluginSettingsFragment used to display this plugins settings
     */
    public @Nullable PluginSettingsFragment getSettingsFragment(Activity activity) {
        throw new RuntimeException("Plugin doesn't reimplement getSettingsFragment: " + getPluginKey());

    }

    /**
     * Return true if the plugin should display something in the Device main view
     */
    public boolean displayAsButton(Context context) {
        return false;
    }

    /**
     * Return true if the entry for this app should appear in the context menu instead of the main view
     */
    public boolean displayInContextMenu() {
        return false;
    }

    /**
     * Implement here what your plugin should do when clicked
     */
    public void startMainActivity(Activity parentActivity) {
    }

    /**
     * Returns false when we should avoid loading this Plugin for {@link #device}.
     * <p>
     *     Called after {@link #setContext(Context, Device)} but before {@link #onCreate()}.
     * </p>
     * <p>
     *     By default, this just checks if {@link #getMinSdk()} is smaller or equal than the
     *     {@link Build.VERSION#SDK_INT SDK version} of this Android device.
     * </p>
     *
     * @return true if it's safe to call {@link #onCreate()}
     */
    @CallSuper
    public boolean isCompatible() {
        return Build.VERSION.SDK_INT >= getMinSdk();
    }

    /**
     * Initialize the listeners and structures in your plugin.
     * <p>
     *     If {@link #isCompatible()} or {@link #checkRequiredPermissions()} returns false, this
     *     will <em>not</em> be called.
     * </p>
     *
     * @return true if initialization was successful, false otherwise
     */
    public boolean onCreate() {
        return true;
    }

    /**
     * Finish any ongoing operations, remove listeners... so
     * this object could be garbage collected. Note that this gets
     * called as well if onCreate threw an exception, so your plugin
     * could be not fully initialized.
     */
    public void onDestroy() { }

    /**
     * Called when a plugin receives a packet. By convention we return true
     * when we have done something in response to the packet or false
     * otherwise, even though that value is unused as of now.
     */
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        return false;
    }

    /**
     * Should return the list of NetworkPacket types that this plugin can handle
     */
    public abstract @NonNull String[] getSupportedPacketTypes();

    /**
     * Should return the list of NetworkPacket types that this plugin can send
     */
    public abstract @NonNull String[] getOutgoingPacketTypes();

    /**
     * Should return the list of permissions from Manifest.permission.* that, if not present,
     * mean the plugin can't be loaded.
     */
    protected @NonNull String[] getRequiredPermissions() {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * Should return the list of permissions from Manifest.permission.* that enable additional
     * functionality in the plugin (without preventing the plugin to load).
     */
    protected @NonNull String[] getOptionalPermissions() {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * Returns the string to display before asking for the required permissions for the plugin.
     */
    protected @StringRes int getPermissionExplanation() {
        return R.string.permission_explanation;
    }

    /**
     * Returns the string to display before asking for the optional permissions for the plugin.
     */
    protected @StringRes int getOptionalPermissionExplanation() {
        return R.string.optional_permission_explanation;
    }

    //Permission from Manifest.permission.*
    protected boolean isPermissionGranted(@NonNull String permission) {
        int result = ContextCompat.checkSelfPermission(context, permission);
        return (result == PackageManager.PERMISSION_GRANTED);
    }

    protected boolean arePermissionsGranted(@NonNull String[] permissions) {
        for (String permission : permissions) {
            if (!isPermissionGranted(permission)) {
                return false;
            }
        }
        return true;
    }

    private @NonNull PermissionsAlertDialogFragment requestPermissionDialog(@NonNull final String[] permissions, @StringRes int reason) {
        return new PermissionsAlertDialogFragment.Builder()
                .setTitle(getDisplayName())
                .setMessage(reason)
                .setPermissions(permissions)
                .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                .create();
    }

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */

    public @NonNull DialogFragment getPermissionExplanationDialog() {
        return requestPermissionDialog(getRequiredPermissions(), getPermissionExplanation());
    }

    public @NonNull AlertDialogFragment getOptionalPermissionExplanationDialog() {
        return requestPermissionDialog(getOptionalPermissions(), getOptionalPermissionExplanation());
    }

    public boolean checkRequiredPermissions() {
        return arePermissionsGranted(getRequiredPermissions());
    }

    public boolean checkOptionalPermissions() {
        return arePermissionsGranted(getOptionalPermissions());
    }

    public int getMinSdk() {
        return Build.VERSION_CODES.BASE;
    }
}
