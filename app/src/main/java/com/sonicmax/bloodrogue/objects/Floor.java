package com.sonicmax.bloodrogue.objects;

public class Floor extends GameObject {
    public final static boolean IS_DOORWAY = true;

    private boolean isDoorway = false;

    public Floor(int x, int y, String tile) {
        super(x, y);
        this.setBlocking(false);
        this.setTraversable(true);
        this.setTile(tile);
    }

    public Floor(int x, int y, String tile, boolean isDoorway) {
        super(x, y);
        this.setBlocking(false);
        this.setTraversable(true);
        this.setTile(tile);
        this.isDoorway = isDoorway;
    }

    public boolean isDoorway() {
        return this.isDoorway;
    }
}
