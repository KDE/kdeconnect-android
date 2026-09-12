/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect;

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

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.plugins.inputdevicesreceiver.InputDevicesReceiverPlugin.Cursor;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayDeque;
import java.util.Deque;

public class KdeConnectAccessibilityService extends AccessibilityService {
    @Nullable
    public static KdeConnectAccessibilityService instance;

    public AccessibilityNodeInfo window = null;

    private View cursorView;
    private LayoutParams cursorLayout;
    private WindowManager windowManager;
    private Handler runHandler;
    private Runnable hideRunnable;
    private GestureDescription.StrokeDescription swipeStoke;
    private double scrollSum;

    @Override
    public void onCreate() {
        KdeConnectAccessibilityService.instance = this;
        Log.i("KdeConnectAccessibilityService", "created");
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

        cursorLayout.gravity = Gravity.START | Gravity.TOP;
        cursorLayout.x = displayMetrics.widthPixels / 2;
        cursorLayout.y = displayMetrics.heightPixels / 2;

        // https://developer.android.com/training/system-ui/navigation.html#behind
        cursorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        windowManager.addView(cursorView, cursorLayout);

        hideRunnable = () -> {
            cursorView.setVisibility(View.GONE);
            Log.i("KdeConnectAccessibilityService", "Hiding pointer due to inactivity");
        };
        runHandler = new Handler();

        cursorView.setVisibility(View.GONE);
    }

    private void hideAfter5Seconds() {
        hide(5000);
    }

    public void hide(int delayMillis) {
        runHandler.removeCallbacks(hideRunnable);
        runHandler.postDelayed(hideRunnable, delayMillis);
    }

    public int getX() {
        return cursorLayout.x + cursorView.getWidth() / 2;
    }

    public int getY() {
        return cursorLayout.y + cursorView.getHeight() / 2;
    }

    public void moveView(int dx, int dy) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);

        cursorLayout.x += dx;
        cursorLayout.y += dy;

        if (getX() > displayMetrics.widthPixels)
            cursorLayout.x = displayMetrics.widthPixels - cursorView.getWidth() / 2;
        if (getY() > displayMetrics.heightPixels)
            cursorLayout.y = displayMetrics.heightPixels - cursorView.getHeight() / 2;
        if (getX() < 0) cursorLayout.x = -cursorView.getWidth() / 2;
        if (getY() < 0) cursorLayout.y = -cursorView.getHeight() / 2;

        Cursor.INSTANCE.setX(getX());
        Cursor.INSTANCE.setY(getY());

        new Handler(getMainLooper()).post(() -> {
            // Log.i("KdeConnectAccessibilityService", "performing move");
            try {
                windowManager.updateViewLayout(cursorView, cursorLayout);
                cursorView.setVisibility(View.VISIBLE);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        });
    }

    public boolean move(int dx, int dy) {
        int fromX = getX();
        int fromY = getY();

        moveView(dx, dy);

        hideAfter5Seconds();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isSwiping()) {
            return continueSwipe(fromX, fromY);
        }

        return true;
    }

    public boolean setPos(int x, int y) {
        return move(x - getX(), y - getY());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static GestureDescription createClick(int x, int y, int duration) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, duration);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean click() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isSwiping()) {
            return stopSwipe();
        }

        return click(getX(), getY());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean click(int x, int y) {
        return dispatchGesture(createClick(x, y, 1 /*ms*/), null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean longClick() {
        return dispatchGesture(createClick(getX(), getY(),
                ViewConfiguration.getLongPressTimeout()), null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean longClickSwipe() {
        if (isSwiping()) {
            return stopSwipe();
        } else {
            return startSwipe();
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
    private boolean continueSwipe(int fromX, int fromY) {
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

    public boolean scroll(int dx, int dy) {
        scrollSum += dy;
        if (Math.signum(dy) != Math.signum(scrollSum)) scrollSum = dy;
        if (Math.abs(scrollSum) < 500) return false;
        scrollSum = 0;

        AccessibilityNodeInfo scrollable = findNodeByAciton(getRootInActiveWindow(),
                dy > 0 ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);

        if (scrollable == null) return false;

        return scrollable.performAction(dy > 0
                ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId()
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId()
        );
    }

    // https://codelabs.developers.google.com/codelabs/developing-android-a11y-service/#6
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

    @Override
    public void onDestroy() {
        if (windowManager != null && cursorView != null) {
            windowManager.removeView(cursorView);
        }
        window = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ignored) {
        window = getRootInActiveWindow();
    }

    @Override
    public void onInterrupt() {

    }
}
