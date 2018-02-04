package com.sonicmax.bloodrogue.data;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.Entity;
import com.sonicmax.bloodrogue.engine.components.AI;
import com.sonicmax.bloodrogue.engine.components.Animation;
import com.sonicmax.bloodrogue.engine.components.Barrier;
import com.sonicmax.bloodrogue.engine.components.Blood;
import com.sonicmax.bloodrogue.engine.components.Collectable;
import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Damage;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Dynamic;
import com.sonicmax.bloodrogue.engine.components.Energy;
import com.sonicmax.bloodrogue.engine.components.Experience;
import com.sonicmax.bloodrogue.engine.components.Input;
import com.sonicmax.bloodrogue.engine.components.Name;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Portal;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.SelfReplicate;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Terrain;
import com.sonicmax.bloodrogue.engine.components.Usable;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.engine.components.Wieldable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 *  Various methods which allow us to parse a blueprint file and return components for a new entity.
 */

public class BlueprintParser {
    private static String LOG_TAG = BlueprintParser.class.getSimpleName();

    /**
     * Creates new entity, parses blueprint object and returns array of components associated
     * with entity.
     */

    public static Component[] getComponentArrayForBlueprint(JSONObject blueprint) {
        try {
            Entity entity = new Entity();
            int componentIndex = 0;
            Component[] components = new Component[blueprint.length()];
            Iterator<String> iterator = blueprint.keys();

            while (iterator.hasNext()) {
                String key = iterator.next();
                Component component = getBlueprintComponent(key, (JSONObject) blueprint.get(key), entity.id);
                if (component != null) {
                    components[componentIndex] = component;
                    componentIndex++;
                }
                else {
                    Log.e(LOG_TAG, "Null component for key \"" + key + "\"");
                }
            }

            return components;

        } catch (JSONException e) {
            Log.v(LOG_TAG, "Error while parsing blueprint", e);
            return null;
        }
    }

