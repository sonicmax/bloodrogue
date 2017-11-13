package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this component can do damage when used against other entities.
 *  Player/enemies will have a base damage component, but can also use weapons with their own damage components.
 *  Some static entities will have damage components (eg. fire/spike hazards) and some damage
 *  components will only be activated contextually (eg. doors/chests if bash attempt fails)
 *
 *  Damage dealt will always be calculated from component strength and target endurance.
 */

public class Damage extends Component {
    public int strength;

    public Damage(long id) {
        super(id);
    }
}
