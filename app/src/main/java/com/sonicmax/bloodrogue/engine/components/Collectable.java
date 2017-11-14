package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this component can be picked up and carried in a container element.
 */

public class Collectable extends Component {
    public int weight;

    public Collectable(long id) {
        super(id);
    }
}
