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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

public class MprisActivity extends ActionBarActivity {

    private String deviceId;
    private final Handler positionSeekUpdateHandler = new Handler();
    private Runnable positionSeekUpdateRunnable = null;
    private String targetPlayer = null;

    private static String milisToProgress(long milis) {
        int length = (int)(milis / 1000); //From milis to seconds
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
        if(seconds < 10) text.append('0'); // needed to show length properly (eg 4:05 instead of 4:5)
        text.append(seconds);
        return text.toString();
    }
    protected void connectToPlugin() {

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                final MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                if (mpris == null) {
                    Log.e("MprisActivity", "device has no mpris plugin!");
                    return;
                }

                mpris.setPlayerStatusUpdatedHandler("activity", new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String song = mpris.getCurrentSong();
                                ((TextView) findViewById(R.id.now_playing_textview)).setText(song);

                                if (mpris.getLength() > -1 && mpris.getPosition() > -1 && !"Spotify".equals(mpris.getPlayer())) {
                                    ((TextView) findViewById(R.id.time_textview)).setText(milisToProgress(mpris.getLength()));

                                    SeekBar positionSeek = (SeekBar)findViewById(R.id.positionSeek);
                                    positionSeek.setMax((int)(mpris.getLength()));
                                    positionSeek.setProgress((int)(mpris.getPosition()));

                                    findViewById(R.id.progress_slider).setVisibility(View.VISIBLE);
                                } else {
                                    findViewById(R.id.progress_slider).setVisibility(View.GONE);
                                }

                                int volume = mpris.getVolume();
                                ((SeekBar) findViewById(R.id.volume_seek)).setProgress(volume);

                                boolean isPlaying = mpris.isPlaying();
                                if (isPlaying) {
                                    ((ImageButton) findViewById(R.id.play_button)).setImageResource(android.R.drawable.ic_media_pause);
                                } else {
                                    ((ImageButton) findViewById(R.id.play_button)).setImageResource(android.R.drawable.ic_media_play);
                                }
                            }
                        });
                    }
                });

                mpris.setPlayerListUpdatedHandler("activity", new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        final ArrayList<String> playerList = mpris.getPlayerList();
                        final ArrayAdapter<String> adapter = new ArrayAdapter<>(MprisActivity.this,
                                android.R.layout.simple_spinner_item,
                                playerList.toArray(new String[playerList.size()])
                        );

                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Spinner spinner = (Spinner) findViewById(R.id.player_spinner);
                                //String prevPlayer = (String)spinner.getSelectedItem();
                                spinner.setAdapter(adapter);

                                if(playerList.isEmpty()){
                                    findViewById(R.id.no_players).setVisibility(View.VISIBLE);
                                    spinner.setVisibility(View.GONE);
                                    ((TextView) findViewById(R.id.now_playing_textview)).setText("");
                                }else{
                                    findViewById(R.id.no_players).setVisibility(View.GONE);
                                    spinner.setVisibility(View.VISIBLE);
                                }

                                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long id) {

                                        if (pos >= playerList.size()) return;

                                        ((TextView) findViewById(R.id.now_playing_textview)).setText("");
                                        String player = playerList.get(pos);
                                        mpris.setPlayer(player);
                                        //Spotify doesn't support changing the volume yet...
                                        //Also doesn't support seeking and telling actual position...
                                        if (player.equals("Spotify")) {
                                            findViewById(R.id.volume_layout).setVisibility(View.INVISIBLE);
                                            findViewById(R.id.rew_button).setVisibility(View.GONE);
                                            findViewById(R.id.ff_button).setVisibility(View.GONE);
                                            findViewById(R.id.positionSeek).setVisibility(View.INVISIBLE);
                                            findViewById(R.id.progress_slider).setVisibility(View.GONE);
                                        } else {
                                            findViewById(R.id.volume_layout).setVisibility(View.VISIBLE);
                                            findViewById(R.id.rew_button).setVisibility(View.VISIBLE);
                                            findViewById(R.id.ff_button).setVisibility(View.VISIBLE);
                                            findViewById(R.id.positionSeek).setVisibility(View.VISIBLE);
                                        }
                                    }

                                    @Override
                                    public void onNothingSelected(AdapterView<?> arg0) {
                                        mpris.setPlayer(null);
                                    }
                                });

                                if (targetPlayer != null) {
                                    int targetIndex = adapter.getPosition(targetPlayer);
                                    if (targetIndex >= 0) {
                                        spinner.setSelection(targetIndex);
                                    }
                                    targetPlayer = null;
                                } else {
                                    // restore last selected player
                                    int position = adapter.getPosition(mpris.getPlayer());
                                    if (position >= 0) {
                                        spinner.setSelection(position);
                                    }
                                }
                            }
                        });
                    }
                });

            }
        });

    }

    private final BaseLinkProvider.ConnectionReceiver connectionReceiver = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(NetworkPackage identityPackage, BaseLink link) {
            connectToPlugin();
        }

        @Override
        public void onConnectionLost(BaseLink link) {

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.removeConnectionListener(connectionReceiver);
            }
        });
    }

    /**
     * Change current volume with provided step.
     *
     * @param mpris multimedia controller
     * @param step  step size volume change
     */
    private void updateVolume(MprisPlugin mpris, int step) {
        final int currentVolume = mpris.getVolume();
        if(currentVolume < 100 || currentVolume > 0) {
            int newVolume = currentVolume + step;
            if(newVolume > 100) {
                newVolume = 100;
            } else if (newVolume <0 ) {
                newVolume = 0;
            }
            mpris.setVolume(newVolume);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        updateVolume(mpris, 5);
                    }
                });
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        updateVolume(mpris, -5);
                    }
                });
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mpris_control);

        targetPlayer = getIntent().getStringExtra("player");
        getIntent().removeExtra("player");
        deviceId = getIntent().getStringExtra("deviceId");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String interval_time_str = prefs.getString(getString(R.string.mpris_time_key),
                getString(R.string.mpris_time_default));
        final int interval_time = Integer.parseInt(interval_time_str);

        BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.addConnectionListener(connectionReceiver);
            }
        });
        connectToPlugin();

        findViewById(R.id.play_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        mpris.sendAction("PlayPause");
                    }
                });
            }
        });

        findViewById(R.id.prev_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        mpris.sendAction("Previous");
                    }
                });
            }
        });

        findViewById(R.id.rew_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        mpris.Seek(interval_time * -1);
                    }
                });
            }
        });

        findViewById(R.id.ff_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        mpris.Seek(interval_time);
                    }
                });
            }
        });

        findViewById(R.id.next_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        mpris.sendAction("Next");
                    }
                });
            }
        });

        ((SeekBar)findViewById(R.id.volume_seek)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris == null) return;
                        mpris.setVolume(seekBar.getProgress());
                    }
                });
            }

        });

        positionSeekUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                final SeekBar positionSeek = (SeekBar)findViewById(R.id.positionSeek);
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris != null) {
                            positionSeek.setProgress((int) (mpris.getPosition()));
                        }
                        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 1000);
                    }
                });
            }

        };
        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200);

        ((SeekBar)findViewById(R.id.positionSeek)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                ((TextView)findViewById(R.id.progress_textview)).setText(milisToProgress(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable);
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
                        if (mpris != null) {
                            mpris.setPosition(seekBar.getProgress());
                        }
                        positionSeekUpdateHandler.postDelayed(positionSeekUpdateRunnable, 200);
                    }
                });
            }

        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        positionSeekUpdateHandler.removeCallbacks(positionSeekUpdateRunnable);
    }

}
