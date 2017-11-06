package com.sonicmax.bloodrogue.utils;

import com.sonicmax.bloodrogue.engine.objects.GameObject;

import java.util.ArrayList;


public class Array2DHelper {
    public static ArrayList<GameObject>[][] createArrayList2D(int width, int height) {
        ArrayList<GameObject>[][] array = new ArrayList[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                array[x][y] = new ArrayList<>();
            }
        }

        return array;
    }

    public static int[][] fillIntArray(int width, int height, int value) {
        int[][] array = new int[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                array[x][y] = value;
            }
        }

        return array;
    }
}
