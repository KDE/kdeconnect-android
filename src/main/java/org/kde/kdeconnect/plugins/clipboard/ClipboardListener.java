/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.clipboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.helpers.ThreadHelper;
import org.kde.kdeconnect_tp.BuildConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public class ClipboardListener {

    public interface ClipboardObserver {
        void clipboardChanged(String content);
    }

    private final HashSet<ClipboardObserver> observers = new HashSet<>();

    private final Context context;
    private String currentContent;
    private long updateTimestamp;

    private ClipboardManager cm = null;

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
        context = ctx.getApplicationContext();

        new Handler(Looper.getMainLooper()).post(() -> {
            cm = ContextCompat.getSystemService(context, ClipboardManager.class);
            cm.addPrimaryClipChangedListener(this::onClipboardChanged);
        });


        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED) {
            ThreadHelper.execute(() -> {
                try {
                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                    // Listen only ClipboardService errors after now
                    String logcatFilter;
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        logcatFilter = "E ClipboardService";
                    } else {
                        logcatFilter = "ClipboardService:E";
                    }
                    Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-T", timeStamp, logcatFilter, "*:S"});
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    );
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        if (line.contains(BuildConfig.APPLICATION_ID)) {
                            context.startActivity(ClipboardFloatingActivity.getIntent(context, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    public void onClipboardChanged() {
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
