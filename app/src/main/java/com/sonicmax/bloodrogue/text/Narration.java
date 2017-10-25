package com.sonicmax.bloodrogue.text;

import com.sonicmax.bloodrogue.renderer.TextObject;

/**
 * Wrapper for TextObject that adds support for start/finish durations
 */

public class Narration extends TextObject {
    private final long DEFAULT_DURATION = 5000L;
    private long start;
    private long end;

    public Narration(String text, int row) {
        super(text, row);

        this.start = System.currentTimeMillis();
        this.end = this.start + DEFAULT_DURATION;
    }

    public boolean hasExpired(long currentTime) {
        if (currentTime >= this.end) {
            return true;
        }

        return false;
    }

    public void setRow(int row) {
        this.row = row;
    }
}
