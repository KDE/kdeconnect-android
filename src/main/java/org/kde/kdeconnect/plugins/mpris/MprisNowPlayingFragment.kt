/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mpris

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import org.kde.kdeconnect.helpers.DEFAULT_MAX_VOLUME
import org.kde.kdeconnect.helpers.DEFAULT_VOLUME_STEP
import org.kde.kdeconnect.helpers.VideoUrlsHelper
import org.kde.kdeconnect.helpers.calculateNewVolume
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.mpris.MprisPlugin.MprisPlayer
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.MprisControlBinding
import org.kde.kdeconnect_tp.databinding.MprisNowPlayingBinding
import java.net.MalformedURLException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias MprisPlayerCallback = (MprisPlayer) -> Unit

class MprisNowPlayingFragment : Fragment(), VolumeKeyListener {
    private val positionSeekUpdateHandler = Handler()
    private lateinit var mprisControlBinding: MprisControlBinding
    private lateinit var activityMprisBinding: MprisNowPlayingBinding
    private var deviceId: String? = null
    private lateinit var positionSeekUpdateRunnable: Runnable

    private var targetPlayerName = ""
    private var targetPlayer: MprisPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        activityMprisBinding = MprisNowPlayingBinding.inflate(inflater)
        mprisControlBinding = activityMprisBinding.mprisControl

        deviceId = requireArguments().getString(MprisPlugin.DEVICE_ID_KEY)

        val activityIntent = requireActivity().intent

        val stringExtra = activityIntent.getStringExtra("player")
        if (stringExtra != null) {
            activityIntent.removeExtra("player")
        }
        targetPlayerName = stringExtra
            ?: savedInstanceState?.getString("targetPlayer")
            ?: "".also {
                Log.i("MprisNowPlayingFragment", "No `targetPlayer` specified in savedInstanceState")
            }

        connectToPlugin()

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val intervalTime = prefs.getString(
            getString(R.string.mpris_time_key),
            getString(R.string.mpris_time_default)
        )!!.toInt()

        performActionOnClick(mprisControlBinding.loopButton) { p: MprisPlayer ->
            when (p.loopStatus) {
                "None" -> p.sendSetLoopStatus("Track")
                "Track" -> p.sendSetLoopStatus("Playlist")
                "Playlist" -> p.sendSetLoopStatus("None")
            }
        }

        performActionOnClick(mprisControlBinding.playButton, MprisPlayer::sendPlayPause)

        performActionOnClick(
            mprisControlBinding.shuffleButton
        ) { p -> p.sendSetShuffle(!p.shuffle) }

        performActionOnClick(mprisControlBinding.prevButton, MprisPlayer::sendPrevious)

        performActionOnClick(
            mprisControlBinding.rewButton
        ) { p -> p.sendSeek(intervalTime * -1) }

        performActionOnClick(
            mprisControlBinding.ffButton
        ) { p -> p.sendSeek(intervalTime) }

        performActionOnClick(mprisControlBinding.nextButton, MprisPlayer::sendNext)

        performActionOnClick(mprisControlBinding.stopButton, MprisPlayer::sendStop)

