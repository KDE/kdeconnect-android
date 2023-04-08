/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.hardware.Sensor;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import androidx.preference.PreferenceManager;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.util.Objects;

public class MousePadActivity extends AppCompatActivity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, MousePadGestureDetector.OnGestureListener, SensorEventListener {
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
    private boolean allowGyro = false;
    private boolean gyroEnabled = false;
    private boolean isScrolling = false;
    private float accumulatedDistanceY = 0;

    private GestureDetector mDetector;
    private SensorManager mSensorManager;
    private MousePadGestureDetector mMousePadGestureDetector;
    private PointerAccelerationProfile mPointerAccelerationProfile;

    private PointerAccelerationProfile.MouseDelta mouseDelta; // to be reused on every touch move event

    private KeyListenerView keyListenerView;

    private SharedPreferences prefs = null;

    enum ClickType {
        LEFT, RIGHT, MIDDLE, NONE;

        static ClickType fromString(String s) {
            switch (s) {
                case "left":
                    return LEFT;
                case "right":
                    return RIGHT;
                case "middle":
                    return MIDDLE;
                default:
                    return NONE;
            }
        }
    }

    private ClickType singleTapAction, doubleTapAction, tripleTapAction;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values;

        float X = -values[2] * 70 * mCurrentSensitivity * displayDpiMultiplier;
        float Y = -values[0] * 70 * mCurrentSensitivity * displayDpiMultiplier;

        if (X < 0.25 && X > -0.25) {
            X = 0;
        } else {
            X = X * mCurrentSensitivity * displayDpiMultiplier;
        }

        if (Y < 0.25 && Y > -0.25) {
            Y = 0;
        } else {
            Y = Y * mCurrentSensitivity * displayDpiMultiplier;
        }

        final float nX = X;
        final float nY = Y;

        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> {
                plugin.sendMouseDelta(nX, nY);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mousepad);

        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        deviceId = getIntent().getStringExtra("deviceId");

        getWindow().getDecorView().setHapticFeedbackEnabled(true);

        mDetector = new GestureDetector(this, this);
        mMousePadGestureDetector = new MousePadGestureDetector(this);
        mDetector.setOnDoubleTapListener(this);
        mSensorManager = ContextCompat.getSystemService(this, SensorManager.class);

        keyListenerView = findViewById(R.id.keyListener);
        keyListenerView.setDeviceId(deviceId);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getBoolean(getString(R.string.mousepad_scroll_direction), false)) {
            scrollDirection = -1;
        } else {
            scrollDirection = 1;
        }
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                && prefs.getBoolean(getString(R.string.gyro_mouse_enabled), false)) {
            allowGyro = true;
        }
        String singleTapSetting = prefs.getString(getString(R.string.mousepad_single_tap_key),
                getString(R.string.mousepad_default_single));
        String doubleTapSetting = prefs.getString(getString(R.string.mousepad_double_tap_key),
                getString(R.string.mousepad_default_double));
        String tripleTapSetting = prefs.getString(getString(R.string.mousepad_triple_tap_key),
                getString(R.string.mousepad_default_triple));
        String sensitivitySetting = prefs.getString(getString(R.string.mousepad_sensitivity_key),
                getString(R.string.mousepad_default_sensitivity));

        String accelerationProfileName = prefs.getString(getString(R.string.mousepad_acceleration_profile_key),
                getString(R.string.mousepad_default_acceleration_profile));

        mPointerAccelerationProfile = PointerAccelerationProfileFactory.getProfileWithName(accelerationProfileName);

        singleTapAction = ClickType.fromString(singleTapSetting);
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
                fullscreenType |= View.SYSTEM_UI_FLAG_FULLSCREEN;
                fullscreenType |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

                getWindow().getDecorView().setSystemUiVisibility(fullscreenType);
            }
        });
    }

    @Override
    protected void onResume() {
        if (allowGyro && !gyroEnabled) {
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
            gyroEnabled = true;
        }

        if (prefs.getBoolean(getString(R.string.mousepad_mouse_buttons_enabled_pref), true)) {
            findViewById(R.id.mouse_buttons).setVisibility(View.VISIBLE);
            findViewById(R.id.mouse_click_left).setOnClickListener(v -> sendLeftClick());
            findViewById(R.id.mouse_click_middle).setOnClickListener(v -> sendMiddleClick());
            findViewById(R.id.mouse_click_right).setOnClickListener(v -> sendRightClick());
        } else {
            findViewById(R.id.mouse_buttons).setVisibility(View.GONE);
        }
        invalidateMenu();

        super.onResume();
    }

    @Override
    protected void onPause() {
        if (gyroEnabled) {
            mSensorManager.unregisterListener(this);
            gyroEnabled = false;
        }
        super.onPause();
    }

    @Override protected void onStop() {
        if (gyroEnabled) {
            mSensorManager.unregisterListener(this);
            gyroEnabled = false;
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_mousepad, menu);

        boolean mouseButtonsEnabled = prefs
                .getBoolean(getString(R.string.mousepad_mouse_buttons_enabled_pref), true);
        menu.findItem(R.id.menu_right_click).setVisible(!mouseButtonsEnabled);
        menu.findItem(R.id.menu_middle_click).setVisible(!mouseButtonsEnabled);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_right_click) {
            sendRightClick();
            return true;
        } else if (id == R.id.menu_middle_click) {
            sendMiddleClick();
            return true;
        } else if (id == R.id.menu_open_mousepad_settings) {
            Intent intent = new Intent(this, PluginSettingsActivity.class)
                    .putExtra(PluginSettingsActivity.EXTRA_DEVICE_ID, deviceId)
                    .putExtra(PluginSettingsActivity.EXTRA_PLUGIN_KEY, MousePadPlugin.class.getSimpleName());
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_show_keyboard) {
            BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> {
                if (plugin.isKeyboardEnabled()) {
                    showKeyboard();
                } else {
                    Toast toast = Toast.makeText(this, R.string.mousepad_keyboard_input_not_supported, Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
            return true;
        } else if (id == R.id.menu_open_compose_send) {
            BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, plugin -> {
                if (plugin.isKeyboardEnabled()) {
                    showCompose();
                } else {
                    Toast toast = Toast.makeText(this, R.string.mousepad_keyboard_input_not_supported, Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
            return true;
        } else {
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
        switch (singleTapAction) {
            case LEFT:
                sendLeftClick();
                break;
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
            case LEFT:
                sendLeftClick();
                break;
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
            case LEFT:
                sendLeftClick();
                break;
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


    private void sendLeftClick() {
        BackgroundService.RunWithPlugin(this, deviceId, MousePadPlugin.class, MousePadPlugin::sendLeftClick);
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

    private void showCompose() {
        Intent intent = new Intent(this, ComposeSendActivity.class);
        intent.putExtra("org.kde.kdeconnect.Plugins.MousePadPlugin.deviceId", deviceId);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}

