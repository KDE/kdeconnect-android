/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognizerIntent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.Plugins.DigitizerPlugin.DigitizerPlugin;
import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityMousepadBinding;

import java.util.Objects;

import kotlin.Lazy;
import kotlin.LazyKt;

public class MousePadActivity
        extends BaseActivity<ActivityMousepadBinding>
        implements GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener,
        MousePadGestureDetector.OnGestureListener,
        SensorEventListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private String deviceId;

    private final static float MinDistanceToSendScroll = 2.5f; // touch gesture scroll
    private final static float MinDistanceToSendGenericScroll = 0.1f; // real mouse scroll wheel event
    private final static float StandardDpi = 240.0f; // = hdpi
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;

    private float mPrevX;
    private float mPrevY;
    boolean dragging = false;
    private float mCurrentSensitivity;
    private float displayDpiMultiplier;
    private int scrollDirection = 1;
    private double scrollCoefficient = 1.0;
    private boolean allowGyro = false;
    private boolean gyroEnabled = false;
    private boolean doubleTapDragEnabled = false;
    private boolean mouseInputDisabled = false;
    private boolean voiceInputMode = false;
    private boolean voiceButtonClickListenerSet = false;
    private boolean enterButtonClickListenerSet = false;
    private boolean deleteButtonClickListenerSet = false;
    private int gyroscopeSensitivity = 100;
    private boolean isScrolling = false;
    private float accumulatedDistanceY = 0;
    private String lastVoiceInput = "";
    private boolean isDeleteLongPress = false;
    private Handler deleteHandler;
    private Runnable deleteRunnable;

    private GestureDetector mDetector;
    private SensorManager mSensorManager;
    private MousePadGestureDetector mMousePadGestureDetector;
    private PointerAccelerationProfile mPointerAccelerationProfile;

    private PointerAccelerationProfile.MouseDelta mouseDelta = new PointerAccelerationProfile.MouseDelta(); // to be reused on every touch move event

    private KeyListenerView keyListenerView;

    private SharedPreferences prefs = null;

    private boolean prefsApplied = false;

    private final Lazy<ActivityMousepadBinding> lazyBinding = LazyKt.lazy(() -> ActivityMousepadBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityMousepadBinding getBinding() {
        return lazyBinding.getValue();
    }

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
        // Disable gyroscope mouse if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return;
        }

        float[] values = event.values;

        float X = -values[2] * 70 * (gyroscopeSensitivity/100.0f);
        float Y = -values[0] * 70 * (gyroscopeSensitivity/100.0f);

        if (X < 0.25 && X > -0.25) {
            X = 0;
        } else {
            X = X * (gyroscopeSensitivity/100.0f);
        }

        if (Y < 0.25 && Y > -0.25) {
            Y = 0;
        } else {
            Y = Y * (gyroscopeSensitivity/100.0f);
        }

        final float nX = X;
        final float nY = Y;

        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.sendMouseDelta(nX, nY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Initialize delete handler
        deleteHandler = new Handler(Looper.getMainLooper());
        deleteRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDeleteLongPress) {
                    sendSingleBackspace();
                    // Schedule next deletion every 100ms (10 characters per second)
                    deleteHandler.postDelayed(this, 100);
                }
            }
        };

        // Check if mouse input is disabled before setting click listeners
        mouseInputDisabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.mousepad_mouse_input_disabled), false);

        if (!mouseInputDisabled) {
            getBinding().mouseClickLeft.setOnClickListener(v -> sendLeftClick());
            getBinding().mouseClickMiddle.setOnClickListener(v -> sendMiddleClick());
            getBinding().mouseClickRight.setOnClickListener(v -> sendRightClick());
        }

        deviceId = getIntent().getStringExtra("deviceId");

        getWindow().getDecorView().setHapticFeedbackEnabled(true);

        mDetector = new GestureDetector(this, this);
        mMousePadGestureDetector = new MousePadGestureDetector(this);
        mDetector.setOnDoubleTapListener(this);
        mSensorManager = ContextCompat.getSystemService(this, SensorManager.class);

        keyListenerView = getBinding().keyListener;
        keyListenerView.setDeviceId(deviceId);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        applyPrefs();

        //Technically xdpi and ydpi should be handled separately,
        //but since ydpi is usually almost equal to xdpi, only xdpi is used for the multiplier.
        displayDpiMultiplier = StandardDpi / getResources().getDisplayMetrics().xdpi;

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
        applyPrefs();

        if (allowGyro && !gyroEnabled) {
            mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
            gyroEnabled = true;
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

    @Override
    protected void onStop() {
        if (gyroEnabled) {
            mSensorManager.unregisterListener(this);
            gyroEnabled = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_mousepad, menu);

        boolean mouseButtonsEnabled = prefs
                .getBoolean(getString(R.string.mousepad_mouse_buttons_enabled_pref), true);
        boolean digitizerEnabled =
                KdeConnect.getInstance().getDevicePlugin(deviceId, DigitizerPlugin.class) != null;

        menu.findItem(R.id.menu_right_click).setVisible(!mouseButtonsEnabled);
        menu.findItem(R.id.menu_middle_click).setVisible(!mouseButtonsEnabled);

        // Show voice input menu item only if voice recognition is available
        menu.findItem(R.id.menu_voice_input).setVisible(isVoiceRecognitionAvailable());
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
        } else if (id == R.id.menu_voice_input) {
            startVoiceRecognition();
            return true;
        } else if (id == R.id.menu_show_keyboard) {
            MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
            if (plugin == null) {
                finish();
                return true;
            }
            if (plugin.isKeyboardEnabled()) {
                toggleKeyboard();
            } else {
                Toast toast = Toast.makeText(this, R.string.mousepad_keyboard_input_not_supported, Toast.LENGTH_SHORT);
                toast.show();
            }
            return true;
        } else if (id == R.id.menu_open_compose_send) {
            MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
            if (plugin == null) {
                finish();
                return true;
            }
            if (plugin.isKeyboardEnabled()) {
                showCompose();
            } else {
                Toast toast = Toast.makeText(this, R.string.mousepad_keyboard_input_not_supported, Toast.LENGTH_SHORT);
                toast.show();
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If mouse input is disabled OR voice input mode is enabled, only allow keyboard input
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            // Only process touch events for the keyboard view
            return false;
        }

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
                float mCurrentX = event.getX();
                float mCurrentY = event.getY();

                MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
                if (plugin == null) {
                    finish();
                    return true;
                }

                float deltaX = (mCurrentX - mPrevX) * displayDpiMultiplier * mCurrentSensitivity;
                float deltaY = (mCurrentY - mPrevY) * displayDpiMultiplier * mCurrentSensitivity;

                // Run the mouse delta through the pointer acceleration profile
                mPointerAccelerationProfile.touchMoved(deltaX, deltaY, event.getEventTime());
                mouseDelta = mPointerAccelerationProfile.commitAcceleratedMouseDelta(mouseDelta);

                plugin.sendMouseDelta(mouseDelta.x, mouseDelta.y);

                mPrevX = mCurrentX;
                mPrevY = mCurrentY;

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
        // Disable mouse scroll if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return super.onGenericMotionEvent(e);
        }

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
        // Disable mouse scroll if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return false;
        }

        // If only one thumb is used then cancel the scroll gesture
        if (e2.getPointerCount() <= 1) {
            return false;
        }

        isScrolling = true;

        accumulatedDistanceY += distanceY * scrollCoefficient;
        if (accumulatedDistanceY > MinDistanceToSendScroll || accumulatedDistanceY < -MinDistanceToSendScroll) {
            sendScroll(scrollDirection * accumulatedDistanceY);

            accumulatedDistanceY = 0;
        }

        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        // Disable long press drag if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return;
        }

        if (!doubleTapDragEnabled) {
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
            if (plugin == null) {
                finish();
                return;
            }
            plugin.sendSingleHold();
            dragging = true;
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        // Disable mouse clicks if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return false;
        }

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
        // Disable mouse double-click if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return false;
        }

        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return true;
        }
        if (doubleTapDragEnabled) {
            plugin.sendSingleHold();
            dragging = true;
        } else {
            plugin.sendDoubleClick();
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onTripleFingerTap(MotionEvent ev) {
        // Disable mouse clicks if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return false;
        }

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
        // Disable mouse clicks if mouse input is disabled OR voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;
        if (effectiveMouseInputDisabled) {
            return false;
        }

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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (prefsApplied) prefsApplied = false;
    }


    private void sendLeftClick() {
        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        if (dragging) {
            plugin.sendSingleRelease();
            dragging = false;
        } else {
            plugin.sendLeftClick();
        }
    }

    private void sendMiddleClick() {
        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.sendMiddleClick();
    }

    private void sendRightClick() {
        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.sendRightClick();
    }

    private void sendEnterKey() {
        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.sendSelect();
    }

    private void deleteLastVoiceInput() {
        if (lastVoiceInput != null && !lastVoiceInput.isEmpty()) {
            // Send backspace characters to delete the last voice input
            String backspaces = new String(new char[lastVoiceInput.length()]).replace('\0', '\b');
            if (keyListenerView != null) {
                keyListenerView.sendChars(backspaces);
            }
            lastVoiceInput = ""; // Clear the stored voice input
        }
    }

    private void sendSingleBackspace() {
        if (keyListenerView != null) {
            keyListenerView.sendChars("\b");
        }
    }

    private void sendScroll(final float y) {
        MousePadPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.sendScroll(0, y);
    }

    private void toggleKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(this, InputMethodManager.class);
        keyListenerView.requestFocus();
        imm.toggleSoftInputFromWindow(keyListenerView.getWindowToken(), 0, 0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(this, InputMethodManager.class);
        imm.hideSoftInputFromWindow(keyListenerView.getWindowToken(), 0);
    }


    private void showCompose() {
        Intent intent = new Intent(this, ComposeSendActivity.class);
        intent.putExtra("org.kde.kdeconnect.Plugins.MousePadPlugin.deviceId", deviceId);
        startActivity(intent);
    }

    private void applyPrefs() {
        if (prefsApplied) return;

        if (prefs.getBoolean(getString(R.string.mousepad_scroll_direction), false)) {
            scrollDirection = -1;
        } else {
            scrollDirection = 1;
        }

        int scrollSensitivity = prefs.getInt(getString(R.string.mousepad_scroll_sensitivity), 100);
        if (scrollSensitivity == 0) scrollSensitivity = 1;
        scrollCoefficient = Math.pow((scrollSensitivity / 100f), 1.5);

        allowGyro = isGyroSensorAvailable() && prefs.getBoolean(getString(R.string.gyro_mouse_enabled), false);
        if (allowGyro) gyroscopeSensitivity = prefs.getInt(getString(R.string.gyro_mouse_sensitivity), 100);

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

        if (prefs.getBoolean(getString(R.string.mousepad_mouse_buttons_enabled_pref), true)) {
            getBinding().mouseButtons.setVisibility(View.VISIBLE);
        } else {
            getBinding().mouseButtons.setVisibility(View.GONE);
        }

        doubleTapDragEnabled = prefs.getBoolean(getString(R.string.mousepad_doubletap_drag_enabled_pref), true);

        mouseInputDisabled = prefs.getBoolean(getString(R.string.mousepad_mouse_input_disabled), false);

        // Hide mouse buttons if mouse input is disabled
        if (mouseInputDisabled) {
            getBinding().mouseButtons.setVisibility(View.GONE);
            getBinding().mousepadInfoText.setText(R.string.mousepad_info_mouse_disabled);
        } else {
            getBinding().mousepadInfoText.setText(R.string.mousepad_info);
        }

        voiceInputMode = prefs.getBoolean(getString(R.string.mousepad_voice_input_mode), false);

        // Automatically disable mouse input when voice input mode is enabled
        boolean effectiveMouseInputDisabled = mouseInputDisabled || voiceInputMode;

        // Show or hide the voice input button based on the setting
        if (voiceInputMode && isVoiceRecognitionAvailable()) {
            getBinding().voiceInputButton.setVisibility(View.VISIBLE);
        } else {
            getBinding().voiceInputButton.setVisibility(View.GONE);
        }

        // Show or hide the enter button based on the voice input mode
        if (voiceInputMode) {
            getBinding().enterKeyButton.setVisibility(View.VISIBLE);
            getBinding().deleteLastInputButton.setVisibility(View.VISIBLE);
        } else {
            getBinding().enterKeyButton.setVisibility(View.GONE);
            getBinding().deleteLastInputButton.setVisibility(View.GONE);
        }

        // Hide mouse buttons if mouse input is disabled OR voice input mode is enabled
        if (effectiveMouseInputDisabled) {
            getBinding().mouseButtons.setVisibility(View.GONE);
            getBinding().mousepadInfoText.setText(R.string.mousepad_info_mouse_disabled);
        } else {
            getBinding().mouseButtons.setVisibility(View.VISIBLE);
            getBinding().mousepadInfoText.setText(R.string.mousepad_info);
        }

        // Set up the voice input button click listener if voice mode is enabled
        if (voiceInputMode && !voiceButtonClickListenerSet) {
            getBinding().voiceInputButton.setOnClickListener(v -> {
                startVoiceRecognition();
            });
            voiceButtonClickListenerSet = true;
        }

        // Set up the enter button click listener if voice mode is enabled
        if (voiceInputMode && !enterButtonClickListenerSet) {
            getBinding().enterKeyButton.setOnClickListener(v -> {
                sendEnterKey();
            });
            enterButtonClickListenerSet = true;
        }

        // Set up the delete button click and long-press listeners if voice mode is enabled
        if (voiceInputMode && !deleteButtonClickListenerSet) {
            getBinding().deleteLastInputButton.setOnClickListener(v -> {
                // Handle quick click - delete last voice input
                deleteLastVoiceInput();
            });

            getBinding().deleteLastInputButton.setOnLongClickListener(v -> {
                isDeleteLongPress = true;
                // First delete any remaining voice input
                deleteLastVoiceInput();
                // Start continuous deletion after 1 second
                deleteHandler.postDelayed(deleteRunnable, 1000);
                return true; // Consume the long click event
            });

            getBinding().deleteLastInputButton.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    // Stop the continuous deletion when finger is lifted
                    isDeleteLongPress = false;
                    deleteHandler.removeCallbacks(deleteRunnable);
                }
                return false; // Let other touch events be processed normally
            });

            deleteButtonClickListenerSet = true;
        }

        prefsApplied = true;
    }

    private boolean isGyroSensorAvailable() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
    }

    private boolean isVoiceRecognitionAvailable() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        return activities.size() > 0;
    }

    private void startVoiceRecognition() {
        if (!isVoiceRecognitionAvailable()) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to send text");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start voice recognition", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);

                // Store the last voice input for potential deletion
                lastVoiceInput = spokenText;

                // Send the spoken text through the KeyListenerView
                if (keyListenerView != null) {
                    keyListenerView.sendChars(spokenText);
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        hideKeyboard();
        super.onBackPressed();
        return true;
    }
}

