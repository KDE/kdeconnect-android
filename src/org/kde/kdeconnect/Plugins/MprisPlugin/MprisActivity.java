/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentManager;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.VideoUrlsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.SystemvolumePlugin.SystemvolumeFragment;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityMprisBinding;
import org.kde.kdeconnect_tp.databinding.MprisControlBinding;

import java.net.MalformedURLException;
import java.util.List;

public class MprisActivity extends AppCompatActivity {

    private String deviceId;
    private final Handler positionSeekUpdateHandler = new Handler();
    private Runnable positionSeekUpdateRunnable = null;
    private MprisPlugin.MprisPlayer targetPlayer = null;

    private ActivityMprisBinding activityMprisBinding;
    private MprisControlBinding mprisControlBinding;

    private static String milisToProgress(long milis) {
        int length = (int) (milis / 1000); //From milis to seconds
        StringBuilder text = new StringBuilder();
        int minutes = length / 60;
        if (minutes > 60) {
            int hours = minutes / 60;
            minutes = minutes % 60;
            text.append(hours).append(':');
            if (minutes < 10) text.append('0');
        }
        text.append(minutes).append(':');
        int seconds = (length % 60);
        if (seconds < 10)
            text.append('0'); // needed to show length properly (eg 4:05 instead of 4:5)
        text.append(seconds);
        return text.toString();
    }

