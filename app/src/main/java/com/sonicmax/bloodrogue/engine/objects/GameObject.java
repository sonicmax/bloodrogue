package com.sonicmax.bloodrogue.engine.objects;

import com.sonicmax.bloodrogue.utils.maths.Vector2D;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;

public class GameObject implements Serializable {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT = 0;
    public static final int FLOOR = 1;
    public static final int DOORWAY = 2;
    public static final int WALL = 3;
    public static final int BORDER = 4;

    public final String id;
    public String name;
    public int type;

    public int x;
    public int y;

    public String sprite;
    public int spriteIndex;
    public ArrayList<String> sprites;
    public int animationLength;
    public int currentFrame;
    public int renderCount;
    public int movementStep;
    public Vector2D lastMove;

    public boolean hasDeathAnimation;
    public GameObject deathAnimation;

    public boolean isProjected;
    public int fovX;
    public int fovY;

    public boolean isBlocking;
    public boolean isTraversable;
    public boolean canInteract;
    public boolean hasAction;
    public boolean isStationary;
    public boolean isImmutable;
    public boolean isPlayerControlled;
    public boolean canSelfReplicate;
    public float chanceToSelfReplicate;
    public boolean isGasOrLiquid;
    public boolean activateOnCollide;
    public boolean activateOnMove;

    // Door/container component
    public String openTile;
    public String closedTile;
    public boolean open;
    public boolean empty;
    public ArrayList<GameObject> contents;

    // AI component
    public int dijkstra;
    public int playerInterest;
    public int state;
    public ArrayList<Vector2D> path;

    public GameObject() {
        this.id = UUID.randomUUID().toString(); // Still want unique id for cloned object
    }

    public GameObject(int x, int y) {
        this.x = x;
        this.y = y;

        this.id = UUID.randomUUID().toString();
        this.name = this.getClass().getSimpleName();
        this.type = GameObject.DEFAULT;

        // Set default values for object fields
        this.sprite = "sprites/transparent.png";
        this.spriteIndex = -1;
        this.sprites = new ArrayList<>();
        this.animationLength = 0;
        this.currentFrame = 0;
        this.renderCount = 0;
        this.movementStep = 0;
        this.lastMove = null;

        this.hasDeathAnimation = false;
        this.deathAnimation = null;

        this.isProjected = false;
        this.fovX = 0;
        this.fovY = 0;

        this.isBlocking = true;
        this.isTraversable = false;
        this.canInteract = false;
        this.hasAction = false;
        this.isStationary = true;
        this.isImmutable = true;
        this.isPlayerControlled = false;
        this.canSelfReplicate = false;
        this.chanceToSelfReplicate = 0f;
        this.isGasOrLiquid = false;
        this.activateOnCollide = false;
        this.activateOnMove = false;

        this.dijkstra = 0;
        this.playerInterest = 0;
        this.state = 0;
        this.path = null;

    }

    /**
     *  Creates clone of original object at specified x,y coords.
     */

    public GameObject(int x, int y, GameObject original) {
        this.x = x;
        this.y = y;
        this.id = UUID.randomUUID().toString(); // Still want unique id for cloned object

        this.sprite = original.sprite;
        this.spriteIndex = original.spriteIndex;
        this.sprites = original.sprites;
        this.animationLength = original.sprites.size();
        this.currentFrame = 0;
        this.renderCount = 0;
        this.movementStep = 0;
        this.lastMove = null;

        this.hasDeathAnimation = original.hasDeathAnimation;
        this.deathAnimation = original.deathAnimation;

        this.isProjected = false;
        this.fovX = 0;
        this.fovY = 0;

        this.isBlocking = original.isBlocking;
        this.isTraversable = original.isTraversable;
        this.canInteract = original.canInteract;
        this.hasAction = original.hasAction;
        this.isStationary = original.isStationary;
        this.isImmutable = original.isImmutable;
        this.isPlayerControlled = false;
        this.canSelfReplicate = original.canSelfReplicate;
        this.chanceToSelfReplicate = original.chanceToSelfReplicate; // todo: maybe lower this for clones?
        this.isGasOrLiquid = original.isGasOrLiquid;
        this.activateOnCollide = original.activateOnCollide;
        this.activateOnMove = original.activateOnMove;

        this.dijkstra = original.dijkstra;
        this.playerInterest = original.playerInterest;
        this.state = original.state;
        this.path = null;
    }


    /**
     *  Returns unique ID created in object constructor.
     */

    public String getId() {
        return this.id;
    }


    /**
     *  Retrieves sprite used to render object.
     *  Most objects will only have a single sprite, while others will be animated
     */

    public String getSprite() {
        return this.sprite;
    }

    public boolean hasAnimation() {
        return this.animationLength > 0;
    }

    public String getSprite(float dt) {
        if (this.renderCount < 4) {
            this.renderCount++;
            return this.sprites.get(currentFrame);
        }
        else if (this.renderCount >= 3 && currentFrame < animationLength - 1) {
            this.renderCount = 0;
            currentFrame++;
            return this.sprites.get(currentFrame);
        }
        else {
            // Reset frame index
            currentFrame = 0;
            this.renderCount = 0;
            return this.sprites.get(currentFrame);
        }
    }

    public void setSprite(String sprite) {
        this.sprite = sprite;
    }

