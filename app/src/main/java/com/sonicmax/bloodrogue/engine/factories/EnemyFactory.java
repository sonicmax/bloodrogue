package com.sonicmax.bloodrogue.engine.factories;

import com.sonicmax.bloodrogue.engine.objects.Actor;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

/**
 *  Creates Actor object for each enemy type (so we don't have to create separate classes for each of them)
 */

public class EnemyFactory {

    public static GameObject getRandomEnemy(int x, int y, int level) {
        int random = new RandomNumberGenerator().getRandomInt(0, 4);
        switch(random) {
            case 0:
                return createZombie(x, y, level);
            case 1:
                return createGiantRat(x, y, level);
            case 2:
                return createOgre(x, y, level);
            case 3:
                return createGreatOgre(x, y, level);
            case 4:
                return createGiantKomodo(x, y, level);

            default:
                return createZombie(x, y, level);
        }
    }

    public static GameObject createZombie(int x, int y, int level) {
        Actor zombie = new Actor(x, y, level);
        zombie.setTile("sprites/zombie_" + new RandomNumberGenerator().getRandomInt(1, 3) + ".png");
        zombie.setName("Zombie");

        // Level 3 zombie would have 3 points in each stat + 3 randomly distributed points.
        // They would end up with 19 to 28 HP

        zombie.setStrength(level);
        zombie.setEndurance(level);
        zombie.setAgility(level);
        zombie.distributeStatPoints(level);

        zombie.setMaxHp(zombie.BASE_HP + (zombie.getEndurance() * level));

        return zombie;
    }

    public static GameObject createGiantRat(int x, int y, int level) {
        Actor rat = new Actor(x, y, level);
        rat.setTile("sprites/giant_rat.png");
        rat.setName("Giant Rat");

        // Level 3 giant rat would have 3 points in each stat + 3 randomly distributed points.
        // Redistribute stats from strength/endurance to agility if rat is level 3 or above

        rat.setStrength(level);
        rat.setEndurance(level);
        rat.setAgility(level);
        rat.distributeStatPoints(level);

        if (level >= 3) {
            int statModifier = Math.round(level / 3);

            if (new RandomNumberGenerator().getRandomInt(0, 1) == 0) {
                if (rat.getStrength() - statModifier > 1) {
                    rat.setStrength(rat.getStrength() - statModifier);
                }
            }

            else {
                if (rat.getEndurance() - statModifier > 1) {
                    rat.setEndurance(rat.getEndurance() - statModifier);
                }
            }

            rat.setAgility(rat.getAgility() + statModifier);
        }

        rat.setMaxHp(rat.BASE_HP + (rat.getEndurance() * level));

        return rat;
    }

    public static GameObject createOgre(int x, int y, int level) {
        Actor ogre = new Actor(x, y, level);
        ogre.setTile("sprites/ogre.png");
        ogre.setName("Ogre");

        // Ogres are stronger and more durable than average enemies, but slower
        ogre.setStrength((int) Math.ceil(level * 1.25));
        ogre.setEndurance((int) Math.ceil(level * 1.5));

        int statModifier = (int) Math.round(level / 1.25);

        if (ogre.getAgility() - statModifier > 1) {
            ogre.setAgility(ogre.getAgility() - statModifier);
        }

        ogre.distributeStatPoints(level);

        ogre.setMaxHp(ogre.BASE_HP + (ogre.getEndurance() * level));

        return ogre;
    }

    public static GameObject createGreatOgre(int x, int y, int level) {
        Actor ogre = new Actor(x, y, level);
        ogre.setTile("sprites/ogre_2.png");
        ogre.setName("Great Ogre");

        // Great ogres are stronger than ogres, but much slower than average enemy
        ogre.setStrength((int) Math.ceil(level * 1.5));
        ogre.setEndurance((int) Math.ceil(level * 1.75));

        int statModifier = (int) Math.round(level / 1.75);

        if (ogre.getAgility() - statModifier > 1) {
            ogre.setAgility(ogre.getAgility() - statModifier);
        }

        ogre.distributeStatPoints(level);

        ogre.setMaxHp(ogre.BASE_HP + (ogre.getEndurance() * level));

        return ogre;
    }

    public static GameObject createGiantKomodo(int x, int y, int level) {
        Actor komodo = new Actor(x, y, level);
        komodo.setTile("sprites/giant_komodo.png");
        komodo.setName("Giant Komodo");

        // Giant komodos are marginally superior to normal enemies in each stat
        komodo.setStrength((int) Math.ceil(level * 1.25));
        komodo.setEndurance((int) Math.ceil(level * 1.25));
        komodo.setAgility((int) Math.ceil(level * 1.25));

        komodo.distributeStatPoints(level);

        komodo.setMaxHp(komodo.BASE_HP + (komodo.getEndurance() * level));

        return komodo;
    }
}
