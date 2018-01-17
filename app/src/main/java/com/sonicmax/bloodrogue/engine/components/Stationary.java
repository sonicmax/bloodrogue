package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with stationary component will not move and are used to represent terrain.
 *  Type is used when handling interactions between entities with dynamic components and when
 *  generating terrain
 */

public class Stationary extends Component {
    public static final int DEFAULT = 0;
    public static final int FLOOR = 1;
    public static final int DOORWAY = 2;
    public static final int WALL = 3;
    public static final int BORDER = 4;

    public final int type;

    public Stationary(int type, long id) {
        super(id);

        if (isValid(type)) {
            this.type = type;
        }
        else {
            throw new IllegalArgumentException("Invalid shader for Stationary component - use provided constants in Stationary class");
        }
    }

    private boolean isValid(int type) {
        return (type >= 0 && type <= 4);
    }
}
