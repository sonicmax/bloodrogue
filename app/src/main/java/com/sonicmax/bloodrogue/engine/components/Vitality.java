package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Entities with vitality can be damaged, and will be destroyed by engine if vitality falls
 *  below a certain threshold. Armour is implemented as an entity with vitality which soaks up
 *  damage before it affects the wearer. Some static objects also have vitality (eg. locked doors)
 */

public class Vitality extends Component {
    public final int BASE_HP = 10;

    public int maxHp;
    public int endurance;
    public int hp;

    public Vitality(long id) {
        super(id);
        this.maxHp = BASE_HP;
        this.hp = BASE_HP;
    }
}
