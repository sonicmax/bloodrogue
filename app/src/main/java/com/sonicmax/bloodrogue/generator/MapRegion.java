package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.generator.tools.GridGeometryHelper;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

/**
 * Class which can be used to define a region of the map using an array of Vectors
 */

public class MapRegion {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private ArrayList<Vector> region;
    private ArrayList<Vector> corners;
    private ArrayList<Vector[]> sides;

    public MapRegion() {
        this.region = new ArrayList<>();
        this.corners = new ArrayList<>();
        this.sides = new ArrayList<>();
    }

    public ArrayList<Vector> getVectors() {
        return this.region;
    }

    public ArrayList<Vector> getCorners() {
        return this.corners;
    }

    public ArrayList<Vector[]> getSides() {
        return this.sides;
    }

    public void set(ArrayList<Vector> region) {
        this.region = region;
    }

    public void add(Vector vector) {
        this.region.add(vector);
    }

    public void addChunk(Chunk chunk) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
                this.region.add(new Vector(x, y));
            }
        }

        this.corners = GridGeometryHelper.findCorners(this.region);

        if (this.corners.size() == 4) {
            this.sides = GridGeometryHelper.findRectSides(this.corners);
        }
        else {
            this.sides = GridGeometryHelper.findSides(this.region);
        }
    }

    public void remove(Vector vector) {
        this.region.remove(vector);
    }

    public void remove(Chunk chunk) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
                this.region.remove(new Vector(x, y));
            }
        }
    }

    public void addAll(ArrayList<Vector> vectors) {
        this.region.addAll(vectors);
    }

    /**
     * Check if map region contains provided Vector.
     *
     * @param vector Vector to check
     * @return True if region contains Vector
     */

    public boolean contains(Vector vector) {
        // Note that Vector class implements its own equals() method
        return this.region.contains(vector);
    }
}
