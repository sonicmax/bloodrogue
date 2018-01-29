package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.generator.factories.TerrainFactory;
import com.sonicmax.bloodrogue.tilesets.BuildingTileset;
import com.sonicmax.bloodrogue.tilesets.ExteriorTileset;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.tilesets.RuinsTileset;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

/**
 *  Class which decides which types of tiles to use depending on tileset selected in ProceduralGenerator.
 *  Contains methods which return String of path and methods that return GameObjects (Wall, Floor, etc)
 */

public class Tiler {
    private String tileset;

    public Tiler(String theme) {
        this.tileset = theme;
    }

    public void setTileset(String tileset) {
        this.tileset = tileset;
    }

    /*
    ---------------------------------------------
      Wall tiles
    ---------------------------------------------
    */

    public String getWallTile(int x, int y, int theme) {
        String sprite;

        switch (tileset) {
            case BuildingTileset.KEY:
                return BuildingTileset.WALLPAPER_3;

            case RuinsTileset.KEY:
                return RuinsTileset.WALL;

            default:
                return BuildingTileset.WALLPAPER_3;
        }
    }

    public String getMansionWallTilePath(int type) {
        switch (type) {
            case 0:
                return BuildingTileset.WALLPAPER_1;

            case 1:
                return BuildingTileset.WALLPAPER_2;

            case 2:
                return BuildingTileset.WALLPAPER_3;

            case 3:
                return BuildingTileset.WOOD_WALL;

            default:
                return BuildingTileset.WOOD_WALL;
        }
    }

    /*
    ---------------------------------------------
      Floor tiles
    ---------------------------------------------
    */

    public String getFloorTile(int x, int y, int type) {
        switch (tileset) {
            case BuildingTileset.KEY:
                return getMansionFloorTilePath(type);

            case RuinsTileset.KEY:
                return RuinsTileset.FLOOR;

            case ExteriorTileset.KEY:
                return ExteriorTileset.GRASS[new RandomNumberGenerator().getRandomInt(0, ExteriorTileset.GRASS.length - 1)];

            default:
                return BuildingTileset.FLOOR;
        }
    }

    public String getMansionFloorTilePath(int type) {
        switch (type) {
            case 0:
                return BuildingTileset.WOOD_FLOOR_1;

            case 1:
                return BuildingTileset.WOOD_FLOOR_2;

            case 2:
            default:
                return BuildingTileset.WOOD_FLOOR_3;
        }
    }

    /*
    ---------------------------------------------
      Border tiles
    ---------------------------------------------
    */

    public String getBorderTilePath() {
        switch (tileset) {
            case BuildingTileset.KEY:
                return BuildingTileset.BRICK_WALL;

            case RuinsTileset.KEY:
                return RuinsTileset.BORDER;

            case ExteriorTileset.KEY:
                return ExteriorTileset.GRASS_1;

            default:
                return GenericTileset.TRANSPARENT;
        }
    }

    public String getBorderObjectPath() {
        switch (tileset) {
            case BuildingTileset.KEY:
                return BuildingTileset.BRICK_WALL;

            case RuinsTileset.KEY:
                return RuinsTileset.BORDER;

            case ExteriorTileset.KEY:
                return ExteriorTileset.TREES[new RandomNumberGenerator().getRandomInt(0, ExteriorTileset.TREES.length - 1)];

            default:
                return GenericTileset.TRANSPARENT;
        }
    }

    /*
    ---------------------------------------------
      Barrier tiles
    ---------------------------------------------
    */

    public String getOpenDoorTilePath() {
        switch (tileset) {
            case BuildingTileset.KEY:
                return BuildingTileset.DOUBLE_DOORS_OPEN;

            default:
                return BuildingTileset.OPEN_DOOR;
        }
    }

    public String getClosedDoorTilePath() {
        switch (tileset) {
            case BuildingTileset.KEY:
                return BuildingTileset.DOUBLE_DOORS;

            default:
                return BuildingTileset.CLOSED_DOOR;
        }
    }

    public Component[] getDoorwayTile(int x, int y) {
        String sprite;

        switch (tileset) {
            case BuildingTileset.KEY:
                sprite = BuildingTileset.WOOD_FLOOR_1;
                break;

            case RuinsTileset.KEY:
                sprite = RuinsTileset.FLOOR;
                break;

            default:
                sprite = BuildingTileset.WOOD_FLOOR_1;
                break;
        }

        return TerrainFactory.createDoorway(x, y, sprite);
    }
}
