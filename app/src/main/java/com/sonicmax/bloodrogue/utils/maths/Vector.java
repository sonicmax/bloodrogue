package com.sonicmax.bloodrogue.utils.maths;

import java.io.Serializable;

/**
 *  Stores a pair of points and provides some methods to perform vector maths with.
 */

public class Vector implements Serializable {
    private static final long serialVersionUID = 1L;
    private int x;
    private int y;
    private String direction;

    private boolean hasDirection = false;

    public Vector(int x, int y) {
        this.x = x;
        this.y = y;
        this.direction = "";
    }

    public Vector(int x, int y, String direction) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.hasDirection = true;
    }

    public boolean hasDirection() {
        return hasDirection;
    }

    public String getDirection() {
        return direction;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public boolean equals(Vector vector) {
        return (this.x == vector.x && this.y == vector.y);
    }

    public Vector add(Vector vector) {
        return new Vector(this.x + vector.x, this.y + vector.y);
    }

    public Vector subtract(Vector vector) {
        return new Vector(this.x - vector.x, this.y - vector.y);
    }

    public Vector scale(int factor) {
        return new Vector(this.x * factor, this.y * factor);
    }

    public Vector divide(int divisor) {
        return new Vector(this.x / divisor, this.y / divisor);
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y);
    }

    public int dotProduct(Vector vector) {
        return (this.x * vector.x) + (this.y * vector.y);
    }

    public int crossProduct(Vector vector) {
        return (this.x * vector.y) - (this.y * vector.x);
    }

    public Vector rotate(int angle, Vector centre) {
        int x = this.x - centre.x;
        int y = this.y - centre.y;

        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double rotatedX = centre.x + (x * cos) - (y * sin);
        double rotatedY = centre.y + (x * sin) + (y * cos);

        return new Vector((int) rotatedX, (int) rotatedY);
    }

    public Vector normalise() {
        double length = Math.sqrt((this.x * this.x) + (this.y * this.y));
        return new Vector(this.x /= length, this.y /= length);
    }

    public int[] toArray() {
        int[] array = {this.x, this.y};
        return array;
    }

    public String toString() {
        return "[" + this.x + ", " + this.y + "]";
    }

    public double getMagnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y);
    }

    public boolean moreThan(Vector vector) {
        return (this.x > vector.x && this.y > vector.y);
    }

    public boolean lessThan(Vector vector) {
        return (this.x < vector.x && this.y < vector.y);
    }
}
