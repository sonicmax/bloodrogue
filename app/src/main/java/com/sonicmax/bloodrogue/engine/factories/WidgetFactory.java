package com.sonicmax.bloodrogue.engine.factories;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.Actions;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.tilesets.Mansion;

import java.util.ArrayList;

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

    public static GameObject createDoor(int x, int y, String openTile, String closedTile) {
        GameObject door = new GameObject(x, y) {

            @Override
            public int collide(GameObject object) {
                if (object.canInteract()) {
                    if (!this.open) {
                        this.setSprite(this.openTile);
                        this.setTraversable(true);
                        this.setBlocking(false);
                        this.open = true;
                        this.setHasAction(false);
                    }
                } else {
                    Log.v("Door", object.getClass().getSimpleName() + " not interactive");
                }

                return Actions.NONE;
            }
        };

        door.openTile = openTile;
        door.closedTile = closedTile;
        door.setHasAction(true);
        door.setStationary(true);
        door.setMutability(true);
        door.setActivateOnCollide(true);
        door.setSprite(closedTile);
        door.setTraversable(false);
        door.setBlocking(true);

        return door;
    }

    public static GameObject createChest(int x, int y) {
        final String CLOSED = "sprites/chest_closed.png";

        GameObject object = new GameObject(x, y) {

            @Override
            public int collide(GameObject object) {
                final String OPEN = "sprites/chest_open.png";
                final String EMPTY = "sprites/chest_empty.png";

                if (object.canInteract()) {
                    if (!this.open) {
                        this.setSprite(OPEN);
                        this.open = true;
                    } else if (!this.empty) {
                        this.setSprite(EMPTY);
                        this.empty = true;
                    }
                }

                // We don't need to do anything else.
                return Actions.NONE;
            }
        };

        object.setSprite(CLOSED);
        object.setDijkstra(1);
        object.setBlocking(false);
        object.setTraversable(false);
        object.setHasAction(true);
        object.setDeathAnimation(AnimationFactory.getChestItemRevealAnimation(x, y));
        object.setStationary(true);
        object.setMutability(true);
        object.setActivateOnCollide(true);

        object.open = false;
        object.empty = false;
        object.contents = new ArrayList<>();

        return object;
    }

    public static GameObject createDecoration(int x, int y, String tile) {
        GameObject object = new GameObject(x, y);
        object.setSprite(tile);
        object.setBlocking(false);
        object.setTraversable(false);
        object.setMutability(false);
        object.setStationary(true);
        return object;
    }
}
