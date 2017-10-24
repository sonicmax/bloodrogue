package com.sonicmax.bloodrogue.objects;

import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.maths.Vector;
import com.sonicmax.bloodrogue.objects.GameObject;
import com.sonicmax.bloodrogue.objects.Wall;
import com.sonicmax.bloodrogue.tilesets.All;
import com.sonicmax.bloodrogue.utils.NumberGenerator;

import java.util.ArrayList;

public class DecalFactory {
    public static GameObject createBloodSplat(Vector target, GameObject[][] mapGrid) {
        ArrayList<Vector> directions = new ArrayList(Directions.All.values());
        int random = NumberGenerator.getRandomInt(0, directions.size() - 1);
        Vector direction = directions.get(random);
        Vector location = target.add(direction);
        int x = location.x();
        int y = location.y();

        // BLOOD_DROPS and BLOOD_DROPS_WALL have same length
        int bloodIndex = NumberGenerator.getRandomInt(0, All.BLOOD_DROPS.length - 1);
        GameObject splat = new GameObject(x, y);
        splat.setTraversable(true);
        splat.setBlocking(false);
        splat.setMutability(true);

        if (!(mapGrid[x][y] instanceof Wall)) {
            splat.setTile(All.BLOOD_DROPS[bloodIndex]);
            return splat;
        } else {
            // Don't add splatter to south walls - they are effectively invisible
            if (!direction.equals(Directions.Cardinal.get("SOUTH"))
                    && !direction.equals(Directions.All.get("SE"))
                    && !direction.equals(Directions.All.get("SW"))) {

                splat.setTile(All.BLOOD_DROPS_WALL[bloodIndex]);
                return splat;
            } else {
                return null;
            }
        }
    }

    public static void createBloodSpray(Vector target, GameObject[][] mapGrid,
                                        ArrayList<GameObject>[][] objectGrid) {

        int size = NumberGenerator.getRandomInt(0, 5);
        for (int i = 0; i < size; i++) {
            GameObject splat = createBloodSplat(target, mapGrid);

            if (splat != null) {
                splat.setMutability(true);
                objectGrid[splat.x()][splat.y()].add(splat);
            }
        }
    }
}
