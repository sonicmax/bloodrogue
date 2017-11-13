package com.sonicmax.bloodrogue.engine;

import java.util.UUID;

public class Entity {
    public final long id;

    public Entity(long id) {
        this.id = id;
    }

    public Entity() {
        this.id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
