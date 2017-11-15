package com.sonicmax.bloodrogue.tilesets;

public class CorpseTileset {
    public static String getCorpseForEntity(String name) {
        switch (name) {
            case "Zombie":
                return "sprites/zombie_corpse.png";

            case "Giant Rat":
                return "sprites/giant_rat_corpse.png";

            case "Ogre":
            case "Great Ogre":
                return "sprites/ogre_corpse.png";

            case "Giant Komodo":
                return "sprites/giant_komodo_corpse.png";

            case "Green Slime":
                return "sprites/green_slime_corpse.png";

            case "Purple Slime":
                return "sprites/purple_slime_corpse.png";

            case "Giant Roach":
                return "sprites/cockroach_corpse.png";

            case "Spirit":
                return "sprites/ogre_spirit_corpse.png";

            default:
                return "sprites/transparent.png";
        }
    }
}
