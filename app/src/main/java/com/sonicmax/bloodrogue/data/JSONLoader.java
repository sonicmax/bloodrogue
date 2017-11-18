package com.sonicmax.bloodrogue.data;

import android.content.res.AssetManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public class JSONLoader {
    public static String loadFile(AssetManager assetManager, String file) {
        try {
            InputStream is = assetManager.open(file);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static JSONObject loadEnemies(AssetManager assetManager) {

        final String file = "blueprints/enemies.json";

        try  {
            return new JSONObject(loadFile(assetManager, file));

        } catch (JSONException e) {
            throw new Error("Error parsing enemy blueprints - can't continue", e);
        }
    }

    public static JSONObject loadFurniture(AssetManager assetManager) {

        final String file = "blueprints/furniture.json";

        try  {
            return new JSONObject(loadFile(assetManager, file));

        } catch (JSONException e) {
            throw new Error("Error parsing furniture blueprints - can't continue", e);
        }
    }

    public static JSONObject loadWeapons(AssetManager assetManager) {

        final String file = "blueprints/weapons.json";

        try  {
            return new JSONObject(loadFile(assetManager, file));

        } catch (JSONException e) {
            throw new Error("Error parsing weapon blueprints - can't continue", e);
        }
    }

    public static JSONObject loadPotions(AssetManager assetManager) {

        final String file = "blueprints/potions.json";

        try  {
            return new JSONObject(loadFile(assetManager, file));

        } catch (JSONException e) {
            throw new Error("Error parsing item blueprints - can't continue", e);
        }
    }
}
