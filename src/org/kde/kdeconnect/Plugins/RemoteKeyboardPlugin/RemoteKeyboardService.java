/*
 * Copyright 2017 Holger Kaelberer <holger.k@elberer.de>
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

package org.kde.kdeconnect.Plugins.RemoteKeyboardPlugin;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.List;

public class RemoteKeyboardService
        extends InputMethodService
        implements OnKeyboardActionListener {

    /**
     * Reference to our instance
     * null if this InputMethod is not currently selected.
     */
    public static RemoteKeyboardService instance = null;

    /**
     * Whether input is currently accepted
     * Implies visible == true
     */
    private boolean active = false;

    /**
     * Whether this InputMethod is currently visible.
     */
    public boolean visible = false;

    private KeyboardView inputView = null;

    Handler handler;

    void updateInputView() {
        if (inputView == null)
            return;
        Keyboard currentKeyboard = inputView.getKeyboard();
        List<Keyboard.Key> keys = currentKeyboard.getKeys();
        boolean connected = RemoteKeyboardPlugin.isConnected();
//        Log.d("RemoteKeyboardService", "Updating keyboard connection icon, connected=" + connected);
        int disconnectedIcon = R.drawable.ic_phonelink_off_36dp;
        int connectedIcon = R.drawable.ic_phonelink_36dp;
        int statusKeyIdx = 3;
        keys.get(statusKeyIdx).icon = ContextCompat.getDrawable(this, connected ? connectedIcon : disconnectedIcon);
        inputView.invalidateKey(statusKeyIdx);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = false;
        visible = false;
        instance = this;
        handler = new Handler();
        Log.d("RemoteKeyboardService", "Remote keyboard initialized");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d("RemoteKeyboardService", "Destroyed");
    }

    @Override
    public View onCreateInputView() {
//        Log.d("RemoteKeyboardService", "onCreateInputView connected=" + RemoteKeyboardPlugin.isConnected());
        inputView = new KeyboardView(this, null);
        inputView.setKeyboard(new Keyboard(this, R.xml.remotekeyboardplugin_keyboard));
        inputView.setPreviewEnabled(false);
        inputView.setOnKeyboardActionListener(this);
        updateInputView();
        return inputView;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
//        Log.d("RemoteKeyboardService", "onStartInputView");
        super.onStartInputView(attribute, restarting);
        visible = true;
        ArrayList<RemoteKeyboardPlugin> instances = RemoteKeyboardPlugin.acquireInstances();
        try {
            for (RemoteKeyboardPlugin i : instances)
                i.notifyKeyboardState(true);
        } finally {
            RemoteKeyboardPlugin.releaseInstances();
        }

        getWindow().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
//        Log.d("RemoteKeyboardService", "onFinishInputView");
        super.onFinishInputView(finishingInput);
        visible = false;
        ArrayList<RemoteKeyboardPlugin> instances = RemoteKeyboardPlugin.acquireInstances();
        try {
            for (RemoteKeyboardPlugin i : instances)
                i.notifyKeyboardState(false);
        } finally {
            RemoteKeyboardPlugin.releaseInstances();
        }

        getWindow().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
//        Log.d("RemoteKeyboardService", "onStartInput");
        super.onStartInput(attribute, restarting);
        active = true;
    }

    @Override
    public void onFinishInput() {
//        Log.d("RemoteKeyboardService", "onFinishInput");
        super.onFinishInput();
        active = false;
    }

    @Override
    public void onPress(int primaryCode) {
        switch (primaryCode) {
            case 0: {  // "hide keyboard"
                requestHideSelf(0);
                break;
            }
            case 1: { // "settings"
                ArrayList<RemoteKeyboardPlugin> instances = RemoteKeyboardPlugin.acquireInstances();
                try {
                    if (instances.size() == 1) {  // single instance of RemoteKeyboardPlugin -> access its settings
                        RemoteKeyboardPlugin plugin = instances.get(0);
                        if (plugin != null) {
                            Intent intent = new Intent(this, PluginSettingsActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra(PluginSettingsActivity.EXTRA_DEVICE_ID, plugin.getDeviceId());
                            intent.putExtra(PluginSettingsActivity.EXTRA_PLUGIN_KEY, plugin.getPluginKey());
                            startActivity(intent);
                        }
                    } else { // != 1 instance of plugin -> show main activity view
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.putExtra("forceOverview", true);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        if (instances.size() < 1)
                            Toast.makeText(this, R.string.remotekeyboard_not_connected, Toast.LENGTH_SHORT).show();
                        else // instances.size() > 1
                            Toast.makeText(this, R.string.remotekeyboard_multiple_connections, Toast.LENGTH_SHORT).show();
                    }
                } finally {
                    RemoteKeyboardPlugin.releaseInstances();
                }
                break;
            }
            case 2: { // "keyboard"
                InputMethodManager imm = ContextCompat.getSystemService(this, InputMethodManager.class);
                imm.showInputMethodPicker();
                break;
            }
            case 3: { // "connected"?
                if (RemoteKeyboardPlugin.isConnected())
                    Toast.makeText(this, R.string.remotekeyboard_connected, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, R.string.remotekeyboard_not_connected, Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
    }

    @Override
    public void onText(CharSequence text) {
    }

    @Override
    public void swipeRight() {
    }

    @Override
    public void swipeLeft() {
    }

    @Override
    public void swipeDown() {
    }

    @Override
    public void swipeUp() {
    }

    @Override
    public void onRelease(int primaryCode) {
    }
}
