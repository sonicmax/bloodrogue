package com.sonicmax.bloodrogue.engine.factories;

import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.objects.Actor;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.tilesets.All;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import java.util.ArrayList;

public class DecalFactory {

    // Todo: possibly bad to instantiate new RNG object here - could do it from calling context

    public static GameObject createBloodSplat(Actor target, GameObject[][] mapGrid) {
        ArrayList<Vector> directions = new ArrayList(Directions.All.values());
        int random = new RandomNumberGenerator().getRandomInt(0, directions.size() - 1);
        Vector direction = directions.get(random);
        Vector location = target.getVector().add(direction);
        int x = location.x();
        int y = location.y();

        // BLOOD_DROPS and BLOOD_DROPS_WALL have same length
        RandomNumberGenerator rng = new RandomNumberGenerator();

        String[] blood = getBloodForActor(target);

        int bloodIndex = rng.getRandomInt(0, blood.length - 1);
        GameObject splat = new GameObject(x, y);
        splat.setTraversable(true);
        splat.setBlocking(false);
        splat.setMutability(true);

        if (!(mapGrid[x][y].type == GameObject.WALL)) {
            splat.setSprite(blood[bloodIndex]);
            return splat;
        } else {
            // Don't add splatter to south walls - they are effectively invisible
            if (!direction.equals(Directions.Cardinal.get("SOUTH"))
                    && !direction.equals(Directions.All.get("SE"))
                    && !direction.equals(Directions.All.get("SW"))) {
                String[] wallBlood = getWallBloodForActor(target);
                splat.setSprite(wallBlood[bloodIndex]);
                return splat;
            } else {
                return null;
            }
        }
    }

    public static void createBloodSpray(Actor target, GameObject[][] mapGrid,
                                        ArrayList<GameObject>[][] objectGrid) {

        int size = new RandomNumberGenerator().getRandomInt(0, 5);
        for (int i = 0; i < size; i++) {
            GameObject splat = createBloodSplat(target, mapGrid);

            if (splat != null) {
                splat.setMutability(true);
                objectGrid[splat.x()][splat.y()].add(splat);
            }
        }
    }

    public static String[] getWallBloodForActor(Actor actor) {
        switch(actor.getBloodColour()) {
            case Actor.RED_BLOOD:
                return All.BLOOD_DROPS_WALL;
            case Actor.GREEN_BLOOD:
                return All.GREEN_DROPS_WALL;
            case Actor.ECTOPLASM:
            default:
                return All.BLOOD_DROPS_WALL;
        }
    }

    public static String[] getBloodForActor(Actor actor) {
        switch(actor.getBloodColour()) {
            case Actor.RED_BLOOD:
                return All.BLOOD_DROPS;
            case Actor.GREEN_BLOOD:
                return All.GREEN_DROPS;
            case Actor.ECTOPLASM:
            default:
                return All.BLOOD_DROPS;
        }
    }
}
