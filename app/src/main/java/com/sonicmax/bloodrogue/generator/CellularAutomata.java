package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

public class CellularAutomata {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private int birthLimit = 4;
    private int deathLimit = 3;
    private int numberOfSmoothingSteps = 2;
    private float chanceToStartAlive = 0.4F;
    private boolean[][] cellMap;
    private RandomNumberGenerator rng;

    public CellularAutomata() {
        rng = new RandomNumberGenerator();
    }

    public void setParams(int birthLimit, int deathLimit, int numberOfSmoothingSteps, float chanceToStartAlive) {
        this.birthLimit = birthLimit;
        this.deathLimit = deathLimit;
        this.numberOfSmoothingSteps = numberOfSmoothingSteps;
        this.chanceToStartAlive = chanceToStartAlive;
    }

    public void setDefaultParams() {
        birthLimit = 4;
        deathLimit = 3;
        numberOfSmoothingSteps = 2;
        chanceToStartAlive = 0.4f;
    }

    public boolean[][] generate(Chunk chunk) {
        cellMap = new boolean[chunk.width][chunk.height];

        for (int x = 0; x < chunk.width; x++) {
            for (int y = 0; y < chunk.height; y++) {
                cellMap[x][y] = false;
            }
        }

        initialiseCellMap(chunk);

        for (int i = 0; i < numberOfSmoothingSteps; i++) {
            cellMap = doSimulationStep(chunk);
        }

        return cellMap;
    }

    private void initialiseCellMap(Chunk chunk) {
        for (int x = 1; x < chunk.width - 1; x++) {
            for (int y = 1; y < chunk.height - 1; y++) {
                if (rng.getRandomFloat(0F, 1F) < chanceToStartAlive){
                    cellMap[x][y] = true;
                }
            }
        }
    }

    private boolean[][] doSimulationStep(Chunk chunk) {
        boolean[][] newMap = new boolean[chunk.width][chunk.height];

        for (int x = 0; x < chunk.width; x++) {
            for (int y = 0; y < chunk.height; y++) {
                newMap[x][y] = false;
            }
        }

        //Loop over each row and column of the map
        for (int x = 1; x < chunk.width - 1; x++) {
            for (int y = 1; y < chunk.height - 1; y++) {
                int neighbours = countAliveNeighbours(x, y, chunk);

                // The new value is based on our simulation rules
                // First, if a cell is alive but has too few neighbours, kill it.

                if (cellMap[x][y]) {
                    if (neighbours < deathLimit) {
                        newMap[x][y] = false;
                    }
                    else {
                        newMap[x][y] = true;
                    }
                }

                //Otherwise, if the cell is dead now, check if it has the right number of neighbours to be 'born'
                else {
                    if (neighbours > birthLimit) {
                        newMap[x][y] = true;
                    }
                    else {
                        newMap[x][y] = false;
                    }
                }
            }
        }

        return newMap;
    }

    private int countAliveNeighbours(int x, int y, Chunk chunk) {
        int count = 0;

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                int neighbourX = x + i;
                int neighbourY = y + j;

                // Do nothing if we're looking at the middle point
                if (i == 0 && j == 0) continue;

                    // In case the index we're looking at it off the edge of the chunk
                else if (neighbourX < 0 || neighbourY < 0
                        || neighbourX >= chunk.width || neighbourY >= chunk.height) {

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
