package com.sonicmax.bloodrogue.generator.buildings;

import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.generator.Chunk;

import java.util.ArrayList;

public class Room extends Chunk {
    private ArrayList<GameObject> objects;
    private boolean isEntrance = false;
    public boolean isAccessible = true;
    public boolean furnished = false;
    public int index = 0;

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

    public ArrayList<GameObject> getObjects() {
        return this.objects;
    }

    // For debugging

    public String toString() {
        return "(" + this.x + ", " + this.y + ") width: " + this.width + ", height: " + this.height;
    }
}
