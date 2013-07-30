package org.kde.connect;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.kde.connect.PackageInterfaces.MprisControlPackageInterface;
import org.kde.kdeconnect.R;

import java.util.ArrayList;

public class MprisActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mpris_control);


        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                final MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);

                mpris.setNowPlayingUpdatedHandler(new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String s = mpris.getNowPlaying();
                                ((TextView) findViewById(R.id.now_playing_textview)).setText(s);
                            }
                        });
                    }
                });

                mpris.setPlayerListUpdatedHandler(new Handler() {
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
                                Spinner spinner = (Spinner)findViewById(R.id.player_spinner);
                                spinner.setAdapter(adapter);
                                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long id) {
                                        ((TextView) findViewById(R.id.now_playing_textview)).setText("");
                                        mpris.setPlayer(playerList.get(pos));

                                    }
                                    @Override
                                    public void onNothingSelected(AdapterView<?> arg0) {

                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        findViewById(R.id.play_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);
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
                        MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);
                        mpris.sendAction("Previous");
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
                        MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);
                        mpris.sendAction("Next");
                    }
                });
            }
        });




    }

}
