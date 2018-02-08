package com.sonicmax.bloodrogue.generator.tools;

import android.util.Log;
import android.util.SparseIntArray;

import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.generator.Cell;
import com.sonicmax.bloodrogue.generator.Chunk;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a maze using provided parameters. The generate() method returns a boolean[][] telling us
 * which tiles to carve. getJunctions() returns an ArrayList of Vectors to aid in door placement.
 * To connect regions that have already been generated elsewhere, we can use carveChunkFromMaze()
 * or excludeChunkFromMaze (for regions that have already been carved) to define a new region
 * that will be connected to during maze generation.
 */

public class MazeGenerator {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final boolean CARVABLE = true;
    private final boolean NOT_CARVABLE = false;

    private Chunk chunk;
    private boolean[][] carvedTiles;
    private boolean[][] excludedTiles;
    private ArrayList<Vector> junctions;

    private RandomNumberGenerator rng;

    private int currentRegion;
    private int[][] mapRegions;

    private int extraConnectorChance;
    private int windingPercent;

    public MazeGenerator() {
        rng = new RandomNumberGenerator();
        extraConnectorChance = 40;
        windingPercent = 35;
    }

    public void setWindingPercent(int windingPercent) {
        this.windingPercent = windingPercent;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
        init();
    }

    public void carveChunkFromMaze(Chunk chunkToCarve) {
        startRegion();

        if (chunk == null) {
            Log.e(LOG_TAG, "Base chunk was null - call setChunk() first");
            return;
        }

        if (!AxisAlignedBoxTester.test(chunkToCarve, chunk)) {
            // Log.d(LOG_TAG, "Chunk to carve not contained in bounds of original chunk");
        }

        else {
            for (int x = chunkToCarve.x; x < chunkToCarve.x + chunkToCarve.width; x++) {
                for (int y = chunkToCarve.y; y < chunkToCarve.y + chunkToCarve.height; y++) {
                    Vector cell = new Vector(x - chunk.x, y - chunk.y);
                    if (inBounds(cell)) {
                        carve(cell);
                    }
                }
            }
        }
    }

    public void excludeChunkFromMaze(Chunk chunkToCarve) {
        startRegion();

        if (chunk == null) {
            Log.e(LOG_TAG, "Base chunk was null - call setChunk() first");
            return;
        }

        if (!AxisAlignedBoxTester.test(chunkToCarve, chunk)) {
            // Log.d(LOG_TAG, "Chunk to carve not contained in bounds of original chunk");
        }

        else {
            for (int x = chunkToCarve.x; x < chunkToCarve.x + chunkToCarve.width; x++) {
                for (int y = chunkToCarve.y; y < chunkToCarve.y + chunkToCarve.height; y++) {
                    excludedTiles[x][y] = true;
                }
            }
        }
    }

    public ArrayList<Vector> getJunctions() {
        return junctions;
    }

    public int getCurrentRegion() {
        return currentRegion;
    }

    public int[][] getMapRegions() {
        return mapRegions;
    }

    public boolean[][] generate() {
        for (int x = 0; x < chunk.width; x++) {
            for (int y = 0; y < chunk.height; y++) {
                Vector tile = new Vector(x, y);
                if (!carvedTiles[x][y] && !excludedTiles[x][y] && adjacentCellsAreCarvable(tile)) {
                    carveMaze(tile);
                }
            }
        }

        connectRegions();
        removeDeadEnds();

        return carvedTiles;
    }

    private void init() {
        junctions = new ArrayList<>();
        carvedTiles = new boolean[chunk.width][chunk.height];
        excludedTiles = new boolean[chunk.width][chunk.height];
        currentRegion = -1;
        mapRegions = Array2DHelper.fillIntArray(chunk.width, chunk.height, currentRegion);
    }