    public static Component[] getComponentArrayForBlueprint(JSONObject blueprint, String key) {
        try {
            return getComponentArrayForBlueprint((JSONObject) blueprint.get(key));
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error while parsing \"" + key + "\" from JSONObject", e);
            return null;
        }
    }

    public static Component getBlueprintComponent(String key, JSONObject object, long entity) throws JSONException {
        switch (key) {
            case "animation":
                return new Animation(entity);

            case "barrier":
                Barrier barrier;

                if (object.has("type")) {
                    switch (object.getString("type")) {
                        case "door":
                        default:
                            barrier = new Barrier(Barrier.DOOR, entity);
                            break;
                    }
                }
                else {
                    barrier = new Barrier(Barrier.DOOR, entity);
                }

                if (object.has("open"))
                    barrier.open = object.getBoolean("open");

                return barrier;

            case "blood":
                String type = "";

                if (object.has("type"))
                    type = object.getString("type");

                switch (type) {
                    case "red":
                        return new Blood(Blood.RED, entity);
                    case "green":
                        return new Blood(Blood.GREEN, entity);
                    case "ectoplasm":
                        return new Blood(Blood.ECTOPLASM, entity);
                    default:
                        return new Blood(Blood.RED, entity);
                }

            case "collectable":
                Collectable collectable = new Collectable(entity);

                if (object.has("weight"))
                    collectable.weight = object.getInt("weight");

                if (object.has("id"))
                    collectable.identity = object.getInt("id");

                if (object.has("unknown")) // (blueprint only has this key if unknown == true)
                    collectable.unknown = true;

                return collectable;

            case "container":
                Container container;

                if (object.has("type")) {
                    switch (object.getString("type")) {
                        case "chest":
                            container = new Container(Container.CHEST, entity);
                            break;

                        default:
                            container = new Container(Container.DEFAULT, entity);
                    }
                }
                else {
                    container = new Container(Container.DEFAULT, entity);
                }

                if (object.has("open"))
                    container.open = object.getBoolean("open");

                if (object.has("empty"))
                    container.empty = object.getBoolean("empty");

                return container;

            case "damage":
                Damage damage = new Damage(entity);

                if (object.has("strength"))
                    damage.strength = object.getInt("strength");

                return damage;

            case "dexterity":
                Dexterity dex = new Dexterity(entity);

                if (object.has("skill"))
                    dex.skill = object.getInt("skill");

                return dex;

            case "dynamic":
                return new Dynamic(entity);

            case "energy":
                Energy energy = new Energy(entity);

                if (object.has("agility")) {
                    energy.agility = object.getInt("agility");
                }

                return new Energy(entity);

            case "input":
                return new Input(entity);

            case "ai":
                AI ai = new AI(entity);

                if (object.has("playerInterest"))
                    ai.playerInterest = object.getInt("playerInterest");

                if (object.has("canInteract"))
                    ai.canInteract = object.getBoolean("canInteract");

                if (object.has("affinity"))
                    switch (object.getString("affinity")) {
                        case "enemy":
                            ai.affinity = AI.ENEMY;
                            break;
                        case "player":
                            ai.affinity = AI.PLAYER;
                            break;
                        case "neutral":
                            ai.affinity = AI.NEUTRAL;
                        default:
                            ai.affinity = AI.ENEMY;
                    }

                return ai;

            case "experience":
                return new Experience(entity);

            case "name":
                String value = "null"; // lol
                if (object.has("value"))
                    value = object.getString("value");

                String description = "null";
                if (object.has("description"))
                    description = object.getString("description");

                return new Name(value, description, entity);

            case "physics":
                Physics physics = new Physics(entity);

                if (object.has("traversable"))
                    physics.isTraversable = object.getBoolean("traversable");

                if (object.has("blocking"))
                    physics.isBlocking = object.getBoolean("blocking");

                if (object.has("gasOrLiquid"))
                    physics.isGasOrLiquid = object.getBoolean("gasOrLiquid");

                if (object.has("activateOnCollide"))
                    physics.activateOnCollide = object.getBoolean("activateOnCollide");

                if (object.has("activateOnMove"))
                    physics.activateOnMove = object.getBoolean("activateOnMove");

                return physics;

            case "portal":
                Portal portal = new Portal(entity);

                if (object.has("activateOnStep"))
                    portal.activateOnStep = object.getBoolean("activateOnStep");

                return new Portal(entity);

            case "position":
                return new Position(entity);

            case "selfreplicate":
                return new SelfReplicate(entity);

            case "sprite":
                Sprite sprite = new Sprite(entity);
                sprite.path = "sprites/transparent.png";

                if (object.has("path"))
                    sprite.path = object.getString("path");

                if (object.has("shader")) {
                    switch (object.getString("shader")) {
                        case "wave":
                            sprite.shader = Sprite.WAVE;
                            break;

                        case "dynamic":
                        default:
                            sprite.shader = Sprite.DYNAMIC;
                            break;
                    }
                }

                return sprite;

            case "stationary":
                if (object.has("type"))
                    switch (object.getString("type")) {
                        case "default":
                        default:
                            return new Terrain(Terrain.DEFAULT, entity);
                    }

                return new Terrain(Terrain.DEFAULT, entity);

            case "usable":
                Usable usable = new Usable(entity);

                if (object.has("id"))
                    usable.effectId = object.getInt("id");

                if (object.has("effect"))
                    usable.effect = object.getString("effect");

                return usable;

            case "vitality":
                Vitality vitality = new Vitality(entity);

                if (object.has("endurance"))
                    vitality.endurance = object.getInt("endurance");

                return vitality;

            case "wieldable":
                Wieldable wieldable = new Wieldable(entity);

                if (object.has("type"))
                    wieldable.type = object.getInt("type");

                if (object.has("hands"))
                    wieldable.hands = object.getInt("hands");

                return wieldable;

            default:
                Log.e(LOG_TAG, "No component for key \"" + key + "\"");
                return null;
        }
    }
}
