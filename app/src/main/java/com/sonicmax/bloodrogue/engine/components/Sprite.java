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

    // Allows support for an overlay to be displayed over original sprite
    // (eg. equipped weapon or armour)
    public String overlayPath;
    public int overlayIndex;
    public int overlayShader;

    // An overlay for graphical effects (eg. we may want a transparent layer using wave shader
    // rendered on top of existing sprites)
    public String effectPath;
    public int effectIndex;
    public int effectShader;

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
        this.shader = Sprite.NONE;

        this.overlayPath = null;
        this.overlayIndex = -1;
        this.overlayShader = Sprite.NONE;

        this.effectPath = null;
        this.effectIndex = -1;
        this.effectShader = Sprite.NONE;

        this.lastX = -1;
        this.lastY = -1;
    }

    /**
     *  Creates clone of Sprite passed into constructor.
     */

    public Sprite(Sprite sprite) {
        super(sprite.id);
        this.x = sprite.x;
        this.y = sprite.y;
        this.path = sprite.path;
        this.shader = sprite.shader;
        this.spriteIndex = sprite.spriteIndex;
        this.overlayPath = sprite.overlayPath;
        this.overlayIndex = sprite.overlayIndex;
        this.effectPath = sprite.effectPath;
        this.effectIndex = sprite.effectIndex;
        this.lastX = sprite.lastX;
        this.lastY = sprite.lastY;
        this.movementStep = sprite.movementStep;
    }
}
