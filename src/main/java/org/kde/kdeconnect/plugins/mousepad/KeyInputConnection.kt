/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <kde@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mousepad

import android.view.inputmethod.BaseInputConnection

internal class KeyInputConnection(private val view: KeyListenerView, fullEditor: Boolean) :
    BaseInputConnection(view, fullEditor) {

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        view.sendChars(text)
        return true
    }

}
