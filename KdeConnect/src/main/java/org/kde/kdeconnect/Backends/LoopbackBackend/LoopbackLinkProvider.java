package org.kde.kdeconnect.Backends.LoopbackBackend;

import android.content.Context;

import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

public class LoopbackLinkProvider extends BaseLinkProvider {

    private Context context;

    public LoopbackLinkProvider(Context context) {
        this.context = context;
    }

    @Override
    public void onStart() {
        onNetworkChange();
    }

    @Override
    public void onStop() {

    }

    @Override
    public void onNetworkChange() {

        NetworkPackage np = NetworkPackage.createIdentityPackage(context);
        connectionAccepted(np, new LoopbackLink(this));

    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getName() {
        return "LoopbackLinkProvider";
    }
}
