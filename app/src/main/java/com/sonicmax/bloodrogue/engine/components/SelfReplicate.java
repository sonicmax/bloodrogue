package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with SelfReplicate component have a chance to self-replicate each turn.
 */

public class SelfReplicate extends Component {
    public boolean canSelfReplicate;
    public float chanceToSelfReplicate;

    public SelfReplicate(long id) {
        super(id);
    }
}
