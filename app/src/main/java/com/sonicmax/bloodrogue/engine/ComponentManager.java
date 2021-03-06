package com.sonicmax.bloodrogue.engine;

import android.util.Log;

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
import com.sonicmax.bloodrogue.engine.components.AI;
import com.sonicmax.bloodrogue.engine.components.Knowledge;
import com.sonicmax.bloodrogue.engine.components.Name;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Portal;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.SelfReplicate;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Terrain;
import com.sonicmax.bloodrogue.engine.components.Trap;
import com.sonicmax.bloodrogue.engine.components.Usable;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.engine.components.Wieldable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 *  Contains lists of sorted components in use, and provides some methods to allow us to add/remove/filter
 *  components by entity ID. (also hides the ugly code)
 *
 *  Uses a singleton pattern to ensure that we only have a single instance of this class and
 *  that it's easily accessible.
 */

public class ComponentManager {
    // Note: update this whenever new components are added
    public final static int MAX_COMPONENTS = 18;

    private final String LOG_TAG = this.getClass().getSimpleName();

    private static ComponentManager INSTANCE = null;

    private HashMap<Long, Barrier> barrierComponents;
    private HashMap<Long, Blood> bloodComponents;
    private HashMap<Long, Collectable> collectableComponents;
    private HashMap<Long, Container> containerComponents;
    private HashMap<Long, Damage> damageComponents;
    private HashMap<Long, Dexterity> dexterityComponents;
    private HashMap<Long, Dynamic> dynamicComponents;
    private HashMap<Long, Energy> energyComponents;
    private HashMap<Long, Input> inputComponents;
    private HashMap<Long, AI> intelligenceComponents;
    private HashMap<Long, Knowledge> knowledgeComponents;
    private HashMap<Long, Experience> levelComponents;
    private HashMap<Long, Name> nameComponents;
    private HashMap<Long, Physics> physicsComponents;
    private HashMap<Long, Portal> portalComponents;
    private HashMap<Long, Position> positionComponents;
    private HashMap<Long, SelfReplicate> selfreplicateComponents;
    private HashMap<Long, Sprite> spriteComponents;
    private HashMap<Long, Terrain> staticComponents;
    private HashMap<Long, Trap> trapComponents;
    private HashMap<Long, Usable> usableComponents;
    private HashMap<Long, Vitality> vitalityComponents;
    private HashMap<Long, Wieldable> wieldableComponents;

    public ComponentManager() {
        barrierComponents = new HashMap<>();
        bloodComponents = new HashMap<>();
        collectableComponents = new HashMap<>();
        containerComponents = new HashMap<>();
        damageComponents = new HashMap<>();
        dexterityComponents = new HashMap<>();
        dynamicComponents = new HashMap<>();
        energyComponents = new HashMap<>();
        inputComponents = new HashMap<>();
        intelligenceComponents = new HashMap<>();
        knowledgeComponents = new HashMap<>();
        levelComponents = new HashMap<>();
        nameComponents = new HashMap<>();
        physicsComponents = new HashMap<>();
        portalComponents = new HashMap<>();
        positionComponents = new HashMap<>();
        selfreplicateComponents = new HashMap<>();
        spriteComponents = new HashMap<>();
        staticComponents = new HashMap<>();
        trapComponents = new HashMap<>();
        usableComponents = new HashMap<>();
        vitalityComponents = new HashMap<>();
        wieldableComponents = new HashMap<>();
    }

