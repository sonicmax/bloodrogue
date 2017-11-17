package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this component can be used as equipment.
 */

public class Wieldable extends Component {
    public static final int NONE = 0;
    public static final int WEAPON = 1;
    public static final int ARMOUR = 2;

    public int type; // Type of equipment (use Wieldable static constants)
    public int hands; // Number of hands required to equip item

    public Wieldable(long id) {
        super(id);
        this.type = Wieldable.NONE;
        this.hands = 1;
    }
}
