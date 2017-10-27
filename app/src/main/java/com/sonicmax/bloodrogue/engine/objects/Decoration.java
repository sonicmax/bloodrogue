package com.sonicmax.bloodrogue.engine.objects;

public class Decoration extends GameObject {
    public Decoration(int x, int y, String tile) {
        super(x, y);
        this.setTile(tile);
        this.setBlocking(false);
        this.setTraversable(false);
        this.setMutability(false);
        this.setStationary(true);
    }

    /**
     *  x/y is the grid square which tile will be rendered to, and fovX/fovY is the grid square
     *  that has to be in FOV for renderer to show tile
     */

    public Decoration(int x, int y, int fovX, int fovY, String tile) {
        super(x, y);
        this.setFovX(fovX);
        this.setFovY(fovY);
        this.setTile(tile);
        this.setProjected(true);
        this.setBlocking(false);
        this.setTraversable(true);
        this.setMutability(true);
        this.setStationary(true);
    }
}
