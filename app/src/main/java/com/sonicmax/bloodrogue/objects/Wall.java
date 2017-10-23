package com.sonicmax.bloodrogue.objects;

public class Wall extends GameObject {

    public Wall(int x, int y, String tile) {
        super(x, y);
        this.setTile(tile);
        this.setBlocking(true);
        this.setTraversable(false);
    }

}
