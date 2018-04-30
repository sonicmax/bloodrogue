package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.utils.maths.Vector2D;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Container for all data required to update renderer
 */

public class FloorData implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int index;
    public final int[][] terrain;
    public final ArrayList<Component[]>[][] objects;
    public final Component[] player;

    public Vector2D entrancePosition;
    public Vector2D exitPosition;


    public FloorData(int index, int[][] terrain, ArrayList<Component[]>[][] objects, Component[] player) {

        this.index = index;
        this.terrain = terrain;
        this.objects = objects;
        this.player = player;
    }

    public void setEntrance(Vector2D entrance) {
        this.entrancePosition = entrance;
    }

    public void setExit(Vector2D exit) {
        this.exitPosition = exit;
    }

    public int[][] getTerrain() {
        return this.terrain;
    }

    public ArrayList<Component[]>[][] getObjects() {
        return this.objects;
    }

    public Component[] getPlayer() {
        return this.player;
    }

    public int getIndex() {
        return this.index;
    }
}
