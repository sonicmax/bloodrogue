package com.sonicmax.bloodrogue.utils;

import java.util.Random;

public class NumberGenerator {
    public static int getRandomInt(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }
}
