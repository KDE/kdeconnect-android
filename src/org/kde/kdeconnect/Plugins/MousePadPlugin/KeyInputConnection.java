/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <kde@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

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
