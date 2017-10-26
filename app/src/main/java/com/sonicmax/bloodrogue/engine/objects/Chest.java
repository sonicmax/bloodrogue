package com.sonicmax.bloodrogue.engine.objects;

import java.util.ArrayList;

// Todo: We should find a way to create a generic class that can support what the Chest class does

public class Chest extends GameObject {
    private final String OPEN = "sprites/chest_open.png";
    private final String CLOSED = "sprites/chest_closed.png";
    private final String EMPTY = "sprites/chest_empty.png";
    private boolean open;
    private boolean empty;
    private ArrayList<GameObject> contents;

    public Chest(int x, int y) {
        super(x, y);
        this.setTile(CLOSED);
        this.setDijkstra(1);
        this.setBlocking(false);
        this.setTraversable(false);
        this.setHasAction(true);
        this.setAnimation(AnimationFactory.getChestItemRevealAnimation(x, y));
        this.setStationary(true);
        this.setMutability(true);

        this.open = false;
        this.empty = false;
        this.contents = new ArrayList<>();
    }

    /**
     *  Chest has three states - closed, open and empty.
     */

    public void collide(GameObject object) {
        if (object.canInteract()) {
            if (!this.open) {
                this.setTile(OPEN);
                this.open = true;
            } else if (this.open && !this.empty) {
                this.setTile(EMPTY);
                this.empty = true;
            }
        }
    }
}
