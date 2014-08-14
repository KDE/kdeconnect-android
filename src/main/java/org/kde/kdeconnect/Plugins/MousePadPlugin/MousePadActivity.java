package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

public class MousePadActivity extends Activity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, MousePadGestureDetector.OnGestureListener {

    String deviceId;

    private final static float MinDistanceToSendScroll = 2.5f;

    private float mPrevX;
    private float mPrevY;
    private float mCurrentX;
    private float mCurrentY;

    boolean isScrolling = false;
    float accumulatedDistanceY = 0;

    private GestureDetector mDetector;
    private MousePadGestureDetector mMousePadGestureDetector;

    KeyListenerView keyListenerView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mousepad);

        deviceId = getIntent().getStringExtra("deviceId");

        mDetector = new GestureDetector(this, this);
        mMousePadGestureDetector = new MousePadGestureDetector(this, this);
        mDetector.setOnDoubleTapListener(this);

        keyListenerView = (KeyListenerView)findViewById(R.id.keyListener);
        keyListenerView.setDeviceId(deviceId);

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
                sendRightClick();
                return true;
            case R.id.menu_middle_click:
                sendMiddleClick();
                return true;
            case R.id.menu_show_keyboard:
                showKeyboard();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }



    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMousePadGestureDetector.onTouchEvent(event)) {
            return true;
        }
        if ( mDetector.onTouchEvent(event) ) {
            return true;
        }

        int actionType = event.getAction();

        if (isScrolling) {
            if (actionType == MotionEvent.ACTION_UP) {
                isScrolling = false;
            } else {
                return false;

            }
        }

        switch (actionType) {
            case MotionEvent.ACTION_DOWN:
                mPrevX = event.getX();
                mPrevY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                mCurrentX = event.getX();
                mCurrentY = event.getY();
                BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                        if (mousePadPlugin == null) return;
                        mousePadPlugin.sendMouseDelta(mCurrentX - mPrevX, mCurrentY - mPrevY);
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
        //From GestureDetector, left empty
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
        //From GestureDetector, left empty
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

    @Override
    public boolean onTripleFingerTap(MotionEvent ev) {
        sendMiddleClick();
        return true;
    }

    @Override
    public boolean onDoubleFingerTap(MotionEvent ev) {
        sendRightClick();
        return true;
    }


    private void sendMiddleClick() {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                if (mousePadPlugin == null) return;
                mousePadPlugin.sendMiddleClick();
            }
        });
    }

    private void sendRightClick() {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                MousePadPlugin mousePadPlugin = (MousePadPlugin)device.getPlugin("plugin_mousepad");
                if (mousePadPlugin == null) return;
                mousePadPlugin.sendRightClick();
            }
        });
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(keyListenerView, InputMethodManager.SHOW_IMPLICIT);
    }

}

