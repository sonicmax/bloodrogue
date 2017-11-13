package com.sonicmax.bloodrogue.engine;

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
    public final double[][] fov;
    public final Component[] player;

    public FloorData(int index, Component[][][] terrain, ArrayList<Component[]>[][] objects,
                     ArrayList<Component[]>[][] animations, double[][] fov, Component[] player) {

        this.index = index;
        this.terrain = terrain;
        this.objects = objects;
        this.animations = animations;
        this.fov = fov;
        this.player = player;
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

    public double[][] getFov() {
        return this.fov;
    }

    public Component[] getPlayer() {
        return this.player;
    }

    public int getIndex() {
        return this.index;
    }
}
