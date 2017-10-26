package com.sonicmax.bloodrogue.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferenceManager {
    public static final String PACKAGE = "com.sonicmax.bloodrogue";

    public static void putString(Context context, String key, String value) {

        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        prefs.edit().putString(key, value).apply();
    }

    public static String getString(Context context, String key) {

        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        return prefs.getString(key, null);
    }

    public static void putBoolean(Context context, String key, boolean value) {

        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(Context context, String key) {

        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        // Return false if boolean doesn't exist, and make sure that keys imply truthy values
        return prefs.getBoolean(key, false);
    }

    public static void putLong(Context context, String key, long value) {
        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        prefs.edit().putLong(key, value).apply();
    }

    public static long getLong(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        // If long isn't present, return -42 so we can handle errors.
        return prefs.getLong(key, -42L);
    }

    public static void putInt(Context context, String key, int value) {
        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        prefs.edit().putInt(key, value).apply();
    }

    public static int getInt(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PACKAGE, Context.MODE_PRIVATE);
        // Return -42 if int is not present
        return prefs.getInt(key, -42);
    }
}