package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.engine.objects.GameObject;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Container for all data required to update renderer
 */

public class FloorData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int index;
    private GameObject[][] terrain;
    private ArrayList<GameObject>[][] objects;
    private ArrayList<GameObject>[][] animations;
    private double[][] fov;
    private double[][] lightMap;
    private GameObject player;

    public FloorData() {

    }

    public FloorData(int index, GameObject[][] terrain, ArrayList<GameObject>[][] objects,
                     ArrayList<GameObject>[][] animations, double[][] fov, double[][] lightMap, GameObject player) {

        this.index = index;
        this.terrain = terrain;
        this.objects = objects;
        this.animations = animations;
        this.fov = fov;
        this.lightMap = lightMap;
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

    public double[][] getLightMap() {
        return this.lightMap;
    }

    public GameObject getPlayer() {
        return this.player;
    }

    public int getIndex() {
        return this.index;
    }
}
