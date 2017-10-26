package com.sonicmax.bloodrogue.renderer.text;

public class Status extends TextObject {
    private final int SCROLL_STEP_MAX = 30;
    private int scrollStep;

    public Status(String text, float x, float y, float[] color) {
        super(text, x, y, color);
        this.scale = 2f;
    }

    public float advanceScroll() {
        if (this.scrollStep >= SCROLL_STEP_MAX) {
            // References to object will be removed if this method returns 1
            return 1;
        }

        scrollStep++;

        // Find fraction that we should move by
        float fraction = 1f / SCROLL_STEP_MAX * scrollStep;
        // Return squared value to provide simple easing effect on movement
        return (fraction * fraction);
    }

}
