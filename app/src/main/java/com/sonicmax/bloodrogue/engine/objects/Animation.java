package com.sonicmax.bloodrogue.engine.objects;

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
    private int renderCount;
    private boolean repeating;

    public Animation(int x, int y) {
        super(x, y);
        this.frames = new ArrayList<>();
        this.frames.add(TRANSPARENT);
        this.finished = false;
        this.currentFrame = 0;
        this.length = 0;
        this.repeating = false;
    }

    public void setRepeating(boolean value) {
        this.repeating = value;
    }

    public void setFrames(ArrayList<String> frames) {
        this.frames = frames;
        this.length = frames.size();
    }

    public boolean isFinished() {
        return this.finished;
    }

    // Todo: this will do for now. but we should be timing animations probably
    public String getNextFrame() {
        if (this.renderCount < 4) {
            this.renderCount++;
            return this.frames.get(currentFrame);
        }
        else if (this.renderCount >= 3 && currentFrame < length - 1) {
            this.renderCount = 0;
            currentFrame++;
            return this.frames.get(currentFrame);
        }
        else {
            if (this.repeating) {
                // Reset frame index
                currentFrame = 0;
                this.renderCount = 0;
                return this.frames.get(currentFrame);

            } else {
                // Set finished flag to true so engine can remove animation
                this.finished = true;
                return TRANSPARENT;
            }
        }
    }
}
