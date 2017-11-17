package com.sonicmax.bloodrogue.engine.systems;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Input;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Wieldable;
import com.sonicmax.bloodrogue.renderer.Shader;
import com.sonicmax.bloodrogue.tilesets.WeaponTileset;

/**
 *  Provides some helper methods for dealing with weapon entities.
 */

public class WeaponsSystem {
    public static final String LOG_TAG = WeaponsSystem.class.getSimpleName();

    public static boolean hasEqupped(ComponentManager componentManager, long entity, int type) {
        Dexterity dex = (Dexterity) componentManager.getEntityComponent(entity, Dexterity.class.getSimpleName());
        switch (type) {
            case Wieldable.WEAPON:
                if (dex.weaponEntity > -1) {
                    return true;
                }
                else {
                    return false;
                }

            case Wieldable.ARMOUR:
                if (dex.armourEntity > -1) {
                    return true;
                }
                else {
                    return false;
                }

            default:
                Log.e(LOG_TAG, "No Wieldable field for type \"" + type + "\"");
                return false;
        }
    }

    /**
     *  Checks if entity is alraedy wielded - if true, then unwields entity.
     *  If false, equips entity as current weapon.
     */

    public static void checkAndToggleWeapon(ComponentManager componentManager, long item, long player) {
        Wieldable wieldable = (Wieldable) componentManager.getEntityComponent(item, Wieldable.class.getSimpleName());
        Dexterity equipment = (Dexterity) componentManager.getEntityComponent(player, Dexterity.class.getSimpleName());

        if (wieldable.id == equipment.weaponEntity) {
            unwieldCurrentWeapon(componentManager, player);
        }

        else if (wieldable.type == Wieldable.WEAPON) {
            wieldWeapon(componentManager, player, item);
        }
    }

    public static void wieldWeapon(ComponentManager componentManager, long actor, long weapon) {
        Dexterity dex = (Dexterity) componentManager.getEntityComponent(actor, Dexterity.class.getSimpleName());
        Wieldable wieldable = (Wieldable) componentManager.getEntityComponent(weapon, Wieldable.class.getSimpleName());
        if (dex != null && wieldable != null && wieldable.type == Wieldable.WEAPON) {
            if (dex.weaponEntity > -1) {
                unwieldCurrentWeapon(componentManager, actor);
            }

            dex.weaponEntity = weapon;

            // Display wielded weapon sprite as overlay on actor and disable original sprite
            Sprite sprite = (Sprite) componentManager.getEntityComponent(actor, Sprite.class.getSimpleName());
            Sprite weaponSprite = (Sprite) componentManager.getEntityComponent(weapon, Sprite.class.getSimpleName());

            sprite.overlayPath = WeaponTileset.getWieldableSpriteForWeapon(weaponSprite.path);
            sprite.overlayIndex = -1;
            if (sprite.overlayPath != null) {
                sprite.overlayShader = Sprite.DYNAMIC;
            }

            switch (wieldable.hands) {
                case 1:
                    sprite.path = "sprites/dude.png";
                    break;
                case 2:
                    sprite.path = "sprites/dude_two_handed.png";
                    break;
                default:
                    Log.e(LOG_TAG, "Weird number of hands? (" + wieldable.hands + ")");
                    sprite.path = "sprites/dude.png";
                    break;
            }

            sprite.spriteIndex = -1;

            sprite.effectPath = WeaponTileset.getEffectForWeapon(sprite.overlayPath);
            if (sprite.effectPath != null) {
                sprite.effectShader = Sprite.WAVE;
            }

            weaponSprite.shader = Sprite.NONE;
        }
    }

    public static void unwieldCurrentWeapon(ComponentManager componentManager, long actor) {
        Dexterity dex = (Dexterity) componentManager.getEntityComponent(actor, Dexterity.class.getSimpleName());

        if (dex != null) {
            Sprite sprite = (Sprite) componentManager.getEntityComponent(actor, Sprite.class.getSimpleName());

            sprite.overlayIndex = -1;
            sprite.overlayPath = null;
            sprite.overlayShader = Sprite.NONE;

            sprite.effectPath = null;
            sprite.effectIndex = -1;
            sprite.effectShader = Sprite.NONE;

            dex.weaponEntity = -1;
        }
    }
}
