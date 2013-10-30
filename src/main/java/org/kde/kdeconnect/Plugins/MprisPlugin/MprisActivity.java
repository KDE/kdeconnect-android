package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
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

public class MprisActivity extends Activity {

    //TODO: Add a loading spinner at the begginning (to distinguish the loading state from a no-players state).
    //TODO 2: Add a message when no players are detected after loading completes

    protected void connectToPlugin() {

        final String deviceId = getIntent().getStringExtra("deviceId");

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                Device device = service.getDevice(deviceId);
                final MprisPlugin mpris = (MprisPlugin) device.getPlugin("plugin_mpris");
                if (mpris == null) {
                    Log.e("MprisActivity", "device has no mpris plugin!");
                    return;
                }

                mpris.setPlayerStatusUpdatedHandler(new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String s = mpris.getCurrentSong();
                                ((TextView) findViewById(R.id.now_playing_textview)).setText(s);

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

                mpris.setPlayerListUpdatedHandler(new Handler() {
                    boolean firstLoad = true;

                    @Override
                    public void handleMessage(Message msg) {
                        final ArrayList<String> playerList = mpris.getPlayerList();
                        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(MprisActivity.this,
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

                                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long id) {
                                        ((TextView) findViewById(R.id.now_playing_textview)).setText("");
                                        String player = playerList.get(pos);
                                        mpris.setPlayer(player);
                                        //Spotify doesn't support changing the volume yet...
                                        if (player.equals("Spotify")) {
                                            findViewById(R.id.volume_layout).setVisibility(View.INVISIBLE);
                                            findViewById(R.id.rew_button).setVisibility(View.GONE);
                                            findViewById(R.id.ff_button).setVisibility(View.GONE);
                                        } else {
                                            findViewById(R.id.volume_layout).setVisibility(View.VISIBLE);
                                            findViewById(R.id.rew_button).setVisibility(View.VISIBLE);
                                            findViewById(R.id.ff_button).setVisibility(View.VISIBLE);
                                        }
                                    }

                                    @Override
                                    public void onNothingSelected(AdapterView<?> arg0) {

                                    }
                                });
                            }
                        });
                        if (firstLoad) {
                            firstLoad = false;
                            if (playerList.size() > 0) {
                                mpris.setPlayer(playerList.get(0));
                            }
                        }
                    }
                });

            }
        });

    }

    BaseLinkProvider.ConnectionReceiver connectionReceiver = new BaseLinkProvider.ConnectionReceiver() {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mpris_control);

        final String deviceId = getIntent().getStringExtra("deviceId");

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
                        MprisPlugin mpris = (MprisPlugin)device.getPlugin("plugin_mpris");
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
                        MprisPlugin mpris = (MprisPlugin)device.getPlugin("plugin_mpris");
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
                        MprisPlugin mpris = (MprisPlugin)device.getPlugin("plugin_mpris");
                        if (mpris == null) return;
                        mpris.Seek(-10000000); // -10 seconds. TODO: plugin settings UI?
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
                        MprisPlugin mpris = (MprisPlugin)device.getPlugin("plugin_mpris");
                        if (mpris == null) return;
                        mpris.Seek(10000000); // 10 seconds. TODO: plugin settings UI?
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
                        MprisPlugin mpris = (MprisPlugin)device.getPlugin("plugin_mpris");
                        if (mpris == null) return;
                        mpris.sendAction("Next");
                    }
                });
            }
        });

        ((SeekBar)findViewById(R.id.volume_seek)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MprisPlugin mpris = (MprisPlugin) device.getPlugin("plugin_mpris");
                        if (mpris == null) return;
                        mpris.setVolume(seekBar.getProgress());
                    }
               });
            }

        });


    }

}
