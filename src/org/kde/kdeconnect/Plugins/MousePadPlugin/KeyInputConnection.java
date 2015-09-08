package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.util.Log;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.InputConnection;

public class KeyInputConnection extends BaseInputConnection {
    private KeyListenerView view;

    public KeyInputConnection(KeyListenerView targetView, boolean fullEditor) {
        super(targetView, fullEditor);
        view = targetView;
    }

    @Override public boolean commitText(CharSequence text, int newCursorPosition) {
        view.sendChars(text);
        return true;
    }
}