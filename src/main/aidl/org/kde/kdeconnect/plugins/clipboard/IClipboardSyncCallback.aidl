package org.kde.kdeconnect.plugins.clipboard;

interface IClipboardSyncCallback {
    void onClipboardChanged(String text, boolean sensitive, boolean plainText, boolean remoteDevice);
}
