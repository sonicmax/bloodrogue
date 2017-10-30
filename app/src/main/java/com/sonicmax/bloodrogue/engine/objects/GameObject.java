package com.sonicmax.bloodrogue.engine.objects;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;
import java.util.UUID;

public class GameObject {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final String id;
    private String name;

    private int x;
    private int y;
    private int fovX;
    private int fovY;

    // Default values
    private int dijkstra = 0;
    private boolean isBlocking = true;
    private boolean isTraversable = false;
    private boolean canInteract = false;
    private boolean isProjected = false;
    private String tile = "sprites/transparent.png";
    private int playerInterest = 0;
    private int state = 0;
    private boolean hasAction = false;
    private boolean hasAnimation = false;
    private GameObject animation;
    private ArrayList<Vector> path;
    private boolean isStationary = true;
    private boolean isImmutable = true;
    private Vector lastMove = null;
    private boolean isPlayerControlled = false;
    private boolean canSelfReplicate = false;
    private float chanceToSelfReplicate = 0f;
    private boolean isGasOrLiquid = false;

    private int movementStep;

    public GameObject(int x, int y) {
        this.x = x;
        this.y = y;
        this.id = UUID.randomUUID().toString();
        this.path = null;
        this.name = this.getClass().getSimpleName();
    }

    /**
     *  Creates clone of original object at specified x,y coords.
     */

    public GameObject(int x, int y, GameObject original) {
        this.x = x;
        this.y = y;
        this.id = UUID.randomUUID().toString(); // Still want unique id for cloned object
        this.path = original.path;
        this.name = original.name;
        this.dijkstra = original.dijkstra;
        this.isBlocking = original.isBlocking;
        this.isTraversable = original.isTraversable;
        this.canInteract = original.canInteract;
        this.isProjected = original.isProjected;
        this.tile = original.tile;
        this.playerInterest = original.playerInterest;
        this.state = original.state;
        this.hasAction = original.hasAction;
        this.hasAnimation = original.hasAnimation;
        this.animation = original.animation;
        this.isStationary = original.isStationary;
        this.isImmutable = original.isImmutable;
        this.canSelfReplicate = original.canSelfReplicate;
        this.chanceToSelfReplicate = original.chanceToSelfReplicate;

        // These fields should only be copied when needed (eg after creating clone)
        this.path = null;
        this.lastMove = null;
        this.isPlayerControlled = false;
    }

    public String getId() {
        return this.id;
    }

    public String tile() {
        return this.tile;
    }

    public void setTile(String tile) {
        this.tile = tile;
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

    public boolean hasAnimation() {
        return this.hasAnimation;
    }

    public void setAnimation(GameObject animation) {
        this.animation = animation;
        this.hasAnimation = true;
    }

    public GameObject getAnimation() {
        return this.animation;
    }


    /**
     Position and movement. */

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public void move(Vector newPos) {
        this.x = newPos.x();
        this.y = newPos.y();
    }

    public Vector getVector() {
        return new Vector(this.x, this.y);
    }

    /**
     Some objects have two sets of grid references - fovX/fovY are used to determine
     whether object should be displayed or not (eg. to make sure LightSources embedded in Wall tiles
     don't shine on both sides of wall). */

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


    /**
     Entities can only move across traversable objects.
     Non-traversable objects prevent movement, but do not block sight. */

    public void setTraversable(boolean value) {
        this.isTraversable = value;
    }

    public boolean isTraversable() {
        return isTraversable;
    }


    /**
     Blocking objects will block field of vision, but may allow movement. */

    public void setBlocking(boolean value) {
        this.isBlocking = value;
    }

    public boolean isBlocking() {
        return isBlocking;
    }


    /**
     Interactive objects can interact with certain types of object (doors, chests, etc). */

    public void setInteractive(boolean value) {
        this.canInteract = value;
    }

    public boolean canInteract() {
        return this.canInteract;
    }


    /**
     Dijkstra value is default value used when generating desire maps. */

    public void setDijkstra(int dijkstra) {
        this.dijkstra = dijkstra;
    }

    public int getDijkstra() {
        return this.dijkstra;
    }


    /**
     * This method is called whenever another object collides with this one.
     * Currently, only non-traversable objects are collidable.
     *
     * @param object Colliding object
     */

    public void collide(GameObject object) {}


    /**
     Methods for path-finding and storing object state */

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

    public ArrayList<Vector> getPath() {
        return this.path;
    }

    public void setPath(ArrayList<Vector> path) {
        this.path = path;
    }

    public Vector removeFromPath(int i) {
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
        // Todo: this is stupid. But if object is mutable then we set isImmutable to inverse of value
        this.isImmutable = !value;
    }

    public void setLastMove(Vector last) {
        this.lastMove = last;
    }

    public Vector getlastMove() {
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

    public boolean isPlayerControlled() {
        return this.isPlayerControlled;
    }

    public void setPlayerControl(boolean value) {
        this.isPlayerControlled = value;
    }

    public float getSelfReplicateChance() {
        return this.chanceToSelfReplicate;
    }

    public void setSelfReplicationChance(float chance) {
        this.chanceToSelfReplicate = chance;
    }

    public void setGasOrLiquid(boolean value) {
        this.isGasOrLiquid = value;
    }

    public boolean isGasOrLiquid() {
        return this.isGasOrLiquid;
    }
}
