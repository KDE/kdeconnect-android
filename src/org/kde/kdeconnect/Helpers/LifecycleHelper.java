package org.kde.kdeconnect.Helpers;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

public class LifecycleHelper {

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

    private final static LifecycleObserver foregroundTracker = new LifecycleObserver();

    public static boolean isInForeground() {
        return foregroundTracker.isInForeground();
    }

    public static void initializeObserver() {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(foregroundTracker);
    }
}