    public void setSprites(ArrayList<String> sprites) {
        this.sprites = sprites;
        this.animationLength = sprites.size();
    }


    /**
     *  Returns name to be used for in-game text references (eg. status text)
     *  Uses class name as default
     */

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     *  Allows us to display specific animation after object has been destroyed
     */

    public boolean hasDeathAnimation() {
        return this.hasDeathAnimation;
    }

    public void setDeathAnimation(GameObject animation) {
        this.deathAnimation = animation;
        this.hasDeathAnimation = true;
    }

    public GameObject getDeathAnimation() {
        return this.deathAnimation;
    }


    /** Position and movement. */

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public void move(Vector2D newPos) {
        this.x = newPos.x();
        this.y = newPos.y();
    }

    public Vector2D getVector() {
        return new Vector2D(this.x, this.y);
    }


    /** Objects with isProjected flag should only be rendered when [fovX, fovY] is in player field of vision */

    public int getFovX() {
        return this.fovX;
    }

    public void setFovX(int x) {
        this.fovX = x;
    }

    public int getFovY() {
        return this.fovY;
    }

    public void setFovY(int y) {
        this.fovY = y;
    }

    public void setProjected(boolean value) {
        this.isProjected = value;
    }

    public boolean isProjected() {
        return this.isProjected;
    }


    /** Entities can only move across traversable objects.
     Non-traversable objects prevent movement, but do not block sight. */

    public void setTraversable(boolean value) {
        this.isTraversable = value;
    }

    public boolean isTraversable() {
        return isTraversable;
    }


    /** Blocking objects will block field of vision, but may still allow movement. */

    public void setBlocking(boolean value) {
        this.isBlocking = value;
    }

    public boolean isBlocking() {
        return isBlocking;
    }


    /** Interactive objects can interact with certain types of object (doors, chests, etc). */

    public void setInteractive(boolean value) {
        this.canInteract = value;
    }

    public boolean canInteract() {
        return this.canInteract;
    }


    /** Dijkstra value is default value used when generating desire maps. */

    public void setDijkstra(int dijkstra) {
        this.dijkstra = dijkstra;
    }

    public int getDijkstra() {
        return this.dijkstra;
    }


    /**
     * This method is called whenever another object collides with this one.
     * Currently, only non-traversable objects are collidable.
     * Returns -1 if nothing happens.
     */

    public int collide(GameObject object) {
        return -1;
    }


    /** Methods for path-finding and storing object state */

    // todo: this is a real mess

    public void setHasAction(boolean value) {
        this.hasAction = value;
    }

    public boolean hasAction() {
        return this.hasAction;
    }

    public void setPlayerInterest(int value) {
        this.playerInterest = value;
    }

    public int getPlayerInterest() {
        return this.playerInterest;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getState() {
        return this.state;
    }

    public ArrayList<Vector2D> getPath() {
        return this.path;
    }

    public void setPath(ArrayList<Vector2D> path) {
        this.path = path;
    }

    public Vector2D removeFromPath(int i) {
        return this.path.remove(i);
    }

    public boolean isStationary() {
        return this.isStationary;
    }

    public boolean isImmutable() {
        return this.isImmutable;
    }

    public void setStationary(boolean value) {
        this.isStationary = value;
    }

    public void setMutability(boolean value) {
        // if object is mutable then we set isImmutable to inverse of value
        this.isImmutable = !value;
    }

    public void setLastMove(Vector2D last) {
        this.lastMove = last;
    }

    public Vector2D getlastMove() {
        return this.lastMove;
    }

    public float advanceMovement() {
        if (movementStep >= 10) {
            this.movementStep = 0;
            return 1;
        }

        movementStep++;

        // Find fraction that we should move by
        float fraction = 1f / 11 * movementStep;
        // Return squared value to provide simple easing effect on movement
        return (fraction * fraction);
    }

    /**
     *  Indicates whether object is currently being controlled by the player.
     *  Generally this will only apply to the player character, but it will be possible to
     *  control enemies and other objects using spells/powers
     */

    public boolean isPlayerControlled() {
        return this.isPlayerControlled;
    }

    public void setPlayerControl(boolean value) {
        this.isPlayerControlled = value;
    }

    /**
     *  Some objects are able to self-replicate. If random float between 0 and 1 is less than
     *  chance to self-replicate, then we attempt to clone object to a free adjacent space.
     *  Cloned object will retain the properties of the original.
     */

    public float getSelfReplicateChance() {
        return this.chanceToSelfReplicate;
    }

    public void setSelfReplicationChance(float chance) {
        this.chanceToSelfReplicate = chance;
    }

    /**
     *  Objects are presumed solid, unless this flag is set. Gases/liquids are rendered using
     *  a wave shader and have different properties to solid objects
     */

    public void setGasOrLiquid(boolean value) {
        this.isGasOrLiquid = value;
    }

    public boolean isGasOrLiquid() {
        return this.isGasOrLiquid;
    }

    /**
     *  Some objects are activated after colliding, while others are activated after moving onto
     *  their tile. (it's not possible to combine these effects)
     */

    public boolean activateOnCollide() {
        return this.activateOnCollide;
    }

    public void setActivateOnCollide(boolean value) {
        this.activateOnCollide = value;
    }

    public boolean activateOnMove() {
        return this.activateOnMove;
    }

    public void setActivateOnMove(boolean value) {
        this.activateOnMove = value;
    }
}
