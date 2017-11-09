package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.engine.objects.GameObject;
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

    public GameObject getWallTile(int x, int y) {
        GameObject object = new GameObject(x, y);
        object.type = GameObject.WALL;
        object.setBlocking(true);
        object.setTraversable(false);

        switch (mTheme) {
            case Mansion.KEY:
                object.setSprite(Mansion.WALLPAPER_3);
                return object;

            case Ruins.KEY:
                object.setSprite(Ruins.WALL);
                return object;

            default:
                object.setSprite(Mansion.WALLPAPER_3);
                return object;
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

    public GameObject getFloorTile(int x, int y, int type) {
        GameObject floor = new GameObject(x, y);
        floor.type = GameObject.FLOOR;
        floor.setBlocking(false);
        floor.setTraversable(true);

        switch (mTheme) {
            case Mansion.KEY:
                floor.setSprite(getMansionFloorTilePath(type));
                return floor;

            case Ruins.KEY:
                floor.setSprite(Ruins.FLOOR);
                return floor;

            default:
                floor.setSprite(Mansion.FLOOR);
                return floor;
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

    public GameObject getDoorwayTile(int x, int y) {
        GameObject doorway = new GameObject(x, y);
        doorway.type = GameObject.DOORWAY;
        doorway.setBlocking(false);
        doorway.setTraversable(true);

        switch (mTheme) {
            case Mansion.KEY:
                doorway.setSprite(Mansion.WOOD_FLOOR_1);
                return doorway;

            case Ruins.KEY:
                doorway.setSprite(Ruins.FLOOR);
                return doorway;

            default:
                doorway.setSprite(Mansion.WOOD_FLOOR_1);
                return doorway;
        }
    }
}
