package com.sonicmax.bloodrogue.generator.tools;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.generator.Chunk;
import com.sonicmax.bloodrogue.generator.MapRegion;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

/**
 * Contains some methods to aid in geometric grid calculations (eg. finding corners in a region,
 * finding sides, finding adjacent sides, etc)
 */

public class GridGeometryHelper {
    public static final String LOG_TAG = "GridGeometryHelper";

    /**
     * Given an array of grid squares, finds corner tiles and returns as array.
     * Note: assumes that region contains a contiguous region of tiles. Will probably give
     * bad results otherwise.
     *
     * @param region Array of vectors to test
     * @return Array of corners
     */

    public static ArrayList<Vector> findCorners(ArrayList<Vector> region) {
        ArrayList<Vector> corners = new ArrayList<>();

        Vector[][] regionGrid = vectorsToGrid(region);
        int width = regionGrid.length;
        int height = regionGrid[0].length;

        // Iterate over tiles to find corners. Tiles with two cardinal neighbours are outer corners,
        // and tiles with a single diagonal neighbour are inner corners.

        ArrayList<Vector> queue = new ArrayList<>();
        ArrayList<Vector> checked = new ArrayList<>();
        queue.add(new Vector(width / 2, height / 2));

        while (queue.size() > 0) {
            Vector cell = queue.remove(0);

            if (checked.contains(cell)) continue;

            if (regionGrid[cell.x][cell.y] == null) continue;

            checked.add(cell);

            int cardinalSpaces = 0;
            int diagonalSpaces = 0;

            for (Vector direction : Directions.Cardinal.values()) {
                Vector adjacent = cell.add(direction);

                // Check if adjacent tile is empty and add to cardinal space count.
                if (inBoundingBox(adjacent, width, height)) {
                    if (regionGrid[adjacent.x][adjacent.y] == null) {
                        cardinalSpaces++;
                    }
                    else {
                        // Add to queue and continue testing
                        queue.add(adjacent);
                    }
                }

                else {
                    cardinalSpaces++;
                }
            }

            if (cardinalSpaces == 2) {
                corners.add(regionGrid[cell.x][cell.y]);
                continue;
            }

            for (Vector direction : Directions.Diagonal.values()) {
                Vector adjacent = cell.add(direction);

                if (inBoundingBox(adjacent, width, height)) {
                    if (regionGrid[adjacent.x][adjacent.y] == null) {
                        diagonalSpaces++;
                    }
                    else {
                        queue.add(adjacent);
                    }
                }

                else {
                    diagonalSpaces++;
                }
            }

            // Tiles with a single diagonal

            if (diagonalSpaces == 1) {
                corners.add(regionGrid[cell.x][cell.y]);
            }
        }

        return corners;
    }

    /**
     * Finds corner tiles and returns array of inner corners (ie. inside corner of room vs. corner of wall)
     */

