package com.sonicmax.bloodrogue.engine.objects;

import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

/**
 * This class is used for player character and enemies. Contains stat points and various methods
 * to determine results of interactions (attacking, defending, etc) and other game events
 */

public class Actor extends GameObject {
    public final int BASE_HP = 10;

    /**
     *  Affinity describes which types of Actor this instance will be friendly to, and which they
     *  will attack.
     */

    public static final int PLAYER = 0; // Friendly to player
    public static final int ENEMY = 1; // Friendly to enemy
    public static final int NEUTRAL = 2; // Won't attack unless provoked

    private int maxHp;
    private int hp;
    private int totalXp;
    private int xp;
    private int xpToNextLevel;
    private int strength;
    private int endurance;
    private int agility;
    private int level;
    private int hunger;
    private int energy;
    private int affinity;

    private RandomNumberGenerator rng;

    public Actor(int x, int y, int level) {
        super(x, y);
        this.maxHp = hp;
        this.hp = BASE_HP;
        this.xp = 0;
        this.level = level;
        this.xpToNextLevel = this.calculateXpForLevel(this.level + 1);

        this.hunger = 100;
        this.energy = 100;

        this.setBlocking(false);
        this.setHasAction(true);
        this.setPlayerInterest(15);
        this.setStationary(false);
        this.setMutability(true);

        this.rng = new RandomNumberGenerator();
    }

    /**
     Getters and setters for HP and other stats */

    public void setMaxHp(int points) {
        this.maxHp = points;
        this.hp = points;
    }

    public void setHp(int points) {
        this.hp = points;
    }

    public void setXp(int points) {
        this.totalXp = points;
        this.xp = points;
    }

    public void setStrength(int points) {
        this.strength = points;
    }

    public void setEndurance(int points) {
        this.endurance = points;
    }

    public void setAgility(int points) {
        this.agility = points;
    }

    public void setHunger(int level) {
        this.hunger = level;
    }

    public void setEnergy(int level) {
        this.energy = level;
    }

    public int getHp() {
        return this.hp;
    }

    public int getXp() {
        return this.xp;
    }

    public int getXpToNextLevel() {
        return this.xpToNextLevel;
    }

    public int getStrength() {
        return this.strength;
    }

    public int getEndurance() {
        return this.endurance;
    }

    public int getAgility() {
        return this.agility;
    }

    public int getHunger() {
        return this.hunger;
    }

    public int getEnergy() {
        return this.energy;
    }

    public int getLevel() {
        return this.level;
    }

    public int getAffinity() {
        return this.affinity;
    }

    public void setAffinity(int affinity) {
        this.affinity = affinity;
    }

    /**
     Methods that determine results of interactions (attacking, defending, death, etc) */

    public void distributeStatPoints(int points) {
        for (int i = 0; i < points; i++) {
            int target = rng.getRandomInt(0, 2);
            switch(target) {
                case 0:
                    this.strength++;
                    break;

                case 1:
                    this.endurance++;
                    break;

                case 2:
                    this.agility++;
                    break;
            }
        }
    }

    public int getXpReward(int playerLevel) {
        return Math.round((this.maxHp + this.strength + this.endurance) * this.level / (playerLevel + 1));
    }

    private int calculateXpForLevel(int newLevel) {
        double points = Math.floor(newLevel + 300 * Math.pow(2, newLevel / 7));
        return (int) Math.floor(points / 4);
    }

    public int getHpRegenRate() {
        return 1 / (this.maxHp / this.endurance) / (this.level + 1);
    }

    public void levelUp() {
        this.level++;

        int leftover = this.xp - this.xpToNextLevel;

        if (leftover > 0) {
            this.xp = leftover;
        }

        else {
            this.xp = 0;
        }

        this.xpToNextLevel = this.calculateXpForLevel(this.level + 1);

        this.strength++;
        this.endurance++;

        this.maxHp = BASE_HP + (this.endurance * this.level);
    }

    /**
     * Calculate results of combat and returns the damage dealt.
     * The result can be safely ignored (or used to display info in UI)
     *
     * @param target Actor that will be attacked
     * @return
     */

    public int attack(Actor target) {
        int damage = this.getDamage(target);

        int defenderHp = target.getHp();

        if (defenderHp - damage >= 0) {
            target.setHp(defenderHp - damage);
        }
        else {
            target.setHp(0);
        }

        return damage;
    }

    public int getDamage(Actor target) {
        int attack = this.getStrength(); // hand-to-hand
        int defence = target.getEndurance(); // defence with no armour
        int criticalChance = 0; // Todo: work out critical hit chance
        int baseDamage = attack * attack / (attack + defence);

        return baseDamage;
    }
}
