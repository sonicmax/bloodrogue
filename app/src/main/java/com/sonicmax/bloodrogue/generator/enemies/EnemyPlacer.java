package com.sonicmax.bloodrogue.generator.enemies;

import android.content.res.AssetManager;

import com.sonicmax.bloodrogue.data.BlueprintParser;
import com.sonicmax.bloodrogue.data.JSONLoader;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.components.Position;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class EnemyPlacer {
    private ArrayList<Long>[][] objectEntities;

    private JSONObject enemyBlueprints;
    private ArrayList<String> allKeys;
    private ComponentManager componentManager;

    public EnemyPlacer(ArrayList<Long>[][] objectEntities, AssetManager assetManager) {
        this.objectEntities = objectEntities;
        this.componentManager = ComponentManager.getInstance();
        this.enemyBlueprints = JSONLoader.loadEnemies(assetManager);

        Iterator<String> keys = enemyBlueprints.keys();
        this.allKeys = new ArrayList<>();

        while (keys.hasNext()) {
            this.allKeys.add(keys.next());
        }
    }

    public void placeEnemy(int x, int y, String key) {
        Component[] enemy = getComponentsFromBlueprint(key);
        long entity = enemy[0].id;
        componentManager.sortComponentArray(enemy);

        objectEntities[x][y].add(entity);
        componentManager.sortComponentArray(enemy);

        Position positionComponent = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());

        if (positionComponent != null) {
            positionComponent.x = x;
            positionComponent.y = y;
        }
    }

    private Component[] getComponentsFromBlueprint(String key) {
        return BlueprintParser.getComponentArrayForBlueprint(enemyBlueprints, key);
    }
}
