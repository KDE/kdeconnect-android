/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.extensions.setOnApplyWindowInsetsListenerCompat
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.ui.about.AboutFragment
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMainBinding

private const val MENU_ENTRY_ADD_DEVICE = 1 //0 means no-selection
private const val MENU_ENTRY_SETTINGS = 2
private const val MENU_ENTRY_ABOUT = 3
private const val MENU_ENTRY_DEVICE_FIRST_ID = 1000 //All subsequent ids are devices in the menu
private const val MENU_ENTRY_DEVICE_UNKNOWN = 9999 //It's still a device, but we don't know which one yet
private const val STATE_SELECTED_MENU_ENTRY = "selected_entry" //Saved only in onSaveInstanceState
private const val STATE_SELECTED_DEVICE = "selected_device" //Saved persistently in preferences

class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mNavigationView: NavigationView by lazy { binding.navigationDrawer }
    private var mDrawerLayout: DrawerLayout? = null

    private val upfrontPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && results[Manifest.permission.ACCESS_LOCAL_NETWORK] == true) {
            BackgroundService.ForceRefreshConnections(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && results[Manifest.permission.POST_NOTIFICATIONS] == true) {
            KdeConnect.getInstance().reloadAllDevicePlugins()
        }
        if (results.containsValue(true)) {
            // If PairingFragment is active, update its permissions header
            (currentFragment as? PairingFragment)?.updatePermissionsHeader()
        }
    }

    private lateinit var mNavViewDeviceName: TextView

    private var mCurrentDevice: String? = null
    private var mCurrentMenuEntry = 0
        set(value) {
            field = value
            //Enabling "go to default fragment on back" callback when user in settings or "about" fragment
            mainFragmentCallback.isEnabled = value == MENU_ENTRY_SETTINGS || value == MENU_ENTRY_ABOUT
        }
    private val preferences: SharedPreferences by lazy { getSharedPreferences("stored_menu_selection", MODE_PRIVATE) }
    private val mMapMenuToDeviceId = HashMap<MenuItem, String>()

    private val closeDrawerCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            mDrawerLayout?.closeDrawer(mNavigationView)
        }
    }

    private val mainFragmentCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            mCurrentMenuEntry = mCurrentDevice?.let { deviceIdToMenuEntryId(it) } ?: MENU_ENTRY_ADD_DEVICE
            mNavigationView.setCheckedItem(mCurrentMenuEntry)
            setContentFragment(mCurrentDevice?.let { DeviceFragment.newInstance(it, false) } ?: PairingFragment())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DeviceHelper.initializeDeviceId(this)

        val root = binding.root
        setContentView(root)
        mDrawerLayout = root as? DrawerLayout

        // Pad the drawer header directly: DrawerLayout's default child dispatch stops at
        // AppBarLayout (which consumes the insets via its own fitsSystemWindows), so neither
        // NavigationView nor its header ever receive a dispatch.
        root.setOnApplyWindowInsetsListenerCompat { _, insets ->
            mNavigationView.getHeaderView(0)?.updatePadding(
                top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            )
            insets
        }

        val mDrawerHeader = mNavigationView.getHeaderView(0)
        mNavViewDeviceName = mDrawerHeader.findViewById(R.id.device_name)
        val mNavViewDeviceType = mDrawerHeader.findViewById<ImageView>(R.id.device_type)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        mDrawerLayout?.let {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            val mDrawerToggle = DrawerToggle(it).apply { syncState() }
            it.addDrawerListener(mDrawerToggle)
            it.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        } ?: {
            supportActionBar?.setDisplayShowHomeEnabled(false)
            supportActionBar?.setHomeButtonEnabled(false)
        }

        // Note: The preference changed listener should be registered before getting the name, because getting
        // it can trigger a background fetch from the internet that will eventually update the preference
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
        val deviceName = DeviceHelper.getDeviceName(this)
        mNavViewDeviceType?.setImageDrawable(DeviceHelper.deviceType.getIcon(this))
        mNavViewDeviceName.text = deviceName
        mNavigationView.setNavigationItemSelectedListener { menuItem: MenuItem ->
            mCurrentMenuEntry = menuItem.itemId
            when (mCurrentMenuEntry) {
                MENU_ENTRY_ADD_DEVICE -> {
                    mCurrentDevice = null
                    preferences.edit { putString(STATE_SELECTED_DEVICE, null) }
                    setContentFragment(PairingFragment())
                }

                MENU_ENTRY_SETTINGS -> {
                    preferences.edit { putString(STATE_SELECTED_DEVICE, null) }
                    setContentFragment(SettingsFragment())
                }

                MENU_ENTRY_ABOUT -> {
                    preferences.edit { putString(STATE_SELECTED_DEVICE, null) }
                    setContentFragment(AboutFragment.newInstance())
                }

                else -> {
                    val deviceId = mMapMenuToDeviceId[menuItem]
                    onDeviceSelected(deviceId)
                }
            }
            mDrawerLayout?.closeDrawer(mNavigationView)
            true
        }

        when {
            savedInstanceState != null -> {
                Log.i(TAG, "Loading selected device from saved activity state")
                mCurrentDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE)
                mCurrentMenuEntry = savedInstanceState.getInt(STATE_SELECTED_MENU_ENTRY, MENU_ENTRY_ADD_DEVICE)
                mNavigationView.setCheckedItem(mCurrentMenuEntry)
            }

            !showScreenFromIntent(intent) -> {
                Log.i(TAG, "Loading selected device from persistent storage")
                onDeviceSelected(preferences.getString(STATE_SELECTED_DEVICE, null))
            }
        }

        val missingPermissions = mutableListOf<String>()

        // Upfront requests for permissions without explanation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
            && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PERMISSION_GRANTED
            && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_LOCAL_NETWORK))
        {
            missingPermissions.add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED
            && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS))
        {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check savedInstanceState to avoid requesting permissions again if the activity is recreated eg on rotation
        if (missingPermissions.isNotEmpty() && savedInstanceState == null) {
            upfrontPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    // MainActivity is singleTop, so intents sent while it's on top of the stack (eg: from notifications) arrive here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showScreenFromIntent(intent)
    }

    /**
     * Shows the screen that [intent] asks for, and applies its pairing action if it has one.
     * Returns false if the intent doesn't ask for any screen.
     * Must be called only once for each intent delivery.
     */
    private fun showScreenFromIntent(intent: Intent): Boolean {
        val intentAlreadyHandled = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (intentAlreadyHandled) {
            return false
        }
        if (intent.hasExtra(FLAG_FORCE_OVERVIEW)) {
            Log.i(TAG, "Requested to start main overview")
            onDeviceSelected(null)
            return true
        }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return false
        Log.i(TAG, "Loading selected device from parameter")
        val pairStatus = intent.getStringExtra(PAIR_REQUEST_STATUS)
        if (pairStatus == null) {
            onDeviceSelected(deviceId)
        } else {
            Log.i(TAG, "Pair status is $pairStatus")
            onDeviceSelected(onPairResultFromNotification(deviceId, pairStatus))
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun onPairResultFromNotification(deviceId: String, pairStatus: String): String? {
        if (pairStatus != PAIRING_PENDING) {
            val device = KdeConnect.getInstance().getDevice(deviceId)
            if (device == null) {
                Log.w(TAG, "Reject pairing - device no longer exists: $deviceId")
                return null
            }
            when (pairStatus) {
                PAIRING_ACCEPTED -> device.acceptPairing()
                PAIRING_REJECTED -> device.cancelPairing()
            }
        }
        return if (pairStatus == PAIRING_ACCEPTED || pairStatus == PAIRING_PENDING) deviceId else null
    }

    private fun deviceIdToMenuEntryId(deviceId: String?): Int {
        for ((key, value) in mMapMenuToDeviceId) {
            if (value == deviceId) {
                return key.itemId
            }
        }
        return MENU_ENTRY_DEVICE_UNKNOWN
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            mDrawerLayout?.openDrawer(mNavigationView)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun updateDeviceList() {
        val menu = mNavigationView.menu
        menu.clear()
        mMapMenuToDeviceId.clear()
        val devicesMenu = menu.addSubMenu(R.string.devices)
        var id = MENU_ENTRY_DEVICE_FIRST_ID
        val devices: Collection<Device> = KdeConnect.getInstance().devices.values
        for (device in devices) {
            if (device.isReachable && device.isPaired) {
                val item = devicesMenu.add(Menu.FIRST, id++, 1, device.name)
                item.icon = device.icon
                item.isCheckable = true
                mMapMenuToDeviceId[item] = device.deviceId
            }
        }
        val addDeviceItem = devicesMenu.add(Menu.FIRST, MENU_ENTRY_ADD_DEVICE, 1000, R.string.pair_new_device)
        addDeviceItem.setIcon(R.drawable.ic_action_content_add_circle_outline_32dp)
        addDeviceItem.isCheckable = true
        val settingsItem = menu.add(Menu.FIRST, MENU_ENTRY_SETTINGS, 1000, R.string.settings)
        settingsItem.setIcon(R.drawable.ic_settings_white_32dp)
        settingsItem.isCheckable = true
        val aboutItem = menu.add(Menu.FIRST, MENU_ENTRY_ABOUT, 1000, R.string.about)
        aboutItem.setIcon(R.drawable.ic_baseline_info_24)
        aboutItem.isCheckable = true

        //Ids might have changed
        if (mCurrentMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID) {
            mCurrentMenuEntry = deviceIdToMenuEntryId(mCurrentDevice)
        }
        mNavigationView.setCheckedItem(mCurrentMenuEntry)
    }

    override fun onStart() {
        super.onStart()
        BackgroundService.ForceRefreshConnections(applicationContext)
        KdeConnect.getInstance().addDeviceListChangedCallback("MainActivity") { runOnUiThread { updateDeviceList() } }
        updateDeviceList()
        onBackPressedDispatcher.addCallback(mainFragmentCallback)
        onBackPressedDispatcher.addCallback(closeDrawerCallback)
        if (mDrawerLayout == null) closeDrawerCallback.isEnabled = false
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("MainActivity")
        mainFragmentCallback.remove()
        closeDrawerCallback.remove()
        super.onStop()
    }

    fun onDeviceSelected(deviceId: String?, fromDeviceList: Boolean = false) {
        mCurrentDevice = deviceId
        preferences.edit { putString(STATE_SELECTED_DEVICE, deviceId) }
        if (deviceId != null) {
            mCurrentMenuEntry = deviceIdToMenuEntryId(deviceId)
            if (mCurrentMenuEntry == MENU_ENTRY_DEVICE_UNKNOWN) {
                uncheckAllMenuItems(mNavigationView.menu)
            } else {
                mNavigationView.setCheckedItem(mCurrentMenuEntry)
            }
            setContentFragment(DeviceFragment.newInstance(deviceId, fromDeviceList))
        } else {
            mCurrentMenuEntry = MENU_ENTRY_ADD_DEVICE
            mNavigationView.setCheckedItem(mCurrentMenuEntry)
            setContentFragment(PairingFragment())
        }
    }

    private val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.container)

    private fun setContentFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_DEVICE, mCurrentDevice)
        outState.putInt(STATE_SELECTED_MENU_ENTRY, mCurrentMenuEntry)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (DeviceHelper.KEY_DEVICE_NAME_PREFERENCE == key) {
            mNavViewDeviceName.text = DeviceHelper.getDeviceName(this)
            BackgroundService.ForceRefreshConnections(this) //Re-send our identity packet
        }
    }

    private fun uncheckAllMenuItems(menu: Menu) {
        val size = menu.size
        for (i in 0 until size) {
            val item = menu[i]
            item.subMenu?.let { uncheckAllMenuItems(it) } ?: item.setChecked(false)
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        const val EXTRA_DEVICE_ID = "deviceId"
        const val PAIR_REQUEST_STATUS = "pair_req_status"
        const val PAIRING_ACCEPTED = "accepted"
        const val PAIRING_REJECTED = "rejected"
        const val PAIRING_PENDING = "pending"
        const val FLAG_FORCE_OVERVIEW = "forceOverview"
    }

    private inner class DrawerToggle(drawerLayout: DrawerLayout) : ActionBarDrawerToggle(
        this,  /* host Activity */
        drawerLayout,  /* DrawerLayout object */
        R.string.open,  /* "open drawer" description */
        R.string.close /* "close drawer" description */
    ) {
        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            closeDrawerCallback.isEnabled = false
        }

        override fun onDrawerOpened(drawerView: View) {
            super.onDrawerOpened(drawerView)
            closeDrawerCallback.isEnabled = true
        }
    }

}
