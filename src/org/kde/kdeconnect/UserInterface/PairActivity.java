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

package org.kde.kdeconnect.UserInterface;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect_tp.R;

public class PairActivity extends ActionBarActivity {

    private String deviceId;
    private Device device = null;

    private final Device.PairingCallback pairingCallback = new Device.PairingCallback() {

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
                if (device == null) return;
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
                        if (device == null) return;
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
                        if (device != null) {
                            device.acceptPairing();
                        }
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
                        if (device != null) {
                            device.rejectPairing();
                        }
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
                device = service.getDevice(deviceId);
                if (device == null) return;
                device.addPairingCallback(pairingCallback);
            }
        });
    }

    @Override
    protected void onStop() {
        if (device != null) device.removePairingCallback(pairingCallback);
        super.onStop();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        menu.add(R.string.encryption_info_title).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Context context = PairActivity.this;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(context.getResources().getString(R.string.encryption_info_title));
                builder.setPositiveButton(context.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

                if (device.certificate == null) {
                    builder.setMessage(R.string.encryption_info_msg_no_ssl);
                } else {
                    builder.setMessage(context.getResources().getString(R.string.my_device_key) + " " + SslHelper.getCertificateHash(device.certificate) + "\n"
                            + context.getResources().getString(R.string.remote_device_key) + " " + SslHelper.getCertificateHash(SslHelper.certificate));
                }
                builder.create().show();
                return true;
            }
        });
        return true;
    }
}
