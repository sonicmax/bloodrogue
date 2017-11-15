package com.sonicmax.bloodrogue.tilesets;

import android.util.Log;

public class WeaponTileset {
    private static final String LOG_TAG = WeaponTileset.class.getSimpleName();

    public static final String SHORTSWORD = "sprites/shortsword.png";
    public static final String SHORTSWORD_WIELD = "sprites/shortsword_wield.png";
    public static final String LONGSWORD = "sprites/longsword.png";
    public static final String LONGSWORD_WIELD = "sprites/longsword_wield.png";
    public static final String SPEAR = "sprites/spear.png";
    public static final String SPEAR_WIELD = "sprites/spear_wield.png";
    public static final String DEMON_SWORD = "sprites/demon_sword.png";
    public static final String DEMON_SWORD_WIELD = "sprites/demon_sword_wield.png";
    public static final String DEMON_SWORD_EFFECT = "sprites/demon_sword_effect.png";

    public static String getWieldableSpriteForWeapon(String weapon) {
        switch (weapon) {
            case SHORTSWORD:
                return SHORTSWORD_WIELD;

            case LONGSWORD:
                return LONGSWORD_WIELD;

            case SPEAR:
                return SPEAR_WIELD;

            case DEMON_SWORD:
                return DEMON_SWORD_WIELD;

            default:
                Log.e(LOG_TAG, "No wieldable sprite for path \"" + weapon + "\"");
                return "";
        }
    }

    public static String getEffectForWeapon(String weapon) {
        switch (weapon) {
            case DEMON_SWORD:
            case DEMON_SWORD_WIELD:
                return DEMON_SWORD_EFFECT;

            default:
                return null;
        }
    }
}
