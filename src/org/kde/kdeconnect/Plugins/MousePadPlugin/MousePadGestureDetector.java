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
