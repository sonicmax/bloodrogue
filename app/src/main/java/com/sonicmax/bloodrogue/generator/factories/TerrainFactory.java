package com.sonicmax.bloodrogue.generator.factories;

import com.sonicmax.bloodrogue.engine.Entity;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Stationary;

public class TerrainFactory {
    public static Component[] createFloor(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;

        Stationary s = new Stationary(Stationary.FLOOR, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;

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

        Stationary s = new Stationary(Stationary.WALL, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = true;
        physics.isTraversable = false;

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

        Stationary s = new Stationary(Stationary.DOORWAY, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;

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

        Stationary s = new Stationary(Stationary.BORDER, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = true;
        physics.isTraversable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }
}
