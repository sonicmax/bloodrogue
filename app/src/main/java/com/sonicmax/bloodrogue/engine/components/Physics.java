package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Physics component contains some flags that determine results of certain interactions (in terms
 *  of the in-game physics, rather than real physics - eg. what happens if we collide with this object)
 */

public class Physics extends Component {
    public boolean isBlocking;
    public boolean isTraversable;
    public boolean isGasOrLiquid;
    public boolean activateOnCollide;
    public boolean activateOnMove;
    public boolean isDestructable;

    public Physics(long id) {
        super(id);

        // Default settings (least likely to cause issues if we forget to set anything)
        this.isBlocking = false;
        this.isTraversable = true;
        this.isGasOrLiquid = false;
        this.activateOnCollide = false;
        this.activateOnMove = false;
        this.isDestructable = true;
    }
}
