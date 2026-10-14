package org.kde.kdeconnect.ui.background;

interface IKeepAliveService {
    String applyProtections(String packageName);
    String inspectProtections(String packageName);
}
