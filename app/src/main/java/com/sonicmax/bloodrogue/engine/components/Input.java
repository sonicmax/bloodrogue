package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this component will receive input events from Activity.
 *  This is essentially saying that entity will be player-controlled (but not always)
 */

public class Input extends Component {
    public Input(long id) {
        super(id);
    }
}
