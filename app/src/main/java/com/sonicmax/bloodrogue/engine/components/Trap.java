package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  This components indicates that entity may do damage when activated.
 *  To function correctly, entity needs to have activateOnCollide/activateOnMove flags enabled in
 *  physics component, a damage component, and chanceToActivate needs to be set to > 0
 */

public class Trap extends Component {
    public float chanceToActivate;

    public Trap(long id) {
        super(id);
        this.chanceToActivate = 0f;
    }
}
