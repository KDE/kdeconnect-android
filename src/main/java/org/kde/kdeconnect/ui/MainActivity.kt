/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.kde.kdeconnect.ui.compose.KdeTheme
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.extensions.setOnApplyWindowInsetsListenerCompat
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.share.ShareSettingsFragment
import org.kde.kdeconnect.plugins.share.ShareFragment
import org.kde.kdeconnect.ui.about.AboutFragment
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMainBinding

private const val MENU_ENTRY_ADD_DEVICE = 1 //0 means no-selection
private const val MENU_ENTRY_SETTINGS = 2
private const val MENU_ENTRY_ABOUT = 3
private const val MENU_ENTRY_SHARE = 4
private const val MENU_ENTRY_DEVICE_FIRST_ID = 1000 //All subsequent ids are devices in the menu
private const val MENU_ENTRY_DEVICE_UNKNOWN = 9999 //It's still a device, but we don't know which one yet
private const val STATE_SELECTED_MENU_ENTRY = "selected_entry" //Saved only in onSaveInstanceState
private const val STATE_SELECTED_DEVICE = "selected_device" //Saved persistently in preferences

class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mNavigationView: NavigationView by lazy { binding.navigationDrawer }

    val isTwoPaneLayout: Boolean
        get() = resources.getBoolean(R.bool.is_two_pane)

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ShareSettingsFragment.saveStorageLocationPreference(this, uri)
        }
    }

    private lateinit var mNavViewDeviceName: TextView

    private var phoneSelectedEntry by mutableIntStateOf(MENU_ENTRY_ADD_DEVICE)
    private var mCurrentDevice: String? = null
    private var mCurrentMenuEntry = 0
        set(value) {
            field = value
            phoneSelectedEntry = value
            mainFragmentCallback.isEnabled = value == MENU_ENTRY_SETTINGS || value == MENU_ENTRY_ABOUT ||
                value == MENU_ENTRY_SHARE ||
                (value >= MENU_ENTRY_DEVICE_FIRST_ID && mCurrentDevice != null)
        }
    private val preferences: SharedPreferences by lazy { getSharedPreferences("stored_menu_selection", MODE_PRIVATE) }
    private val mMapMenuToDeviceId = HashMap<MenuItem, String>()

    private val mainFragmentCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mCurrentMenuEntry == MENU_ENTRY_SETTINGS || mCurrentMenuEntry == MENU_ENTRY_ABOUT ||
                mCurrentMenuEntry == MENU_ENTRY_SHARE
            ) {
                mCurrentMenuEntry = mCurrentDevice?.let { deviceIdToMenuEntryId(it) } ?: MENU_ENTRY_ADD_DEVICE
                mNavigationView.setCheckedItem(mCurrentMenuEntry)
                setContentFragment(mCurrentDevice?.let { DeviceFragment.newInstance(it, false) } ?: PairingFragment())
            } else {
                mCurrentDevice = null
                preferences.edit { putString(STATE_SELECTED_DEVICE, null) }
                mCurrentMenuEntry = MENU_ENTRY_ADD_DEVICE
                mNavigationView.setCheckedItem(mCurrentMenuEntry)
                setContentFragment(PairingFragment())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DeviceHelper.initializeDeviceId(this)

        val root = binding.root
        setContentView(root)
        if (isTwoPaneLayout) binding.toolbarLayout.root.visibility = View.GONE

        // Pad the drawer header directly: DrawerLayout's default child dispatch stops at
        // AppBarLayout (which consumes the insets via its own fitsSystemWindows), so neither
        // NavigationView nor its header ever receive a dispatch.
        root.setOnApplyWindowInsetsListenerCompat { _, insets ->
            mNavigationView.getHeaderView(0)?.updatePadding(
                top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            )
            binding.container.updatePadding(
                top = if (isTwoPaneLayout) insets.getInsets(WindowInsetsCompat.Type.statusBars()).top else 0
            )
            insets
        }

        val mDrawerHeader = mNavigationView.getHeaderView(0)
        mNavViewDeviceName = mDrawerHeader.findViewById(R.id.device_name)
        val mNavViewDeviceType = mDrawerHeader.findViewById<ImageView>(R.id.device_type)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        binding.bottomNavigation?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KdeTheme(this@MainActivity) {
                    PhoneNavigationBar(phoneSelectedEntry) { entry ->
                        when (entry) {
                            MENU_ENTRY_SHARE -> openShareScreen(mCurrentDevice)
                            MENU_ENTRY_SETTINGS -> {
                                mCurrentMenuEntry = MENU_ENTRY_SETTINGS
                                setContentFragment(SettingsFragment())
                            }
                            else -> onDeviceSelected(mCurrentDevice)
                        }
                    }
                }
            }
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
                    setContentFragment(SettingsFragment())
                }

                MENU_ENTRY_ABOUT -> {
                    setContentFragment(AboutFragment.newInstance())
                }

                MENU_ENTRY_SHARE -> openShareScreen(mCurrentDevice)

                else -> {
                    val deviceId = mMapMenuToDeviceId[menuItem]
                    onDeviceSelected(deviceId)
                }
            }
            true
        }

        // Decide which menu entry should be selected at start
        var savedDevice: String?
        var savedMenuEntry: Int
        when {
            savedInstanceState != null -> {
                Log.i(LOG_TAG, "Loading selected device from saved activity state")
                savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE)
                savedMenuEntry = savedInstanceState.getInt(STATE_SELECTED_MENU_ENTRY, MENU_ENTRY_ADD_DEVICE)
            }

            intent.hasExtra(FLAG_FORCE_OVERVIEW) -> {
                Log.i(LOG_TAG, "Requested to start main overview")
                savedDevice = null
                savedMenuEntry = MENU_ENTRY_ADD_DEVICE
            }

            isIncomingShareIntent(intent) || intent.getBooleanExtra(EXTRA_SHOW_SHARE, false) -> {
                savedDevice = intent.getStringExtra(EXTRA_DEVICE_ID)
                    ?: preferences.getString(STATE_SELECTED_DEVICE, null)
                savedMenuEntry = MENU_ENTRY_SHARE
            }

            intent.hasExtra(EXTRA_DEVICE_ID) -> {
                Log.i(LOG_TAG, "Loading selected device from parameter")
                savedDevice = intent.getStringExtra(EXTRA_DEVICE_ID)
                savedMenuEntry = MENU_ENTRY_DEVICE_UNKNOWN
                // If pairStatus is not empty, then the user has accepted/reject the pairing from the notification
                val pairStatus = intent.getStringExtra(PAIR_REQUEST_STATUS)
                if (pairStatus != null) {
                    Log.i(LOG_TAG, "Pair status is $pairStatus")
                    savedDevice = onPairResultFromNotification(savedDevice, pairStatus)
                    if (savedDevice == null) {
                        savedMenuEntry = MENU_ENTRY_ADD_DEVICE
                    }
                }
            }

            else -> {
                Log.i(LOG_TAG, "Loading selected device from persistent storage")
                savedDevice = preferences.getString(STATE_SELECTED_DEVICE, null)
                savedMenuEntry = if (savedDevice != null) MENU_ENTRY_DEVICE_UNKNOWN else MENU_ENTRY_ADD_DEVICE
            }
        }
        mCurrentMenuEntry = savedMenuEntry
        mCurrentDevice = savedDevice
        mNavigationView.setCheckedItem(savedMenuEntry)
        updatePhoneToolbar()

        // FragmentManager restores the previous fragment, but it may not match the saved
        // selection when the activity was recreated during a size/orientation change.
        if (savedInstanceState != null) {
            val frag = supportFragmentManager.findFragmentById(R.id.container)
            val fragmentMatchesSelection = when {
                savedMenuEntry == MENU_ENTRY_SETTINGS -> frag is SettingsFragment
                savedMenuEntry == MENU_ENTRY_ABOUT -> frag is AboutFragment
                savedMenuEntry == MENU_ENTRY_SHARE -> frag is ShareFragment
                savedMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID && savedDevice != null ->
                    frag is DeviceFragment && frag.deviceId == savedDevice
                else -> frag is PairingFragment
            }
            if (fragmentMatchesSelection) return
        }

        // Activate the chosen fragment and select the entry in the menu
        if (savedMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID && savedDevice != null) {
            onDeviceSelected(savedDevice)
        } else {
            when (mCurrentMenuEntry) {
                MENU_ENTRY_SETTINGS -> setContentFragment(SettingsFragment())
                MENU_ENTRY_ABOUT -> setContentFragment(AboutFragment.newInstance())
                MENU_ENTRY_SHARE -> setContentFragment(ShareFragment.newInstance(savedDevice, shareIntentOrNull(intent)))
                else -> setContentFragment(PairingFragment())
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

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), RESULT_NOTIFICATIONS_ENABLED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun onPairResultFromNotification(deviceId: String?, pairStatus: String): String? {
        assert(deviceId != null)
        if (pairStatus != PAIRING_PENDING) {
            val device = KdeConnect.getInstance().getDevice(deviceId)
            if (device == null) {
                Log.w(LOG_TAG, "Reject pairing - device no longer exists: $deviceId")
                return null
            }
            when (pairStatus) {
                PAIRING_ACCEPTED -> device.acceptPairing()
                PAIRING_REJECTED -> device.cancelPairing()
            }
        }
        return if (pairStatus == PAIRING_ACCEPTED || pairStatus == PAIRING_PENDING) deviceId else null
    }

    fun openShareScreen(deviceId: String?) {
        mCurrentDevice = deviceId
        if (deviceId != null) {
            preferences.edit { putString(STATE_SELECTED_DEVICE, deviceId) }
        }
        mCurrentMenuEntry = MENU_ENTRY_SHARE
        mNavigationView.setCheckedItem(MENU_ENTRY_SHARE)
        setContentFragment(ShareFragment.newInstance(deviceId))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!isIncomingShareIntent(intent) && !intent.getBooleanExtra(EXTRA_SHOW_SHARE, false)) return

        val selectedDevice = intent.getStringExtra(EXTRA_DEVICE_ID)
            ?: mCurrentDevice
            ?: preferences.getString(STATE_SELECTED_DEVICE, null)
        mCurrentDevice = selectedDevice
        if (selectedDevice != null) {
            preferences.edit { putString(STATE_SELECTED_DEVICE, selectedDevice) }
        }
        mCurrentMenuEntry = MENU_ENTRY_SHARE
        mNavigationView.setCheckedItem(MENU_ENTRY_SHARE)
        setContentFragment(ShareFragment.newInstance(selectedDevice, shareIntentOrNull(intent)))
    }

    private fun isIncomingShareIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE

    private fun shareIntentOrNull(intent: Intent): Intent? = intent.takeIf(::isIncomingShareIntent)

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
            onBackPressedDispatcher.onBackPressed()
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
        val shareItem = menu.add(Menu.FIRST, MENU_ENTRY_SHARE, 999, R.string.share_files_title)
        shareItem.setIcon(R.drawable.ic_share_white)
        shareItem.isCheckable = true
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
        BackgroundService.Start(applicationContext)
        KdeConnect.getInstance().addDeviceListChangedCallback("MainActivity") { runOnUiThread { updateDeviceList() } }
        updateDeviceList()
        onBackPressedDispatcher.addCallback(mainFragmentCallback)
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("MainActivity")
        mainFragmentCallback.remove()
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

    private fun updatePhoneToolbar() {
        if (binding.bottomNavigation == null) return
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(mCurrentMenuEntry != MENU_ENTRY_ADD_DEVICE)
            subtitle = null
            title = when (mCurrentMenuEntry) {
                MENU_ENTRY_SETTINGS -> getString(R.string.settings)
                MENU_ENTRY_ABOUT -> getString(R.string.about)
                else -> mCurrentDevice?.let { KdeConnect.getInstance().getDevice(it)?.name }
                    ?: getString(R.string.devices)
            }
        }
    }

    private fun setContentFragment(fragment: Fragment) {
        updatePhoneToolbar()
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RESULT_NEEDS_RELOAD -> {
                CoroutineScope(Dispatchers.IO).launch {
                    KdeConnect.getInstance().devices.values.forEach(Device::reloadPluginsFromSettings)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun isPermissionGranted(permissions: Array<String>, grantResults: IntArray, permission : String) : Boolean {
        val index = permissions.indexOf(permission)
        return index != -1 && grantResults[index] == PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsGranted = grantResults.contains(PERMISSION_GRANTED)
        if (permissionsGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isPermissionGranted(permissions, grantResults, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // To get a writeable path manually on Android 10 and later for Share and Receive Plugin.
                // Otherwise, Receiving files will keep failing until the user chooses a path manually to receive files.
                openDocumentTreeLauncher.launch(null)
            }

            if (isPermissionGranted(permissions, grantResults, Manifest.permission.BLUETOOTH_CONNECT) &&
                isPermissionGranted(permissions, grantResults, Manifest.permission.BLUETOOTH_SCAN)) {
                PreferenceManager.getDefaultSharedPreferences(this).edit {
                    putBoolean(SettingsFragment.KEY_BLUETOOTH_ENABLED, true)
                }
                setContentFragment(SettingsFragment())
            }

            if (
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isPermissionGranted(permissions, grantResults, Manifest.permission.POST_NOTIFICATIONS))
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && isPermissionGranted(permissions, grantResults, Manifest.permission.ACCESS_LOCAL_NETWORK))
            ) {
                // If PairingFragment is active, reload it
                if (mCurrentDevice == null) {
                    setContentFragment(PairingFragment())
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && isPermissionGranted(permissions, grantResults, Manifest.permission.ACCESS_LOCAL_NETWORK)) {
                BackgroundService.ForceRefreshConnections(this)
            }

            //New permission granted, reload plugins
            CoroutineScope(Dispatchers.IO).launch {
                KdeConnect.getInstance().devices.values.forEach(Device::reloadPluginsFromSettings)
            }
        }
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
        const val LOG_TAG = "MainActivity"

        const val EXTRA_DEVICE_ID = "deviceId"
        const val PAIR_REQUEST_STATUS = "pair_req_status"
        const val PAIRING_ACCEPTED = "accepted"
        const val PAIRING_REJECTED = "rejected"
        const val PAIRING_PENDING = "pending"
        const val RESULT_NEEDS_RELOAD = RESULT_FIRST_USER
        const val RESULT_NOTIFICATIONS_ENABLED = RESULT_FIRST_USER+1
        const val FLAG_FORCE_OVERVIEW = "forceOverview"
        const val EXTRA_SHOW_SHARE = "showShare"

        fun getSelectedDeviceId(context: Context): String? = context
            .getSharedPreferences("stored_menu_selection", Context.MODE_PRIVATE)
            .getString(STATE_SELECTED_DEVICE, null)

        fun isShareIntent(intent: Intent?): Boolean =
            intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE
    }

}
