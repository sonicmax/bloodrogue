package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.generator.factories.TerrainFactory;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.tilesets.MansionTileset;
import com.sonicmax.bloodrogue.tilesets.RuinsTileset;

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
            case MansionTileset.KEY:
                sprite = MansionTileset.WALLPAPER_3;
                break;

            case RuinsTileset.KEY:
                sprite = RuinsTileset.WALL;
                break;

            default:
                sprite = MansionTileset.WALLPAPER_3;
                break;
        }

        return TerrainFactory.createWall(x, y, sprite);
    }

    public String getMansionWallTilePath(int type) {
        switch (type) {
            case 0:
                return MansionTileset.WALLPAPER_1;

            case 1:
                return MansionTileset.WALLPAPER_2;

            case 2:
                return MansionTileset.WALLPAPER_3;

            case 3:
                return MansionTileset.WOOD_WALL;

            default:
                return MansionTileset.WOOD_WALL;
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
            case MansionTileset.KEY:
                sprite = getMansionFloorTilePath(type);
                break;

            case RuinsTileset.KEY:
                sprite = RuinsTileset.FLOOR;
                break;

            default:
                sprite = MansionTileset.FLOOR;
                break;
        }

        return TerrainFactory.createFloor(x, y, sprite);
    }

    public String getMansionFloorTilePath(int type) {
        switch (type) {
            case 0:
                return MansionTileset.WOOD_FLOOR_1;

            case 1:
                return MansionTileset.WOOD_FLOOR_2;

            case 2:
            default:
                return MansionTileset.WOOD_FLOOR_3;
        }
    }

    /*
    ---------------------------------------------
      Border tiles
    ---------------------------------------------
    */

    public String getBorderTilePath() {
        switch (mTheme) {
            case MansionTileset.KEY:
                return MansionTileset.BRICK_WALL;

            case RuinsTileset.KEY:
                return RuinsTileset.BORDER;

            default:
                return GenericTileset.DEFAULT_BORDER;
        }
    }

    /*
    ---------------------------------------------
      Barrier tiles
    ---------------------------------------------
    */

    public String getOpenDoorTilePath() {
        switch (mTheme) {
            case MansionTileset.KEY:
                return MansionTileset.DOUBLE_DOORS_OPEN;

            default:
                return MansionTileset.OPEN_DOOR;
        }
    }

    public String getClosedDoorTilePath() {
        switch (mTheme) {
            case MansionTileset.KEY:
                return MansionTileset.DOUBLE_DOORS;

            default:
                return MansionTileset.CLOSED_DOOR;
        }
    }

    public Component[] getDoorwayTile(int x, int y) {
        String sprite;

        switch (mTheme) {
            case MansionTileset.KEY:
                sprite = MansionTileset.WOOD_FLOOR_1;
                break;

            case RuinsTileset.KEY:
                sprite = RuinsTileset.FLOOR;
                break;

            default:
                sprite = MansionTileset.WOOD_FLOOR_1;
                break;
        }

        return TerrainFactory.createDoorway(x, y, sprite);
    }
}
