package com.sonicmax.bloodrogue.utils.maths;

import java.util.Random;

public class RandomNumberGenerator {
    public RandomNumberGenerator() {
        // To reproduce results of terrain generation/etc, we would presumably have to store an
        // array of seeds used by each instance of RandomNumberGenerator. Using the same seed for every
        // instance seems to break the RNG.
    }

    public int getRandomInt(int min, int max) {
        return new Random(System.currentTimeMillis()).nextInt((max - min) + 1) + min;
    }

    public float getRandomFloat(float min, float max) {
        return (float) (min + Math.random() * (max - min));
    }
}
