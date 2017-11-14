package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities require at least a sprite component to be visible to player. Typically contains position,
 *  a sprite path, and some other data to aid in rendering
 */

public class Sprite extends Component {
    // Tells game how to render sprite.
    public static int NONE = -1; // When we want to hide sprite
    public static int STATIC = 0; // Sprite never changes or moves position
    public static int DYNAMIC = 1; // Sprite can change and move
    public static int WAVE = 2; // Same as dynamic, but rendered using wave shader
    public static int ANIMATION = 3; // Todo: animation support

    public String path;
    public int spriteIndex;
    public int shader;

    public int x;
    public int y;

    // Fields used to calculate sliding movement
    public int lastX;
    public int lastY;
    public int movementStep;

    public Sprite(long id) {
        super(id);
        this.path = "sprites/transparent.png";
        this.spriteIndex = -1;
        this.lastX = -1;
        this.lastY = -1;
    }
}