        mprisControlBinding.volumeSeek.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, isFromUser: Boolean) {
                if (isFromUser) targetPlayer?.sendSetVolume(seekBar.progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        positionSeekUpdateRunnable = Runnable {
            if (!isAdded) return@Runnable  // Fragment was already detached

            targetPlayer?.let {
                mprisControlBinding.positionSeek.progress = it.position.toInt()
            }
            positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable)
            positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 1000)
        }
        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200)

        mprisControlBinding.positionSeek.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, byUser: Boolean) {
                mprisControlBinding.progressTextview.text = durationToProgress(progress.milliseconds)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                targetPlayer?.sendSetPosition(seekBar.progress)
                positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200)
            }
        })

        mprisControlBinding.nowPlayingTextview.isSelected = true

        return activityMprisBinding.root
    }

    override fun onDestroyView() {
        disconnectFromPlugin()
        mprisControlBinding.playerSpinner.adapter = null
        super.onDestroyView()
    }

    private fun disconnectFromPlugin() {
        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MprisPlugin::class.java) ?: return
        plugin.apply {
            removePlayerListUpdatedHandler("activity")
            removePlayerStatusUpdatedHandler("activity")
        }
    }

    private fun connectToPlugin() {
        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MprisPlugin::class.java)
        if (plugin == null) {
            if (isAdded) {
                requireActivity().finish()
            }
            return
        }
        targetPlayer = plugin.getPlayerStatus(targetPlayerName)

        plugin.setPlayerStatusUpdatedHandler("activity") {
            requireActivity().runOnUiThread {
                updatePlayerStatus(plugin)
            }
        }
        plugin.setPlayerListUpdatedHandler("activity") {
            requireActivity().runOnUiThread {
                val playerList = plugin.playerList
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    playerList.toTypedArray()
                )

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

                mprisControlBinding.playerSpinner.adapter = adapter

                if (playerList.isEmpty()) {
                    mprisControlBinding.noPlayers.visibility = View.VISIBLE
                    mprisControlBinding.playerSpinner.visibility = View.GONE
                    mprisControlBinding.nowPlayingTextview.text = ""
                } else {
                    mprisControlBinding.noPlayers.visibility = View.GONE
                    mprisControlBinding.playerSpinner.visibility = View.VISIBLE
                }

                mprisControlBinding.playerSpinner.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(arg0: AdapterView<*>?, arg1: View?, pos: Int, id: Long) {

                            if (pos >= playerList.size) return

                            val player = playerList[pos]
                            if (player == targetPlayer?.playerName) {
                                return  //Player hasn't actually changed
                            }
                            targetPlayer = plugin.getPlayerStatus(player)?.also {
                                targetPlayerName = it.playerName
                            }

                            updatePlayerStatus(plugin)

                            if (targetPlayer?.isPlaying == true) {
                                MprisMediaSession.instance.playerSelected(targetPlayer)
                            }
                        }

                        override fun onNothingSelected(arg0: AdapterView<*>?) {
                            targetPlayer = null
                        }
                    }

                if (targetPlayer == null) {
                    //If no player is selected, try to select a playing player
                    targetPlayer = plugin.playingPlayer
                }
                //Try to select the specified player
                targetPlayer?.let {
                    val targetIndex = adapter.getPosition(it.playerName)
                    if (targetIndex >= 0) {
                        mprisControlBinding.playerSpinner.setSelection(targetIndex)
                    } else {
                        targetPlayer = null
                    }
                }
                //If no player selected, select the first one (if any)
                if (targetPlayer == null && playerList.isNotEmpty()) {
                    targetPlayer = plugin.getPlayerStatus(playerList[0])
                    mprisControlBinding.playerSpinner.setSelection(0)
                }
                updatePlayerStatus(plugin)
            }
        }
    }

    private inline fun performActionOnClick(v: View, crossinline l: MprisPlayerCallback) {
        v.setOnClickListener {
            val targetPlayer = targetPlayer ?: return@setOnClickListener
            l(targetPlayer)
        }
    }

    private fun updatePlayerStatus(plugin: MprisPlugin) {
        if (!isAdded) {
            //Fragment is not attached to an activity. We will crash if we try to do anything here.
            return
        }

        var playerStatus = targetPlayer
        if (playerStatus == null) {
            //No player with that name found, just display "empty" data
            playerStatus = plugin.getEmptyPlayer()
        }

        var song = playerStatus.title
        if (playerStatus.artist.isNotEmpty()) {
            song += " - " + playerStatus.artist
        }
        if (mprisControlBinding.nowPlayingTextview.text.toString() != song) {
            mprisControlBinding.nowPlayingTextview.text = song
        }

        val albumArt = playerStatus.getAlbumArt()
        if (albumArt == null) {
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_album_art_placeholder)!!
            activityMprisBinding.albumArt.setImageDrawable(DrawableCompat.wrap(drawable))
        } else {
            activityMprisBinding.albumArt.setImageBitmap(albumArt)
        }

        if (playerStatus.isSeekAllowed) {
            mprisControlBinding.timeTextview.text = durationToProgress(playerStatus.length.milliseconds)
            mprisControlBinding.positionSeek.max = playerStatus.length.toInt()
            mprisControlBinding.positionSeek.progress = playerStatus.position.toInt()
            mprisControlBinding.progressSlider.visibility = View.VISIBLE
        } else {
            mprisControlBinding.progressSlider.visibility = View.GONE
        }

        val volume = playerStatus.volume
        mprisControlBinding.volumeSeek.progress = volume
        if (!playerStatus.isSetVolumeAllowed) {
            mprisControlBinding.volumeSeek.isEnabled = false
        }
        val isPlaying = playerStatus.isPlaying
        if (isPlaying) {
            mprisControlBinding.playButton.setIconResource(R.drawable.ic_pause_black)
            mprisControlBinding.playButton.isEnabled = playerStatus.isPauseAllowed
        } else {
            mprisControlBinding.playButton.setIconResource(R.drawable.ic_play_black)
            mprisControlBinding.playButton.isEnabled = playerStatus.isPlayAllowed
        }

        val loopStatus = playerStatus.loopStatus
        when (loopStatus) {
            "None" -> mprisControlBinding.loopButton.setIconResource(R.drawable.ic_loop_none_black)
            "Track" -> mprisControlBinding.loopButton.setIconResource(R.drawable.ic_loop_track_black)
            "Playlist" -> mprisControlBinding.loopButton.setIconResource(R.drawable.ic_loop_playlist_black)
        }
        val shuffle = playerStatus.shuffle
        if (shuffle) {
            mprisControlBinding.shuffleButton.setIconResource(R.drawable.ic_shuffle_on_black)
        } else {
            mprisControlBinding.shuffleButton.setIconResource(R.drawable.ic_shuffle_off_black)
        }

        mprisControlBinding.loopButton.visibility = if (playerStatus.isLoopStatusAllowed) View.VISIBLE else View.GONE
        mprisControlBinding.shuffleButton.visibility = if (playerStatus.isShuffleAllowed) View.VISIBLE else View.GONE
        mprisControlBinding.volumeLayout.visibility =
            if (playerStatus.isSetVolumeAllowed) View.VISIBLE else View.GONE
        mprisControlBinding.rewButton.visibility = if (playerStatus.isSeekAllowed) View.VISIBLE else View.GONE
        mprisControlBinding.ffButton.visibility =
            if (playerStatus.isSeekAllowed) View.VISIBLE else View.GONE

        requireActivity().invalidateOptionsMenu()

        //Show and hide previous/next buttons simultaneously
        if (playerStatus.isGoPreviousAllowed || playerStatus.isGoNextAllowed) {
            mprisControlBinding.prevButton.visibility = View.VISIBLE
            mprisControlBinding.prevButton.isEnabled = playerStatus.isGoPreviousAllowed
            mprisControlBinding.nextButton.visibility = View.VISIBLE
            mprisControlBinding.nextButton.isEnabled = playerStatus.isGoNextAllowed
        } else {
            mprisControlBinding.prevButton.visibility = View.GONE
            mprisControlBinding.nextButton.visibility = View.GONE
        }
    }

    /**
     * Change current volume with provided step.
     *
     * @param step step size volume change
     */
    private fun updateVolume(step: Int) {
        val targetPlayer = targetPlayer ?: return

        val newVolume = calculateNewVolume(targetPlayer.volume, DEFAULT_MAX_VOLUME, step)

        if (targetPlayer.volume != newVolume) {
            targetPlayer.sendSetVolume(newVolume)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit
            override fun onPrepareMenu(menu: Menu) {
                menu.clear()
                if (!targetPlayer?.getHttpUrl().isNullOrEmpty()) {
                    menu.add(0, MENU_OPEN_URL, Menu.NONE, R.string.mpris_open_url)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val targetPlayer = targetPlayer
                if (targetPlayer != null && menuItem.itemId == MENU_OPEN_URL) {
                    try {
                        val httpUrl = targetPlayer.getHttpUrl() ?: return false
                        val transformedUrl = httpUrl
                            .let { VideoUrlsHelper.convertToAndFromYoutubeTvLinks(it) }
                            .let { VideoUrlsHelper.formatUriWithSeek(it, targetPlayer.position) }
                            .toUri()
                        val browserIntent = Intent(Intent.ACTION_VIEW, transformedUrl)
                        startActivity(browserIntent)
                        targetPlayer.sendPause()
                        return true
                    } catch (e: MalformedURLException) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), getString(R.string.cant_open_url), Toast.LENGTH_LONG).show()
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), getString(R.string.cant_open_url), Toast.LENGTH_LONG).show()
                    }
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onVolumeUp() {
        updateVolume(DEFAULT_VOLUME_STEP)
    }

    override fun onVolumeDown() {
        updateVolume(-DEFAULT_VOLUME_STEP)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (targetPlayer != null) {
            outState.putString("targetPlayer", targetPlayerName)
        }
    }

    companion object {
        const val MENU_OPEN_URL: Int = Menu.FIRST
        fun newInstance(deviceId: String?): MprisNowPlayingFragment {
            val mprisNowPlayingFragment = MprisNowPlayingFragment()

            val arguments = Bundle()
            arguments.putString(MprisPlugin.DEVICE_ID_KEY, deviceId)

            mprisNowPlayingFragment.arguments = arguments

            return mprisNowPlayingFragment
        }

        private fun durationToProgress(duration: Duration): String = buildString {
            val length = duration.inWholeSeconds
            var minutes = length / 60
            if (minutes > 60) {
                val hours = minutes / 60
                minutes %= 60
                append(hours)
                append(':')
                if (minutes < 10) append('0')
            }
            append(minutes)
            append(':')
            val seconds = (length % 60)
            if (seconds < 10) append('0') // needed to show length properly (eg 4:05 instead of 4:5)

            append(seconds)
        }
    }
}
