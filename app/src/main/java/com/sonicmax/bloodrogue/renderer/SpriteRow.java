package com.sonicmax.bloodrogue.renderer;

import java.util.ArrayList;

/**
 * Simple class that contains some information to be fed to SpriteSheetRenderer
 */

public class SpriteRow {
    public ArrayList<Integer> indexes;
    public ArrayList<Double> lighting;

    public float x;
    public float y;
    public int tileY;
    public float[] color;
    private boolean hasArray;
    private ArrayList<Integer>[] indexArrays;

    public SpriteRow() {
        this.indexes = new ArrayList<>();
        this.lighting = new ArrayList<>();
        this.x = 10f;
        this.y = 10f;
        this.color = new float[] {1f, 1f, 1f, 1.0f};
        this.hasArray = false;
    }

    public boolean hasArray() {
        return this.hasArray;
    }

    public void setObjectArrays(ArrayList<Integer>[] indexArrays) {
        this.indexArrays = indexArrays;
        this.hasArray = true;
    }

    public ArrayList<Integer>[] getIndexArray() {
        return this.indexArrays;
    }

    public ArrayList<Double> getDefaultLighting(int width) {
        ArrayList<Double> empty = new ArrayList<>();
        for (int i = 0; i <= width; i++) {
            empty.add(1.0);
        }

        return empty;
    }

    public ArrayList<Integer> getEmptySpriteRow(int width) {
        ArrayList<Integer> empty = new ArrayList<>();
        for (int i = 0; i <= width; i++) {
            // Todo: make sure that transparent tile is 1st in sprite sheet so we can just add 0
            empty.add(164);
        }

        return empty;
    }
}
