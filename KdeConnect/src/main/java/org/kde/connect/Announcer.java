package org.kde.connect;

import android.net.nsd.NsdServiceInfo;

/**
 * Created by vaka on 6/10/13.
 */
public interface Announcer {

    public interface ConnexionReceiver {
        public void onPair(ComputerLink p);
    }

    public boolean startAnnouncing(ConnexionReceiver cr);

    public void stopAnnouncing();

}
