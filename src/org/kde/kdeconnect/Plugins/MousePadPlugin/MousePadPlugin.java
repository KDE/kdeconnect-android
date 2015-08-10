/*
 * Copyright 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

public class MousePadPlugin extends Plugin {

    @Override
    public String getDisplayName() {
        return context.getString(R.string.pref_plugin_mousepad);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.pref_plugin_mousepad_desc);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.touchpad_plugin_action);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, MousePadActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.open_mousepad);
    }

    public void sendMouseDelta(float dx, float dy) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("dx", dx);
        np.set("dy", dy);
        device.sendPackage(np);
    }

    public void sendSingleClick() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("singleclick", true);
        device.sendPackage(np);
    }

    public void sendDoubleClick() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("doubleclick", true);
        device.sendPackage(np);
    }

    public void sendMiddleClick() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("middleclick", true);
        device.sendPackage(np);
    }

    public void sendRightClick() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("rightclick", true);
        device.sendPackage(np);
    }

    public void sendSingleHold(){
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("singlehold", true);
        device.sendPackage(np);
    }

    public void sendScroll(float dx, float dy) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MOUSEPAD);
        np.set("scroll", true);
        np.set("dx", dx);
        np.set("dy", dy);
        device.sendPackage(np);
    }

    public void sendKeyboardPacket(NetworkPackage np) {
        device.sendPackage(np);
    }

}
