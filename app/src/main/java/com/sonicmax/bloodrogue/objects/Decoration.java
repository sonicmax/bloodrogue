package com.sonicmax.bloodrogue.objects;

public class Decoration extends GameObject {
    public Decoration(int x, int y, String tile) {
        super(x, y);
        this.setTile(tile);
        this.setBlocking(false);
        this.setTraversable(false);
    }

    /**
     *  fovX/fovY is the grid square which has to be in FOV for tile to be rendered, and destX/destY is the grid square
     *  that the tile is rendered to. This prevents player from seeing decorations from wrong side of wall
     */

    public Decoration(int fovX, int fovY, int destX, int destY, String tile) {
        super(fovX, fovY);
        this.setDestX(destX);
        this.setDestY(destY);
        this.setTile(tile);
        this.setProjected(true);
        this.setBlocking(false);
        this.setTraversable(true);
    }
}