    public static ArrayList<Vector> findInsideCorners(ArrayList<Vector> region) {
        ArrayList<Vector> corners = new ArrayList<>();

        Vector[][] regionGrid = vectorsToGrid(region);
        int width = regionGrid.length;
        int height = regionGrid[0].length;

        // Iterate over tiles to find corners. Tiles with two cardinal neighbours are outer corners,
        // and tiles with a single diagonal neighbour are inner corners.

        ArrayList<Vector> queue = new ArrayList<>();
        ArrayList<Vector> checked = new ArrayList<>();
        queue.add(new Vector(width / 2, height / 2));

        while (queue.size() > 0) {
            Vector cell = queue.remove(0);

            if (checked.contains(cell)) continue;

            if (regionGrid[cell.x][cell.y] == null) continue;

            checked.add(cell);

            int diagonalSpaces = 0;

            ArrayList<Vector> cardinalVectors = new ArrayList<>();

            for (Vector direction : Directions.Cardinal.values()) {
                Vector adjacent = cell.add(direction);

                // Check if adjacent tile is empty and add to cardinal space count.
                if (inBoundingBox(adjacent, width, height)) {
                    if (regionGrid[adjacent.x][adjacent.y] == null) {
                        cardinalVectors.add(direction);
                    }
                    else {
                        // Add to queue and continue testing
                        queue.add(adjacent);
                    }
                }

                else {
                    cardinalVectors.add(direction);
                }
            }

            if (cardinalVectors.size() == 2) {
                // We can find the inside corner by adding the two cardinal directions together
                // and reversing the signs

                /*Vector direction = cardinalVectors.get(0).add(cardinalVectors.get(1));
                direction.x = -direction.x;
                direction.y = -direction.y;
                Vector corner = cell.add(direction);
                Vector translatedCorner = regionGrid[corner.x][corner.y];
                if (translatedCorner == null) {
                    Log.e(LOG_TAG, "couldn't find inside region of inner corner");
                    break;
                }
                corners.add(translatedCorner);*/

                corners.add(regionGrid[cell.x][cell.y]);
                continue;
            }

            // Inner corners have one diagonal space (and outer corners have already been filtered out)

            for (Vector direction : Directions.Diagonal.values()) {
                Vector adjacent = cell.add(direction);

                if (inBoundingBox(adjacent, width, height)) {
                    if (regionGrid[adjacent.x][adjacent.y] == null) {
                        diagonalSpaces++;
                    }
                    else {
                        queue.add(adjacent);
                    }
                }

                else {
                    diagonalSpaces++;
                }
            }

            // Tiles with a single diagonal

            if (diagonalSpaces == 1) {
                /*Vector corner = cell.subtract(spaceDirection);
                Vector translatedCorner = regionGrid[corner.x][corner.y];
                if (translatedCorner == null) {
                    Log.e(LOG_TAG, "couldn't find inside region of inner corner");
                    break;
                }
                corners.add(translatedCorner);*/
                corners.add(regionGrid[cell.x][cell.y]);
            }
        }

        return corners;
    }

    public static Vector[][] vectorsToGrid(ArrayList<Vector> vectors) {
        // We need to get appropriate bounds for shape by finding smallest and largest possible
        // values for array. This will create bounding box for region.

        int smallestX = Integer.MAX_VALUE;
        int largestX = Integer.MIN_VALUE;
        int smallestY = Integer.MAX_VALUE;
        int largestY = Integer.MIN_VALUE;

        for (Vector vector : vectors) {
            if (vector.x < smallestX) {
                smallestX = vector.x;
            }
            if (vector.x > largestX) {
                largestX = vector.x;
            }
            if (vector.y < smallestY) {
                smallestY = vector.y;
            }
            if (vector.y > largestY) {
                largestY = vector.y;
            }
        }

        // Now init 2d array using width/height of bounding box and add vectors.
        int width = largestX - smallestX + 1;
        int height = largestY - smallestY + 1;

        Vector[][] regionGrid = new Vector[width][height];

        for (Vector vector : vectors) {
            // Translate vectors on grid so that grid origin is 0,0
            regionGrid[vector.x - smallestX][vector.y - smallestY] = vector;
        }

        return regionGrid;
    }

    public static ArrayList<Vector> getBorderVectorsFromRegion(MapRegion region) {
        ArrayList<Vector> borderVectors = new ArrayList<>();
        ArrayList<Vector> regionVectors = region.getVectors();
        Chunk boundingBox = GridGeometryHelper.getBoundingBox(regionVectors);
        Vector[][] grid = GridGeometryHelper.vectorsToGrid(regionVectors);

        for (Vector vector : regionVectors) {
            for (Vector direction : Directions.Cardinal.values()) {
                Vector adjacent = vector.add(direction);
                if (!inBoundingBox(adjacent, boundingBox) || grid[adjacent.x][adjacent.y] == null) {
                    borderVectors.add(adjacent);
                    break;
                }
            }
        }

        return borderVectors;
    }

    private static boolean inBoundingBox(Vector vector, Chunk boundingBox) {
        return (vector.x >= 0 && vector.x < boundingBox.width)
                && (vector.y >= 0 && vector.y < boundingBox.height);
    }

    public static ArrayList<Vector[]> findRectSides(ArrayList<Vector> corners) {
        ArrayList<Vector[]> sides = new ArrayList<>();

        int lowestX = Integer.MAX_VALUE;
        int highestX = Integer.MIN_VALUE;
        int lowestY = Integer.MAX_VALUE;
        int highestY = Integer.MIN_VALUE;

        for (Vector corner : corners) {
            if (corner.x < lowestX) {
                lowestX = corner.x;
            }

            if (corner.x > highestX) {
                highestX = corner.x;
            }

            if (corner.y < lowestY) {
                lowestY = corner.y;
            }

            if (corner.y > highestY) {
                highestY = corner.y;
            }
        }

        Vector topLeft = new Vector(lowestX, highestY);
        Vector topRight = new Vector(highestX, highestY);
        Vector bottomLeft = new Vector(lowestX, lowestY);
        Vector bottomRight = new Vector(highestX, lowestY);

        sides.add(new Vector[] {topLeft, topRight});
        sides.add(new Vector[] {topRight, bottomRight});
        sides.add(new Vector[] {bottomRight, bottomLeft});
        sides.add(new Vector[] {bottomLeft, topLeft});

        return sides;
    }

