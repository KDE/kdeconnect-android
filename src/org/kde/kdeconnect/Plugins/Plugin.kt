/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Plugins

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import org.apache.commons.lang3.ArrayUtils
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.UserInterface.AlertDialogFragment
import org.kde.kdeconnect.UserInterface.MainActivity
import org.kde.kdeconnect.UserInterface.PermissionsAlertDialogFragment
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment
import org.kde.kdeconnect_tp.R

abstract class Plugin {
    protected lateinit var device: Device
    protected lateinit var context: Context

    protected val isDeviceInitialized: Boolean
        get() = ::device.isInitialized

    var preferences: SharedPreferences? = null
        protected set

    fun setContext(context: Context, device: Device?) {
        this.context = context

        if (device != null) {
            this.device = device
            this.preferences =
                this.context.getSharedPreferences(this.sharedPreferencesName, Context.MODE_PRIVATE)
        }
    }

    val sharedPreferencesName: String
        get() {
            if (isDeviceInitialized.not()) {
                throw RuntimeException("You have to call setContext() before you can call getSharedPreferencesName()")
            }
            if (this.supportsDeviceSpecificSettings()) {
                return device.deviceId + "_" + this.pluginKey + "_preferences"
            }
            return pluginKey + "_preferences"
        }

    /**
     * To receive the network packet from the unpaired device, override
     * listensToUnpairedDevices to return true and this method.
     */
    open fun onUnpairedDevicePacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    /**
     * Returns whether this plugin should be loaded or not, to listen to NetworkPackets
     * from the unpaired devices. By default, returns false.
     */
    open fun listensToUnpairedDevices(): Boolean {
        return false
    }

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it.
     * Use the class name as `key`.
     */
    val pluginKey: String = getPluginKey(this.javaClass)

    /**
     * Return the human-readable plugin name. This function can
     * access this.context to provide translated text.
     */
    abstract val displayName: String

    /**
     * Return the human-readable description of this plugin. This
     * function can access this.context to provide translated text.
     */
    abstract val description: String

    /**
     * Return the action name displayed in the main activity, that
     * will call startMainActivity when clicked
     */
    open val actionName: String
        get() = displayName

    /**
     * Return an icon associated to this plugin.
     * Only needed if hasMainActivity() returns true and displayInContextMenu() returns false
     */
    @get:DrawableRes
    open val icon: Int = -1

    /**
     * Return true if this plugin should be enabled on new devices.
     * This function can access this.context and perform compatibility
     * checks with the Android version, but cannot access this.device.
     */
    open val isEnabledByDefault: Boolean = true

    /**
     * Return true if this plugin needs a specific UI settings.
     */
    open fun hasSettings(): Boolean = false

    /**
     * Called to find out if a plugin supports device-specific settings.
     * If you return `true` your PluginSettingsFragment will use the device
     * specific SharedPreferences to store the settings.
     *
     * @return true if this plugin supports device-specific settings
     */
    open fun supportsDeviceSpecificSettings(): Boolean = false

    /**
     * If hasSettings returns true, this will be called when the user
     * wants to access this plugin's preferences. The default implementation
     * will return a PluginSettingsFragment with content from "yourplugin"_preferences.xml
     *
     * @return The PluginSettingsFragment used to display this plugin's settings
     */
    open fun getSettingsFragment(activity: Activity): PluginSettingsFragment? {
        throw RuntimeException("Plugin doesn't reimplement getSettingsFragment: $pluginKey")
    }

    /**
     * Return true if the plugin should display something in the Device main view
     */
    open fun displayAsButton(context: Context): Boolean = false

    /**
     * Return true if the entry for this app should appear in the context menu instead of the main view
     */
    open fun displayInContextMenu(): Boolean = false

    /**
     * Implement here what your plugin should do when clicked
     */
    open fun startMainActivity(parentActivity: Activity) {
        /* no-op */
    }

    @get:CallSuper
    open val isCompatible: Boolean
        /**
         * Returns false when we should avoid loading this Plugin for [device].
         *
         * Called after [setContext] but before [onCreate].
         *
         * By default, this just checks if [minSdk] is smaller or equal than the
         * [SDK version][Build.VERSION.SDK_INT] of this Android device.
         *
         * @return true if it's safe to call [onCreate]
         */
        get() = Build.VERSION.SDK_INT >= minSdk

    /**
     * Initialize the listeners and structures in your plugin.
     *
     * If [isCompatible] or [checkRequiredPermissions] returns false, this
     * will *not* be called.
     *
     * @return true if initialization was successful, false otherwise
     */
    open fun onCreate(): Boolean {
        return true
    }

    /**
     * Finish any ongoing operations, remove listeners... so
     * this object could be garbage collected. Note that this gets
     * called as well if onCreate threw an exception, so your plugin
     * could be not fully initialized.
     */
    open fun onDestroy() {}

    /**
     * Called when a plugin receives a packet.
     * By convention, we return true when we have done something in response to the packet or false otherwise,
     * even though that value is unused as of now.
     */
    open fun onPacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    /**
     * Should return the list of NetworkPacket types that this plugin can handle
     */
    abstract val supportedPacketTypes: Array<String>

    /**
     * Should return the list of NetworkPacket types that this plugin can send
     */
    abstract val outgoingPacketTypes: Array<String>

    protected open val requiredPermissions: Array<String>
        /**
         * Should return the list of permissions from Manifest.permission.* that, if not present,
         * mean the plugin can't be loaded.
         */
        get() = ArrayUtils.EMPTY_STRING_ARRAY

    protected open val optionalPermissions: Array<String>
        /**
         * Should return the list of permissions from Manifest.permission.* that enable additional
         * functionality in the plugin (without preventing the plugin to load).
         */
        get() = ArrayUtils.EMPTY_STRING_ARRAY

    /**
     * Returns the string to display before asking for the required permissions for the plugin.
     */
    @get:StringRes
    protected open val permissionExplanation: Int = R.string.permission_explanation

    /**
     * Returns the string to display before asking for the optional permissions for the plugin.
     */
    @get:StringRes
    protected open val optionalPermissionExplanation: Int = R.string.optional_permission_explanation

    // Permission from Manifest.permission.*
    protected fun isPermissionGranted(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(context, permission)
        return result == PackageManager.PERMISSION_GRANTED
    }

    protected fun arePermissionsGranted(permissions: Array<String>): Boolean {
        return permissions.all(::isPermissionGranted)
    }

    private fun requestPermissionDialog(
        permissions: Array<String>,
        @StringRes reason: Int
    ): PermissionsAlertDialogFragment {
        return PermissionsAlertDialogFragment.Builder()
            .setTitle(displayName)
            .setMessage(reason)
            .setPermissions(permissions)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()
    }

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    open val permissionExplanationDialog: DialogFragment
        get() = requestPermissionDialog(requiredPermissions, permissionExplanation)

    open val optionalPermissionExplanationDialog: AlertDialogFragment
        get() = requestPermissionDialog(optionalPermissions, optionalPermissionExplanation)

    open fun checkRequiredPermissions(): Boolean {
        return arePermissionsGranted(requiredPermissions)
    }

    open fun checkOptionalPermissions(): Boolean {
        return arePermissionsGranted(optionalPermissions)
    }

    open fun onDeviceUnpaired(context: Context, deviceId: String) {}

    open val minSdk: Int = Build.VERSION_CODES.BASE

    companion object {
        @JvmStatic
        fun getPluginKey(p: Class<out Plugin>): String {
            return p.simpleName
        }
    }
}