    private void carveMaze(Vector start) {
        ArrayList<Vector> cells = new ArrayList<>();
        Vector lastCell = null;
        startRegion();
        carve(start);
        cells.add(start);

        while (cells.size() > 0) {
            Vector cell = cells.remove(cells.size() - 1);

            // See which adjacent cells are open.
            HashMap<String, Vector> unmadeCells = new HashMap<>();

            HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

            for (Map.Entry pair : adjacentCells.entrySet()) {
                String direction = (String) pair.getKey();
                Vector adjacentCell = (Vector) pair.getValue();

                if (canCarve(adjacentCell, getVectorForDirection(direction))) {
                    unmadeCells.put(adjacentCell.toString(), adjacentCell);
                }
            }

            if (unmadeCells.size() > 0) {
                Vector firstCarve;

                if (lastCell != null && unmadeCells.containsKey(lastCell.toString()) && rng.getRandomInt(0, 100) > windingPercent) {
                    firstCarve = lastCell;
                } else {
                    firstCarve = (Vector) unmadeCells.values().toArray()[rng.getRandomInt(0, unmadeCells.size() - 1)];
                }

                Vector secondCarve = firstCarve.add(getVectorForDirection(firstCarve.getDirection()));

                carve(firstCarve);
                carve(secondCarve);

                cells.add(secondCarve);
                lastCell = firstCarve;
            }

            else {
                // No adjacent uncarved cells.
                if (cells.size() > 0) {
                    cells.remove(cells.size() - 1);
                }

                // This path has ended.
                lastCell = null;
            }
        }
    }

    private void connectRegions() {
        // Find all of the tiles that can connect two (or more) regions.
        HashMap<Vector, Set> connectorRegions = new HashMap<>();

        for (int x = 1; x < chunk.width; x++) {
            for (int y = 1; y < chunk.height; y++) {
                Vector cell = new Vector(x, y, "");

                // Ignore everything but walls
                if (carvedTiles[cell.x][cell.y]) continue;
                if (excludedTiles[cell.x][cell.y]) continue;

                Set<Integer> regions = new HashSet<>();

                HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

                for (Vector adjacentCell : adjacentCells.values()) {
                    if (!inBounds(adjacentCell)) continue;

                    int region = mapRegions[adjacentCell.x()][adjacentCell.y()];
                    if (region > -1) {
                        regions.add(region);
                    }
                }

                if (regions.size() < 2) continue;

                connectorRegions.put(cell, regions);
            }
        }

        // Get array of connecting vectors from connectorRegions
        ArrayList<Vector> connectors = new ArrayList<>(connectorRegions.keySet());

        // Keep track of which regions have been merged. This maps an original region index to the one it has been merged to.
        SparseIntArray merged = new SparseIntArray();
        Set<Integer> openRegions = new HashSet<>();

        for (int i = 0; i <= currentRegion; i++) {
            merged.put(i, i);
            openRegions.add(i);
        }

        // Keep connecting regions until we're down to one.
        while (openRegions.size() > 1 && connectors.size() > 0) {
            Vector connector = connectors.get(rng.getRandomInt(0, connectors.size() - 1));

            carvedTiles[connector.x][connector.y] = true;
            junctions.add(connector);

            // Merge the connected regions. We'll pick one region (arbitrarily) and map all of the other regions to its index.
            ArrayList<Integer> arrayFromConnector = new ArrayList<>(connectorRegions.get(connector));
            ArrayList<Integer> regions = new ArrayList<>();

            for (int region : arrayFromConnector) {
                regions.add(merged.get(region));
            }

            int dest = regions.get(0);
            List<Integer> sources = regions.subList(1, regions.size());

            // Merge all of the affected regions. We have to look at *all* of the
            // regions because other regions may have previously been merged with
            // some of the ones we're merging now.
            for (int i = 0; i <= currentRegion; i++) {
                if (sources.contains(merged.get(i))) {
                    merged.put(i, dest);
                }
            }

            // The sources are no longer in use.
            for (int source : sources) {
                openRegions.remove(source);
            }

            // Remove any connectors that aren't needed anymore
            Iterator<Vector> it = connectors.iterator();

            while (it.hasNext()) {
                Vector pos = it.next();

                // Don't allow connectors right next to each other.
                if (connector.subtract(pos).getMagnitude() < 2) {
                    it.remove();
                    continue;
                }

                // If the connector no long spans different regions, we don't need it.

                ArrayList<Integer> regionsArray = new ArrayList<>(connectorRegions.get(pos));
                HashSet<Integer> spannedRegions = new HashSet<>();

                for (int region : regionsArray) {
                    spannedRegions.add(merged.get(region));
                }

                if (spannedRegions.size() <= 1)  {
                    // This connecter isn't needed, but connect it occasionally so that the maze isn't singly-connected.
                    if (rng.getRandomInt(0, 100) < extraConnectorChance) {
                        carvedTiles[pos.x][pos.y] = true;
                        junctions.add(pos);
                    }

                    it.remove();
                }
            }
        }
    }

