/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.ClibpoardPlugin;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.HashSet;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ClipboardListener {

    public interface ClipboardObserver {
        void clipboardChanged(String content);
    }

    private final HashSet<ClipboardObserver> observers = new HashSet<>();

    private final Context context;
    private String currentContent;
    private long updateTimestamp;

    private ClipboardManager cm = null;
    private ClipboardManager.OnPrimaryClipChangedListener listener;

    private static ClipboardListener _instance = null;

    public static ClipboardListener instance(Context context) {
        if (_instance == null) {
            _instance = new ClipboardListener(context);
            // FIXME: The _instance we return won't be completely initialized yet since initialization happens on a new thread (why?)
        }
        return _instance;
    }

    public void registerObserver(ClipboardObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(ClipboardObserver observer) {
        observers.remove(observer);
    }

    private ClipboardListener(final Context ctx) {
        context = ctx;

        new Handler(Looper.getMainLooper()).post(() -> {
            cm = ContextCompat.getSystemService(context, ClipboardManager.class);
            listener = () -> {
                try {

                    ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                    String content = item.coerceToText(context).toString();

                    if (content.equals(currentContent)) {
                        return;
                    }
                    updateTimestamp = System.currentTimeMillis();
                    currentContent = content;

                    for (ClipboardObserver observer : observers) {
                        observer.clipboardChanged(content);
                    }

                } catch (Exception e) {
                    //Probably clipboard was not text
                }
            };
            cm.addPrimaryClipChangedListener(listener);
        });
    }

    public String getCurrentContent() {
        return currentContent;
    }

    public long getUpdateTimestamp() {
        return updateTimestamp;
    }

    @SuppressWarnings("deprecation")
    public void setText(String text) {
        if (cm != null) {
            updateTimestamp = System.currentTimeMillis();
            currentContent = text;
            cm.setText(text);
        }
    }

}
