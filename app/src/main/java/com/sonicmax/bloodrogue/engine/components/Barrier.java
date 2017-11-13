package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with barrier component block entity movement until "opened". By default these are closed
 */

public class Barrier extends Component {
    public static final int DOOR = 0;

    public final int type;
    public boolean open;

    public Barrier(int type, long id) {
        super(id);
        this.open = false;
        this.type = type;
    }
}
