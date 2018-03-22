package com.sonicmax.bloodrogue.generator.tools;

import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.utils.maths.GeometryHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

/**
 * Class which uses poisson disk sampling to create evenly distributed random noise.
 */

public class PoissonDiskSampler {
    private RandomNumberGenerator rng;
    private int density;

    public PoissonDiskSampler() {
        rng = new RandomNumberGenerator();
        density = 10;
    }

    private ArrayList<Vector>[][] initGrid(int width, int height) {
        ArrayList<Vector>[][] grid = new ArrayList [width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new ArrayList<>();
            }
        }

        return grid;
    }

    public void setDensity(int density) {
        this.density = density;
    }

    /**
     * Uses poisson disk sampling to generate random noise with a roughly even distribution.
     * By default, this method will attempt to place adjacent points 10 times for each newly generated
     * point, but this can be altered using setDensity().
     *
     * @param width Output grid width
     * @param height Output grid height
     * @param minDist Minimum distance between points
     * @param pointLimit Method will return results if we exceed this number
     * @return ArrayList of Vectors for each point
     */

    public ArrayList<Vector> generateNoise(int width, int height, int minDist, int pointLimit) {
        // We want to divide the grid into squares that represent the minimum distance allowed between points
        ArrayList<Vector>[][] grid = initGrid((width / minDist) + 1, (height / minDist) + 1);

        ArrayList<Vector> processList = new ArrayList<>();
        ArrayList<Vector> samplePoints = new ArrayList<>();
        Vector firstPoint = new Vector(rng.getRandomInt(0, width), rng.getRandomInt(0, height));

        processList.add(firstPoint);
        samplePoints.add(firstPoint);

        Vector firstGrid = pointToGrid(firstPoint, minDist);
        grid[firstGrid.x][firstGrid.y].add(firstPoint);

        // Start to generate points
        while (processList.size() > 0) {
            Vector point = processList.remove(rng.getRandomInt(0, processList.size() - 1));

            for (int i = 0; i < density; i++) {
                Vector newPoint = generateRandomPointAround(point, minDist);
                // Check that points is in bounds and that no other points exist around it
                if (inBounds(newPoint, width, height) && !pointInNeighbourhood(grid, newPoint, minDist)) {
                    samplePoints.add(newPoint);

                    if (samplePoints.size() > pointLimit) {
                        return samplePoints;
                    }
                    else {
                        // Add to process list and continue iterating
                        processList.add(newPoint);

                        Vector gridPosition = pointToGrid(newPoint, minDist);
                        grid[gridPosition.x][gridPosition.y].add(newPoint);
                    }
                }
            }
        }

        return samplePoints;
    }

    private boolean inBounds(Vector cell, int width, int height) {
        return (cell.x >= 0 && cell.y >= 0 && cell.x < width && cell.y < height);
    }

    private Vector pointToGrid(Vector point, int minDist) {
        int gridX = point.x / minDist;
        int gridY = point.y / minDist;
        return new Vector(gridX, gridY);
    }

    private Vector generateRandomPointAround(Vector point, int minDist) {
        //non-uniform, favours points closer to the inner ring, leads to denser packings
        double r1 = rng.getRandomFloat(0.0f, 1.0f);
        double r2 = rng.getRandomFloat(0.0f, 1.0f);

        //random radius between mindist and 2 * mindist
        double radius = minDist * (r1 + 1);
        //random angle
        double angle = 2 * Math.PI * r2;
        //the new point is generated around the point (x, y)
        double newX = point.x + radius * Math.cos(angle);
        double newY = point.y + radius * Math.sin(angle);

        return new Vector((int) newX, (int) newY);
    }

    private boolean pointInNeighbourhood(ArrayList<Vector>[][] grid, Vector point, int minDist) {
        Vector gridPoint = pointToGrid(point, minDist);

        ArrayList<Vector> cellsAroundPoint = getAdjacentCells(gridPoint, grid);
        for (Vector cell : cellsAroundPoint) {
            if (cell != null && GeometryHelper.getDistance(cell, point) < minDist) {
                return true;
            }
        }

        return false;
    }

    private ArrayList<Vector> getAdjacentCells(Vector point, ArrayList<Vector>[][] grid) {
        int width = grid.length;
        int height = grid[0].length;

        ArrayList<Vector> cells = new ArrayList<>();

        cells.addAll(grid[point.x][point.y]);

        for (Vector direction : Directions.All.values()) {
            Vector adjacent = point.add(direction);
            if (adjacent.x >= 0 && adjacent.x < width && adjacent.y >= 0 && adjacent.y < height) {
                cells.addAll(grid[adjacent.x][adjacent.y]);
            }
        }

        return cells;
    }
}
