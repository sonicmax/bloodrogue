package com.sonicmax.bloodrogue.generator.factories;

import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.Entity;
import com.sonicmax.bloodrogue.engine.components.Animation;
import com.sonicmax.bloodrogue.engine.components.Blood;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Stationary;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.tilesets.All;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import java.util.ArrayList;

public class DecalFactory {

    public static Component[] getEmptyAnimation(int x, int y) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Animation animation = new Animation(entity.id);

        Stationary s = new Stationary(Stationary.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;

        array[0] = position;
        array[1] = animation;
        array[2] = s;
        array[3] = physics;

        return array;
    }

    public static Component[] getCorpse(int x, int y, String type) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.shader = Sprite.DYNAMIC;

        Stationary s = new Stationary(Stationary.DEFAULT, entity.id);

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

    public static Component[] createBloodSplat(Position position, Blood blood, long[][] mapGrid, ComponentManager componentManager) {
        ArrayList<Vector> directions = new ArrayList(Directions.All.values());
        int random = new RandomNumberGenerator().getRandomInt(0, directions.size() - 1);
        Vector direction = directions.get(random);
        Vector location = new Vector(position.x, position.y).add(direction);
        int x = location.x();
        int y = location.y();

        // BLOOD_DROPS and BLOOD_DROPS_WALL have same length
        RandomNumberGenerator rng = new RandomNumberGenerator();

        String[] bloodSprites = getBloodForActor(blood);

        int bloodIndex = rng.getRandomInt(0, bloodSprites.length - 1);

        long terrain = mapGrid[x][y];
        Stationary stat = (Stationary) componentManager.getEntityComponent(terrain, Stationary.class.getSimpleName());

        if (stat.type != Stationary.WALL && stat.type != Stationary.BORDER) {
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

    public static ArrayList<Component[]> createBloodSpray(Position position, Blood blood,
                                                          long[][] mapGrid, ComponentManager componentManager) {

        ArrayList<Component[]> spray = new ArrayList<>();

        int size = new RandomNumberGenerator().getRandomInt(0, 5);
        for (int i = 0; i < size; i++) {
            Component[] splat = createBloodSplat(position, blood, mapGrid, componentManager);

            if (splat != null) {
                spray.add(splat);
            }
        }

        return spray;
    }

    public static String[] getWallBloodForActor(Blood blood) {
        switch(blood.type) {
            case Blood.RED:
                return All.BLOOD_DROPS_WALL;
            case Blood.GREEN:
                return All.GREEN_DROPS_WALL;
            case Blood.ECTOPLASM:
            default:
                return All.BLOOD_DROPS_WALL;
        }
    }

    public static String[] getBloodForActor(Blood blood) {
        switch(blood.type) {
            case Blood.RED:
                return All.BLOOD_DROPS;
            case Blood.GREEN:
                return All.GREEN_DROPS;
            case Blood.ECTOPLASM:
            default:
                return All.BLOOD_DROPS;
        }
    }

    public static Component[] createDecoration(int x, int y, String tile) {
        Entity entity = new Entity();

        Component[] array = new Component[4];

        Position position = new Position(entity.id);
        position.x = x;
        position.y = y;

        Sprite sprite = new Sprite(entity.id);
        sprite.path = tile;

        Stationary s = new Stationary(Stationary.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = false;

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
        sprite.path = tile;

        Stationary s = new Stationary(Stationary.DEFAULT, entity.id);

        Physics physics = new Physics(entity.id);
        physics.isBlocking = false;
        physics.isTraversable = true;

        array[0] = position;
        array[1] = sprite;
        array[2] = s;
        array[3] = physics;

        return array;
    }
}
