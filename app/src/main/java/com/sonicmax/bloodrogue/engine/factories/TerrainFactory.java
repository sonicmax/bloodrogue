package com.sonicmax.bloodrogue.engine.factories;

import com.sonicmax.bloodrogue.engine.objects.GameObject;

public class TerrainFactory {
    public static GameObject createFloor(int x, int y, String sprite) {
        GameObject object = new GameObject(x, y);
        object.type = GameObject.FLOOR;
        object.sprite = sprite;
        object.isTraversable = true;
        object.isBlocking = false;

        return object;
    }

    public static GameObject createWall(int x, int y, String sprite) {
        GameObject object = new GameObject(x, y);
        object.type = GameObject.WALL;
        object.sprite = sprite;
        object.isTraversable = false;
        object.isBlocking = true;

        return object;
    }

    public static GameObject createDoorway(int x, int y, String sprite) {
        GameObject object = new GameObject(x, y);
        object.type = GameObject.DOORWAY;
        object.sprite = sprite;
        object.isTraversable = true;
        object.isBlocking = false;

        return object;
    }

    public static GameObject createBorder(int x, int y, String sprite) {
        GameObject object = new GameObject(x, y);
        object.type = GameObject.BORDER;
        object.sprite = sprite;
        object.isTraversable = false;
        object.isBlocking = true;

        return object;
    }
}
