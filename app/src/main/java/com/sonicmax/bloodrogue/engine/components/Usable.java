package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Component which indicates whether item is usable, and contains action when used/consumed.
 *  Action will typically relate to the component that is used to perform the action
 *  (eg. vitality for health restoration)
 */

public class Usable extends Component {
    public String effect;
    public int effectId;

    public Usable(long entity) {
        super(entity);
    }
}
