package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;

/**
 *  Portal component allows us to transport other entities to different locations - whether it's
 *  a different tile on the map, or a different floor entirely.
 */

public class Portal extends Component {
    public int destFloor;
    public Vector2D destTile;
    public boolean activateOnStep;

    public Portal(long id) {
        super(id);
        this.destFloor = -1;
        this.destTile = null;
    }
}
