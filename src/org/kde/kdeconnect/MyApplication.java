package org.kde.kdeconnect;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDexApplication;

public class MyApplication extends MultiDexApplication {
    private static class LifecycleObserver implements DefaultLifecycleObserver {
        private boolean inForeground = false;

        @Override
        public void onStart(@NonNull LifecycleOwner owner) {
            inForeground = true;
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            inForeground = false;
        }

        boolean isInForeground() {
            return inForeground;
        }
    }

    private static final LifecycleObserver foregroundTracker = new LifecycleObserver();

    @Override
    public void onCreate() {
        super.onCreate();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(foregroundTracker);
    }

    public static boolean isInForeground() {
        return foregroundTracker.isInForeground();
    }
}
