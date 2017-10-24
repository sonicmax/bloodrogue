package com.sonicmax.bloodrogue.objects;

import android.util.Log;

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

        if (isOpen) {
            this.setTile(this.openTile);
            this.setTraversable(true);
            this.setBlocking(false);
        }
        else {
            this.setTile(this.closedTile);
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

        if (isOpen) {
            this.setTile(this.openTile);
            this.setTraversable(true);
            this.setBlocking(false);
        }
        else {
            this.setTile(this.closedTile);
            this.setTraversable(false);
            this.setBlocking(true);
        }
    }

    public void collide(GameObject object) {
        if (object.canInteract()) {
            if (!isOpen) {
                this.setTile(this.openTile);
                this.setTraversable(true);
                this.setBlocking(false);
                this.isOpen = true;
            }
            else {
                this.setTile(this.closedTile);
                this.setTraversable(false);
                this.setBlocking(true);
                this.isOpen = false;
            }
        }
        else {
            Log.v("Door", object.getClass().getSimpleName() + " not interactive");
        }
    }
}
