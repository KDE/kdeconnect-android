/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.notifications

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.core.widget.TextViewCompat
import com.google.android.material.materialswitch.MaterialSwitch
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityNotificationFilterBinding
import java.util.ArrayList
import java.util.HashSet
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

// TODO: Turn this into a PluginSettingsFragment
class NotificationFilterActivity : BaseActivity<ActivityNotificationFilterBinding>() {

    private lateinit var appDatabase: AppDatabase

    internal class AppListInfo(
        val pkg: String,
        val name: String,
        val icon: Drawable,
        var isEnabled: Boolean = false,
    )

    // This variable stores all app information and serves as a data source for filtering.
    private var mAllApps: List<AppListInfo> = emptyList()
    private var apps: MutableList<AppListInfo> = mutableListOf() // Filtered data.

    override val binding: ActivityNotificationFilterBinding by lazy {
        ActivityNotificationFilterBinding.inflate(layoutInflater)
    }

    internal inner class AppListAdapter : BaseAdapter() {

        override fun getCount(): Int = apps.size + 1

        override fun getItem(position: Int): AppListInfo = apps[position - 1]

        override fun getItemId(position: Int): Long = (position - 1).toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
            val checkedTextView = view as CheckedTextView
            if (position == 0) {
                checkedTextView.setText(R.string.all)
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(checkedTextView, null, null, null, null)
                binding.lvFilterApps.setItemChecked(position, appDatabase.allEnabled)
            } else {
                val info = apps[position - 1]
                checkedTextView.text = info.name
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(checkedTextView, info.icon, null, null, null)
                checkedTextView.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
                binding.lvFilterApps.setItemChecked(position, info.isEnabled)
            }

            return view
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appDatabase = AppDatabase.getInstance(this)

        val prefKey = intent.getStringExtra(NotificationsPlugin.PREFERENCE_KEY)!!
        val preferences = getSharedPreferences(prefKey, MODE_PRIVATE)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        requireNotNull(supportActionBar).setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        configureSwitch(preferences)

        ThreadHelper.execute {
            val packageManager = packageManager
            val appList = packageManager.getInstalledApplications(0)
            val count = appList.size

            val allPackageNames = HashSet<String>(count)
            val allApps = ArrayList<AppListInfo>(count)
            for (i in 0 until count) {
                val appInfo = appList[i]
                if (!canPostNotifications(packageManager, appInfo)) {
                    continue
                }
                val appListInfo = AppListInfo(
                    pkg = appInfo.packageName,
                    name = appInfo.loadLabel(packageManager).toString(),
                    icon = resizeIcon(appInfo.loadIcon(packageManager)),
                    isEnabled = appDatabase.isEnabled(appInfo.packageName),
                )

                allApps.add(appListInfo)
                allPackageNames.add(appInfo.packageName)
            }

            // Find apps from work profile
            try {
                val currentUser = Process.myUserHandle()
                val launcher = getSystemService<LauncherApps>()
                val um = getSystemService<UserManager>()
                val userProfiles = um?.userProfiles ?: emptyList()
                for (userProfile in userProfiles) {
                    if (userProfile == currentUser) {
                        continue
                    }

                    val userActivityList = launcher?.getActivityList(null, userProfile) ?: emptyList()
                    for (app in userActivityList) {
                        if (allPackageNames.contains(app.applicationInfo.packageName)) {
                            continue
                        }

                        val appInfo = app.applicationInfo
                        if (!canPostNotifications(packageManager, appInfo)) {
                            continue
                        }
                        val appListInfo = AppListInfo(
                            pkg = appInfo.packageName,
                            name = appInfo.loadLabel(packageManager).toString(),
                            icon = resizeIcon(appInfo.loadIcon(packageManager)),
                            isEnabled = appDatabase.isEnabled(appInfo.packageName),
                        )

                        allApps.add(appListInfo)
                        allPackageNames.add(app.applicationInfo.packageName)
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationFilterActiv", "Failed to get apps from work profile", e)
            }

            allApps.sortWith { lhs, rhs -> lhs.name.compareTo(rhs.name, ignoreCase = true) }
            mAllApps = allApps
            apps = ArrayList(allApps)
            runOnUiThread { displayAppList() }
        }
    }

    private fun canPostNotifications(packageManager: PackageManager, appInfo: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || appInfo.targetSdkVersion < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        try {
            val packageInfo = packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
            if (packageInfo.requestedPermissions != null) {
                for (requestedPermission in packageInfo.requestedPermissions) {
                    if (Manifest.permission.POST_NOTIFICATIONS == requestedPermission) {
                        return true
                    }
                }
            }
            return false
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("NotificationFilterActiv", "Package not found for ${appInfo.packageName}", e)
            return true
        }
    }

    private fun configureSwitch(sharedPreferences: SharedPreferences) {
        val smScreenOffNotification: MaterialSwitch = findViewById(R.id.smScreenOffNotification)
        smScreenOffNotification.isChecked = sharedPreferences.getBoolean(NotificationsPlugin.PREF_NOTIFICATION_SCREEN_OFF, false)
        smScreenOffNotification.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit {
                putBoolean(NotificationsPlugin.PREF_NOTIFICATION_SCREEN_OFF,isChecked)
            }
        }
    }

    private fun displayAppList() {
        val listView = binding.lvFilterApps
        val adapter = AppListAdapter()
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        listView.isLongClickable = true
        listView.setOnItemClickListener { adapterView, _, i, _ ->
            if (i == 0) {
                val enabled = listView.isItemChecked(0)
                for (app in mAllApps) {
                    app.isEnabled = enabled
                }
                appDatabase.allEnabled = enabled
                (adapterView.adapter as AppListAdapter).notifyDataSetChanged()
            } else {
                val checked = listView.isItemChecked(i)
                apps[i - 1].isEnabled = checked
                appDatabase.setEnabled(apps[i - 1].pkg, checked)
                (adapterView.adapter as AppListAdapter).notifyDataSetChanged()
            }
        }
        listView.setOnItemLongClickListener { _, _, i, _ ->
            if (i == 0) return@setOnItemLongClickListener true
            val builder = AlertDialog.Builder(this)
            val mView = layoutInflater.inflate(R.layout.popup_notificationsfilter, null)
            builder.setMessage(getString(R.string.extra_options))

            val lv = mView.findViewById<ListView>(R.id.extra_options_list)
            val options = arrayOf(getString(R.string.privacy_options))
            val extraOptionsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
            lv.adapter = extraOptionsAdapter
            builder.setView(mView)

            val ad = builder.create()

            lv.setOnItemClickListener { _, _, newI, _ ->
                when (newI) {
                    0 -> {
                        val myBuilder = AlertDialog.Builder(this)
                        val packageName = apps[i - 1].pkg

                        val myView = layoutInflater.inflate(R.layout.privacy_options, null)
                        val checkboxContents = myView.findViewById<CheckBox>(R.id.checkbox_contents)
                        checkboxContents.isChecked = appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS)
                        checkboxContents.text = getString(R.string.block_contents)
                        val checkboxImages = myView.findViewById<CheckBox>(R.id.checkbox_images)
                        checkboxImages.isChecked = appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES)
                        checkboxImages.text = getString(R.string.block_images)

                        myBuilder.setView(myView)
                        myBuilder.setTitle(getString(R.string.privacy_options))
                        myBuilder.setPositiveButton(getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
                        myBuilder.setMessage(getString(R.string.set_privacy_options))

                        checkboxContents.setOnCheckedChangeListener { compoundButton, _ ->
                            appDatabase.setPrivacy(
                                packageName,
                                AppDatabase.PrivacyOptions.BLOCK_CONTENTS,
                                compoundButton.isChecked,
                            )
                        }
                        checkboxImages.setOnCheckedChangeListener { compoundButton, _ ->
                            appDatabase.setPrivacy(
                                packageName,
                                AppDatabase.PrivacyOptions.BLOCK_IMAGES,
                                compoundButton.isChecked,
                            )
                        }

                        ad.cancel()
                        myBuilder.show()
                    }
                }
            }

            ad.show()
            true
        }

        listView.setItemChecked(0, appDatabase.allEnabled) // "Select all" button
        for (i in apps.indices) {
            listView.setItemChecked(i + 1, apps[i].isEnabled)
        }

        listView.visibility = View.VISIBLE
        binding.spinner.visibility = View.GONE
    }

    private fun resizeIcon(icon: Drawable, maxSize: Int = 48): Drawable {
        val res = resources

        // Convert to display pixels
        val pixelSize = (maxSize * res.displayMetrics.density).toInt()

        val bitmap = createBitmap(pixelSize, pixelSize)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, canvas.width, canvas.height)
        icon.draw(canvas)

        return bitmap.toDrawable(res)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.add(android.R.string.search_go).apply {
            setIcon(R.drawable.ic_search_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            actionView = SearchView(this@NotificationFilterActivity).apply {
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = true

                    override fun onQueryTextChange(newText: String?): Boolean {
                        apps.clear()
                        if (newText.isNullOrEmpty()) {
                            apps.addAll(mAllApps)
                        } else {
                            val query = newText.trim()
                            for (s in mAllApps) {
                                if (s.name.contains(query, ignoreCase = true)) {
                                    apps.add(s)
                                }
                            }
                        }

                        (binding.lvFilterApps.adapter as? AppListAdapter)?.notifyDataSetChanged()
                        return true
                    }
                })
            }
        }

        return true
    }
}
