package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.GameObject;

import java.util.ArrayList;
import java.util.HashMap;

public class MapData {
    private ArrayList<GameObject> rooms;
    private HashMap<String, GameObject> doors;
    private ArrayList<GameObject> objects;
    private ArrayList<GameObject> enemies;
    private Vector startPosition;
    private int type;

    public MapData(ArrayList<GameObject> rooms, HashMap<String, GameObject> doors, ArrayList<GameObject> objects, ArrayList<GameObject> enemies, Vector start, int type) {
        this.rooms = rooms;
        this.doors = doors;
        this.objects = objects;
        this.enemies = enemies;
        this.startPosition = start;
        this.type = type;
    }

    public Vector getStartPosition() {
        return this.startPosition;
    }

    public ArrayList<GameObject> getObjects() {
        return this.objects;
    }

    public ArrayList<GameObject> getDoors() {
        return new ArrayList<>(this.doors.values());
    }

    public ArrayList<GameObject> getEnemies() {
        return this.enemies;
    }

    public ArrayList<GameObject> getRooms() {
        return this.rooms;
    }
}
