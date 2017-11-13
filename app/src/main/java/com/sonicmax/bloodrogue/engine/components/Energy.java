package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Energy component will affect results of some in-game events (eg. attacking) if present in entity.
 *  Can be combined in unusual ways (eg. a weapon that becomes "hungry" and weak when not used regularly)
 */

public class Energy extends Component {
    public int agility;
    public int energy;
    public int hunger;

    public Energy(long id) {
        super(id);
    }
}
