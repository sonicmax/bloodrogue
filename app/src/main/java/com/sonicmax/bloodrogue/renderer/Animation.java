package com.sonicmax.bloodrogue.renderer;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Contains all the information required to display an animation onscreen, with some helper methods
 * to handle timing.
 */

public class Animation implements Serializable {
    public final String TAG = this.getClass().getSimpleName();
    private static final long serialVersionUID = 1L;

    // Shaders
    public static final int DEFAULT = 0;
    public static final int WAVE = 1;

    private final String TRANSPARENT = "sprites/transparent.png";

    private ArrayList<String> frames;
    private int currentFrame;
    private int length;
    private boolean repeating;

    public int x;
    public int y;
    public boolean finished;
    public boolean destroyable; // If true, instance will be destroyed after reaching finished state
    public int type;

    private float elapsedTime; // In milliseconds
    private float timeTilNextFrame; // In milliseconds

    public Animation(int x, int y) {
        this.x = x;
        this.y = y;
        frames = new ArrayList<>();
        finished = false;
        currentFrame = 0;
        length = 0;
        repeating = false;
        destroyable = true;
        timeTilNextFrame = 66.66F;
    }

    public void setRepeating(boolean value) {
        repeating = value;
    }

    public void setDestroyable(boolean value) {
        destroyable = value;
    }

    public void setFrames(ArrayList<String> frames) {
        this.frames = frames;
        length = frames.size();
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFrameLength(float length) {
        timeTilNextFrame = length;
    }

    public void reset() {
        currentFrame = 0;
        elapsedTime = 0;
        finished = false;
    }

    public String getNextFrame(float dt) {
        if (length == 0) {
            // Something went wrong - log & move on
            Log.w(TAG, "Animation initialised with no frames - removing");
            finished = true;
            return TRANSPARENT;
        }

        if (elapsedTime < timeTilNextFrame) {
            elapsedTime += dt;
            return frames.get(currentFrame);
        }

        else if (elapsedTime >= timeTilNextFrame && currentFrame < length - 1) {
            elapsedTime = 0;
            currentFrame++;
            return frames.get(currentFrame);
        }

        else {
            // Ran out of frames to render - check whether we need to repeat animation
            if (this.repeating) {
                reset();
                return frames.get(currentFrame);

            } else {
                // Set finished flag to true so engine can remove animation
                finished = true;
                return TRANSPARENT;
            }
        }
    }
}