    public static Chunk getBoundingBox(ArrayList<Vector> corners) {
        ArrayList<Vector[]> sides = new ArrayList<>();

        int lowestX = Integer.MAX_VALUE;
        int highestX = Integer.MIN_VALUE;
        int lowestY = Integer.MAX_VALUE;
        int highestY = Integer.MIN_VALUE;

        for (Vector corner : corners) {
            if (corner.x < lowestX) {
                lowestX = corner.x;
            }

            if (corner.x > highestX) {
                highestX = corner.x;
            }

            if (corner.y < lowestY) {
                lowestY = corner.y;
            }

            if (corner.y > highestY) {
                highestY = corner.y;
            }
        }

        Vector bottomLeft = new Vector(lowestX, lowestY);

        int width = highestX - lowestX;
        int height = highestY - lowestY;

        return new Chunk(bottomLeft.x, bottomLeft.y, width, height);
    }

    public static ArrayList<Vector[]> findSides(ArrayList<Vector> region) {
        ArrayList<Vector[]> sides = new ArrayList<>();

        Vector[][] grid = vectorsToGrid(region);
        int width = grid.length;
        int height = grid[0].length;

        Vector start = null;

        // Find a corner on the grid to start checking from. If we iterate in rows starting from
        // the bottom, we can guarantee that the first tile we find will be a corner

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[x][y] != null) {
                    // Save the grid position (not the stored vector, which refers to its world position)
                    start = new Vector(x, y);
                    break;
                }
            }

            if (start != null) {
                break;
            }
        }

        if (start == null) {
            Log.e(LOG_TAG, "Couldn't find start position? (region size: " + region.size() + ")");
            return sides;
        }

        ArrayList<Vector> checked = new ArrayList<>();
        ArrayList<Vector> queue = new ArrayList<>();
        queue.add(start);

        while (queue.size() > 0) {
            Vector current = queue.remove(0);

            // Make sure we don't backtrack. Handle comparisons to start separately
            if (!current.equals(start)) {
                checked.add(current);
            }

            // Get world position vector from grid
            Vector[] side = new Vector[2];
            side[0] = grid[current.x][current.y];

            boolean foundCorner = false;

            // Check in each cardinal direction until we find an edge that we can follow.
            for (Vector direction : Directions.Cardinal.values()) {

                // Once we've found a corner, break out of this loop and go to the next tile
                if (foundCorner) break;

                Vector posToCheck = current.add(direction);

                // Don't check out of bounds or null tiles.
                if (!inBoundingBox(posToCheck, width, height) || grid[posToCheck.x][posToCheck.y] == null) {
                    checked.add(posToCheck);
                    continue;
                }

                // We can find corners by checking adjacent tiles for spaces - outer corners will have 2
                // spaces in cardinal directions, and inner corners will have 1 space in a diagonal direction.
                // Edge tiles only have 1 cardinal space (or 2 diagonal)

                // Todo: this assumes that shape has at least 2 tile width/height. We could handle single tiles by checking for 3 cardinal spaces

                int cardinalSpaces = 0;
                int diagonalSpaces = 0;

                for (Vector cardinal : Directions.Cardinal.values()) {
                    Vector adjacent = posToCheck.add(cardinal);
                    if (inBoundingBox(adjacent, width, height)) {
                        if (grid[adjacent.x][adjacent.y] == null) {
                            // Todo: we could probably save time here by breaking from loop if we exceed 2 spaces
                            cardinalSpaces++;
                        }
                    }
                    else {
                        // Tiles that are not in bounds are considered the same as null tiles within bounds
                        cardinalSpaces++;
                    }
                }

                if (cardinalSpaces == 1) {
                    // Found a direction to continue looking in. (but make sure we aren't backtracking)
                    if (!checked.contains(posToCheck) && !posToCheck.equals(start)) {

                        // Iterate along edge until we find the next corner
                        Vector corner = findNextCorner(posToCheck, direction, checked, grid);

                        if (corner == null) {
                            // Technically this should be impossible
                            Log.e(LOG_TAG, "Couldn't find next corner?");
                            Log.v(LOG_TAG, "found " + sides.size() + " sides");
                            return sides;
                        }

                        side[1] = grid[corner.x][corner.y];
                        sides.add(side);
                        foundCorner = true;

                        // Once we return to start vector, we have iterated over the entire perimeter
                        // of shape and can return the sides
                        if (corner.equals(start)) {
                            return sides;
                        }

                        else {
                            // Start iterating again from next corner on grid
                            checked.add(corner);
                            queue.add(corner);
                            continue;
                        }
                    }
                }

                // It's possible that we would step immediately onto next corner.

                else if (cardinalSpaces == 2) {
                    // Found an outer corner

                    if (!checked.contains(posToCheck) && !posToCheck.equals(start)) {
                        Vector gridPos = grid[posToCheck.x][posToCheck.y];
                        side[1] = gridPos;
                        sides.add(side);
                        foundCorner = true;
                        checked.add(side[1]);
                        queue.add(posToCheck);
                        continue;
                    }
                }

                // Finally check diagonally adjacent tiles to see if this was an inner corner

                for (Vector diagonal : Directions.Diagonal.values()) {
                    Vector adjacent = posToCheck.add(diagonal);
                    if (inBoundingBox(adjacent, width, height)) {
                        if (grid[adjacent.x][adjacent.y] == null) {
                            diagonalSpaces++;
                        }
                    }
                    else {
                        diagonalSpaces++;
                    }
                }

                if (diagonalSpaces == 1) {
                    // Found an inner corner

                    // (make sure we aren't backtracking)
                    if (!checked.contains(posToCheck) && !posToCheck.equals(start)) {
                        Vector gridPos = grid[posToCheck.x][posToCheck.y];
                        side[1] = gridPos;
                        sides.add(side);
                        foundCorner = true;
                        checked.add(side[1]);
                        queue.add(posToCheck);
                    }
                }
            }
        }

        return sides;
    }

    private static Vector findNextCorner(Vector cell, Vector direction, ArrayList<Vector> checked, Vector[][] grid) {
        int width = grid.length;
        int height = grid[0].length;

        ArrayList<Vector> queue = new ArrayList<>();
        queue.add(cell);

        while (queue.size() > 0) {
            Vector start = queue.remove(0);
            Vector next = start.add(direction);

            int cardinalSpaces = 0;
            int diagonalSpaces = 0;

            for (Vector cardinal : Directions.Cardinal.values()) {
                Vector adjacent = next.add(cardinal);
                if (inBoundingBox(adjacent, width, height)) {
                    if (grid[adjacent.x][adjacent.y] == null) {
                        cardinalSpaces++;
                    }
                }
                else {
                    cardinalSpaces++;
                }
            }

            if (cardinalSpaces == 1) {
                // Continue looking in same direction.
                if (!checked.contains(next)) {
                    checked.add(next);
                    queue.add(next);
                    continue;
                }
            }

            else if (cardinalSpaces == 2) {
                // Found an outer corner
                if (!checked.contains(next)) {
                    checked.add(next);
                    return next;
                }
            }

            for (Vector diagonal : Directions.Diagonal.values()) {
                Vector adjacent = next.add(diagonal);
                if (inBoundingBox(adjacent, width, height)) {
                    if (grid[adjacent.x][adjacent.y] == null) {
                        diagonalSpaces++;
                    }
                }
                else {
                    diagonalSpaces++;
                }
            }

            if (diagonalSpaces == 1) {
                // Found an inner corner
                if (!checked.contains(next)) {
                    checked.add(next);
                    return next;
                }
            }
        }

        // If we didn't find a corner by this point, there was probably an issue with the region.
        // Handle null when checking response
        return null;
    }

    public static boolean inBoundingBox(Vector cell, int width, int height) {
        return (cell.x >= 0 && cell.x < width && cell.y >= 0 && cell.y < height);
    }

    public static float getDistance(int ax, int ay, int bx, int by) {
        return (float) Math.sqrt(Math.pow(ax - bx, 2) + Math.pow(ay - by, 2));
    }
}
