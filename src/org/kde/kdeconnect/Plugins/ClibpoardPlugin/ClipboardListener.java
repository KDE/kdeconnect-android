/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Plugins.ClibpoardPlugin;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ClipboardListener {


    private final Context context;
    private String currentContent;

    private ClipboardManager cm = null;
    private ClipboardManager.OnPrimaryClipChangedListener listener;

    ClipboardListener(final Context ctx, final Device device) {
        context = ctx;
        if(android.os.Build.VERSION.SDK_INT < 11) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                listener = new ClipboardManager.OnPrimaryClipChangedListener() {
                    @Override
                    public void onPrimaryClipChanged() {
                        try {

                            ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                            String content = item.coerceToText(context).toString();

                            if (!content.equals(currentContent)) {
                                NetworkPackage np = new NetworkPackage(ClipboardPlugin.PACKAGE_TYPE_CLIPBOARD);
                                np.set("content", content);
                                device.sendPackage(np);
                                currentContent = content;
                            }

                        } catch (Exception e) {
                            //Probably clipboard was not text
                        }
                    }
                };
                cm.addPrimaryClipChangedListener(listener);
            }
        });
    }

    public void stop() {
        if(android.os.Build.VERSION.SDK_INT < 11) {
            return;
        }

        cm.removePrimaryClipChangedListener(listener);
    }

    @SuppressWarnings("deprecation")
    public void setText(String text) {
        currentContent = text;
        if(android.os.Build.VERSION.SDK_INT < 11) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
        }
        else
        {
            cm.setText(text);
        }
    }

}
