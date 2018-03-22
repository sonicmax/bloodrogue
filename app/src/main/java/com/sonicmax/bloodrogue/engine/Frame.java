package com.sonicmax.bloodrogue.engine;


import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.renderer.Animation;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Container for all data required to update renderer
 */

public class Frame implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int index;
    public final Sprite[][] terrain;
    public final ArrayList<Sprite>[][] objects;
    public final ArrayList<Animation> animations;
    public final double[][] fov;
    public final boolean[][] visited;
    public final boolean[][] indoorRegions;
    public final boolean[][] waterRegions;
    public final int[][] snowCover;
    public final Component[] player;

    public Frame(int index, Sprite[][] terrain, ArrayList<Sprite>[][] objects,
                 ArrayList<Animation> animations, double[][] fov, boolean[][] visited,
                 boolean[][] indoorRegions, boolean[][] waterRegions, int[][] snowCover,
                 Component[] player) {

        this.index = index;
        this.terrain = terrain;
        this.objects = objects;
        this.animations = animations;
        this.fov = fov;
        this.visited = visited;
        this.indoorRegions = indoorRegions;
        this.waterRegions = waterRegions;
        this.snowCover = snowCover;
        this.player = player;
    }

    public Sprite[][] getTerrain() {
        return this.terrain;
    }

    public ArrayList<Sprite>[][] getObjects() {
        return this.objects;
    }

    public ArrayList<Animation> getAnimations() {
        return this.animations;
    }

    public double[][] getFov() {
        return this.fov;
    }

    public boolean[][] getVisitedTiles() {
        return this.visited;
    }

    public Component[] getPlayer() {
        return this.player;
    }

    public int getIndex() {
        return this.index;
    }
}
