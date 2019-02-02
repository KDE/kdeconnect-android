/*
 * Copyright 2019 Ondřej Hruška <ondra@ondrovo.com>
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

package org.kde.kdeconnect.Plugins.TextInputPlugin;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import androidx.appcompat.app.AppCompatActivity;

public class TextInputActivity extends AppCompatActivity {
    private String deviceId;

    public void sendChars(CharSequence chars) {
        final NetworkPacket np = new NetworkPacket(TextInputPlugin.PACKET_TYPE_TEXTINPUT_REQUEST);
        np.set("key", chars.toString());
        sendKeyPressPacket(np);
    }

    private void sendKeyPressPacket(final NetworkPacket np) {
        BackgroundService.RunCommand(getBaseContext(), service -> {
            Device device = service.getDevice(deviceId);
            TextInputPlugin textInputPlugin = device.getPlugin(TextInputPlugin.class);
            if (textInputPlugin == null) return;
            textInputPlugin.sendKeyboardPacket(np);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);
        setContentView(R.layout.activity_textinput);
        deviceId = getIntent().getStringExtra("deviceId");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_textinput, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_submit_text:
                submitText();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void submitText() {
        TextView v = findViewById(R.id.textInputField);
        CharSequence text = v.getText();
        v.setText("");
        sendChars(text);
    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackgroundService.removeGuiInUseCounter(this);
    }
}
