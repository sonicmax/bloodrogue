package com.sonicmax.bloodrogue.generator.factories;

import com.sonicmax.bloodrogue.engine.Entity;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Terrain;

public class TerrainFactory {
    public static Component[] createFloor(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;
        sprite.zLayer = 0;

        Terrain s = new Terrain(Terrain.FLOOR, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;
        physics.isDestructable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createWall(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;
        sprite.zLayer = 1;

        Terrain s = new Terrain(Terrain.WALL, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = true;
        physics.isTraversable = false;
        physics.isDestructable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createDoorway(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;
        sprite.zLayer = 0;

        Terrain s = new Terrain(Terrain.DOORWAY, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;
        physics.isDestructable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createBackground(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;
        sprite.zLayer = Sprite.BACKGROUND;
        sprite.zLayer = 0;

        Terrain s = new Terrain(Terrain.FLOOR, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        // Background tiles are effectively gaps in the terrain zLayer and have to be moved around
        physics.isTraversable = false;
        physics.isDestructable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createBorder(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;

        Terrain s = new Terrain(Terrain.BORDER, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = true;
        physics.isTraversable = false;
        physics.isDestructable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }
}
