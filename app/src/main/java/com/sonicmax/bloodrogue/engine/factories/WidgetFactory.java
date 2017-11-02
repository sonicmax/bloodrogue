package com.sonicmax.bloodrogue.engine.factories;

import com.sonicmax.bloodrogue.engine.Actions;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.tilesets.Mansion;

/**
 *  Widgets are GameObjects that Actors can interact with to perform certain actions.
 *  Eg. level entrances and exits, chests, doors, etc
 */

public class WidgetFactory {

    public static GameObject createEntrance(int x, int y) {
        GameObject entrance = new GameObject(x, y)  {

            @Override
            public int collide(GameObject object) {

                if (object.isPlayerControlled()) {
                    return Actions.EXIT_PREVIOUS_FLOOR;
                }
                else {
                    return Actions.NONE;
                }
            }

        };

        entrance.setSprite(Mansion.ENTRANCE);
        entrance.setBlocking(false);
        entrance.setTraversable(true);
        entrance.setHasAction(true);
        entrance.setActivateOnMove(true);

        return entrance;
    }

    public static GameObject createExit(int x, int y) {
        GameObject exit = new GameObject(x, y) {

            @Override
            public int collide(GameObject object) {
                if (object.isPlayerControlled()) {
                    return Actions.EXIT_FLOOR;
                }
                else {
                    return Actions.NONE;
                }
            }

        };

        exit.setSprite(Mansion.EXIT);
        exit.setBlocking(false);
        exit.setTraversable(true);
        exit.setHasAction(true);
        exit.setActivateOnMove(true);

        return exit;
    }
}
