package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.utils.maths.Vector2D;

import java.util.ArrayList;

public class MapData {
    private int[][] terrainEntities;
    private ArrayList<Long>[][] objectEntities;

    private Vector2D entrancePosition;
    private Vector2D exitPosition;
    private int type;

    public MapData(int[][] terrainEntities, ArrayList<Long>[][] objectEntities, Vector2D entrance, Vector2D exit, int type) {

        this.terrainEntities = terrainEntities;
        this.objectEntities = objectEntities;

        this.entrancePosition = entrance;
        this.exitPosition = exit;
        this.type = type;
    }

    public int[][] getTerrainEntities() {
        return this.terrainEntities;
    }

    public ArrayList<Long>[][] getObjectEntities() {
        return this.objectEntities;
    }

    public Vector2D getEntrancePosition() {
        return this.entrancePosition;
    }

    public Vector2D getExitPosition() {
        return this.exitPosition;
    }
}
