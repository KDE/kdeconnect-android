/*
 * Copyright 2014 Saikrishna Arcot <saiarcot895@gmail.com>
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

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.NetworkPacket;

public class KeyListenerView extends View {

    private String deviceId;

    public static final SparseIntArray SpecialKeysMap = new SparseIntArray();

    static {
        int i = 0;
        SpecialKeysMap.put(KeyEvent.KEYCODE_DEL, ++i);              // 1
        SpecialKeysMap.put(KeyEvent.KEYCODE_TAB, ++i);              // 2
        SpecialKeysMap.put(KeyEvent.KEYCODE_ENTER, 12);
        ++i;        // 3 is not used, return is 12 instead
        SpecialKeysMap.put(KeyEvent.KEYCODE_DPAD_LEFT, ++i);        // 4
        SpecialKeysMap.put(KeyEvent.KEYCODE_DPAD_UP, ++i);          // 5
        SpecialKeysMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, ++i);       // 6
        SpecialKeysMap.put(KeyEvent.KEYCODE_DPAD_DOWN, ++i);        // 7
        SpecialKeysMap.put(KeyEvent.KEYCODE_PAGE_UP, ++i);          // 8
        SpecialKeysMap.put(KeyEvent.KEYCODE_PAGE_DOWN, ++i);        // 9
        SpecialKeysMap.put(KeyEvent.KEYCODE_MOVE_HOME, ++i);    // 10
        SpecialKeysMap.put(KeyEvent.KEYCODE_MOVE_END, ++i);     // 11
        SpecialKeysMap.put(KeyEvent.KEYCODE_NUMPAD_ENTER, ++i); // 12
        SpecialKeysMap.put(KeyEvent.KEYCODE_FORWARD_DEL, ++i);  // 13
        SpecialKeysMap.put(KeyEvent.KEYCODE_ESCAPE, ++i);       // 14
        SpecialKeysMap.put(KeyEvent.KEYCODE_SYSRQ, ++i);        // 15
        SpecialKeysMap.put(KeyEvent.KEYCODE_SCROLL_LOCK, ++i);  // 16
        ++i;           // 17
        ++i;           // 18
        ++i;           // 19
        ++i;           // 20
        SpecialKeysMap.put(KeyEvent.KEYCODE_F1, ++i);           // 21
        SpecialKeysMap.put(KeyEvent.KEYCODE_F2, ++i);           // 22
        SpecialKeysMap.put(KeyEvent.KEYCODE_F3, ++i);           // 23
        SpecialKeysMap.put(KeyEvent.KEYCODE_F4, ++i);           // 24
        SpecialKeysMap.put(KeyEvent.KEYCODE_F5, ++i);           // 25
        SpecialKeysMap.put(KeyEvent.KEYCODE_F6, ++i);           // 26
        SpecialKeysMap.put(KeyEvent.KEYCODE_F7, ++i);           // 27
        SpecialKeysMap.put(KeyEvent.KEYCODE_F8, ++i);           // 28
        SpecialKeysMap.put(KeyEvent.KEYCODE_F9, ++i);           // 29
        SpecialKeysMap.put(KeyEvent.KEYCODE_F10, ++i);          // 30
        SpecialKeysMap.put(KeyEvent.KEYCODE_F11, ++i);          // 31
        SpecialKeysMap.put(KeyEvent.KEYCODE_F12, ++i);          // 21
    }

    public void setDeviceId(String id) {
        deviceId = id;
    }

    public KeyListenerView(Context context, AttributeSet set) {
        super(context, set);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new KeyInputConnection(this, true);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    public void sendChars(CharSequence chars) {
        final NetworkPacket np = new NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("key", chars.toString());
        sendKeyPressPacket(np);
    }

    private void sendKeyPressPacket(final NetworkPacket np) {
        BackgroundService.RunWithPlugin(getContext(), deviceId, MousePadPlugin.class, plugin -> plugin.sendKeyboardPacket(np));
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            //We don't want to swallow the back button press
            return false;
        }

        // NOTE: Most keyboards, and specifically the Android default keyboard when
        // entering non-ascii characters, will not trigger KeyEvent events as documented
        // here: http://developer.android.com/reference/android/view/KeyEvent.html

        //Log.e("KeyDown", "------------");
        //Log.e("KeyDown", "keyChar:" + (int) event.getDisplayLabel());
        //Log.e("KeyDown", "utfChar:" + (char)event.getUnicodeChar());
        //Log.e("KeyDown", "intUtfChar:" + event.getUnicodeChar());

        final NetworkPacket np = new NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST);

        boolean modifier = false;
        if (event.isAltPressed()) {
            np.set("alt", true);
            modifier = true;
        }

        if (event.isCtrlPressed()) {
            np.set("ctrl", true);
            modifier = true;
        }

        if (event.isShiftPressed()) {
            np.set("shift", true);
        }

        if (event.isMetaPressed()) {
            np.set("super", true);
            modifier = true;
        }

        int specialKey = SpecialKeysMap.get(keyCode, -1);

        if (specialKey != -1) {
            np.set("specialKey", specialKey);
        } else if (event.getDisplayLabel() != 0 && modifier) {
            //Alt will change the utf symbol to non-ascii characters, we want the plain original letter
            //Since getDisplayLabel will always have a value, we have to check for special keys before
            char keyCharacter = event.getDisplayLabel();
            np.set("key", new String(new char[]{keyCharacter}).toLowerCase());
        } else {
            //A normal key, but still not handled by the KeyInputConnection (happens with numbers)
            np.set("key", new String(new char[]{(char) event.getUnicodeChar()}));
        }

        sendKeyPressPacket(np);
        return true;

    }

}