    public static ComponentManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ComponentManager();
        }

        return INSTANCE;
    }

    /**
     * Clears all component hashmaps. Primarily used when switching floors
     */

    public void clear() {
        barrierComponents.clear();
        bloodComponents.clear();
        collectableComponents.clear();
        containerComponents.clear();
        damageComponents.clear();
        dexterityComponents.clear();
        dynamicComponents.clear();
        energyComponents.clear();
        inputComponents.clear();
        intelligenceComponents.clear();
        knowledgeComponents.clear();
        levelComponents.clear();
        nameComponents.clear();
        physicsComponents.clear();
        portalComponents.clear();
        positionComponents.clear();
        selfreplicateComponents.clear();
        spriteComponents.clear();
        staticComponents.clear();
        trapComponents.clear();
        usableComponents.clear();
        vitalityComponents.clear();
        wieldableComponents.clear();
    }

    /**
     * Given a Component[], sorts each Component by renderState and entity.
     *
     * @param components
     */

    public void sortComponentArray(Component[] components) {
        for (int i = 0; i < components.length; i++) {
            if (components[i] != null) {
                sortComponent(components[i]);
            }
        }
    }

    /**
     * Adds Component to HashMap, organised by entity.
     *
     * @param component
     */

    public void sortComponent(Component component) {
        switch (component.TAG) {
            case "Barrier":
                barrierComponents.put(component.id, (Barrier) component);
                break;

            case "Blood":
                bloodComponents.put(component.id, (Blood) component);
                break;

            case "Collectable":
                collectableComponents.put(component.id, (Collectable) component);
                break;

            case "Container":
                containerComponents.put(component.id, (Container) component);
                break;

            case "Damage":
                damageComponents.put(component.id, (Damage) component);
                break;

            case "Dexterity":
                dexterityComponents.put(component.id, (Dexterity) component);
                break;

            case "Dynamic":
                dynamicComponents.put(component.id, (Dynamic) component);
                break;

            case "Energy":
                energyComponents.put(component.id, (Energy) component);
                break;

            case "Input":
                inputComponents.put(component.id, (Input) component);
                break;

            case "Knowledge":
                knowledgeComponents.put(component.id, (Knowledge) component);
                break;

            case "AI":
                intelligenceComponents.put(component.id, (AI) component);
                break;

            case "Experience":
                levelComponents.put(component.id, (Experience) component);
                break;

            case "Name":
                nameComponents.put(component.id, (Name) component);
                break;

            case "Physics":
                physicsComponents.put(component.id, (Physics) component);
                break;

            case "Portal":
                portalComponents.put(component.id, (Portal) component);
                break;

            case "Position":
                positionComponents.put(component.id, (Position) component);
                break;

            case "SelfReplicate":
                selfreplicateComponents.put(component.id, (SelfReplicate) component);
                break;

            case "Sprite":
                spriteComponents.put(component.id, (Sprite) component);
                break;

            case "Terrain":
                staticComponents.put(component.id, (Terrain) component);
                break;

            case "Trap":
                trapComponents.put(component.id, (Trap) component);
                break;

            case "Usable":
                usableComponents.put(component.id, (Usable) component);
                break;

            case "Vitality":
                vitalityComponents.put(component.id, (Vitality) component);
                break;

            case "Wieldable":
                wieldableComponents.put(component.id, (Wieldable) component);
                break;

            default:
                Log.e(LOG_TAG, "No bucket found for component renderState \"" + component.TAG + "\"");
        }
    }

    /**
     * Returns all components of a given renderState.
     *
     * @param type
     * @return ArrayList of Components
     */

    public ArrayList getComponents(String type) {
        switch (type) {
            case "Barrier":
                return new ArrayList<>(barrierComponents.values());

            case "Blood":
                return new ArrayList<>(bloodComponents.values());

            case "Collectable":
                return new ArrayList<>(collectableComponents.values());

            case "Container":
                return new ArrayList<>(containerComponents.values());

            case "Damage":
                return new ArrayList<>(damageComponents.values());

            case "Dexterity":
                return new ArrayList<>(dexterityComponents.values());

            case "Dynamic":
                return new ArrayList<>(dynamicComponents.values());

            case "Energy":
                return new ArrayList<>(energyComponents.values());

            case "Input":
                return new ArrayList<>(inputComponents.values());

            case "Knowledge":
                return new ArrayList<>(knowledgeComponents.values());

            case "AI":
                return new ArrayList<>(intelligenceComponents.values());

            case "Experience":
                return new ArrayList<>(levelComponents.values());

            case "Name":
                return new ArrayList<>(nameComponents.values());

            case "Physics":
                return new ArrayList<>(physicsComponents.values());

            case "Portal":
                return new ArrayList<>(portalComponents.values());

            case "Position":
                return new ArrayList<>(positionComponents.values());

            case "SelfReplicate":
                return new ArrayList<>(selfreplicateComponents.values());

            case "Sprite":
                return new ArrayList<>(spriteComponents.values());

            case "Terrain":
                return new ArrayList<>(staticComponents.values());

            case "Trap":
                return new ArrayList<>(trapComponents.values());

            case "Usable":
                return new ArrayList<>(usableComponents.values());

            case "Vitality":
                return new ArrayList<>(vitalityComponents.values());

            case "Wieldable":
                return new ArrayList<>(wieldableComponents.values());

            default:
                Log.e(LOG_TAG, "No bucket found for component renderState \"" + type + "\"");
                return new ArrayList();
        }
    }

    /**
     * Returns all components associated with entity.
     *
     * @param entity
     * @return ArrayList of Components
     */

    public ArrayList<Component> getEntityComponents(long entity) {
        ArrayList<Component> array = new ArrayList<>();

        array.add(barrierComponents.get(entity));
        array.add(bloodComponents.get(entity));
        array.add(collectableComponents.get(entity));
        array.add(containerComponents.get(entity));
        array.add(damageComponents.get(entity));
        array.add(dexterityComponents.get(entity));
        array.add(dynamicComponents.get(entity));
        array.add(energyComponents.get(entity));
        array.add(inputComponents.get(entity));
        array.add(intelligenceComponents.get(entity));
        array.add(knowledgeComponents.get(entity));
        array.add(levelComponents.get(entity));
        array.add(nameComponents.get(entity));
        array.add(physicsComponents.get(entity));
        array.add(portalComponents.get(entity));
        array.add(positionComponents.get(entity));
        array.add(selfreplicateComponents.get(entity));
        array.add(spriteComponents.get(entity));
        array.add(staticComponents.get(entity));
        array.add(trapComponents.get(entity));
        array.add(usableComponents.get(entity));
        array.add(vitalityComponents.get(entity));
        array.add(wieldableComponents.get(entity));

        Iterator<Component> iterator = array.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == null) {
                iterator.remove();
            }
        }

        return array;
    }

    /**
     * Returns requested component for a given entity.
     *
     * @param entity
     * @param type
     * @return
     */

    public Component getEntityComponent(long entity, String type) {
        switch (type) {
            case "Barrier":
                return barrierComponents.get(entity);

            case "Blood":
               return bloodComponents.get(entity);

            case "Collectable":
                return collectableComponents.get(entity);

            case "Container":
                return containerComponents.get(entity);

            case "Damage":
                return damageComponents.get(entity);

            case "Dexterity":
                return dexterityComponents.get(entity);

            case "Dynamic":
                return dynamicComponents.get(entity);

            case "Energy":
                return energyComponents.get(entity);

            case "Input":
                return inputComponents.get(entity);

            case "Knowledge":
                return knowledgeComponents.get(entity);

            case "AI":
                return intelligenceComponents.get(entity);

            case "Experience":
                return levelComponents.get(entity);

            case "Name":
                return nameComponents.get(entity);

            case "Physics":
                return physicsComponents.get(entity);

            case "Portal":
                return portalComponents.get(entity);

            case "Position":
                return positionComponents.get(entity);

            case "SelfReplicate":
                return selfreplicateComponents.get(entity);

            case "Sprite":
                return spriteComponents.get(entity);

            case "Terrain":
                return staticComponents.get(entity);

            case "Trap":
                return trapComponents.get(entity);

            case "Usable":
                return usableComponents.get(entity);

            case "Vitality":
                return vitalityComponents.get(entity);

            case "Wieldable":
                return wieldableComponents.get(entity);

            default:
                Log.e(LOG_TAG, "No bucket found for component renderState \"" + type + "\"");
                return null;
        }
    }

    /**
     * Removes specific component for a given entity.
     *
     * @param entity
     * @param type
     */

    public void removeEntityComponent(long entity, String type) {
        switch (type) {
            case "Barrier":
                barrierComponents.remove(entity);
                break;

            case "Blood":
                bloodComponents.remove(entity);
                break;

            case "Collectable":
                collectableComponents.remove(entity);
                break;

            case "Container":
                containerComponents.remove(entity);
                break;

            case "Damage":
                damageComponents.remove(entity);
                break;

            case "Dexterity":
                dexterityComponents.remove(entity);
                break;

            case "Dynamic":
                dynamicComponents.remove(entity);
                break;

            case "Energy":
                energyComponents.remove(entity);
                break;

            case "Input":
                inputComponents.remove(entity);
                break;

            case "Knowledge":
                knowledgeComponents.remove(entity);
                break;

            case "AI":
                intelligenceComponents.remove(entity);
                break;

            case "Experience":
                levelComponents.remove(entity);
                break;

            case "Name":
                nameComponents.remove(entity);
                break;

            case "Physics":
                physicsComponents.remove(entity);
                break;

            case "Portal":
                portalComponents.remove(entity);
                break;

            case "Position":
                positionComponents.remove(entity);
                break;

            case "SelfReplicate":
                selfreplicateComponents.remove(entity);
                break;

            case "Sprite":
                spriteComponents.remove(entity);
                break;

            case "Terrain":
                staticComponents.remove(entity);
                break;

            case "Trap":
                trapComponents.remove(entity);
                break;

            case "Usable":
                usableComponents.remove(entity);
                break;

            case "Vitality":
                vitalityComponents.remove(entity);
                break;

            case "Wieldable":
                wieldableComponents.remove(entity);
                break;

            default:
                Log.e(LOG_TAG, "No bucket found for component renderState \"" + type + "\"");
        }
    }

    /**
     * Removes all components for a given entity. Used when we want to completely remove
     * an entity from game
     *
     * @param entity
     */

    public void removeEntityComponents(long entity) {
        barrierComponents.remove(entity);
        bloodComponents.remove(entity);
        collectableComponents.remove(entity);
        containerComponents.remove(entity);
        damageComponents.remove(entity);
        dexterityComponents.remove(entity);
        dynamicComponents.remove(entity);
        energyComponents.remove(entity);
        inputComponents.remove(entity);
        intelligenceComponents.remove(entity);
        knowledgeComponents.remove(entity);
        levelComponents.remove(entity);
        nameComponents.remove(entity);
        physicsComponents.remove(entity);
        portalComponents.remove(entity);
        positionComponents.remove(entity);
        selfreplicateComponents.remove(entity);
        spriteComponents.remove(entity);
        staticComponents.remove(entity);
        trapComponents.remove(entity);
        usableComponents.remove(entity);
        vitalityComponents.remove(entity);
        wieldableComponents.remove(entity);
    }

    public boolean has(long entity, String component) {
        return (getEntityComponent(entity, component) != null);
    }
}
