package com.sonicmax.bloodrogue.generator.mansion;

/**
 *  Defines a square/rectangle space of the map area. Used when generating mansion hallways and rooms
 */

public class Chunk {
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    /**
     *
     * @param x bottom-left x coord
     * @param y bottom-left y coord
     * @param width
     * @param height
     */

    public Chunk(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int[] bottomLeft() {
        return new int[] {x, y};
    }

    public int[] bottomRight() {
        return new int[] {x + width, y};
    }

    public int[] topLeft() {
        return new int[] {x, y + height};
    }

    public int[] topRight() {
        return new int[] {x + width, y + height};
    }

    // For debugging lol

    public String toString() {
        return "(" + this.x + ", " + this.y + ") width: " + this.width + ", height: " + this.height;
    }
}
