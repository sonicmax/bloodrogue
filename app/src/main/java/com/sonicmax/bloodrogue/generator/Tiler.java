package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.factories.TerrainFactory;
import com.sonicmax.bloodrogue.tilesets.All;
import com.sonicmax.bloodrogue.tilesets.Mansion;
import com.sonicmax.bloodrogue.tilesets.Ruins;

/**
 *  Class which decides which types of tiles to use depending on theme selected in ProceduralGenerator.
 *  Contains methods which return String of path and methods that return GameObjects (Wall, Floor, etc)
 */

public class Tiler {
    private final String mTheme;

    public Tiler(String theme) {
        this.mTheme = theme;
    }

    /*
    ---------------------------------------------
      Wall tiles
    ---------------------------------------------
    */

    public Component[] getWallTile(int x, int y) {
        String sprite;

        switch (mTheme) {
            case Mansion.KEY:
                sprite = Mansion.WALLPAPER_3;
                break;

            case Ruins.KEY:
                sprite = Ruins.WALL;
                break;

            default:
                sprite = Mansion.WALLPAPER_3;
                break;
        }

        return TerrainFactory.createWall(x, y, sprite);
    }

    public String getMansionWallTilePath(int type) {
        switch (type) {
            case 0:
                return Mansion.WALLPAPER_1;

            case 1:
                return Mansion.WALLPAPER_2;

            case 2:
                return Mansion.WALLPAPER_3;

            case 3:
                return Mansion.WOOD_WALL;

            default:
                return Mansion.WOOD_WALL;
        }
    }

    /*
    ---------------------------------------------
      Floor tiles
    ---------------------------------------------
    */

    public Component[] getFloorTile(int x, int y, int type) {
        String sprite;

        switch (mTheme) {
            case Mansion.KEY:
                sprite = getMansionFloorTilePath(type);
                break;

            case Ruins.KEY:
                sprite = Ruins.FLOOR;
                break;

            default:
                sprite = Mansion.FLOOR;
                break;
        }

        return TerrainFactory.createFloor(x, y, sprite);
    }

    public String getMansionFloorTilePath(int type) {
        switch (type) {
            case 0:
                return Mansion.MARBLE_FLOOR_1;

            case 1:
                return Mansion.TILED_FLOOR_1;

            case 2:
                return Mansion.WOOD_FLOOR_2;

            case 3:
                return Mansion.WOOD_FLOOR_1;

            default:
                return Mansion.WOOD_FLOOR_1;
        }
    }

    /*
    ---------------------------------------------
      Border tiles
    ---------------------------------------------
    */

    public String getBorderTilePath() {
        switch (mTheme) {
            case Mansion.KEY:
                return Mansion.BRICK_WALL;

            case Ruins.KEY:
                return Ruins.BORDER;

            default:
                return All.DEFAULT_BORDER;
        }
    }

    /*
    ---------------------------------------------
      Barrier tiles
    ---------------------------------------------
    */

    public String getOpenDoorTilePath() {
        switch (mTheme) {
            case Mansion.KEY:
                return Mansion.DOUBLE_DOORS_OPEN;

            default:
                return Mansion.OPEN_DOOR;
        }
    }

    public String getClosedDoorTilePath() {
        switch (mTheme) {
            case Mansion.KEY:
                return Mansion.DOUBLE_DOORS;

            default:
                return Mansion.CLOSED_DOOR;
        }
    }

    public Component[] getDoorwayTile(int x, int y) {
        String sprite;

        switch (mTheme) {
            case Mansion.KEY:
                sprite = Mansion.WOOD_FLOOR_1;
                break;

            case Ruins.KEY:
                sprite = Ruins.FLOOR;
                break;

            default:
                sprite = Mansion.WOOD_FLOOR_1;
                break;
        }

        return TerrainFactory.createDoorway(x, y, sprite);
    }
}
