package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.Entity;

import java.util.ArrayList;

/**
 *  Entities with container component can hold collections of other entities.
 */

public class Container extends Component {
    public static final int DEFAULT = 0; // No special behaviours. Used for inventories
    public static final int CHEST = 1;

    public final int type;
    public final ArrayList<Sprite> contents;

    public int totalWeight;
    public int capacity;
    public boolean open;
    public boolean empty;

    public Container(int type, long id) {
        super(id);
        this.type = type;
        this.contents = new ArrayList<>();
    }
}
