package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.utils.maths.Vector;

/**
 *  Defines a square/rectangle space of the map area.
 */

public class Chunk {
    public int x;
    public int y;
    public int width;
    public int height;
    public final int tag;

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
        this.tag = 0;
    }

    public Chunk(int x, int y, int width, int height, int tag) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.tag = tag;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
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

    public int area() {
        return this.width * this.height;
    }

    public boolean equals(Chunk comparator) {
        return (this.x == comparator.x && this.y == comparator.y
                && this.width == comparator.width && this.height == comparator.height);
    }

    public Vector roundedCentre() {
        int x = Math.round(this.x + (this.width / 2));
        int y = Math.round(this.y + (this.height / 2));

        return new Vector(x, y);
    }

    // For debugging lol

    public String toString() {
        return "(" + this.x + ", " + this.y + ") width: " + this.width + ", height: " + this.height;
    }
}
