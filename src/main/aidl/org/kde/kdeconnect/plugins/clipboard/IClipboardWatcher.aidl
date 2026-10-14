package org.kde.kdeconnect.plugins.clipboard;

import org.kde.kdeconnect.plugins.clipboard.IClipboardSyncCallback;

interface IClipboardWatcher {
    void setCallback(IClipboardSyncCallback callback);
    boolean start();
    void stop();
}
