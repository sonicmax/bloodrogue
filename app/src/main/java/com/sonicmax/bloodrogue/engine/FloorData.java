package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Container for all data required to update renderer
 */

public class FloorData implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int index;
    public final Component[][][] terrain;
    public final ArrayList<Component[]>[][] objects;
    public final ArrayList<Component[]>[][] animations;
    public final Component[] player;

    public Vector entrancePosition;
    public Vector exitPosition;


    public FloorData(int index, Component[][][] terrain, ArrayList<Component[]>[][] objects,
                     ArrayList<Component[]>[][] animations, Component[] player) {

        this.index = index;
        this.terrain = terrain;
        this.objects = objects;
        this.animations = animations;
        this.player = player;
    }

    public void setEntrance(Vector entrance) {
        this.entrancePosition = entrance;
    }

    public void setExit(Vector exit) {
        this.exitPosition = exit;
    }

    public Component[][][] getTerrain() {
        return this.terrain;
    }

    public ArrayList<Component[]>[][] getObjects() {
        return this.objects;
    }

    public ArrayList<Component[]>[][] getAnimations() {
        return this.animations;
    }

    public Component[] getPlayer() {
        return this.player;
    }

    public int getIndex() {
        return this.index;
    }
}
