package com.sonicmax.bloodrogue.engine.collisions;

import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Terrain;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;

import java.util.ArrayList;

public class FieldOfVisionCalculator {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private double darknessFactor; // Lower value = darker lighting. Has to be > 0

    private int[][] mapGrid;
    private ArrayList<Long>[][] objectGrid;
    private ComponentManager componentManager;
    private int startX;
    private int startY;
    private int fovRadius;
    private int width;
    private int height;
    private double[][] lightMap;
    private boolean[][] visitedTiles;

    private ArrayList<Vector2D> directions;

    public FieldOfVisionCalculator() {
        directions = new ArrayList<>(Directions.Diagonal.values());
    }

    public boolean[][] getVisitedTiles() {
        return this.visitedTiles;
    }

    public void setValues(int[][] mapGrid, ArrayList<Long>[][] objectGrid, int x, int y, int radius) {

        this.mapGrid = mapGrid;
        this.objectGrid = objectGrid;
        this.componentManager = ComponentManager.getInstance();
        this.startX = x;
        this.startY = y;
        this.fovRadius = radius;

        this.width = mapGrid.length;
        this.height = mapGrid[0].length;
        this.lightMap = new double[width][height];
        if (this.visitedTiles == null) {
            this.visitedTiles = new boolean[width][height];
        }

        this.darknessFactor = 1;
    }

    public double[][] calculate() {
        // Light starting cell
        lightMap[startX][startY] = 1 * darknessFactor;

        for (int i = 0; i < 4; i++) {
            Vector2D direction = directions.get(i);
            castLight(1, 1.0, 0.0, 0, direction.x(), direction.y(), 0);
            castLight(1, 1.0, 0.0, direction.x(), 0, 0, direction.y());
        }

        return lightMap;
    }

    public void setDarknessFactor(double factor) {
        darknessFactor = factor;
    }

    private void castLight(int row, double start, double end, int xx, int xy, int yx, int yy) {
        double newStart = 0.0;

        if (start < end) {
            return;
        }

        boolean blocked = false;

        for (int distance = row; distance <= fovRadius && !blocked; distance++) {
            int deltaY = -distance;

            for (int deltaX = -distance; deltaX <= 0; deltaX++) {
                int currentX = startX + (deltaX * xx) + (deltaY * xy);
                int currentY = startY + (deltaX * yx) + (deltaY * yy);
                double leftSlope = (deltaX - 0.5) / (deltaY + 0.5);
                double rightSlope = (deltaX + 0.5) / (deltaY - 0.5);

                if (!(currentX >= 0 && currentY >= 0 && currentX < width && currentY < height) || start < rightSlope) {
                    continue;
                } else if (end > leftSlope) {
                    break;
                }

                //check if it's within the lightable area and light if needed
                if (getRadius(deltaX, deltaY) <= fovRadius) {
                    double bright = (1 - (getRadius(deltaX, deltaY) / fovRadius));
                    lightMap[currentX][currentY] = bright * darknessFactor;
                    visitedTiles[currentX][currentY] = true;
                }

                if (blocked) { //previous cell was a blocking one
                    if (tileBlocksFov(new Vector2D(currentX, currentY))) {
                        newStart = rightSlope;
                        continue;
                    } else {
                        blocked = false;
                        start = newStart;
                    }
                } else {
                    if (tileBlocksFov(new Vector2D(currentX, currentY)) && distance < fovRadius) { //hit a wall within sight line
                        blocked = true;
                        castLight(distance + 1, start, leftSlope, xx, xy, yx, yy);
                        newStart = rightSlope;
                    }
                }
            }
        }
    }

    private double getRadius(int dx, int dy) {
        dx = Math.abs(dx);
        dy = Math.abs(dy);

        // Standard circular radius
        return Math.sqrt(dx * dx + dy * dy); // standard circular radius

        // Square - radius is longest axial distance
        // return Math.max(dx, dy); // SQUARE - radius is longest axial distance

        // Insane bit shifting sqrt thing
        // double d = dx * dx + dy * dy;
        // return Double.longBitsToDouble( ( ( Double.doubleToLongBits( d )-(1L<<52) )>>1 ) + ( 1L<<61 ) );
    }

    /**
     * Checks map grid and object grid to see whether FOV is blocked by this tile.
     *
     * @param position Vector to check
     * @return Returns true if tile blocks FOV
     */

    private boolean tileBlocksFov(Vector2D position) {
        int x = position.x();
        int y = position.y();

        long terrainEntity = mapGrid[x][y];
        Terrain stat = (Terrain) componentManager.getEntityComponent(terrainEntity, Terrain.class.getSimpleName());

        if (stat != null) {
            if (stat.type == Terrain.WALL || stat.type == Terrain.BORDER) {
                return true;
            }
        }

        int objectsSize = objectGrid[x][y].size();

        for (int i = 0; i < objectsSize; i++) {

            long objectEntity = objectGrid[x][y].get(i);
            Physics physics = (Physics) componentManager.getEntityComponent(objectEntity, Physics.class.getSimpleName());

            if (physics != null && physics.isBlocking) {
                return true;
            }
        }

        return false;
    }
}
