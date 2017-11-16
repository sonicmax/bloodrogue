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
            sprite.overlayShader = Sprite.NONE;
            sprite.effectShader = Sprite.NONE;
            dex.weaponEntity = -1;
        }
    }
}