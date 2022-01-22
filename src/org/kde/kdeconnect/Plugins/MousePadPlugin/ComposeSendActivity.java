/*
 * SPDX-FileCopyrightText: 2021 Forrest Hilton <forrestmhilton@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */


package org.kde.kdeconnect.Plugins.MousePadPlugin;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Objects;


public class ComposeSendActivity extends AppCompatActivity {

    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        setContentView(R.layout.activity_compose_send);

        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        Intent intent = getIntent();

        deviceId = intent.getStringExtra("org.kde.kdeconnect.Plugins.MousePadPlugin.deviceId");

        EditText editText = findViewById(R.id.compose);

        editText.requestFocus();
        editText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override

            // this is almost never used
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendComposed();
                    return true;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    clear();
                    return true;
                }
                return false;
            }
        });
    }

    public void sendChars(CharSequence chars) {
        final NetworkPacket np = new NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("key", chars.toString());
        sendKeyPressPacket(np);
    }

    private void sendKeyPressPacket(final NetworkPacket np) {
        try {
            Log.d("packed", np.serialize());
        } catch (Exception e) {
            Log.e("KDE/ComposeSend", "Exception", e);
        }

        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> plugin.sendKeyboardPacket(np));
    }

    public void sendComposed() {
        EditText editText = findViewById(R.id.compose);

        String editTextStr = editText.getText().toString();
        sendChars(editTextStr);
        clear();
    }

    public void clear() {
        EditText editText = findViewById(R.id.compose);
        editText.setText("");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_compose_send, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_clear_compose) {
            clear();
            return true;
        } else if (id == R.id.menu_send_compose) {
            sendComposed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
