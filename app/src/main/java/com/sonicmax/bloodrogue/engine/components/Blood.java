package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this entity will bleed when attacked.
 */

public class Blood extends Component {
    public static final int RED = 0;
    public static final int GREEN = 1;
    public static final int ECTOPLASM = 2;

    public final int type;

    public Blood(int type, long id) {
        super(id);
        this.type = type;
    }
}
