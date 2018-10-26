package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.view.inputmethod.BaseInputConnection;

class KeyInputConnection extends BaseInputConnection {
    private final KeyListenerView view;

    public KeyInputConnection(KeyListenerView targetView, boolean fullEditor) {
        super(targetView, fullEditor);
        view = targetView;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        view.sendChars(text);
        return true;
    }
}