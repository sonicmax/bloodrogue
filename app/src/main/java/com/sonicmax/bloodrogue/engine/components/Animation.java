package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

import java.util.ArrayList;

/**
 *  Entities with animation component will cycle through frames when being rendered.
 *  If oneShot flag is set to true, then entity will be destroyed after animation completes
 */

public class Animation extends Component {
    // public final String TAG = this.getClass().getSimpleName();
    public ArrayList<String> sprites;
    public int animationLength;
    public int currentFrame;
    public int renderCount;
    public boolean oneShot;
    public boolean finished;

    public Animation(long id) {
        super(id);
    }
}
