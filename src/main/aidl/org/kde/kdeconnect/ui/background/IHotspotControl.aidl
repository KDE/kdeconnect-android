package org.kde.kdeconnect.ui.background;

interface IHotspotControl {
    String startHotspot(String ssid, String passphrase);
    String joinHotspot(String ssid, String passphrase);
    String stopHotspot();
}
