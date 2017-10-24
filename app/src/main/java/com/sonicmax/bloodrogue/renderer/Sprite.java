package com.sonicmax.bloodrogue.renderer;

public class Sprite {
    public int x;
    public int y;
    public int index;
    public float lighting;

    public Sprite(int x, int y, int index) {
        this.x = x;
        this.y = y;
        this.index = index;
        this.lighting = -1f;
    }
}
