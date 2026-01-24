/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 * SPDX-FileCopyrightText: 2020 Sylvia van Os <sylvia@hackerchick.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mousepad

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.KdeConnect.Companion.getInstance
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.extensions.viewBinding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityBigscreenBinding

class BigscreenActivity : BaseActivity<ActivityBigscreenBinding>() {

    override val binding : ActivityBigscreenBinding by viewBinding(ActivityBigscreenBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val deviceId = intent.getStringExtra("deviceId")

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.micButton.isEnabled = false
            binding.micButton.visibility = View.INVISIBLE
        }

        val plugin = getInstance().getDevicePlugin(deviceId, MousePadPlugin::class.java)
        if (plugin == null) {
            finish()
            return
        }

        binding.leftButton.setOnClickListener { v: View? -> plugin.sendLeft() }
        binding.rightButton.setOnClickListener { v: View? -> plugin.sendRight() }
        binding.upButton.setOnClickListener { v: View? -> plugin.sendUp() }
        binding.downButton.setOnClickListener { v: View? -> plugin.sendDown() }
        binding.selectButton.setOnClickListener { v: View? -> plugin.sendSelect() }
        binding.homeButton.setOnClickListener { v: View? -> plugin.sendHome() }
        binding.backButton.setOnClickListener { v: View? -> plugin.sendBack() }
        binding.micButton.setOnClickListener { v: View? ->
            if (plugin.hasMicPermission()) {
                activateSTT()
            } else {
                PermissionsAlertDialogFragment.Builder()
                    .setTitle(plugin.displayName)
                    .setMessage(R.string.bigscreen_optional_permission_explanation)
                    .setPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
                    .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                    .create().show(supportFragmentManager, null)
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(getString(R.string.pref_bigscreen_show_back), true)) {
            binding.backButton.visibility = View.VISIBLE
        } else {
            binding.backButton.visibility = View.INVISIBLE
        }
        if (prefs.getBoolean(getString(R.string.pref_bigscreen_show_home), false)) {
            binding.homeButton.visibility = View.VISIBLE
        } else {
            binding.homeButton.visibility = View.INVISIBLE
        }
    }

    fun activateSTT() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.bigscreen_speech_extra_prompt)
        startActivityForResult(intent, REQUEST_SPEECH)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_bigscreen, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_use_mouse_and_keyboard) {
            val intent = Intent(this, MousePadActivity::class.java)
            intent.putExtra("deviceId", getIntent().getStringExtra("deviceId"))
            startActivity(intent)
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK) {
            // The results are ordered by confidence, use the first one
            val firstResult = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.first()
            if (firstResult != null) {
                val deviceId = intent.getStringExtra("deviceId")
                val plugin = getInstance().getDevicePlugin(deviceId,MousePadPlugin::class.java)
                if (plugin == null) {
                    finish()
                    return
                }
                plugin.sendText(firstResult)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onBackPressed()
        return true
    }

    companion object {
        private const val REQUEST_SPEECH = 100
    }
}

