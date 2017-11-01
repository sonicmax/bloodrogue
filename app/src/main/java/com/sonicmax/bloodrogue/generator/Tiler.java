package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.engine.objects.Floor;
import com.sonicmax.bloodrogue.engine.objects.Wall;
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

    public Wall getWallTile(int x, int y) {
        switch (mTheme) {
            case Mansion.KEY:
                return new Wall(x, y, Mansion.WALLPAPER_3);

            case Ruins.KEY:
                return new Wall(x, y, Ruins.WALL);

            default:
                return new Wall(x, y, Mansion.WALL);
        }
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

    public Floor getFloorTile(int x, int y, int type) {
        switch (mTheme) {
            case Mansion.KEY:
                return new Floor(x, y, getMansionFloorTilePath(type));

            case Ruins.KEY:
                return new Floor(x, y, Ruins.FLOOR);

            default:
                return new Floor(x, y, Mansion.FLOOR);
        }
    }

    public String getMansionFloorTilePath(int type) {
        switch (type) {
            case 0:
                return Mansion.MARBLE_FLOOR_1;

            case 1:
                return Mansion.TILED_FLOOR_1;

            case 2:
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
      Door tiles
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

    public Floor getDoorwayTile(int x, int y) {
        switch (mTheme) {
            case Mansion.KEY:
                return new Floor(x, y, Mansion.WOOD_FLOOR_1, Floor.IS_DOORWAY);

            case Ruins.KEY:
                return new Floor(x, y, Ruins.FLOOR, Floor.IS_DOORWAY);

            default:
                return new Floor(x, y, Mansion.WOOD_FLOOR_1, Floor.IS_DOORWAY);
        }
    }
}
