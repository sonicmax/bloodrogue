package com.sonicmax.bloodrogue.engine.systems;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.components.Collectable;
import com.sonicmax.bloodrogue.engine.components.Damage;
import com.sonicmax.bloodrogue.engine.components.Knowledge;
import com.sonicmax.bloodrogue.engine.components.Name;
import com.sonicmax.bloodrogue.engine.components.Usable;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class PotionSystem {
    private static final String LOG_TAG = PotionSystem.class.getSimpleName();

    /**
     * Iterates over potions and assigns each one a random effect.
     * The resulting JSONObject will be saved with player data
     *
     * @param potions
     * @return
     */

    public static JSONObject generateRandomPotionEffects(JSONObject potions) {
        Iterator<String> keys = potions.keys();
        int size = potions.length();
        ArrayList<Integer> types = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            types.add(i);
        }

        try {
            while (keys.hasNext()) {
                String type = keys.next();
                JSONObject basePotion = potions.getJSONObject(type);
                int random = types.remove(new RandomNumberGenerator().getRandomInt(0, types.size() - 1));
                getPotionEffect(random, basePotion);
            }

            return potions;

        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error while building potions", e);
            return null;
        }
    }

    /**
     * Adds potion effect to base potion.
     * These will be used in BlueprintParser to generate in-game components
     *
     * @param type
     * @param potion
     * @throws JSONException
     */

    public static void getPotionEffect(int type, JSONObject potion) throws JSONException {
        JSONObject vitality;
        JSONObject damage;

        switch (type) {
            case 0:
                potion.getJSONObject("usable").put("effect", "vitality");
                potion.getJSONObject("usable").put("id", type);
                vitality = new JSONObject();
                vitality.put("endurance", 1);
                potion.put("vitality", vitality);
                break;

            case 1:
                potion.getJSONObject("usable").put("effect", "damage");
                potion.getJSONObject("usable").put("id", type);
                damage = new JSONObject();
                damage.put("strength", 1);
                potion.put("damage", damage);
                break;

            case 2:
                potion.getJSONObject("usable").put("effect", "strength");
                potion.getJSONObject("usable").put("id", type);
                damage = new JSONObject();
                damage.put("strength", 1);
                potion.put("damage", damage);
                break;
        }
    }

    public static String getIdentifiedPotionName(int effectId) {
        switch (effectId) {
            case -1:
                return "Unknown";
            case 0:
                return "Potion of Vitality";
            case 1:
                return "Weak Poison";
            case 2:
                return "Potion of Strength";
            default:
                Log.e(LOG_TAG, "No corresponding value for effect ID \"" + effectId + "\"");
                return "Unknown";
        }
    }

    public static String getIdentifiedPotionNarration(int effectId) {
        switch (effectId) {
            case -1:
                return "unidentified potion";
            case 0:
                return "potion of vitality";
            case 1:
                return "weak poison";
            case 2:
                return "potion of strength";
            default:
                Log.e(LOG_TAG, "No corresponding value for effect ID \"" + effectId + "\"");
                return "Unknown";
        }
    }

    public static void performPotionEffect(ComponentManager comp, long actor, long potion, int effectId) {
        Vitality actorVitality = (Vitality) comp.getEntityComponent(actor, Vitality.class.getSimpleName());
        Damage actorStrength = (Damage) comp.getEntityComponent(actor, Damage.class.getSimpleName());

        Vitality vitality;
        Damage damage;

        switch (effectId) {
            case 0: // Health restore
                vitality = (Vitality) comp.getEntityComponent(potion, Vitality.class.getSimpleName());
                int restore = vitality.endurance * vitality.endurance / (vitality.endurance + actorVitality.endurance);
                if (actorVitality.hp + restore <= actorVitality.maxHp) {
                    actorVitality.hp += restore;
                }
                else {
                    actorVitality.hp = actorVitality.maxHp;
                }
                break;

            case 1: // Poison damage
                damage = (Damage) comp.getEntityComponent(potion, Damage.class.getSimpleName());
                int poisonDamage = damage.strength * damage.strength / (damage.strength + actorVitality.endurance);
                if (actorVitality.hp - poisonDamage >= 0) {
                    actorVitality.hp -= poisonDamage;
                }
                else {
                    actorVitality.hp = 0;
                }
                break;

            case 2:
                Log.d(LOG_TAG, "yum yum a potion of strength lol");
                break;

            default:
                Log.e(LOG_TAG, "No corresponding value for effect ID \"" + effectId + "\"");
                break;
        }
    }

    /**
     *
     */

    public static void quaff(ComponentManager componentManager, long actor, long potion) {
        Collectable collectable = (Collectable) componentManager.getEntityComponent(potion, Collectable.class.getSimpleName());
        Usable usableComponent = (Usable) componentManager.getEntityComponent(potion, Usable.class.getSimpleName());

        performPotionEffect(componentManager, actor, potion, usableComponent.effectId);

        // Add ID mapping to knowledge component
        Name nameComponent = (Name) componentManager.getEntityComponent(potion, Name.class.getSimpleName());
        nameComponent.value = getIdentifiedPotionName(usableComponent.effectId);

        Knowledge knowledgeComponent = (Knowledge) componentManager.getEntityComponent(actor, Knowledge.class.getSimpleName());
        knowledgeComponent.identifiedItems.put(collectable.identity, usableComponent.effectId);
    }
}