    private void connectToPlugin(final String targetPlayerName) {
        BackgroundService.RunWithPlugin(this, deviceId, MprisPlugin.class, mpris -> {
            targetPlayer = mpris.getPlayerStatus(targetPlayerName);

            addSystemVolumeFragment();

            mpris.setPlayerStatusUpdatedHandler("activity", new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    runOnUiThread(() -> updatePlayerStatus(mpris));
                }
            });

            mpris.setPlayerListUpdatedHandler("activity", new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    final List<String> playerList = mpris.getPlayerList();
                    final ArrayAdapter<String> adapter = new ArrayAdapter<>(MprisActivity.this,
                            android.R.layout.simple_spinner_item,
                            playerList.toArray(ArrayUtils.EMPTY_STRING_ARRAY)
                    );

                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    runOnUiThread(() -> {
                        mprisControlBinding.playerSpinner.setAdapter(adapter);

                        if (playerList.isEmpty()) {
                            mprisControlBinding.noPlayers.setVisibility(View.VISIBLE);
                            mprisControlBinding.playerSpinner.setVisibility(View.GONE);
                            mprisControlBinding.nowPlayingTextview.setText("");
                        } else {
                            mprisControlBinding.noPlayers.setVisibility(View.GONE);
                            mprisControlBinding.playerSpinner.setVisibility(View.VISIBLE);
                        }

                        mprisControlBinding.playerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long id) {

                                if (pos >= playerList.size()) return;

                                String player = playerList.get(pos);
                                if (targetPlayer != null && player.equals(targetPlayer.getPlayer())) {
                                    return; //Player hasn't actually changed
                                }
                                targetPlayer = mpris.getPlayerStatus(player);
                                updatePlayerStatus(mpris);

                                if (targetPlayer.isPlaying()) {
                                    MprisMediaSession.getInstance().playerSelected(targetPlayer);
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> arg0) {
                                targetPlayer = null;
                            }
                        });

                        if (targetPlayer == null) {
                            //If no player is selected, try to select a playing player
                            targetPlayer = mpris.getPlayingPlayer();
                        }
                        //Try to select the specified player
                        if (targetPlayer != null) {
                            int targetIndex = adapter.getPosition(targetPlayer.getPlayer());
                            if (targetIndex >= 0) {
                                mprisControlBinding.playerSpinner.setSelection(targetIndex);
                            } else {
                                targetPlayer = null;
                            }
                        }
                        //If no player selected, select the first one (if any)
                        if (targetPlayer == null && !playerList.isEmpty()) {
                            targetPlayer = mpris.getPlayerStatus(playerList.get(0));
                            mprisControlBinding.playerSpinner.setSelection(0);
                        }
                        updatePlayerStatus(mpris);
                    });
                }
            });
        });
    }

    private void addSystemVolumeFragment() {
        if (findViewById(R.id.systemvolume_fragment) == null)
            return;

        FragmentManager fragmentManager = getSupportFragmentManager();
        ((SystemvolumeFragment) fragmentManager.findFragmentById(R.id.systemvolume_fragment)).connectToPlugin(deviceId);
    }

    private final BaseLinkProvider.ConnectionReceiver connectionReceiver = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(NetworkPacket identityPacket, BaseLink link) {
            connectToPlugin(null);
        }

        @Override
        public void onConnectionLost(BaseLink link) {

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BackgroundService.RunCommand(MprisActivity.this, service -> service.removeConnectionListener(connectionReceiver));
    }

    private void updatePlayerStatus(MprisPlugin mpris) {
        MprisPlugin.MprisPlayer playerStatus = targetPlayer;
        if (playerStatus == null) {
            //No player with that name found, just display "empty" data
            playerStatus = mpris.getEmptyPlayer();
        }
        String song = playerStatus.getCurrentSong();

        if (!mprisControlBinding.nowPlayingTextview.getText().toString().equals(song)) {
            mprisControlBinding.nowPlayingTextview.setText(song);
        }

        Bitmap albumArt = playerStatus.getAlbumArt();
        if (albumArt == null) {
            final Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_album_art_placeholder);
            assert drawable != null;
            Drawable placeholder_art = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(placeholder_art, ContextCompat.getColor(this, R.color.primary));
            activityMprisBinding.albumArt.setImageDrawable(placeholder_art);
        } else {
            activityMprisBinding.albumArt.setImageBitmap(albumArt);
        }

        if (playerStatus.isSeekAllowed()) {
            mprisControlBinding.timeTextview.setText(milisToProgress(playerStatus.getLength()));
            mprisControlBinding.positionSeek.setMax((int) (playerStatus.getLength()));
            mprisControlBinding.positionSeek.setProgress((int) (playerStatus.getPosition()));
            mprisControlBinding.progressSlider.setVisibility(View.VISIBLE);
        } else {
            mprisControlBinding.progressSlider.setVisibility(View.GONE);
        }

        int volume = playerStatus.getVolume();
        mprisControlBinding.volumeSeek.setProgress(volume);

        boolean isPlaying = playerStatus.isPlaying();
        if (isPlaying) {
            mprisControlBinding.playButton.setImageResource(R.drawable.ic_pause_black);
            mprisControlBinding.playButton.setEnabled(playerStatus.isPauseAllowed());
        } else {
            mprisControlBinding.playButton.setImageResource(R.drawable.ic_play_black);
            mprisControlBinding.playButton.setEnabled(playerStatus.isPlayAllowed());
        }

        mprisControlBinding.volumeLayout.setVisibility(playerStatus.isSetVolumeAllowed() ? View.VISIBLE : View.GONE);
        mprisControlBinding.rewButton.setVisibility(playerStatus.isSeekAllowed() ? View.VISIBLE : View.GONE);
        mprisControlBinding.ffButton.setVisibility(playerStatus.isSeekAllowed() ? View.VISIBLE : View.GONE);

        invalidateOptionsMenu();

        //Show and hide previous/next buttons simultaneously
        if (playerStatus.isGoPreviousAllowed() || playerStatus.isGoNextAllowed()) {
            mprisControlBinding.prevButton.setVisibility(View.VISIBLE);
            mprisControlBinding.prevButton.setEnabled(playerStatus.isGoPreviousAllowed());
            mprisControlBinding.nextButton.setVisibility(View.VISIBLE);
            mprisControlBinding.nextButton.setEnabled(playerStatus.isGoNextAllowed());
        } else {
            mprisControlBinding.prevButton.setVisibility(View.GONE);
            mprisControlBinding.nextButton.setVisibility(View.GONE);
        }
    }

    /**
     * Change current volume with provided step.
     *
     * @param step step size volume change
     */
    private void updateVolume(int step) {
        if (targetPlayer == null) {
            return;
        }
        final int currentVolume = targetPlayer.getVolume();

        if (currentVolume <= 100 && currentVolume >= 0) {
            int newVolume = currentVolume + step;
            if (newVolume > 100) {
                newVolume = 100;
            } else if (newVolume < 0) {
                newVolume = 0;
            }
            targetPlayer.setVolume(newVolume);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                updateVolume(5);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                updateVolume(-5);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private interface MprisPlayerCallback {
        void performAction(MprisPlugin.MprisPlayer player);
    }

    private void performActionOnClick(View v, MprisPlayerCallback l) {
        v.setOnClickListener(view -> BackgroundService.RunCommand(MprisActivity.this, service -> {
            if (targetPlayer == null) return;
            l.performAction(targetPlayer);
        }));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        activityMprisBinding = ActivityMprisBinding.inflate(getLayoutInflater());
        mprisControlBinding = activityMprisBinding.mprisControl;

        setContentView(activityMprisBinding.getRoot());

        String targetPlayerName = getIntent().getStringExtra("player");
        getIntent().removeExtra("player");

        if (TextUtils.isEmpty(targetPlayerName)) {
            if (savedInstanceState != null) {
                targetPlayerName = savedInstanceState.getString("targetPlayer");
            }
        }

        deviceId = getIntent().getStringExtra("deviceId");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String interval_time_str = prefs.getString(getString(R.string.mpris_time_key),
                getString(R.string.mpris_time_default));
        final int interval_time = Integer.parseInt(interval_time_str);

        BackgroundService.RunCommand(MprisActivity.this, service -> service.addConnectionListener(connectionReceiver));
        connectToPlugin(targetPlayerName);

        performActionOnClick(mprisControlBinding.playButton, MprisPlugin.MprisPlayer::playPause);

        performActionOnClick(mprisControlBinding.prevButton, MprisPlugin.MprisPlayer::previous);

        performActionOnClick(mprisControlBinding.rewButton, p -> targetPlayer.seek(interval_time * -1));

        performActionOnClick(mprisControlBinding.ffButton, p -> p.seek(interval_time));

        performActionOnClick(mprisControlBinding.nextButton, MprisPlugin.MprisPlayer::next);

        performActionOnClick(mprisControlBinding.stopButton, MprisPlugin.MprisPlayer::stop);

        mprisControlBinding.volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                BackgroundService.RunCommand(MprisActivity.this, service -> {
                    if (targetPlayer == null) return;
                    targetPlayer.setVolume(seekBar.getProgress());
                });
            }
        });

        positionSeekUpdateRunnable = () -> BackgroundService.RunCommand(MprisActivity.this, service -> {
            if (targetPlayer != null) {
                mprisControlBinding.positionSeek.setProgress((int) (targetPlayer.getPosition()));
            }
            positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable);
            positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 1000);
        });
        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200);

        mprisControlBinding.positionSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                mprisControlBinding.progressTextview.setText(milisToProgress(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable);
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                BackgroundService.RunCommand(MprisActivity.this, service -> {
                    if (targetPlayer != null) {
                        targetPlayer.setPosition(seekBar.getProgress());
                    }
                    positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200);
                });
            }
        });

        mprisControlBinding.nowPlayingTextview.setSelected(true);
    }

    final static int MENU_OPEN_URL = Menu.FIRST;

    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if(targetPlayer != null && !"".equals(targetPlayer.getUrl())) {
            menu.add(0, MENU_OPEN_URL, Menu.NONE, R.string.mpris_open_url);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (targetPlayer != null && item.getItemId() == MENU_OPEN_URL) {
            try {
                String url = VideoUrlsHelper.formatUriWithSeek(targetPlayer.getUrl(), targetPlayer.getPosition()).toString();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                targetPlayer.pause();
                return true;
            } catch (MalformedURLException | ActivityNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), getString(R.string.cant_open_url), Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (targetPlayer != null) {
            outState.putString("targetPlayer", targetPlayer.getPlayer());
        }
        super.onSaveInstanceState(outState);
    }
}
