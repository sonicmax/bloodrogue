package com.sonicmax.bloodrogue.utils.maths;

import java.util.Random;

/**
 * Just a simple wrapper for Random that provides some convenience methods for seed management,
 * getting numbers within given range, dice rolling, etc
 */

public class RandomNumberGenerator {
    private long seed;
    private Random random;

    /**
     * If no arguments are provided, uses System.currentTimeMillis() to generate seed.
     */

    public RandomNumberGenerator() {
        long seed = System.currentTimeMillis();
        initRandomWithSeed(seed);
    }

    public RandomNumberGenerator(long seed) {
        initRandomWithSeed(seed);
    }

    public void initRandomWithSeed(long seed) {
        this.seed = seed;
        random = new Random(seed);
    }

    public long getSeed() {
        return seed;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Methods for number generation
    ///////////////////////////////////////////////////////////////////////////

    public int getRandomInt(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }

    public float getRandomFloat(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public boolean coinflip() {
        return random.nextInt(2) == 1;
    }

    public String getRandomItemFromStringArray(String[] array) {
        return array[getRandomInt(0, array.length - 1)];
    }

    public int d6(int numberOfDice) {
        int total = 0;

        for (int i = 0; i < numberOfDice; i++) {
            total += getRandomInt(1, 6);
        }

        return total;
    }

    public boolean d6(int numberOfDice, int target) {
        for (int i = 0; i < numberOfDice; i++) {

            int roll = getRandomInt(1, 6);
            if (roll == target) {
                return true;
            }
        }

        return false;
    }
}
