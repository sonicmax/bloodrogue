package com.sonicmax.bloodrogue.renderer;

/**
 * Silly class to aid in counting frames for animations/etc.
 * Todo: this should really be counting time, not frames
 */

public class FrameCounter {
    private int frameCount;
    private int countLimit;

    public FrameCounter(int limit) {
        frameCount = 0;
        countLimit = limit;
    }

    public void setLimit(int limit) {
        countLimit = limit;
    }

    public boolean tickAndCheckCount() {
        frameCount++;

        if (frameCount > countLimit) {
            frameCount = 0;
            return true;
        }
        else {
            return false;
        }
    }
}
