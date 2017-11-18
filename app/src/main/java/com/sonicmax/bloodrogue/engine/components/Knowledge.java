package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

import java.util.HashMap;

/**
 *  Entities with this component have the ability to remember the identities of collectable entities
 *  with unknown flag set to true. The identified hashmap stores the collectable component id and
 *  maps it with the intended in-game identity (as defined in PotionSystem).
 *
 *  Usable items will be identified after being consumed/cast, but entities with high experience
 *  level may identify after collecting item.
 *
 *  Wieldable items have a small chance to be identified after each combat turn. Equipping an
 *  unknown item will generally reduce its efficiency and potentially cause undesired effects
 *  (if cursed/trapped/etc).
 *
 *  Most unknown items can be identified using magic.
 */

public class Knowledge extends Component {
    public HashMap<Integer, Integer> identifiedItems;

    public Knowledge(long entity) {
        super(entity);
        this.identifiedItems = new HashMap<>();
    }
}
