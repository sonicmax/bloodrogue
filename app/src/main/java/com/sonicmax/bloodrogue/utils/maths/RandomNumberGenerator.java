package com.sonicmax.bloodrogue.utils.maths;

import android.util.Log;

import java.util.Random;

public class RandomNumberGenerator {
    public RandomNumberGenerator() {
        // To reproduce results of terrain generation/etc, we would presumably have to store an
        // array of seeds used by each instance of RandomNumberGenerator. Using the same seed for every
        // instance seems to break the RNG.
    }

    public int getRandomInt(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }

    public float getRandomFloat(float min, float max) {
        return (float) (min + Math.random() * (max - min));
    }

    public boolean coinflip() {
        return new Random().nextInt(2) == 1;
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
