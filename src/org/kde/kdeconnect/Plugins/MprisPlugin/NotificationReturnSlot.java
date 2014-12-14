/*
 * Copyright 2014 Da-Jin Chu <dajinchu@gmail.com>
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
package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;

public class NotificationReturnSlot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");
        final String deviceId = intent.getStringExtra("deviceId");
        final String player = intent.getStringExtra("player");

        //Log.e("onReceive",""+deviceId+player);
        if (action.equals("play")) {
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    Device device = service.getDevice(deviceId);
                    MprisPlugin mpris = (MprisPlugin) device.getPlugin("plugin_mpris");
                    if (mpris == null) return;
                    mpris.setPlayer(player);
                    mpris.sendAction("PlayPause");
                }
            });
        } else if (action.equals("next")) {
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    Device device = service.getDevice(deviceId);
                    MprisPlugin mpris = (MprisPlugin) device.getPlugin("plugin_mpris");
                    if (mpris == null) return;
                    mpris.setPlayer(player);
                    mpris.sendAction("Next");
                }
            });
        }else if(action.equals("prev")){
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    Device device = service.getDevice(deviceId);
                    MprisPlugin mpris = (MprisPlugin) device.getPlugin("plugin_mpris");
                    if (mpris == null) return;
                    mpris.setPlayer(player);
                    mpris.sendAction("Previous");
                }
            });
        }
    }
}
