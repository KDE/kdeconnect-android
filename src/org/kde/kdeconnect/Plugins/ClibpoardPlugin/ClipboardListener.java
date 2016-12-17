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

import java.util.HashSet;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ClipboardListener {

    public interface ClipboardObserver {
        void clipboardChanged(String content);
    }
    private HashSet<ClipboardObserver> observers = new HashSet<>();

    private final Context context;
    private String currentContent;

    private ClipboardManager cm = null;
    private ClipboardManager.OnPrimaryClipChangedListener listener;

    private static ClipboardListener _instance = null;
    public static ClipboardListener instance(Context context) {
        if (_instance == null) {
            _instance = new ClipboardListener(context);
        }
        return _instance;
    }

    public void registerObserver(ClipboardObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(ClipboardObserver observer) {
        observers.remove(observer);
    }

    ClipboardListener(final Context ctx) {
        context = ctx;

        if(android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
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

                            if (content.equals(currentContent)) {
                                return;
                            }

                            currentContent = content;

                            for (ClipboardObserver observer : observers) {
                                observer.clipboardChanged(content);
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

    @SuppressWarnings("deprecation")
    public void setText(String text) {
        currentContent = text;
        if(android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
        }
        else
        {
            cm.setText(text);
        }
    }

}
