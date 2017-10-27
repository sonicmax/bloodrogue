package com.sonicmax.bloodrogue.engine.factories;

import com.sonicmax.bloodrogue.engine.objects.GameObject;

public class CorpseFactory {
    public static GameObject getCorpse(int x, int y, String type) {
        GameObject corpse = new GameObject(x, y);
        corpse.setBlocking(false);
        corpse.setTraversable(true);
        corpse.setMutability(true);

        switch(type) {
            case "sprites/zombie_1.png":
            case "sprites/zombie_2.png":
            case "sprites/zombie_3.png":
                corpse.setTile("sprites/zombie_corpse.png");
                break;

            case "sprites/giant_rat.png":
                corpse.setTile("sprites/giant_rat_corpse.png");
                break;

            case "sprites/ogre.png":
            case "sprites/ogre_2.png":
                corpse.setTile("sprites/ogre_corpse.png");
                break;

            case "sprites/giant_komodo.png":
                corpse.setTile("sprites/giant_komodo_corpse.png");
                break;

            default:
                corpse.setTile("sprites/transparent.png");
                break;
        }

        return corpse;
    }
}
