package com.sonicmax.bloodrogue.tilesets;

/**
 * Provides various helper methods to determine what type of tile a given sprite path should be handled as
 */

public class TileCategorySorter {
    public static boolean isWall(String tile) {
        switch (tile) {
            case BuildingTileset.WALL:
            case BuildingTileset.WALLPAPER_1:
            case BuildingTileset.WALLPAPER_2:
            case BuildingTileset.WALLPAPER_3:
            case BuildingTileset.WOOD_WALL:
            case BuildingTileset.BRICK_WALL:
            case ExteriorTileset.BLUE_WALL_1:
            case ExteriorTileset.GREEN_WALL_1:
            case ExteriorTileset.PINK_WALL_1:
            case ExteriorTileset.WHITE_WALL_1:
            case ExteriorTileset.WHITE_WALL_2:
            case ExteriorTileset.WHITE_WALL_3:
            case ExteriorTileset.RED_BRICKS_1:
            case ExteriorTileset.RED_BRICKS_2:
            case ExteriorTileset.RED_BRICKS_3:
            case ExteriorTileset.WHITE_BRICKS:
                return true;

            default:
                return false;
        }
    }

    public static boolean isFloor(String tile) {
        switch (tile) {
            case BuildingTileset.FLOOR:
            case BuildingTileset.MARBLE_FLOOR_1:
            case BuildingTileset.MARBLE_FLOOR_2:
            case BuildingTileset.WOOD_FLOOR_1:
            case BuildingTileset.WOOD_FLOOR_2:
            case BuildingTileset.WOOD_FLOOR_3:
                return true;

            default:
                return false;
        }
    }

    public static boolean isBorder(String tile) {
        switch (tile) {
            default:
                return false;
        }
    }

    public static boolean isDoorway(String tile) {
        switch (tile) {
            case BuildingTileset.DOORWAY:
                return true;

            default:
                return false;
        }
    }
}
