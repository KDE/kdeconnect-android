package org.kde.kdeconnect.UserInterface

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import org.apache.commons.lang3.ArrayUtils
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Plugins.SharePlugin.ShareSettingsFragment
import org.kde.kdeconnect.UserInterface.About.AboutFragment.Companion.newInstance
import org.kde.kdeconnect.UserInterface.About.getApplicationAboutData
import org.kde.kdeconnect.UserInterface.DeviceFragment.Companion.newInstance
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMainBinding

private const val MENU_ENTRY_ADD_DEVICE = 1 //0 means no-selection
private const val MENU_ENTRY_SETTINGS = 2
private const val MENU_ENTRY_ABOUT = 3
private const val MENU_ENTRY_DEVICE_FIRST_ID = 1000 //All subsequent ids are devices in the menu
private const val MENU_ENTRY_DEVICE_UNKNOWN = 9999 //It's still a device, but we don't know which one yet
private const val STORAGE_LOCATION_CONFIGURED = 2020
private const val STATE_SELECTED_MENU_ENTRY = "selected_entry" //Saved only in onSaveInstanceState
private const val STATE_SELECTED_DEVICE = "selected_device" //Saved persistently in preferences

class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mNavigationView: NavigationView by lazy { binding.navigationDrawer }
    private val mDrawerLayout: DrawerLayout? by lazy { binding.drawerLayout }

    private lateinit var mNavViewDeviceName: TextView

    private var mCurrentDevice: String? = null
    private var mCurrentMenuEntry = 0
        private set(value) {
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
            setContentFragment(mCurrentDevice?.let { newInstance(it, false) } ?: PairingFragment())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceHelper.initializeDeviceId(this)

        setContentView(binding.root)

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
        mNavViewDeviceType?.setImageDrawable(DeviceHelper.getDeviceType(this).getIcon(this))
        mNavViewDeviceName.text = deviceName
        mNavigationView.setNavigationItemSelectedListener { menuItem: MenuItem ->
            mCurrentMenuEntry = menuItem.itemId
            when (mCurrentMenuEntry) {
                MENU_ENTRY_ADD_DEVICE -> {
                    mCurrentDevice = null
                    preferences.edit().putString(STATE_SELECTED_DEVICE, null).apply()
                    setContentFragment(PairingFragment())
                }

                MENU_ENTRY_SETTINGS -> {
                    preferences.edit().putString(STATE_SELECTED_DEVICE, null).apply()
                    setContentFragment(SettingsFragment())
                }

                MENU_ENTRY_ABOUT -> {
                    preferences.edit().putString(STATE_SELECTED_DEVICE, null).apply()
                    setContentFragment(newInstance(getApplicationAboutData(this)))
                }

                else -> {
                    val deviceId = mMapMenuToDeviceId[menuItem]
                    onDeviceSelected(deviceId)
                }
            }
            mDrawerLayout?.closeDrawer(mNavigationView)
            true
        }

        // Decide which menu entry should be selected at start
        var savedDevice: String?
        var savedMenuEntry: Int
        when {
            intent.hasExtra(FLAG_FORCE_OVERVIEW) -> {
                Log.i(this::class.simpleName, "Requested to start main overview")
                savedDevice = null
                savedMenuEntry = MENU_ENTRY_ADD_DEVICE
            }

            intent.hasExtra(EXTRA_DEVICE_ID) -> {
                Log.i(this::class.simpleName, "Loading selected device from parameter")
                savedDevice = intent.getStringExtra(EXTRA_DEVICE_ID)
                savedMenuEntry = MENU_ENTRY_DEVICE_UNKNOWN
                // If pairStatus is not empty, then the user has accepted/reject the pairing from the notification
                val pairStatus = intent.getStringExtra(PAIR_REQUEST_STATUS)
                if (pairStatus != null) {
                    Log.i(this::class.simpleName, "Pair status is $pairStatus")
                    savedDevice = onPairResultFromNotification(savedDevice, pairStatus)
                    if (savedDevice == null) {
                        savedMenuEntry = MENU_ENTRY_ADD_DEVICE
                    }
                }
            }

            savedInstanceState != null -> {
                Log.i(this::class.simpleName, "Loading selected device from saved activity state")
                savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE)
                savedMenuEntry = savedInstanceState.getInt(STATE_SELECTED_MENU_ENTRY, MENU_ENTRY_ADD_DEVICE)
            }

            else -> {
                Log.i(this::class.simpleName, "Loading selected device from persistent storage")
                savedDevice = preferences.getString(STATE_SELECTED_DEVICE, null)
                savedMenuEntry = if (savedDevice != null) MENU_ENTRY_DEVICE_UNKNOWN else MENU_ENTRY_ADD_DEVICE
            }
        }
        mCurrentMenuEntry = savedMenuEntry
        mCurrentDevice = savedDevice
        mNavigationView.setCheckedItem(savedMenuEntry)

        //FragmentManager will restore whatever fragment was there
        if (savedInstanceState != null) {
            val frag = supportFragmentManager.findFragmentById(R.id.container)
            if (frag !is DeviceFragment || frag.deviceId == savedDevice) return
        }

        // Activate the chosen fragment and select the entry in the menu
        if (savedMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID && savedDevice != null) {
            onDeviceSelected(savedDevice)
        } else {
            when (mCurrentMenuEntry) {
                MENU_ENTRY_SETTINGS -> setContentFragment(SettingsFragment())
                MENU_ENTRY_ABOUT -> setContentFragment(newInstance(getApplicationAboutData(this)))
                else -> setContentFragment(PairingFragment())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun onPairResultFromNotification(deviceId: String?, pairStatus: String): String? {
        assert(deviceId != null)
        if (pairStatus != PAIRING_PENDING) {
            BackgroundService.RunCommand(this) { service: BackgroundService ->
                val device = service.getDevice(deviceId)
                if (device == null) {
                    Log.w(this::class.simpleName, "Reject pairing - device no longer exists: $deviceId")
                    return@RunCommand
                }
                when (pairStatus) {
                    PAIRING_ACCEPTED -> device.acceptPairing()
                    PAIRING_REJECTED -> device.rejectPairing()
                }
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
        BackgroundService.RunCommand(this@MainActivity) { service: BackgroundService ->
            val menu = mNavigationView.menu
            menu.clear()
            mMapMenuToDeviceId.clear()
            val devicesMenu = menu.addSubMenu(R.string.devices)
            var id = MENU_ENTRY_DEVICE_FIRST_ID
            val devices: Collection<Device> = service.devices.values
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
    }

    override fun onStart() {
        super.onStart()
        BackgroundService.RunCommand(this) { service: BackgroundService ->
            service.onNetworkChange()
            service.addDeviceListChangedCallback(this::class.simpleName) { updateDeviceList() }
        }
        updateDeviceList()
        onBackPressedDispatcher.addCallback(mainFragmentCallback)
        onBackPressedDispatcher.addCallback(closeDrawerCallback)
        if (mDrawerLayout == null) closeDrawerCallback.isEnabled = false
    }

    override fun onStop() {
        BackgroundService.RunCommand(this) { service: BackgroundService -> service.removeDeviceListChangedCallback(this::class.simpleName) }
        super.onStop()
        mainFragmentCallback.remove()
        closeDrawerCallback.remove()
    }

    @JvmOverloads
    fun onDeviceSelected(deviceId: String?, fromDeviceList: Boolean = false) {
        mCurrentDevice = deviceId
        preferences.edit().putString(STATE_SELECTED_DEVICE, deviceId).apply()
        if (mCurrentDevice != null) {
            mCurrentMenuEntry = deviceIdToMenuEntryId(deviceId)
            if (mCurrentMenuEntry == MENU_ENTRY_DEVICE_UNKNOWN) {
                uncheckAllMenuItems(mNavigationView.menu)
            } else {
                mNavigationView.setCheckedItem(mCurrentMenuEntry)
            }
            setContentFragment(newInstance(deviceId, fromDeviceList))
        } else {
            mCurrentMenuEntry = MENU_ENTRY_ADD_DEVICE
            mNavigationView.setCheckedItem(mCurrentMenuEntry)
            setContentFragment(PairingFragment())
        }
    }

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when {
            requestCode == RESULT_NEEDS_RELOAD -> BackgroundService.RunCommand(this) { service: BackgroundService ->
                val device = service.getDevice(mCurrentDevice)
                device.reloadPluginsFromSettings()
            }

            requestCode == STORAGE_LOCATION_CONFIGURED && resultCode == RESULT_OK && data != null -> {
                val uri = data.data
                ShareSettingsFragment.saveStorageLocationPreference(this, uri)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsGranted = ArrayUtils.contains(grantResults, PackageManager.PERMISSION_GRANTED)
        if (permissionsGranted) {
            val i = ArrayUtils.indexOf(permissions, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val writeStoragePermissionGranted = i != ArrayUtils.INDEX_NOT_FOUND &&
                    grantResults[i] == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && writeStoragePermissionGranted) {
                // To get a writeable path manually on Android 10 and later for Share and Receive Plugin.
                // Otherwise, Receiving files will keep failing until the user chooses a path manually to receive files.
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, STORAGE_LOCATION_CONFIGURED)
            }

            //New permission granted, reload plugins
            BackgroundService.RunCommand(this) { service: BackgroundService ->
                val device = service.getDevice(mCurrentDevice)
                device.reloadPluginsFromSettings()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (DeviceHelper.KEY_DEVICE_NAME_PREFERENCE == key) {
            mNavViewDeviceName.text = DeviceHelper.getDeviceName(this)
            BackgroundService.RunCommand(this) { obj: BackgroundService -> obj.onNetworkChange() } //Re-send our identity packet
        }
    }

    private fun uncheckAllMenuItems(menu: Menu) {
        val size = menu.size()
        for (i in 0 until size) {
            val item = menu.getItem(i)
            item.subMenu?.let { uncheckAllMenuItems(it) } ?: item.setChecked(false)
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
        const val PAIR_REQUEST_STATUS = "pair_req_status"
        const val PAIRING_ACCEPTED = "accepted"
        const val PAIRING_REJECTED = "rejected"
        const val PAIRING_PENDING = "pending"
        const val RESULT_NEEDS_RELOAD = RESULT_FIRST_USER
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
