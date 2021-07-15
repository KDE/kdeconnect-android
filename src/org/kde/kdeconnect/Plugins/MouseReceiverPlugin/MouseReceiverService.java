/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MouseReceiverPlugin;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect_tp.R;

import java.util.ArrayDeque;
import java.util.Deque;

public class MouseReceiverService extends AccessibilityService {
    public static MouseReceiverService instance;

    private View cursorView;
    private LayoutParams cursorLayout;
    private WindowManager windowManager;
    private Handler runHandler;
    private Runnable hideRunnable;
    private GestureDescription.StrokeDescription swipeStoke;
    private double scrollSum;

    @Override
    public void onCreate() {
        super.onCreate();
        MouseReceiverService.instance = this;
        Log.i("MouseReceiverService", "created");
    }

    @Override
    protected void onServiceConnected() {
        // Create an overlay and display the cursor
        windowManager = ContextCompat.getSystemService(this, WindowManager.class);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        cursorView = View.inflate(getBaseContext(), R.layout.mouse_receiver_cursor, null);
        cursorLayout = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                LayoutParams.FLAG_DISMISS_KEYGUARD | LayoutParams.FLAG_NOT_FOCUSABLE
                        | LayoutParams.FLAG_NOT_TOUCHABLE | LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        // allow cursor to move over status bar on devices having a display cutout
        // https://developer.android.com/guide/topics/display-cutout/#render_content_in_short_edge_cutout_areas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cursorLayout.layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        cursorLayout.gravity = Gravity.LEFT | Gravity.TOP;
        cursorLayout.x = displayMetrics.widthPixels / 2;
        cursorLayout.y = displayMetrics.heightPixels / 2;

        // https://developer.android.com/training/system-ui/navigation.html#behind
        cursorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        windowManager.addView(cursorView, cursorLayout);

        hideRunnable = () -> {
            cursorView.setVisibility(View.GONE);
            Log.i("MouseReceiverService", "Hiding pointer due to inactivity");
        };
        runHandler = new Handler();

        cursorView.setVisibility(View.GONE);
    }

    private void hideAfter5Seconds() {
        runHandler.removeCallbacks(hideRunnable);
        runHandler.postDelayed(hideRunnable, 5000);
    }

    public float getX() {
        return cursorLayout.x + cursorView.getWidth() / 2;
    }

    public float getY() {
        return cursorLayout.y + cursorView.getHeight() / 2;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void moveView(double dx, double dy) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        instance.windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);

        cursorLayout.x += dx;
        cursorLayout.y += dy;

        if (getX() > displayMetrics.widthPixels)
            cursorLayout.x = displayMetrics.widthPixels - cursorView.getWidth() / 2;
        if (getY() > displayMetrics.heightPixels)
            cursorLayout.y = displayMetrics.heightPixels - cursorView.getHeight() / 2;
        if (getX() < 0) cursorLayout.x = -cursorView.getWidth() / 2;
        if (getY() < 0) cursorLayout.y = -cursorView.getHeight() / 2;

        new Handler(instance.getMainLooper()).post(() -> {
            // Log.i("MouseReceiverService", "performing move");
            instance.windowManager.updateViewLayout(instance.cursorView, instance.cursorLayout);
            instance.cursorView.setVisibility(View.VISIBLE);
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean move(double dx, double dy) {
        if (instance == null) return false;

        float fromX = instance.getX();
        float fromY = instance.getY();

        instance.moveView(dx, dy);

        instance.hideAfter5Seconds();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && instance.isSwiping()) {
            return instance.continueSwipe(fromX, fromY);
        }

        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static GestureDescription createClick(float x, float y, int duration) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, duration);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean click() {
        if (instance == null) return false;
        // Log.i("MouseReceiverService", "x: " + instance.getX() + " y:" + instance.getY());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && instance.isSwiping()) {
            return instance.stopSwipe();
        }

        return click(instance.getX(), instance.getY());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean click(float x, float y) {
        if (instance == null) return false;
        return instance.dispatchGesture(createClick(x, y, 1 /*ms*/), null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean longClick() {
        if (instance == null) return false;
        return instance.dispatchGesture(createClick(instance.getX(), instance.getY(),
                ViewConfiguration.getLongPressTimeout()), null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean longClickSwipe() {
        if (instance == null) return false;

        if (instance.isSwiping()) {
            return instance.stopSwipe();
        } else {
            return instance.startSwipe();
        }
    }

    private boolean isSwiping() {
        return swipeStoke != null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean startSwipe() {
        assert swipeStoke == null;
        Path path = new Path();
        path.moveTo(getX(), getY());
        swipeStoke = new GestureDescription.StrokeDescription(path, 0, 1, true);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(swipeStoke);
        ((ImageView) cursorView.findViewById(R.id.mouse_cursor)).setImageResource(R.drawable.mouse_pointer_clicked);
        return dispatchGesture(builder.build(), null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean continueSwipe(float fromX, float fromY) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(getX(), getY());
        swipeStoke = swipeStoke.continueStroke(path, 0, 5, true);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(swipeStoke);
        return dispatchGesture(builder.build(), null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean stopSwipe() {
        Path path = new Path();
        path.moveTo(getX(), getY());
        if (swipeStoke == null) {
            return true;
        }
        swipeStoke = swipeStoke.continueStroke(path, 0, 1, false);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(swipeStoke);
        swipeStoke = null;
        ((ImageView) cursorView.findViewById(R.id.mouse_cursor)).setImageResource(R.drawable.mouse_pointer);
        return dispatchGesture(builder.build(), null, null);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean scroll(double dx, double dy) {
        if (instance == null) return false;

        instance.scrollSum += dy;
        if (Math.signum(dy) != Math.signum(instance.scrollSum)) instance.scrollSum = dy;
        if (Math.abs(instance.scrollSum) < 500) return false;
        instance.scrollSum = 0;

        AccessibilityNodeInfo scrollable = instance.findNodeByAciton(instance.getRootInActiveWindow(),
                dy > 0 ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);

        if (scrollable == null) return false;

        return scrollable.performAction(dy > 0
                ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId()
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId()
        );
    }

    // https://codelabs.developers.google.com/codelabs/developing-android-a11y-service/#6
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private AccessibilityNodeInfo findNodeByAciton(AccessibilityNodeInfo root, AccessibilityNodeInfo.AccessibilityAction action) {
        Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
        deque.add(root);
        while (!deque.isEmpty()) {
            AccessibilityNodeInfo node = deque.removeFirst();
            if (node.getActionList().contains(action)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                deque.addLast(node.getChild(i));
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static boolean backButton() {
        if (instance == null) return false;
        return instance.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static boolean homeButton() {
        if (instance == null) return false;
        return instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static boolean recentButton() {
        if (instance == null) return false;
        return instance.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static boolean powerButton() {
        if (instance == null) return false;

        return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (windowManager != null && cursorView != null) {
            windowManager.removeView(cursorView);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }
}
