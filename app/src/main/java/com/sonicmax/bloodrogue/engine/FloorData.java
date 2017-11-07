package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.engine.objects.GameObject;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Container for all data required to update renderer
 */

public class FloorData implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int index;
    public final GameObject[][] terrain;
    public final ArrayList<GameObject>[][] objects;
    public final ArrayList<GameObject>[][] animations;
    public final double[][] fov;
    public final GameObject player;

    public FloorData(int index, GameObject[][] terrain, ArrayList<GameObject>[][] objects,
                     ArrayList<GameObject>[][] animations, double[][] fov, GameObject player) {

        this.index = index;
        this.terrain = terrain;
        this.objects = objects;
        this.animations = animations;
        this.fov = fov;
        this.player = player;
    }

    public GameObject[][] getTerrain() {
        return this.terrain;
    }

    public ArrayList<GameObject>[][] getObjects() {
        return this.objects;
    }

    public ArrayList<GameObject>[][] getAnimations() {
        return this.animations;
    }

    public double[][] getFov() {
        return this.fov;
    }

    public GameObject getPlayer() {
        return this.player;
    }

    public int getIndex() {
        return this.index;
    }
}
