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

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

public class MousePadActivity extends AppCompatActivity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, MousePadGestureDetector.OnGestureListener {
    private String deviceId;

    private final static float MinDistanceToSendScroll = 2.5f; // touch gesture scroll
    private final static float MinDistanceToSendGenericScroll = 0.1f; // real mouse scroll wheel event
    private final static float StandardDpi = 240.0f; // = hdpi

    private float mPrevX;
    private float mPrevY;
    private float mCurrentX;
    private float mCurrentY;
    private float mCurrentSensitivity;
    private float displayDpiMultiplier;
    private int scrollDirection = 1;

    private boolean isScrolling = false;
    private float accumulatedDistanceY = 0;

    private GestureDetector mDetector;
    private MousePadGestureDetector mMousePadGestureDetector;
    private PointerAccelerationProfile mPointerAccelerationProfile;

    private PointerAccelerationProfile.MouseDelta mouseDelta; // to be reused on every touch move event

    private KeyListenerView keyListenerView;

    enum ClickType {
        RIGHT, MIDDLE, NONE;

        static ClickType fromString(String s) {
            switch (s) {
                case "right":
                    return RIGHT;
                case "middle":
                    return MIDDLE;
                default:
                    return NONE;
            }
        }
    }

    private ClickType doubleTapAction, tripleTapAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        setContentView(R.layout.activity_mousepad);

        deviceId = getIntent().getStringExtra("deviceId");

        getWindow().getDecorView().setHapticFeedbackEnabled(true);

        mDetector = new GestureDetector(this, this);
        mMousePadGestureDetector = new MousePadGestureDetector(this);
        mDetector.setOnDoubleTapListener(this);

        keyListenerView = findViewById(R.id.keyListener);
        keyListenerView.setDeviceId(deviceId);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(getString(R.string.mousepad_scroll_direction), false)) {
            scrollDirection = -1;
        } else {
            scrollDirection = 1;
        }
        String doubleTapSetting = prefs.getString(getString(R.string.mousepad_double_tap_key),
                getString(R.string.mousepad_default_double));
        String tripleTapSetting = prefs.getString(getString(R.string.mousepad_triple_tap_key),
                getString(R.string.mousepad_default_triple));
        String sensitivitySetting = prefs.getString(getString(R.string.mousepad_sensitivity_key),
                getString(R.string.mousepad_default_sensitivity));

        String accelerationProfileName = prefs.getString(getString(R.string.mousepad_acceleration_profile_key),
                getString(R.string.mousepad_default_acceleration_profile));

        mPointerAccelerationProfile = PointerAccelerationProfileFactory.getProfileWithName(accelerationProfileName);

        doubleTapAction = ClickType.fromString(doubleTapSetting);
        tripleTapAction = ClickType.fromString(tripleTapSetting);

        //Technically xdpi and ydpi should be handled separately,
        //but since ydpi is usually almost equal to xdpi, only xdpi is used for the multiplier.
        displayDpiMultiplier = StandardDpi / getResources().getDisplayMetrics().xdpi;

        switch (sensitivitySetting) {
            case "slowest":
                mCurrentSensitivity = 0.2f;
                break;
            case "aboveSlowest":
                mCurrentSensitivity = 0.5f;
                break;
            case "default":
                mCurrentSensitivity = 1.0f;
                break;
            case "aboveDefault":
                mCurrentSensitivity = 1.5f;
                break;
            case "fastest":
                mCurrentSensitivity = 2.0f;
                break;
            default:
                mCurrentSensitivity = 1.0f;
                return;
        }

        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {

                int fullscreenType = 0;

                fullscreenType |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    fullscreenType |= View.SYSTEM_UI_FLAG_FULLSCREEN;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    fullscreenType |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }

                getWindow().getDecorView().setSystemUiVisibility(fullscreenType);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_mousepad, menu);

        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> {
            if (!plugin.isKeyboardEnabled()) {
                menu.removeItem(R.id.menu_show_keyboard);
            }
        });

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
        if (mDetector.onTouchEvent(event)) {
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

                BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> {
                    float deltaX = (mCurrentX - mPrevX) * displayDpiMultiplier * mCurrentSensitivity;
                    float deltaY = (mCurrentY - mPrevY) * displayDpiMultiplier * mCurrentSensitivity;

                    // Run the mouse delta through the pointer acceleration profile
                    mPointerAccelerationProfile.touchMoved(deltaX, deltaY, event.getEventTime());
                    mouseDelta = mPointerAccelerationProfile.commitAcceleratedMouseDelta(mouseDelta);

                    plugin.sendMouseDelta(mouseDelta.x, mouseDelta.y);

                    mPrevX = mCurrentX;
                    mPrevY = mCurrentY;
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
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_SCROLL) {
            final float distanceY = e.getAxisValue(MotionEvent.AXIS_VSCROLL);

            accumulatedDistanceY += distanceY;

            if (accumulatedDistanceY > MinDistanceToSendGenericScroll || accumulatedDistanceY < -MinDistanceToSendGenericScroll) {
                sendScroll(accumulatedDistanceY);
                accumulatedDistanceY = 0;
            }
        }

        return super.onGenericMotionEvent(e);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, final float distanceX, final float distanceY) {
        // If only one thumb is used then cancel the scroll gesture
        if (e2.getPointerCount() <= 1) {
            return false;
        }

        isScrolling = true;

        accumulatedDistanceY += distanceY;
        if (accumulatedDistanceY > MinDistanceToSendScroll || accumulatedDistanceY < -MinDistanceToSendScroll) {
            sendScroll(scrollDirection * accumulatedDistanceY);

            accumulatedDistanceY = 0;
        }

        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, MousePadPlugin::sendSingleHold);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, MousePadPlugin::sendSingleClick);
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, MousePadPlugin::sendDoubleClick);
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onTripleFingerTap(MotionEvent ev) {
        switch (tripleTapAction) {
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
        switch (doubleTapAction) {
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
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, MousePadPlugin::sendMiddleClick);
    }

    private void sendRightClick() {
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, MousePadPlugin::sendRightClick);
    }

    private void sendScroll(final float y) {
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> plugin.sendScroll(0, y));
    }

    //TODO: Does not work on KitKat with or without requestFocus()
    private void showKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(this, InputMethodManager.class);
        keyListenerView.requestFocus();
        imm.toggleSoftInputFromWindow(keyListenerView.getWindowToken(), 0, 0);
    }

}

