package com.sonicmax.bloodrogue.objects;

import android.util.Log;

import java.util.ArrayList;

/**
 * Wrapper for GameObject which allows for animations to be displayed in renderer.
 * Frame array is ordered in reverse (so first frame is last)
 */

public class Animation extends GameObject {
    private String TRANSPARENT = "sprites/transparent.png";
    private ArrayList<String> frames;
    private int currentFrame;
    private int length;
    private boolean finished;

    public Animation(int x, int y) {
        super(x, y);
        this.frames = new ArrayList<>();
        this.frames.add(TRANSPARENT);
        this.finished = false;
    }

    public void setFrames(ArrayList<String> frames) {
        this.frames = frames;
        this.length = frames.size();
    }

    public boolean isFinished() {
        return this.finished;
    }

    public String getNextFrame() {
        if (this.frames.size() > 0) {
            return this.frames.remove(0);
        }
        else {
            this.finished = true;
            return TRANSPARENT;
        }
    }
}
