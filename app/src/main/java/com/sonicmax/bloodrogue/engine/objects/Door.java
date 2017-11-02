package com.sonicmax.bloodrogue.engine.objects;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.Actions;

public class Door extends GameObject {
    private String openTile;
    private String closedTile;
    private boolean isOpen = false;

    public Door(int x, int y, String open, String closed) {
        super(x, y);
        this.openTile = open;
        this.closedTile = closed;
        this.setHasAction(true);
        this.setStationary(true);
        this.setMutability(true);
        this.setActivateOnCollide(true);

        if (isOpen) {
            this.setSprite(this.openTile);
            this.setTraversable(true);
            this.setBlocking(false);
        }
        else {
            this.setSprite(this.closedTile);
            this.setTraversable(false);
            this.setBlocking(true);
        }
    }

    public Door(int x, int y, String open, String closed, boolean isOpen) {
        super(x, y);
        this.openTile = open;
        this.closedTile = closed;
        this.isOpen = isOpen;
        this.setHasAction(true);
        this.setActivateOnCollide(true);

        if (isOpen) {
            this.setSprite(this.openTile);
            this.setTraversable(true);
            this.setBlocking(false);
        }
        else {
            this.setSprite(this.closedTile);
            this.setTraversable(false);
            this.setBlocking(true);
        }
    }

    public int collide(GameObject object) {
        if (object.canInteract()) {
            if (!isOpen) {
                this.setSprite(this.openTile);
                this.setTraversable(true);
                this.setBlocking(false);
                this.isOpen = true;
                this.setHasAction(false);
            }
            /*else {
                this.setSprite(this.closedTile);
                this.setTraversable(false);
                this.setBlocking(true);
                this.isOpen = false;
            }*/
        }
        else {
            Log.v("Door", object.getClass().getSimpleName() + " not interactive");
        }

        return Actions.NONE;
    }
}
