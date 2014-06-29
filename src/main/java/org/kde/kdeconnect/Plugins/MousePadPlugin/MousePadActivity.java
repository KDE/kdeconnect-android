package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.app.Activity;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

public class MousePadActivity extends Activity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private final static float MinDistanceToSendScroll = 2.5f;

    private float mPrevX;
    private float mPrevY;
    private float mCurrentX;
    private float mCurrentY;

    boolean isScrolling = false;

    float accumulatedDistanceY = 0;

    private String deviceId;

    private GestureDetector mDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mousepad);
        deviceId = getIntent().getStringExtra("deviceId");
        mDetector = new GestureDetector(this, this);
        mDetector.setOnDoubleTapListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_mousepad, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_right_click:
                BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                        if (mousePadPlugin == null) return;
                        mousePadPlugin.sendRightClick();
                    }
                });
                break;
            case R.id.menu_middle_click:
                BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                        if (mousePadPlugin == null) return;
                        mousePadPlugin.sendMiddleClick();
                    }
                });
                break;
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ( mDetector.onTouchEvent(event) ) {
            return true;
        }
        int actionType = event.getAction();
        final float x = event.getX();
        final float y = event.getY();
        if (isScrolling) {
            if (actionType == MotionEvent.ACTION_UP) {
                isScrolling = false;
            } else {
                return false;

            }
        }
        switch (actionType) {
            case MotionEvent.ACTION_DOWN:
                mPrevX = x;
                mPrevY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                mCurrentX = x;
                mCurrentY = y;
                BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                        if (mousePadPlugin == null) return;
                        mousePadPlugin.sendPoints(mCurrentX - mPrevX, mCurrentY - mPrevY);
                        mPrevX = mCurrentX;
                        mPrevY = mCurrentY;
                    }
                });
                break;
        }
        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, final float distanceX, final float distanceY) {
        // If only one thumb is used then cancel the scroll gesture
        if (e2.getPointerCount() <= 1) {
            return false;
        }

        isScrolling = true;

        accumulatedDistanceY += distanceY;
        if (accumulatedDistanceY > MinDistanceToSendScroll || accumulatedDistanceY < -MinDistanceToSendScroll)
        {
            final float scrollToSendY = accumulatedDistanceY;

            BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    Device device = service.getDevice(deviceId);
                    MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                    if (mousePadPlugin == null) return;
                    mousePadPlugin.sendScroll(0, scrollToSendY);
                }
            });

            accumulatedDistanceY = 0;
        }

        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                if (mousePadPlugin == null) return;
                mousePadPlugin.sendSingleClick();
            }
        });
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                if (mousePadPlugin == null) return;
                mousePadPlugin.sendDoubleClick();
            }
        });
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }
}
