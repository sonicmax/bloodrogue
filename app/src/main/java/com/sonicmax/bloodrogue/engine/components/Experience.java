package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this class are capable of gaining experience and levelling up.
 *  This will affect values in other components (eg vitality, damage, etc)
 *  Having this as a separate component will allow us to create behaviours like entities that can
 *  be used as weapons or armour which "lose experience" when used and become weaker
 */

public class Experience extends Component {
    public int level;
    public int totalXp;
    public int xp;
    public int xpToNextLevel;

    public Experience(long id) {
        super(id);
    }
}
