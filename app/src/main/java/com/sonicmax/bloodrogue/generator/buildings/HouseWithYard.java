package com.sonicmax.bloodrogue.generator.buildings;

import com.sonicmax.bloodrogue.generator.Chunk;

public class HouseWithYard {
    public final Chunk house;
    public final Chunk yard;

    public HouseWithYard(Chunk house, Chunk yard) {
        this.house = house;
        this.yard = yard;
    }
}
