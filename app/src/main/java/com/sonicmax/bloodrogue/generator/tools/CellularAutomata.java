package com.sonicmax.bloodrogue.generator.tools;

import com.sonicmax.bloodrogue.generator.Chunk;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;

import java.util.ArrayList;

public class CellularAutomata {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final int NONE = -1;

    private int birthLimit;
    private int deathLimit;
    private int numberOfSmoothingSteps;
    private float chanceToStartAlive;
    private float chanceToDie;
    private float chanceToResurrect;
    private boolean bringAlive;
    private boolean killAlive;

    private boolean[][] cellMap;
    private int[][] edgeMap;
    private Chunk chunk;
    private RandomNumberGenerator rng;

    public CellularAutomata() {
        rng = new RandomNumberGenerator();
        useDefaultParams();
    }

    public void setParams(int birthLimit, int deathLimit, int numberOfSmoothingSteps, float chanceToStartAlive) {
        this.birthLimit = birthLimit;
        this.deathLimit = deathLimit;
        this.numberOfSmoothingSteps = numberOfSmoothingSteps;
        this.chanceToStartAlive = chanceToStartAlive;
    }

    public CellularAutomata setBirthLimit(int limit) {
        birthLimit = limit;
        return this;
    }

    public CellularAutomata setDeathLimit(int limit) {
        deathLimit = limit;
        return this;
    }

    public CellularAutomata setChanceToStartAlive(float chance) {
        chanceToStartAlive = chance;
        return this;
    }

    public CellularAutomata setChanceToDie(float chance) {
        chanceToDie = chance;
        return this;
    }

    public CellularAutomata setChanceToResurrect(float chance) {
        chanceToResurrect = chance;
        return this;
    }

    public CellularAutomata setBringAlive(boolean value) {
        bringAlive = value;
        return this;
    }

    public CellularAutomata setKillAlive(boolean value) {
        killAlive = value;
        return this;
    }

    public void useDefaultParams() {
        birthLimit = 4;
        deathLimit = 3;
        numberOfSmoothingSteps = 2;
        chanceToStartAlive = 0.4f;
        chanceToDie = 0.0f;
        bringAlive = false;
        killAlive = false;
    }

    public int[][] getEdgeMap() {
        return edgeMap;
    }

    public boolean[][] generate(Chunk chunk) {
        this.chunk = chunk;
        cellMap = new boolean[chunk.width][chunk.height];
        edgeMap = Array2DHelper.fillIntArray(chunk.width, chunk.height, NONE);

        initialiseCellMap();

        for (int i = 0; i < numberOfSmoothingSteps; i++) {
            cellMap = doSimulationStep();
        }

        return cellMap;
    }

    public void prepareSimulation(Chunk chunk) {
        this.chunk = chunk;
        cellMap = new boolean[chunk.width][chunk.height];
        edgeMap = Array2DHelper.fillIntArray(chunk.width, chunk.height, NONE);
        initialiseCellMap();
    }

    public ArrayList<Vector2D> getVectors() {
        ArrayList<Vector2D> vectors = new ArrayList<>();

        for (int x = 0; x < cellMap.length; x++) {
            for (int y = 0; y < cellMap[0].length; y++) {
                if (cellMap[x][y]) {
                    vectors.add(new Vector2D(x, y));
                }
            }
        }

        return vectors;
    }

    private void initialiseCellMap() {
        for (int x = 1; x < chunk.width - 1; x++) {
            for (int y = 1; y < chunk.height - 1; y++) {
                if (rng.getRandomFloat(0F, 1F) < chanceToStartAlive){
                    cellMap[x][y] = true;
                }
            }
        }
    }

    public boolean[][] doSimulationStep() {
        boolean[][] newMap = new boolean[chunk.width][chunk.height];

        for (int x = 1; x < chunk.width - 1; x++) {
            for (int y = 1; y < chunk.height - 1; y++) {
                int neighbours = countAliveNeighbours(x, y);

                // First, if a cell is alive but has too few neighbours, kill it.

                if (cellMap[x][y]) {
                    if (neighbours < deathLimit) {
                        newMap[x][y] = false;

                    } else if (killAlive && rng.getRandomFloat(0F, 1F) < chanceToDie){
                        newMap[x][y] = false;
                    }
                    else {
                        newMap[x][y] = true;
                    }
                }

                // Otherwise, if the cell is dead now, check if it has the right number of neighbours to be 'born'

                else {
                    if (neighbours > birthLimit) {
                        newMap[x][y] = true;

                    } else if (bringAlive && rng.getRandomFloat(0F, 1F) < chanceToResurrect){
                        newMap[x][y] = true;
                    }
                }
            }
        }

        cellMap = newMap;
        doFinalNeighbourCount();

        return newMap;
    }

    private void doFinalNeighbourCount() {
        for (int x = 1; x < chunk.width - 1; x++) {
            for (int y = 1; y < chunk.height - 1; y++) {
                if (cellMap[x][y]) {
                    int neighbours = countAliveNeighbours(x, y);
                    int intensity;

                    switch (neighbours) {
                        case 0:
                            intensity = 0;
                            break;
                        case 1:
                        case 2:
                        case 3:
                            intensity = 1;
                            break;
                        case 4:
                        case 5:
                        case 6:
                            intensity = 2;
                            break;
                        case 7:
                        case 8:
                            intensity = 3;
                            break;
                        default:
                            intensity = -1;
                    }

                    edgeMap[x][y] = intensity;
                }

                else {
                    edgeMap[x][y] = -1;
                }
            }
        }
    }

    private int countAliveNeighbours(int x, int y) {
        int count = 0;

        // iteration order: bottom-left, left, top-left, bottom, top, bottom-right, right, top-right
        // (-1 -1), (-1 0), (-1, 1), (0 -1), (0 1), (1 -1), (1 0), (1 1)

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                int neighbourX = x + i;
                int neighbourY = y + j;

                // Do nothing if we're looking at the middle point.
                if (i == 0 && j == 0) continue;

                // In case the index we're looking at it off the edge of the chunk
                else if (neighbourX < 0 || neighbourY < 0 || neighbourX >= chunk.width || neighbourY >= chunk.height) {
                    count++;
                }

                // Otherwise, a normal check of the neighbour
                else if (cellMap[neighbourX][neighbourY]) {
                    count++;
                }
            }
        }

        return count;
    }
}
