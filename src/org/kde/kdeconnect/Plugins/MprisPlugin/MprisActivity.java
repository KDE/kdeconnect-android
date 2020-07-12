/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
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

import java.net.MalformedURLException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MprisActivity extends AppCompatActivity {

    private String deviceId;
    private final Handler positionSeekUpdateHandler = new Handler();
    private Runnable positionSeekUpdateRunnable = null;
    private MprisPlugin.MprisPlayer targetPlayer = null;

    @BindView(R.id.play_button)
    ImageButton playButton;

    @BindView(R.id.prev_button)
    ImageButton prevButton;

    @BindView(R.id.next_button)
    ImageButton nextButton;

    @BindView(R.id.rew_button)
    ImageButton rewButton;

    @BindView(R.id.ff_button)
    ImageButton ffButton;

    @BindView(R.id.time_textview)
    TextView timeText;

    @BindView(R.id.album_art)
    ImageView albumArtView;

    @BindView(R.id.player_spinner)
    Spinner playerSpinner;

    @BindView(R.id.no_players)
    TextView noPlayers;

    @BindView(R.id.now_playing_textview)
    TextView nowPlayingText;

    @BindView(R.id.positionSeek)
    SeekBar positionBar;

    @BindView(R.id.progress_slider)
    LinearLayout progressSlider;

    @BindView(R.id.volume_seek)
    SeekBar volumeSeek;

    @BindView(R.id.volume_layout)
    LinearLayout volumeLayout;

    @BindView(R.id.stop_button)
    ImageButton stopButton;

    @BindView(R.id.progress_textview)
    TextView progressText;


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

                        playerSpinner.setAdapter(adapter);

                        if (playerList.isEmpty()) {
                            noPlayers.setVisibility(View.VISIBLE);
                            playerSpinner.setVisibility(View.GONE);
                            nowPlayingText.setText("");
                        } else {
                            noPlayers.setVisibility(View.GONE);
                            playerSpinner.setVisibility(View.VISIBLE);
                        }

                        playerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
                                playerSpinner.setSelection(targetIndex);
                            } else {
                                targetPlayer = null;
                            }
                        }
                        //If no player selected, select the first one (if any)
                        if (targetPlayer == null && !playerList.isEmpty()) {
                            targetPlayer = mpris.getPlayerStatus(playerList.get(0));
                            playerSpinner.setSelection(0);
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

        if (!nowPlayingText.getText().toString().equals(song)) {
            nowPlayingText.setText(song);
        }

        Bitmap albumArt = playerStatus.getAlbumArt();
        if (albumArt == null) {
            final Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_album_art_placeholder);
            assert drawable != null;
            Drawable placeholder_art = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(placeholder_art, ContextCompat.getColor(this, R.color.primary));
            albumArtView.setImageDrawable(placeholder_art);
        } else {
            albumArtView.setImageBitmap(albumArt);
        }

        if (playerStatus.isSeekAllowed()) {
            timeText.setText(milisToProgress(playerStatus.getLength()));
            positionBar.setMax((int) (playerStatus.getLength()));
            positionBar.setProgress((int) (playerStatus.getPosition()));
            progressSlider.setVisibility(View.VISIBLE);
        } else {
            progressSlider.setVisibility(View.GONE);
        }

        int volume = playerStatus.getVolume();
        volumeSeek.setProgress(volume);

        boolean isPlaying = playerStatus.isPlaying();
        if (isPlaying) {
            playButton.setImageResource(R.drawable.ic_pause_black);
            playButton.setEnabled(playerStatus.isPauseAllowed());
        } else {
            playButton.setImageResource(R.drawable.ic_play_black);
            playButton.setEnabled(playerStatus.isPlayAllowed());
        }

        volumeLayout.setVisibility(playerStatus.isSetVolumeAllowed() ? View.VISIBLE : View.GONE);
        rewButton.setVisibility(playerStatus.isSeekAllowed() ? View.VISIBLE : View.GONE);
        ffButton.setVisibility(playerStatus.isSeekAllowed() ? View.VISIBLE : View.GONE);

        invalidateOptionsMenu();

        //Show and hide previous/next buttons simultaneously
        if (playerStatus.isGoPreviousAllowed() || playerStatus.isGoNextAllowed()) {
            prevButton.setVisibility(View.VISIBLE);
            prevButton.setEnabled(playerStatus.isGoPreviousAllowed());
            nextButton.setVisibility(View.VISIBLE);
            nextButton.setEnabled(playerStatus.isGoNextAllowed());
        } else {
            prevButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.GONE);
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
        setContentView(R.layout.activity_mpris);
        ButterKnife.bind(this);

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

        performActionOnClick(playButton, MprisPlugin.MprisPlayer::playPause);

        performActionOnClick(prevButton, MprisPlugin.MprisPlayer::previous);

        performActionOnClick(rewButton, p -> targetPlayer.seek(interval_time * -1));

        performActionOnClick(ffButton, p -> p.seek(interval_time));

        performActionOnClick(nextButton, MprisPlugin.MprisPlayer::next);

        performActionOnClick(stopButton, MprisPlugin.MprisPlayer::stop);

        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
                positionBar.setProgress((int) (targetPlayer.getPosition()));
            }
            positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable);
            positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 1000);
        });
        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200);

        positionBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                progressText.setText(milisToProgress(progress));
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

        nowPlayingText.setSelected(true);
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
