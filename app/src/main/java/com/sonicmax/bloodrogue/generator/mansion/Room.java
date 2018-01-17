package com.sonicmax.bloodrogue.generator.mansion;

import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

public class Room {
    private int x;
    private int y;
    private int width;
    private int height;
    private ArrayList<GameObject> objects;
    private boolean isEntrance = false;
    public boolean isAccessible = true;
    public boolean furnished = false;

    // Todo: this should just extend Chunk class

    public Room(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        objects = new ArrayList<>();
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public void setEntrance() {
        this.isEntrance = true;
    }

    public boolean isEntrance() {
        return this.isEntrance;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public Vector roundedCentre() {
        int x = Math.round(this.x + (this.width / 2));
        int y = Math.round(this.y + (this.height / 2));

        return new Vector(x, y);
    }

    public ArrayList<GameObject> getObjects() {
        return this.objects;
    }

    public void addObject(GameObject object) {
        this.objects.add(object);
    }

    // For debugging

    public String toString() {
        return "(" + this.x + ", " + this.y + ") width: " + this.width + ", height: " + this.height;
    }
}
