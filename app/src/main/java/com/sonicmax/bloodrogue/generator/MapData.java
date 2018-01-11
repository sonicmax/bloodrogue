package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.objects.Room;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;
import java.util.HashMap;

public class MapData {
    private ArrayList<Room> rooms;
    private HashMap<String, Component[]> doors;
    private ArrayList<Component[]> objects;
    private ArrayList<Component[]> enemies;
    private Vector entrancePosition;
    private Vector exitPosition;
    private int type;

    public MapData(ArrayList<Room> rooms, HashMap<String, Component[]> doors, ArrayList<Component[]> objects,
                   ArrayList<Component[]> enemies, Vector entrance, Vector exit, int type) {
        this.rooms = rooms;
        this.doors = doors;
        this.objects = objects;
        this.enemies = enemies;
        this.entrancePosition = entrance;
        this.exitPosition = exit;
        this.type = type;
    }

    public Vector getEntrancePosition() {
        return this.entrancePosition;
    }

    public Vector getExitPosition() {
        return this.exitPosition;
    }

    public ArrayList<Component[]> getObjects() {
        return this.objects;
    }

    public ArrayList<Component[]> getDoors() {
        return new ArrayList<>(this.doors.values());
    }

    public ArrayList<Component[]> getEnemies() {
        return this.enemies;
    }

    public ArrayList<Room> getRooms() {
        return this.rooms;
    }
}
