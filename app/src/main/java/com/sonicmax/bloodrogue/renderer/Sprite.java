package com.sonicmax.bloodrogue.renderer;

public class Sprite {
    public int x;
    public int y;
    public int index;
    public float lighting;
    public float offsetX;
    public float offsetY;

    public Sprite(int x, int y, int index) {
        this.x = x;
        this.y = y;
        this.index = index;
        this.lighting = -1f;
    }

    public Sprite(int x, int y, int index, float offsetX, float offsetY) {
        this.x = x;
        this.y = y;
        this.index = index;
        this.lighting = -1f;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }
}
