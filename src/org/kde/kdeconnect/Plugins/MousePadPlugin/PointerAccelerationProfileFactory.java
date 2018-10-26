package org.kde.kdeconnect.Plugins.MousePadPlugin;


public class PointerAccelerationProfileFactory {

    /* The simplest profile. Merely adds the mouse deltas without any processing. */
    private static class DefaultProfile extends PointerAccelerationProfile {
        float accumulatedX = 0.0f;
        float accumulatedY = 0.0f;

        @Override
        public void touchMoved(float deltaX, float deltaY, long eventTime) {
            accumulatedX += deltaX;
            accumulatedY += deltaY;
        }

        @Override
        public MouseDelta commitAcceleratedMouseDelta(MouseDelta reusedObject) {
            MouseDelta result;
            if (reusedObject == null) result = new MouseDelta();
            else result = reusedObject;

            result.x = accumulatedX;
            result.y = accumulatedY;
            accumulatedY = 0;
            accumulatedX = 0;
            return result;
        }
    }


    /* Base class for acceleration profiles that takes touch movement speed into account.
     * To calculate the speed, a history of 32 touch events are stored
     * and then later used to calculate the speed. */
    private static abstract class SpeedBasedAccelerationProfile extends PointerAccelerationProfile {
        float accumulatedX = 0.0f;
        float accumulatedY = 0.0f;

        // Higher values will reduce the amount of noise in the speed calculation
        // but will also increase latency until the acceleration kicks in.
        // 150ms seemed like a nice middle ground.
        final long freshThreshold = 150;

        private static class TouchDeltaEvent {
            final float x;
            final float y;
            final long time;
            TouchDeltaEvent(float x, float y, long t) {
                this.x = x;
                this.y = y;
                this.time = t;
            }
        }

        private final TouchDeltaEvent[] touchEventHistory = new TouchDeltaEvent[32];

        /* add an event to the touchEventHistory array, shifting everything else in the array. */
        private void addHistory(float deltaX, float deltaY, long eventTime) {
            System.arraycopy(touchEventHistory, 0, touchEventHistory, 1, touchEventHistory.length - 1);
            touchEventHistory[0] = new TouchDeltaEvent(deltaX, deltaY, eventTime);
        }

        @Override
        public void touchMoved(float deltaX, float deltaY, long eventTime) {

            // To calculate the touch movement speed,
            // we iterate through the touchEventHistory array,
            // adding up the distance moved for each event.
            // Breaks if a touchEventHistory entry is too "stale".
            float distanceMoved = 0.0f;
            long deltaT = 0;
            for (TouchDeltaEvent aTouchEventHistory : touchEventHistory) {
                if (aTouchEventHistory == null) break;
                if (eventTime - aTouchEventHistory.time > freshThreshold) break;

                distanceMoved += (float) Math.sqrt(
                        aTouchEventHistory.x * aTouchEventHistory.x
                                + aTouchEventHistory.y * aTouchEventHistory.y);
                deltaT = eventTime - aTouchEventHistory.time;
            }


            float multiplier;

            if (deltaT == 0) {
                // Edge case when there are no historical touch data to calculate speed from.
                multiplier = 0;
            } else {
                float speed = distanceMoved / (deltaT / 1000.0f); // units: px/sec

                multiplier = calculateMultiplier(speed);
            }

            if (multiplier < 0.01f) multiplier = 0.01f;

            accumulatedX += deltaX * multiplier;
            accumulatedY += deltaY * multiplier;

            addHistory(deltaX, deltaY, eventTime);
        }

        /* Should be overridden by the child class.
         * Given the current touch movement speed, this method should return a multiplier
         * for the touch delta. ( mouse_delta = touch_delta * multiplier ) */
        abstract float calculateMultiplier(float speed);

        @Override
        public MouseDelta commitAcceleratedMouseDelta(MouseDelta reusedObject) {
            MouseDelta result;
            if (reusedObject == null) result = new MouseDelta();
            else result = reusedObject;

            /* This makes sure that only the integer components of the deltas are sent,
             * since the coordinates are converted to integers in the desktop client anyway.
             * The leftover fractional part is stored and added later; this makes
             * the cursor move much smoother in slow speeds. */
            result.x = (int) accumulatedX;
            result.y = (int) accumulatedY;
            accumulatedY = accumulatedY % 1.0f;
            accumulatedX = accumulatedX % 1.0f;
            return result;
        }
    }

    /* Pointer acceleration with mouse_delta = touch_delta * ( touch_speed ^ exponent )
     * It is similar to x.org server's Polynomial pointer acceleration profile. */
    private static class PolynomialProfile extends SpeedBasedAccelerationProfile {
        final float exponent;

        PolynomialProfile(float exponent) {
            this.exponent = exponent;
        }

        @Override
        float calculateMultiplier(float speed) {
            // The value 600 was chosen arbitrarily.
            return (float) (Math.pow(speed / 600, exponent));
        }
    }


    /* Mimics the behavior of xorg's Power profile.
     * Currently not visible to the user since it is rather hard to control. */
    private static class PowerProfile extends SpeedBasedAccelerationProfile {
        @Override
        float calculateMultiplier(float speed) {
            return (float) (Math.pow(2, speed / 1000)) - 1;
        }
    }


    public static PointerAccelerationProfile getProfileWithName(String name) {
        switch (name) {
            case "noacceleration":
                return new DefaultProfile();
            case "weaker":
                return new PolynomialProfile(0.25f);
            case "weak":
                return new PolynomialProfile(0.5f);
            case "medium":
                return new PolynomialProfile(1.0f);
            case "strong":
                return new PolynomialProfile(1.5f);
            case "stronger":
                return new PolynomialProfile(2.0f);
            default:
                return new DefaultProfile();
        }
    }
}
