package org.kde.kdeconnect.UserInterface;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

public class PairActivity extends ActionBarActivity {

    private String deviceId;
    private Device device = null;

    private Device.PairingCallback pairingCallback = new Device.PairingCallback() {

        @Override
        public void incomingRequest() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.pair_message)).setText(R.string.pair_requested);
                    findViewById(R.id.pair_progress).setVisibility(View.GONE);
                    findViewById(R.id.pair_button).setVisibility(View.GONE);
                    findViewById(R.id.pair_request).setVisibility(View.VISIBLE);
                }
            });
            NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(device.getNotificationId());
        }

        @Override
        public void pairingSuccessful() {
            finish();
        }

        @Override
        public void pairingFailed(final String error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.pair_message)).setText(error);
                    findViewById(R.id.pair_progress).setVisibility(View.GONE);
                    findViewById(R.id.pair_button).setVisibility(View.VISIBLE);
                    findViewById(R.id.pair_request).setVisibility(View.GONE);
                }
            });
        }

        @Override
        public void unpaired() {

        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pair);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayHomeAsUpEnabled(true);

        deviceId = getIntent().getStringExtra("deviceId");

        BackgroundService.RunCommand(PairActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device = service.getDevice(deviceId);
                setTitle(device.getName());
                NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(device.getNotificationId());
            }

        });

        final Button pairButton = (Button)findViewById(R.id.pair_button);
        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairButton.setVisibility(View.GONE);
                ((TextView) findViewById(R.id.pair_message)).setText("");
                findViewById(R.id.pair_progress).setVisibility(View.VISIBLE);
                BackgroundService.RunCommand(PairActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        device = service.getDevice(deviceId);
                        device.requestPairing();
                    }
                });
            }
        });

        findViewById(R.id.accept_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(PairActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        device.acceptPairing();
                        finish();
                    }
                });
            }
        });

        findViewById(R.id.reject_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(PairActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        device.rejectPairing();
                        finish();
                    }
                });
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.RunCommand(PairActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device.addPairingCallback(pairingCallback);
            }
        });
    }

    @Override
    protected void onStop() {
        if (device != null) device.removePairingCallback(pairingCallback);
        super.onStop();
    }

}