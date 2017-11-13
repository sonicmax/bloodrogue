package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Holds position data for entity.
 */

public class Position extends Component {
    public int x;
    public int y;

    public Position(long id) {
        super(id);
    }
}
