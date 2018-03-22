package com.sonicmax.bloodrogue.utils;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.renderer.Animation;

import java.util.ArrayList;


public class Array2DHelper {
    public static ArrayList<Component[]>[][] createComponentGrid(int width, int height) {
        ArrayList<Component[]>[][] array = new ArrayList[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                array[x][y] = new ArrayList<>();
            }
        }

        return array;
    }

    public static ArrayList<Sprite>[][] create2DSpriteArray(int width, int height) {
        ArrayList<Sprite>[][] array = new ArrayList[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                array[x][y] = new ArrayList<>();
            }
        }

        return array;
    }

    public static ArrayList<Animation>[][] create2DAnimationArray(int width, int height) {
        ArrayList<Animation>[][] array = new ArrayList[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                array[x][y] = new ArrayList<>();
            }
        }

        return array;
    }

    public static ArrayList<Long>[][] create2dLongStack(int width, int height) {
        ArrayList<Long>[][] array = new ArrayList[width][height];

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
