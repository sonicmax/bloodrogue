package com.sonicmax.bloodrogue.engine.objects;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

public class Room extends GameObject {
    private int x;
    private int y;
    private int width;
    private int height;
    private ArrayList<GameObject> objects;
    private boolean isEntrance = false;

    // TODO: convert into private fields with getter/setters
    public boolean isAccessible = true;
    public boolean furnished = false;

    public Room(int x, int y, int width, int height) {
        super(x, y);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        objects = new ArrayList<>();
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
}
