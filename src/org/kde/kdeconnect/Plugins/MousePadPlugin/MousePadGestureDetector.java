/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.view.MotionEvent;
import android.view.ViewConfiguration;


class MousePadGestureDetector {

    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout() + 100;
    private OnGestureListener mGestureListener;

    private long mFirstDownTime = 0;

    private boolean mIsGestureHandled;

    public interface OnGestureListener {

        boolean onTripleFingerTap(MotionEvent ev);

        boolean onDoubleFingerTap(MotionEvent ev);
    }

    MousePadGestureDetector(OnGestureListener gestureListener) {
        if (gestureListener == null) {
            throw new IllegalArgumentException("gestureListener cannot be null");
        }
        mGestureListener = gestureListener;
    }

    boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mIsGestureHandled = false;
                mFirstDownTime = event.getEventTime();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int count = event.getPointerCount();
                if (event.getEventTime() - mFirstDownTime <= TAP_TIMEOUT) {
                    if (count == 3) {
                        if (!mIsGestureHandled) {
                            mIsGestureHandled = mGestureListener.onTripleFingerTap(event);
                        }
                    } else if (count == 2) {
                        if (!mIsGestureHandled) {
                            mIsGestureHandled = mGestureListener.onDoubleFingerTap(event);
                        }
                    }
                }
                mFirstDownTime = 0;
                break;
        }
        return mIsGestureHandled;
    }
}
