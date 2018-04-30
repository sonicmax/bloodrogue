package com.sonicmax.bloodrogue.utils.maths;

/**
 * Class with some static vector maths methods for use with float arrays.
 * Doesn't attempt to validate any parameters, so be careful
 */

public class Vector {
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;

    public static float dot(float[] u, float[] v) {
        return ((u[X] * v[X]) + (u[Y] * v[Y]) + (u[Z] * v[Z]));
    }
    public static float[] subtract(float[] u, float[] v){
        return new float[]{u[X]-v[X],u[Y]-v[Y],u[Z]-v[Z]};
    }

    public static float[] add(float[] u, float[] v){
        return new float[]{u[X]+v[X],u[Y]+v[Y],u[Z]+v[Z]};
    }

    public static float[] scale(float[] u, float r){
        return new float[] {u[X] * r, u[Y] * r, u[Z] * r};
    }

    public static float[] cross(float[] u, float[] v){
        return new float[]{(u[Y]*v[Z]) - (u[Z]*v[Y]),(u[Z]*v[X]) - (u[X]*v[Z]),(u[X]*v[Y]) - (u[Y]*v[X])};
    }

    public static float length(float[] u){
        return (float) Math.abs(Math.sqrt((u[X] *u[X]) + (u[Y] *u[Y]) + (u[Z] *u[Z])));
    }
}
