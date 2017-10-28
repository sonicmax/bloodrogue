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
        Actor enemy = new Actor(x, y, level);
        enemy.setTile("sprites/zombie_" + new RandomNumberGenerator().getRandomInt(1, 3) + ".png");
        enemy.setName("Zombie");
        enemy.setAffinity(Actor.ENEMY);

        // Level 3 zombie would have 3 points in each stat + 3 randomly distributed points.
        // They would end up with 19 to 28 HP

        enemy.setStrength(level);
        enemy.setEndurance(level);
        enemy.setAgility(level);
        enemy.distributeStatPoints(level);

        enemy.setMaxHp(enemy.BASE_HP + (enemy.getEndurance() * level));

        return enemy;
    }

    public static GameObject createGiantRat(int x, int y, int level) {
        Actor enemy = new Actor(x, y, level);
        enemy.setTile("sprites/giant_rat.png");
        enemy.setName("Giant Rat");
        enemy.setAffinity(Actor.ENEMY);

        // Level 3 giant rat would have 3 points in each stat + 3 randomly distributed points.
        // Redistribute stats from strength/endurance to agility if rat is level 3 or above

        enemy.setStrength(level);
        enemy.setEndurance(level);
        enemy.setAgility(level);
        enemy.distributeStatPoints(level);

        if (level >= 3) {
            int statModifier = Math.round(level / 3);

            if (new RandomNumberGenerator().getRandomInt(0, 1) == 0) {
                if (enemy.getStrength() - statModifier > 1) {
                    enemy.setStrength(enemy.getStrength() - statModifier);
                }
            }

            else {
                if (enemy.getEndurance() - statModifier > 1) {
                    enemy.setEndurance(enemy.getEndurance() - statModifier);
                }
            }

            enemy.setAgility(enemy.getAgility() + statModifier);
        }

        enemy.setMaxHp(enemy.BASE_HP + (enemy.getEndurance() * level));

        return enemy;
    }

    public static GameObject createOgre(int x, int y, int level) {
        Actor enemy = new Actor(x, y, level);
        enemy.setTile("sprites/ogre.png");
        enemy.setName("Ogre");
        enemy.setAffinity(Actor.ENEMY);

        // Ogres are stronger and more durable than average enemies, but slower
        enemy.setStrength((int) Math.ceil(level * 1.25));
        enemy.setEndurance((int) Math.ceil(level * 1.5));

        int statModifier = (int) Math.round(level / 1.25);

        if (enemy.getAgility() - statModifier > 1) {
            enemy.setAgility(enemy.getAgility() - statModifier);
        }

        enemy.distributeStatPoints(level);

        enemy.setMaxHp(enemy.BASE_HP + (enemy.getEndurance() * level));

        return enemy;
    }

    public static GameObject createGreatOgre(int x, int y, int level) {
        Actor enemy = new Actor(x, y, level);
        enemy.setTile("sprites/ogre_2.png");
        enemy.setName("Great Ogre");
        enemy.setAffinity(Actor.ENEMY);

        // Great ogres are stronger than ogres, but much slower than average enemy
        enemy.setStrength((int) Math.ceil(level * 1.5));
        enemy.setEndurance((int) Math.ceil(level * 1.75));

        int statModifier = (int) Math.round(level / 1.75);

        if (enemy.getAgility() - statModifier > 1) {
            enemy.setAgility(enemy.getAgility() - statModifier);
        }

        enemy.distributeStatPoints(level);

        enemy.setMaxHp(enemy.BASE_HP + (enemy.getEndurance() * level));

        return enemy;
    }

    public static GameObject createGiantKomodo(int x, int y, int level) {
        Actor enemy = new Actor(x, y, level);
        enemy.setTile("sprites/giant_komodo.png");
        enemy.setName("Giant Komodo");
        enemy.setAffinity(Actor.ENEMY);

        // Giant komodos are marginally superior to normal enemies in each stat
        enemy.setStrength((int) Math.ceil(level * 1.25));
        enemy.setEndurance((int) Math.ceil(level * 1.25));
        enemy.setAgility((int) Math.ceil(level * 1.25));

        enemy.distributeStatPoints(level);

        enemy.setMaxHp(enemy.BASE_HP + (enemy.getEndurance() * level));

        return enemy;
    }
}
