package com.sonicmax.bloodrogue.text;

/**
 * Wrapper for TextObject that adds support for start/finish durations
 */

public class Narration extends TextObject {
    private final long DEFAULT_DURATION = 5000L;
    private long start;
    private long end;
    // private float[] color; // RGBA

    public Narration(String text, int row) {
        super(text, row);

        this.start = System.currentTimeMillis();
        this.end = this.start + DEFAULT_DURATION;
        this.color = new float[] {1f, 1f, 1f, 1f};
    }

    public Narration(String text, int row, float[] color) {
        super(text, row, color);

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
