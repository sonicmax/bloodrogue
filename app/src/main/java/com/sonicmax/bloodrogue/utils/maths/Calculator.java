package com.sonicmax.bloodrogue.utils.maths;

public class Calculator {
    public static double getDistance(Vector a, Vector b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2));
    }
}
