/*
 * SPDX-FileCopyrightText: 2017 Holger Kaelberer <holger.k@elberer.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.RemoteKeyboardPlugin;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect.UserInterface.StartActivityAlertDialogFragment;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@PluginFactory.LoadablePlugin
public class RemoteKeyboardPlugin extends Plugin implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";
    private final static String PACKET_TYPE_MOUSEPAD_ECHO = "kdeconnect.mousepad.echo";
    private final static String PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate";

    /**
     * Track and expose plugin instances to allow for a 'connected'-indicator in the IME:
     */
    private static final ArrayList<RemoteKeyboardPlugin> instances = new ArrayList<>();
    private static final ReentrantLock instancesLock = new ReentrantLock(true);

    private static ArrayList<RemoteKeyboardPlugin> getInstances() {
        return instances;
    }

    public static ArrayList<RemoteKeyboardPlugin> acquireInstances() {
        instancesLock.lock();
        return getInstances();
    }

    public static ArrayList<RemoteKeyboardPlugin> releaseInstances() {
        instancesLock.unlock();
        return getInstances();
    }

    public static boolean isConnected() {
        return !instances.isEmpty();
    }

    private static final SparseIntArray specialKeyMap = new SparseIntArray();

    static {
        int i = 0;
        specialKeyMap.put(++i, KeyEvent.KEYCODE_DEL);              // 1
        specialKeyMap.put(++i, KeyEvent.KEYCODE_TAB);              // 2
        ++i; //specialKeyMap.put(++i, KeyEvent.KEYCODE_ENTER, 12); // 3 is not used
        specialKeyMap.put(++i, KeyEvent.KEYCODE_DPAD_LEFT);        // 4
        specialKeyMap.put(++i, KeyEvent.KEYCODE_DPAD_UP);          // 5
        specialKeyMap.put(++i, KeyEvent.KEYCODE_DPAD_RIGHT);       // 6
        specialKeyMap.put(++i, KeyEvent.KEYCODE_DPAD_DOWN);        // 7
        specialKeyMap.put(++i, KeyEvent.KEYCODE_PAGE_UP);          // 8
        specialKeyMap.put(++i, KeyEvent.KEYCODE_PAGE_DOWN);        // 9
        specialKeyMap.put(++i, KeyEvent.KEYCODE_MOVE_HOME);    // 10
        specialKeyMap.put(++i, KeyEvent.KEYCODE_MOVE_END);     // 11
        specialKeyMap.put(++i, KeyEvent.KEYCODE_ENTER); // 12
        specialKeyMap.put(++i, KeyEvent.KEYCODE_FORWARD_DEL);  // 13
        specialKeyMap.put(++i, KeyEvent.KEYCODE_ESCAPE);       // 14
        specialKeyMap.put(++i, KeyEvent.KEYCODE_SYSRQ);        // 15
        specialKeyMap.put(++i, KeyEvent.KEYCODE_SCROLL_LOCK);  // 16
        ++i;           // 17
        ++i;           // 18
        ++i;           // 19
        ++i;           // 20
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F1);           // 21
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F2);           // 22
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F3);           // 23
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F4);           // 24
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F5);           // 25
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F6);           // 26
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F7);           // 27
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F8);           // 28
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F9);           // 29
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F10);          // 30
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F11);          // 31
        specialKeyMap.put(++i, KeyEvent.KEYCODE_F12);          // 21
    }

    @Override
    public boolean onCreate() {
        Log.d("RemoteKeyboardPlugin", "Creating for device " + getDevice().getName());
        acquireInstances();
        try {
            instances.add(this);
        } finally {
            releaseInstances();
        }
        if (RemoteKeyboardService.instance != null)
            RemoteKeyboardService.instance.handler.post(() -> RemoteKeyboardService.instance.updateInputView());

        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);

        final boolean editingOnly = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.remotekeyboard_editing_only), true);
        final boolean visible = RemoteKeyboardService.instance != null && RemoteKeyboardService.instance.visible;
        notifyKeyboardState(!editingOnly || visible);

        return true;
    }

    @Override
    public void onDestroy() {
        acquireInstances();
        try {
            if (instances.contains(this)) {
                instances.remove(this);
                if (instances.isEmpty() && RemoteKeyboardService.instance != null)
                    RemoteKeyboardService.instance.handler.post(() -> RemoteKeyboardService.instance.updateInputView());
            }
        } finally {
            releaseInstances();
        }

        Log.d("RemoteKeyboardPlugin", "Destroying for device " + getDevice().getName());
    }

    @Override
    public @NonNull String getDisplayName() {
        return context.getString(R.string.pref_plugin_remotekeyboard);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getString(R.string.pref_plugin_remotekeyboard_desc);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return PluginSettingsFragment.newInstance(getPluginKey(), R.xml.remotekeyboardplugin_preferences);
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_ECHO, PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE};
    }

    private boolean isValidSpecialKey(int key) {
        return (specialKeyMap.get(key, 0) > 0);
    }

    private int getCharPos(ExtractedText extractedText, char ch, boolean forward) {
        int pos = -1;
        if (extractedText != null) {
            if (!forward)  // backward
                pos = extractedText.text.toString().lastIndexOf(" ", extractedText.selectionEnd - 2);
            else
                pos = extractedText.text.toString().indexOf(" ", extractedText.selectionEnd + 1);
            return pos;
        }
        return pos;
    }

    private int currentTextLength(ExtractedText extractedText) {
        if (extractedText != null)
            return extractedText.text.length();
        return -1;
    }

    private int currentCursorPos(ExtractedText extractedText) {
        if (extractedText != null)
            return extractedText.selectionEnd;
        return -1;
    }

    private Pair<Integer, Integer> currentSelection(ExtractedText extractedText) {
        if (extractedText != null)
            return new Pair<>(extractedText.selectionStart, extractedText.selectionEnd);
        return new Pair<>(-1, -1);
    }

    private boolean handleSpecialKey(int key, boolean shift, boolean ctrl, boolean alt) {
        int keyEvent = specialKeyMap.get(key, 0);
        if (keyEvent == 0)
            return false;
        InputConnection inputConn = RemoteKeyboardService.instance.getCurrentInputConnection();
//        Log.d("RemoteKeyboardPlugin", "Handling special key " + key + " translated to " + keyEvent + " shift=" + shift + " ctrl=" + ctrl + " alt=" + alt);

        // special sequences:
        if (ctrl && (keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // Ctrl + right -> next word
            ExtractedText extractedText = inputConn.getExtractedText(new ExtractedTextRequest(), 0);
            int pos = getCharPos(extractedText, ' ', keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT);
            if (pos == -1)
                pos = currentTextLength(extractedText);
            else
                pos++;
            int startPos = pos;
            int endPos = pos;
            if (shift) { // Shift -> select word (otherwise jump)
                Pair<Integer, Integer> sel = currentSelection(extractedText);
                int cursor = currentCursorPos(extractedText);
//                Log.d("RemoteKeyboardPlugin", "Selection (to right): " + sel.first + " / " + sel.second + " cursor: " + cursor);
                startPos = cursor;
                if (sel.first < cursor ||   // active selection from left to right -> grow
                        sel.first > sel.second) // active selection from right to left -> shrink
                    startPos = sel.first;
            }
            inputConn.setSelection(startPos, endPos);
        } else if (ctrl && keyEvent == KeyEvent.KEYCODE_DPAD_LEFT) {
            // Ctrl + left -> previous word
            ExtractedText extractedText = inputConn.getExtractedText(new ExtractedTextRequest(), 0);
            int pos = getCharPos(extractedText, ' ', keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT);
            if (pos == -1)
                pos = 0;
            else
                pos++;
            int startPos = pos;
            int endPos = pos;
            if (shift) {
                Pair<Integer, Integer> sel = currentSelection(extractedText);
                int cursor = currentCursorPos(extractedText);
//                Log.d("RemoteKeyboardPlugin", "Selection (to left): " + sel.first + " / " + sel.second + " cursor: " + cursor);
                startPos = cursor;
                if (cursor < sel.first ||    // active selection from right to left -> grow
                        sel.first < sel.second)  // active selection from right to left -> shrink
                    startPos = sel.first;
            }
            inputConn.setSelection(startPos, endPos);
        } else if (shift
                && (keyEvent == KeyEvent.KEYCODE_DPAD_LEFT
                || keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyEvent == KeyEvent.KEYCODE_DPAD_UP
                || keyEvent == KeyEvent.KEYCODE_DPAD_DOWN
                || keyEvent == KeyEvent.KEYCODE_MOVE_HOME
                || keyEvent == KeyEvent.KEYCODE_MOVE_END)) {
            // Shift + up/down/left/right/home/end
            long now = SystemClock.uptimeMillis();
            inputConn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0));
            inputConn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyEvent, 0, KeyEvent.META_SHIFT_LEFT_ON));
            inputConn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyEvent, 0, KeyEvent.META_SHIFT_LEFT_ON));
            inputConn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0));
        } else if (keyEvent == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyEvent == KeyEvent.KEYCODE_ENTER) {
            // Enter key
            EditorInfo editorInfo = RemoteKeyboardService.instance.getCurrentInputEditorInfo();
//            Log.d("RemoteKeyboardPlugin", "Enter: " + editorInfo.imeOptions);
            if (editorInfo != null
                    && (((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
                    || ctrl)) {  // Ctrl+Return overrides IME_FLAG_NO_ENTER_ACTION (FIXME: make configurable?)
                // check for special DONE/GO/etc actions first:
                int[] actions = {EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT,
                        EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_SEARCH,
                        EditorInfo.IME_ACTION_DONE};  // note: DONE should be last or we might hide the ime instead of "go"
                for (int action : actions) {
                    if ((editorInfo.imeOptions & action) == action) {
//                        Log.d("RemoteKeyboardPlugin", "Enter-action: " + actions[i]);
                        inputConn.performEditorAction(action);
                        return true;
                    }
                }
            } else {
                // else: fall back to regular Enter-event:
//                Log.d("RemoteKeyboardPlugin", "Enter: normal keypress");
                inputConn.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEvent));
                inputConn.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEvent));
            }
        } else {
            // default handling:
            inputConn.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEvent));
            inputConn.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEvent));
        }

        return true;
    }

    private boolean handleVisibleKey(String key, boolean shift, boolean ctrl, boolean alt) {
//        Log.d("RemoteKeyboardPlugin", "Handling visible key " + key + " shift=" + shift + " ctrl=" + ctrl + " alt=" + alt + " " + key.equalsIgnoreCase("c") + " " + key.length());

        if (key.isEmpty())
            return false;

        InputConnection inputConn = RemoteKeyboardService.instance.getCurrentInputConnection();
        if (inputConn == null)
            return false;

        // ctrl+c/v/x
        if (key.equalsIgnoreCase("c") && ctrl) {
            return inputConn.performContextMenuAction(android.R.id.copy);
        } else if (key.equalsIgnoreCase("v") && ctrl)
            return inputConn.performContextMenuAction(android.R.id.paste);
        else if (key.equalsIgnoreCase("x") && ctrl)
            return inputConn.performContextMenuAction(android.R.id.cut);
        else if (key.equalsIgnoreCase("a") && ctrl)
            return inputConn.performContextMenuAction(android.R.id.selectAll);

//        Log.d("RemoteKeyboardPlugin", "Committing visible key '" + key + "'");
        inputConn.commitText(key, key.length());
        return true;
    }

    private boolean handleEvent(NetworkPacket np) {
        if (np.has("specialKey") && isValidSpecialKey(np.getInt("specialKey")))
            return handleSpecialKey(np.getInt("specialKey"), np.getBoolean("shift"),
                    np.getBoolean("ctrl"), np.getBoolean("alt"));

        // try visible key
        return handleVisibleKey(np.getString("key"), np.getBoolean("shift"),
                np.getBoolean("ctrl"), np.getBoolean("alt"));
    }


    public enum MousePadPacketType {
        Keyboard,
        Mouse,
    }

    public static MousePadPacketType getMousePadPacketType(NetworkPacket np) {
        if (np.has("key") || np.has("specialKey")) {
            return MousePadPacketType.Keyboard;
        } else {
            return MousePadPacketType.Mouse;
        }
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {

        if (!np.getType().equals(PACKET_TYPE_MOUSEPAD_REQUEST)) {
            Log.e("RemoteKeyboardPlugin", "Invalid packet type for RemoteKeyboardPlugin: "+np.getType());
            return false;
        }

        if (getMousePadPacketType(np) != MousePadPacketType.Keyboard) {
            return false; // This packet will be handled by the MouseReceiverPlugin instead, silently ignore
        }

        if (RemoteKeyboardService.instance == null) {
            Log.i("RemoteKeyboardPlugin", "Remote keyboard is not the currently selected input method, dropping key");
            return false;
        }

        if (!RemoteKeyboardService.instance.visible &&
                PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.remotekeyboard_editing_only), true)) {
            Log.i("RemoteKeyboardPlugin", "Remote keyboard is currently not visible, dropping key");
            return false;
        }

        if (!handleEvent(np)) {
            Log.i("RemoteKeyboardPlugin", "Could not handle event!");
            return false;
        }

        if (np.getBoolean("sendAck")) {
            NetworkPacket reply = new NetworkPacket(PACKET_TYPE_MOUSEPAD_ECHO);
            reply.set("key", np.getString("key"));
            if (np.has("specialKey"))
                reply.set("specialKey", np.getInt("specialKey"));
            if (np.has("shift"))
                reply.set("shift", np.getBoolean("shift"));
            if (np.has("ctrl"))
                reply.set("ctrl", np.getBoolean("ctrl"));
            if (np.has("alt"))
                reply.set("alt", np.getBoolean("alt"));
            reply.set("isAck", true);
            getDevice().sendPacket(reply);
        }

        return true;
    }

    public void notifyKeyboardState(boolean state) {
        Log.d("RemoteKeyboardPlugin", "Keyboardstate changed to " + state);
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE);
        np.set("state", state);
        getDevice().sendPacket(np);
    }

    String getDeviceId() {
        return getDevice().getDeviceId();
    }

    @Override
    public boolean checkRequiredPermissions() {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> inputMethodList = inputMethodManager.getEnabledInputMethodList();
        return inputMethodList.stream().anyMatch(
                info -> context.getPackageName().equals(info.getPackageName()));
    }

    @Override
    public @NonNull DialogFragment getPermissionExplanationDialog() {
        return new StartActivityAlertDialogFragment.Builder()
                .setTitle(R.string.pref_plugin_remotekeyboard)
                .setMessage(R.string.no_permissions_remotekeyboard)
                .setPositiveButton(R.string.open_settings)
                .setNegativeButton(R.string.cancel)
                .setIntentAction(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .setStartForResult(true)
                .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                .create();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(context.getString(R.string.remotekeyboard_editing_only))) {
            final boolean editingOnly = sharedPreferences.getBoolean(context.getString(R.string.remotekeyboard_editing_only), true);
            final boolean visible = RemoteKeyboardService.instance != null && RemoteKeyboardService.instance.visible;
            notifyKeyboardState(!editingOnly || visible);
        }
    }
}
