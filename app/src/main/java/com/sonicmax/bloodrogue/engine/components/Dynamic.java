package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Dynamic component indicates that entity state is likely to change (eg. via opening, moving, etc)
 *  Todo: do we even need this?
 */

public class Dynamic extends Component {
    public Dynamic(long id) {
        super(id);
    }
}
