package org.kde.connect.Announcers;

import org.kde.connect.ComputerLinks.BaseComputerLink;

public class BaseAnnouncer {

    public interface ConnexionReceiver {
        public void onPair(BaseComputerLink p);
    }

    protected ConnexionReceiver connexionReceiver;

    //To override
    public boolean startAnnouncing(ConnexionReceiver cr) {
        connexionReceiver = cr;
        return true;
    }

    public void stopAnnouncing() {
        connexionReceiver = null;
    }

}
