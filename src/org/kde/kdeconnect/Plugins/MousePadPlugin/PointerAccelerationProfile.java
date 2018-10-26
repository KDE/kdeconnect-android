package org.kde.kdeconnect.Plugins.MousePadPlugin;

/* Base class for a pointer acceleration profile. */
public abstract class PointerAccelerationProfile {

    /* Class representing a mouse delta, a pair of floats.*/
    static class MouseDelta {
        public float x, y;

        MouseDelta() {
            this(0,0);
        }

        MouseDelta(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /* Touch coordinate deltas are fed through this method. */
    public abstract void touchMoved(float deltaX, float deltaY, long eventTime);

    /* An acceleration profile should 'commit' the processed delta when this method is called.
     * The value returned here will be directly sent to the desktop client.
     *
     * A MouseDelta object can be provided by the caller (or it can be null);
     * if not null, subclasses should use and return this object, to reduce object allocations.*/
    public abstract MouseDelta commitAcceleratedMouseDelta(MouseDelta reusedObject);
}
