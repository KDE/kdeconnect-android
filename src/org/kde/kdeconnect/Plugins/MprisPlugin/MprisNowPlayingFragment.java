package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.VideoUrlsHelper;
import org.kde.kdeconnect.Helpers.VolumeHelperKt;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.MprisControlBinding;
import org.kde.kdeconnect_tp.databinding.MprisNowPlayingBinding;

import java.net.MalformedURLException;
import java.util.List;

public class MprisNowPlayingFragment extends Fragment implements VolumeKeyListener {

    final static int MENU_OPEN_URL = Menu.FIRST;
    private final Handler positionSeekUpdateHandler = new Handler();
    MprisControlBinding mprisControlBinding;
    private MprisNowPlayingBinding activityMprisBinding;
    private String deviceId;
    private Runnable positionSeekUpdateRunnable = null;
    private MprisPlugin.MprisPlayer targetPlayer = null;
    private final BaseLinkProvider.ConnectionReceiver connectionReceiver = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(NetworkPacket identityPacket, BaseLink link) {
            connectToPlugin(null);
        }

        @Override
        public void onConnectionLost(BaseLink link) {

        }
    };

    public static MprisNowPlayingFragment newInstance(String deviceId) {
        MprisNowPlayingFragment mprisNowPlayingFragment = new MprisNowPlayingFragment();

        Bundle arguments = new Bundle();
        arguments.putString(MprisPlugin.DEVICE_ID_KEY, deviceId);

        mprisNowPlayingFragment.setArguments(arguments);

        return mprisNowPlayingFragment;
    }

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        if (activityMprisBinding == null) {
            activityMprisBinding = MprisNowPlayingBinding.inflate(inflater);
            mprisControlBinding = activityMprisBinding.mprisControl;

            String targetPlayerName = "";
            Intent activityIntent = requireActivity().getIntent();
            activityIntent.getStringExtra("player");
            activityIntent.removeExtra("player");

            if (TextUtils.isEmpty(targetPlayerName)) {
                if (savedInstanceState != null) {
                    targetPlayerName = savedInstanceState.getString("targetPlayer");
                }
            }

            deviceId = requireArguments().getString(MprisPlugin.DEVICE_ID_KEY);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String interval_time_str = prefs.getString(getString(R.string.mpris_time_key),
                    getString(R.string.mpris_time_default));
            final int interval_time = Integer.parseInt(interval_time_str);

            BackgroundService.RunCommand(requireContext(), service -> service.addConnectionListener(connectionReceiver));
            connectToPlugin(targetPlayerName);

            performActionOnClick(mprisControlBinding.loopButton, p -> {
                switch (p.getLoopStatus()) {
                    case "None":
                        p.setLoopStatus("Track");
                        break;
                    case "Track":
                        p.setLoopStatus("Playlist");
                        break;
                    case "Playlist":
                        p.setLoopStatus("None");
                        break;
                }
            });

            performActionOnClick(mprisControlBinding.playButton, MprisPlugin.MprisPlayer::playPause);

            performActionOnClick(mprisControlBinding.shuffleButton, p -> p.setShuffle(!p.getShuffle()));

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
                    BackgroundService.RunCommand(requireContext(), service -> {
                        if (targetPlayer == null) return;
                        targetPlayer.setVolume(seekBar.getProgress());
                    });
                }
            });

            positionSeekUpdateRunnable = () -> {
                Context context = getContext();
                if (context == null) return; // Fragment was already detached
                BackgroundService.RunCommand(context, service -> {
                    if (targetPlayer != null) {
                        mprisControlBinding.positionSeek.setProgress((int) (targetPlayer.getPosition()));
                    }
                    positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable);
                    positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 1000);
                });
            };
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
                    BackgroundService.RunCommand(requireContext(), service -> {
                        if (targetPlayer != null) {
                            targetPlayer.setPosition(seekBar.getProgress());
                        }
                        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200);
                    });
                }
            });

            mprisControlBinding.nowPlayingTextview.setSelected(true);

        }

        return activityMprisBinding.getRoot();
    }

    private void connectToPlugin(final String targetPlayerName) {
        BackgroundService.RunWithPlugin(requireContext(), deviceId, MprisPlugin.class, mpris -> {
            targetPlayer = mpris.getPlayerStatus(targetPlayerName);

            mpris.setPlayerStatusUpdatedHandler("activity", () -> requireActivity().runOnUiThread(() -> updatePlayerStatus(mpris)));
            mpris.setPlayerListUpdatedHandler("activity", () -> {
                final List<String> playerList = mpris.getPlayerList();
                final ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item,
                        playerList.toArray(ArrayUtils.EMPTY_STRING_ARRAY)
                );

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                requireActivity().runOnUiThread(() -> {
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

                            if (targetPlayer != null && targetPlayer.isPlaying()) {
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
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BackgroundService.RunCommand(requireContext(), service -> service.removeConnectionListener(connectionReceiver));
    }

    private void performActionOnClick(View v, MprisPlayerCallback l) {
        v.setOnClickListener(view -> BackgroundService.RunCommand(requireContext(), service -> {
            if (targetPlayer == null) return;
            l.performAction(targetPlayer);
        }));
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
            final Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_album_art_placeholder);
            assert drawable != null;
            Drawable placeholder_art = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(placeholder_art, ContextCompat.getColor(requireContext(), R.color.primary));
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
        if(!playerStatus.isSetVolumeAllowed()) {
            mprisControlBinding.volumeSeek.setEnabled(false);
        }
        boolean isPlaying = playerStatus.isPlaying();
        if (isPlaying) {
            mprisControlBinding.playButton.setIconResource(R.drawable.ic_pause_black);
            mprisControlBinding.playButton.setEnabled(playerStatus.isPauseAllowed());
        } else {
            mprisControlBinding.playButton.setIconResource(R.drawable.ic_play_black);
            mprisControlBinding.playButton.setEnabled(playerStatus.isPlayAllowed());
        }

        String loopStatus = playerStatus.getLoopStatus();
        switch (loopStatus) {
            case "None":
                mprisControlBinding.loopButton.setIconResource(R.drawable.ic_loop_none_black);
                break;
            case "Track":
                mprisControlBinding.loopButton.setIconResource(R.drawable.ic_loop_track_black);
                break;
            case "Playlist":
                mprisControlBinding.loopButton.setIconResource(R.drawable.ic_loop_playlist_black);
                break;
        }

        boolean shuffle = playerStatus.getShuffle();
        if (shuffle) {
            mprisControlBinding.shuffleButton.setIconResource(R.drawable.ic_shuffle_on_black);
        } else {
            mprisControlBinding.shuffleButton.setIconResource(R.drawable.ic_shuffle_off_black);
        }

        mprisControlBinding.loopButton.setVisibility(playerStatus.isLoopStatusAllowed() ? View.VISIBLE : View.GONE);
        mprisControlBinding.shuffleButton.setVisibility(playerStatus.isShuffleAllowed() ? View.VISIBLE : View.GONE);
        mprisControlBinding.volumeLayout.setVisibility(playerStatus.isSetVolumeAllowed() ? View.VISIBLE : View.GONE);
        mprisControlBinding.rewButton.setVisibility(playerStatus.isSeekAllowed() ? View.VISIBLE : View.GONE);
        mprisControlBinding.ffButton.setVisibility(playerStatus.isSeekAllowed() ? View.VISIBLE : View.GONE);

        requireActivity().invalidateOptionsMenu();

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
        if (targetPlayer == null) return;

        int newVolume = VolumeHelperKt.calculateNewVolume(targetPlayer.getVolume(), VolumeHelperKt.DEFAULT_MAX_VOLUME, step);

        if (targetPlayer.getVolume() != newVolume) {
            targetPlayer.setVolume(newVolume);
        }
    }

    @Override
    public void onVolumeUp() {
        updateVolume(VolumeHelperKt.DEFAULT_VOLUME_STEP);
    }

    @Override
    public void onVolumeDown() {
        updateVolume(-VolumeHelperKt.DEFAULT_VOLUME_STEP);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.clear();
        if (targetPlayer != null && !"".equals(targetPlayer.getUrl())) {
            menu.add(0, MENU_OPEN_URL, Menu.NONE, R.string.mpris_open_url);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (targetPlayer != null && item.getItemId() == MENU_OPEN_URL) {
            try {
                String url = VideoUrlsHelper.formatUriWithSeek(targetPlayer.getUrl(), targetPlayer.getPosition()).toString();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                targetPlayer.pause();
                return true;
            } catch (MalformedURLException | ActivityNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), getString(R.string.cant_open_url), Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (targetPlayer != null) {
            outState.putString("targetPlayer", targetPlayer.getPlayer());
        }
    }

    private interface MprisPlayerCallback {
        void performAction(MprisPlugin.MprisPlayer player);
    }
}
