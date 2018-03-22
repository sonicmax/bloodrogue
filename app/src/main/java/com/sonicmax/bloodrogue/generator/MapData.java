package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

public class MapData {
    private long[][] terrainEntities;
    private ArrayList<Long>[][] objectEntities;

    private Vector entrancePosition;
    private Vector exitPosition;
    private int type;

    public MapData(long[][] terrainEntities, ArrayList<Long>[][] objectEntities, Vector entrance, Vector exit, int type) {

        this.terrainEntities = terrainEntities;
        this.objectEntities = objectEntities;

        this.entrancePosition = entrance;
        this.exitPosition = exit;
        this.type = type;
    }

    public long[][] getTerrainEntities() {
        return this.terrainEntities;
    }

    public ArrayList<Long>[][] getObjectEntities() {
        return this.objectEntities;
    }

    public Vector getEntrancePosition() {
        return this.entrancePosition;
    }

    public Vector getExitPosition() {
        return this.exitPosition;
    }
}
