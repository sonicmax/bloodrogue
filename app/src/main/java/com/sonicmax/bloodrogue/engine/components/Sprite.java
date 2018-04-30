package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.renderer.Animation;

/**
 *  Entities require at least a sprite component to be visible to player. Typically contains position,
 *  a sprite path, and some other data to aid in rendering
 */

public class Sprite extends Component {
    // Tells game how to render sprite.
    public static final int NONE = -1; // When we want to hide sprite
    public static final int STATIC = 0; // Sprite never changes or moves position
    public static final int DYNAMIC = 1; // Sprite can change and move
    public static final int WAVE = 2; // Same as dynamic, but rendered using wave renderState
    public static final int ANIMATION = 3; // Todo: animation support

    public static final int BACKGROUND = 0;
    public static final int FOREGROUND = 1;

    // Animation states
    public static final int NO_ANIMATION = 0;
    public static final int IDLE_ANIMATION = 1;
    public static final int HIT_ANIMATION = 2;

    public String path;
    public int spriteIndex;
    public int renderState;

    public boolean hasIdleAnimation;
    public Animation idleAnimation;

    public boolean hasHitAnimation;
    public Animation hitAnimation;

    // Allows support for an overlay to be displayed over original sprite
    // (eg. equipped weapon or armour)
    public String overlayPath;
    public int overlayIndex;
    public int overlayRenderState;

    // An overlay for graphical effects (eg. we may want a transparent zLayer using wave renderState
    // rendered on top of existing sprites)
    public String effectPath;
    public int effectIndex;
    public int effectRenderState;

    public int x;
    public int y;

    // Fields used to calculate sliding movement
    public int lastX;
    public int lastY;
    public int movementStep;

    public int currentAnimationState;
    public int nextAnimationState;

    public int zLayer;
    public boolean wrapToCube;
    public boolean dirty;

    public Sprite(long id) {
        super(id);

        path = "sprites/transparent.png";
        spriteIndex = -1;
        renderState = Sprite.NONE;

        hasIdleAnimation = false;
        idleAnimation = null;
        hasHitAnimation = false;
        hitAnimation = null;

        overlayPath = null;
        overlayIndex = -1;
        overlayRenderState = Sprite.NONE;

        effectPath = null;
        effectIndex = -1;
        effectRenderState = Sprite.NONE;

        currentAnimationState = NO_ANIMATION;
        nextAnimationState = NO_ANIMATION;

        lastX = -1;
        lastY = -1;

        zLayer = Sprite.FOREGROUND;
        wrapToCube = true;
        dirty = false;
    }

    /**
     *  Creates clone of Sprite passed into constructor.
     */

    public Sprite(Sprite sprite) {
        super(sprite.id);
        x = sprite.x;
        y = sprite.y;
        path = sprite.path;
        renderState = sprite.renderState;
        spriteIndex = sprite.spriteIndex;
        overlayPath = sprite.overlayPath;
        overlayIndex = sprite.overlayIndex;
        overlayRenderState = sprite.overlayRenderState;
        effectPath = sprite.effectPath;
        effectIndex = sprite.effectIndex;
        effectRenderState = sprite.effectRenderState;
        lastX = sprite.lastX;
        lastY = sprite.lastY;
        movementStep = sprite.movementStep;
        zLayer = sprite.zLayer;
    }

    /**
     * We will determine Sprite components to be equal if they belong to the same entity.
     *
     *
     * @param object Object to compare
     * @return Whether object is equal to this
     */

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Sprite)) {
            return false;
        }

        Sprite sprite = (Sprite) object;
        return (sprite.id == this.id);
    }
}