    private void removeDeadEnds() {
        boolean done = false;

        while (!done) {
            done = true;

            for (int x = 1; x < chunk.width - 1; x++) {
                for (int y = 1; y < chunk.height - 1; y++) {
                    Vector cell = new Vector(x, y);
                    if (!carvedTiles[cell.x][cell.y]) continue;

                    // If it only has one exit, it's a dead end.
                    int exits = 0;

                    HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

                    for (Vector adjacentCell : adjacentCells.values()) {
                        if (carvedTiles[adjacentCell.x][adjacentCell.y]) {
                            exits++;
                        }
                    }

                    if (exits != 1) continue;

                    done = false;

                    carvedTiles[cell.x][cell.y] = false;
                }
            }
        }
    }

    private void startRegion() {
        currentRegion++;
    }

    private void carve(Vector pos) {
        carvedTiles[pos.x][pos.y] = true;
        mapRegions[pos.x()][pos.y()] = currentRegion;
    }

    /*
    ---------------------------------------------
    Helper methods
    ---------------------------------------------
    */

    private HashMap<String, Vector> getAdjacentCells(Vector coords, int lookahead, boolean directlyAdjacent) {
        int x = coords.x();
        int y = coords.y();

        HashMap<String, Vector> cells = new HashMap<>();

        cells.put("up", new Cell(x, y + lookahead, "up"));
        cells.put("right", new Cell(x + lookahead, y, "right"));
        cells.put("down", new Cell(x, y - lookahead, "down"));
        cells.put("left", new Cell(x - lookahead, y, "left"));

        if (directlyAdjacent) {
            return cells;
        }

        else {
            // Include diagonally adjacent cells
            cells.put("up-right", new Cell(x + lookahead, y + lookahead, "up-right"));
            cells.put("up-left", new Cell(x - lookahead, y + lookahead, "up-left"));
            cells.put("down-right", new Cell(x + lookahead, y - lookahead, "down-right"));
            cells.put("down-left", new Cell(x - lookahead, y - lookahead, "down-left"));
            return cells;
        }
    }

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < chunk.width && cell.y() >= 0 && cell.y() < chunk.height);
    }

    private boolean canCarve(Vector cell, Vector direction) {
        return inBounds(cell)
                && adjacentCellsAreCarvable(cell)
                && adjacentCellsAreCarvable(cell.add(direction));
    }

    private boolean adjacentCellsAreCarvable(Vector cell) {
        HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, false);

        String[] oppositeDirections = getOppositeWithDiagonals(cell.getDirection());

        // We only want to check squares in direction that maze is being carved in
        for (String direction : oppositeDirections) {
            if (adjacentCells.containsKey(direction)) {
                adjacentCells.remove(direction);
            }
        }

        for (Vector adjacent : adjacentCells.values()) {

            if (inBounds(adjacent)) {
                if (carvedTiles[adjacent.x][adjacent.y] || excludedTiles[adjacent.x][adjacent.y]) {
                    return false;
                }
            }

            else {
                return false;
            }
        }

        return true;
    }

    private String[] getOppositeWithDiagonals (String direction) {
        switch (direction) {
            case "up":
                return new String[] {"down-left", "down", "down-right"};

            case "right":
                return new String[] {"up-left", "left", "down-left"};

            case "down":
                return new String[] {"up-left", "up", "up-right"};

            case "left":
                return new String[] {"up-right", "right", "down-right"};

            default:
                return new String[] {};
        }
    }

    private Vector getVectorForDirection(String direction) {
        switch (direction) {
            case "up":
                return new Vector(0, 1);

            case "right":
                return new Vector(1, 0);

            case "down":
                return new Vector(0, -1);

            case "left":
                return new Vector(-1, 0);

            default:
                return new Vector(0, 0);
        }
    }
}
