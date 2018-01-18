package com.sonicmax.bloodrogue.generator.mansion;

import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.generator.Chunk;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

public class Room extends Chunk {
    private ArrayList<GameObject> objects;
    private boolean isEntrance = false;
    public boolean isAccessible = true;
    public boolean furnished = false;

    // Todo: this should just extend Chunk class

    public Room(int x, int y, int width, int height) {
        super(x, y, width, height);
        objects = new ArrayList<>();
    }

    public void setEntrance() {
        this.isEntrance = true;
    }

    public boolean isEntrance() {
        return this.isEntrance;
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
