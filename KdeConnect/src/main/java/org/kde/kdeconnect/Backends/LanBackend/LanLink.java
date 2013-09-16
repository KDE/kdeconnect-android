package org.kde.kdeconnect.Backends.LanBackend;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.apache.mina.core.session.IoSession;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class LanLink extends BaseLink {

    private IoSession session = null;

    public void disconnect() {
        Log.i("LanLink","Disconnect: "+session.getRemoteAddress().toString());
        session.close(true);
    }

    public LanLink(IoSession session, String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.session = session;
    }

    @Override
    public boolean sendPackage(NetworkPackage np) {
        if (session == null) {
            Log.e("LanLink","sendPackage failed: not yet connected");
            return false;
        } else {
            session.write(np.serialize());
            return true;
        }
    }

    @Override
    public boolean sendPackageEncrypted(NetworkPackage np, PublicKey key) {
        try {
            np.encrypt(key);
            return sendPackage(np);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LanLink", "Encryption exception");
        }
        return false;
    }

    public void injectNetworkPackage(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {

            try {
                np = np.decrypt(privateKey);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("onPackageReceived","Exception reading the key needed to decrypt the package");
            }

        }

        packageReceived(np);
    }
}
