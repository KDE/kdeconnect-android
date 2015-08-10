/*
 * Copyright 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
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

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

public class MousePadActivity extends ActionBarActivity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, MousePadGestureDetector.OnGestureListener {

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

    enum ClickType {
        RIGHT, MIDDLE, NONE;
        static ClickType fromString(String s) {
            switch(s) {
                case "right": return RIGHT;
                case "middle": return MIDDLE;
                default: return NONE;
            }
        }
    }

    private ClickType doubleTapAction, tripleTapAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mousepad);

        deviceId = getIntent().getStringExtra("deviceId");

        getWindow().getDecorView().setHapticFeedbackEnabled(true);

        mDetector = new GestureDetector(this, this);
        mMousePadGestureDetector = new MousePadGestureDetector(this, this);
        mDetector.setOnDoubleTapListener(this);

        keyListenerView = (KeyListenerView)findViewById(R.id.keyListener);
        keyListenerView.setDeviceId(deviceId);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String doubleTapSetting = prefs.getString(getString(R.string.mousepad_double_tap_key),
                getString(R.string.mousepad_double_default));
        String tripleTapSetting = prefs.getString(getString(R.string.mousepad_triple_tap_key),
                getString(R.string.mousepad_triple_default));

        doubleTapAction = ClickType.fromString(doubleTapSetting);
        tripleTapAction = ClickType.fromString(tripleTapSetting);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {

                        int fullscreenType = 0;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            fullscreenType |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            fullscreenType |= View.SYSTEM_UI_FLAG_FULLSCREEN;
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            fullscreenType |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                        }

                        getWindow().getDecorView().setSystemUiVisibility(fullscreenType);
                    }
                }
            });
        }

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
                        MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
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
                    MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
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

        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
                if (mousePadPlugin == null) return;
                mousePadPlugin.sendSingleHold();
            }
        });
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
                MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
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
                MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
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
        switch(tripleTapAction){
            case RIGHT:
                sendRightClick();
                break;
            case MIDDLE:
                sendMiddleClick();
                break;
            default:
        }
        return true;
    }

    @Override
    public boolean onDoubleFingerTap(MotionEvent ev) {
        switch(doubleTapAction){
            case RIGHT:
                sendRightClick();
                break;
            case MIDDLE:
                sendMiddleClick();
                break;
            default:
        }
        return true;
    }


    private void sendMiddleClick() {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
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
                MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
                if (mousePadPlugin == null) return;
                mousePadPlugin.sendRightClick();
            }
        });
    }
        private void sendSingleHold() {
            BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    Device device = service.getDevice(deviceId);
                    MousePadPlugin mousePadPlugin = device.getPlugin(MousePadPlugin.class);
                    if (mousePadPlugin == null) return;
                    mousePadPlugin.sendSingleHold();
                }
            });
        }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInputFromWindow(keyListenerView.getWindowToken(), 0, 0);
    }

}

