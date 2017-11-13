package com.sonicmax.bloodrogue.engine;

import java.io.Serializable;

/**
 *  Base class for component system which holds the entity that component is associated with.
 *  Each instantiated component can only ever be associated with a single entity.
 */

public class Component implements Serializable {
    public final String TAG = this.getClass().getSimpleName();

    private static final long serialVersionUID = 1L;
    public final long id;

    public Component(long id) {
        this.id = id;
    }
}
