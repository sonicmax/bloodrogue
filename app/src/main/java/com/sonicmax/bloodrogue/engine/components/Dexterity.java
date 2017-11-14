package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with this component are able to use equipment. Skill field determines the extent
 *  that equipment enhances their base values, and the equipped entities are stored in
 *  weaponEntity/armourEntity fields.
 */

public class Dexterity extends Component {
    public int skill;
    public long weaponEntity;
    public long armourEntity;

    public Dexterity(long id) {
        super(id);
        this.weaponEntity = -1;
        this.armourEntity = -1;
    }
}
