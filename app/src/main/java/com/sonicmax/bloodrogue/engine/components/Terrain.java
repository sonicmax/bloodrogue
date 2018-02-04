package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Component for entities that represent terrain (vs. objects, items, enemies, etc)
 *  Type is used when handling interactions between entities with dynamic components and when
 *  generating terrain
 */

public class Terrain extends Component {
    public static final int DEFAULT = 0;
    public static final int FLOOR = 1;
    public static final int DOORWAY = 2;
    public static final int WALL = 3;
    public static final int BORDER = 4;

    public final int type;

    public Terrain(int type, long id) {
        super(id);

        if (isValid(type)) {
            this.type = type;
        }
        else {
            throw new IllegalArgumentException("Invalid shader for Terrain component - use provided constants in Terrain class");
        }
    }

    private boolean isValid(int type) {
        return (type >= DEFAULT && type <= BORDER);
    }
}
