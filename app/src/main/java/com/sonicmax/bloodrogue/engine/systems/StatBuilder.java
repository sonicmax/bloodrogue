package com.sonicmax.bloodrogue.engine.systems;

import com.sonicmax.bloodrogue.engine.components.Damage;
import com.sonicmax.bloodrogue.engine.components.Experience;
import com.sonicmax.bloodrogue.engine.components.Vitality;

/**
 *  Handles population of stat-related fields (vitality, damage, strength, etc)
 */

public class StatBuilder {

    /**
     *  Initialises component and calculates XP points to next level
     */

    public static void setDefaultLevel(Experience component) {
        component.level = 1;
        component.xpToNextLevel = getXpToNextLevel(component.level + 1);
    }

    public static void setLevel(int level, Experience component) {
        component.level = level;
        component.xpToNextLevel = getXpToNextLevel(component.level + 1);
    }

    public static int getXpToNextLevel(int level) {
        double points = Math.floor(level + 300 * Math.pow(2, level / 7));
        return (int) Math.floor(points / 4);
    }

    public static int getXpReward(Vitality v, Damage d, Experience target, Experience origin) {
        return Math.round((v.maxHp + d.strength + v.endurance) * target.level / (origin.level + 1));
    }





}
