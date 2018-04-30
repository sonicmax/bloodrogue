package com.sonicmax.bloodrogue.generator.factories;

import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.Entity;
import com.sonicmax.bloodrogue.engine.components.Blood;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Terrain;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import java.util.ArrayList;

public class DecalFactory {

    public static Component[] getCorpse(int x, int y, String type) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.DYNAMIC;
        sprite.wrapToCube = false;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;

        switch(type) {
            case "Zombie":
                sprite.path = "sprites/zombie_corpse.png";
                break;

            case "Giant Rat":
                sprite.path = "sprites/giant_rat_corpse.png";
                break;

            case "Ogre":
            case "Great Ogre":
                sprite.path = "sprites/ogre_corpse.png";
                break;

            case "Giant Komodo":
                sprite.path = "sprites/giant_komodo_corpse.png";
                break;

            case "Green Slime":
                sprite.path = "sprites/green_slime_corpse.png";
                break;

            case "Purple Slime":
                sprite.path = "sprites/purple_slime_corpse.png";
                break;

            case "Giant Bug":
                sprite.path = "sprites/cockroach_corpse.png";
                break;

            case "Spirit":
                sprite.path = "sprites/ogre_spirit_corpse.png";
                break;

            default:
                sprite.path = "sprites/transparent.png";
                break;
        }

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createBloodSplat(Position position, Blood blood, long[][] mapGrid) {
        ArrayList<Vector2D> directions = new ArrayList(Directions.All.values());
        int random = new RandomNumberGenerator().getRandomInt(0, directions.size() - 1);
        Vector2D direction = directions.get(random);
        Vector2D location = new Vector2D(position.x, position.y).add(direction);
        int x = location.x();
        int y = location.y();

        if (x < 0 || x >= mapGrid.length && y < 0 || y >= mapGrid[0].length) return null;

        // BLOOD_DROPS and BLOOD_DROPS_WALL have same length
        RandomNumberGenerator rng = new RandomNumberGenerator();

        String[] bloodSprites = getBloodForActor(blood);

        int bloodIndex = rng.getRandomInt(0, bloodSprites.length - 1);

        long terrain = mapGrid[x][y];
        Terrain stat = (Terrain) ComponentManager.getInstance().getEntityComponent(terrain, Terrain.class.getSimpleName());

        if (stat.type != Terrain.WALL && stat.type != Terrain.BORDER) {
            return createTraversableDecoration(x, y, bloodSprites[bloodIndex]);
        }

        else {
            // Don't add splatter to south walls - they are effectively invisible
            if (!direction.equals(Directions.Cardinal.get("SOUTH"))
                    && !direction.equals(Directions.All.get("SE"))
                    && !direction.equals(Directions.All.get("SW"))) {

                String[] wallBlood = getWallBloodForActor(blood);
                return createTraversableDecoration(x, y, wallBlood[bloodIndex]);

            } else {
                return null;
            }
        }
    }

    public static ArrayList<Component[]> createBloodSpray(Position position, Blood blood, long[][] mapGrid) {

        ArrayList<Component[]> spray = new ArrayList<>();

        int size = new RandomNumberGenerator().getRandomInt(0, 5);
        for (int i = 0; i < size; i++) {
            Component[] splat = createBloodSplat(position, blood, mapGrid);

            if (splat != null) {
                spray.add(splat);
            }
        }

        return spray;
    }

    public static String[] getWallBloodForActor(Blood blood) {
        switch(blood.type) {
            case Blood.RED:
                return GenericTileset.BLOOD_DROPS_WALL;
            case Blood.GREEN:
                return GenericTileset.GREEN_DROPS_WALL;
            case Blood.ECTOPLASM:
            default:
                return GenericTileset.BLOOD_DROPS_WALL;
        }
    }

    public static String[] getBloodForActor(Blood blood) {
        switch(blood.type) {
            case Blood.RED:
                return GenericTileset.BLOOD_DROPS;
            case Blood.GREEN:
                return GenericTileset.GREEN_DROPS;
            case Blood.ECTOPLASM:
            default:
                return GenericTileset.BLOOD_DROPS;
        }
    }

    public static Component[] createDecal(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.STATIC;
        sprite.path = tile;
        sprite.wrapToCube = false;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createCubeDecal(int x, int y, int z, String tile, boolean isBlocking, boolean isTraversable) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.STATIC;
        sprite.path = tile;
        sprite.wrapToCube = true;
        sprite.zLayer = z;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = isBlocking;
        physics.isTraversable = isTraversable;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createFovBlockingDecal(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.STATIC;
        sprite.path = tile;
        sprite.wrapToCube = false;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = true;
        physics.isTraversable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createLiquid(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.WAVE;
        sprite.path = tile;
        sprite.zLayer = 0;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = false;
        physics.isGasOrLiquid = true;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createTraversableDecoration(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.STATIC;
        sprite.path = tile;
        sprite.wrapToCube = false;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] createTransparentDecal(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.renderState = Sprite.STATIC;
        sprite.path = tile;
        sprite.wrapToCube = false;

        Terrain s = new Terrain(Terrain.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = false;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }
}
