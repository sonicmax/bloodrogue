package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.maths.Vector;

public class Cell extends Vector {
    private String direction;

    public Cell(int x, int y, String direction) {
        super(x, y);
        this.direction = direction;
    }

    public String getDirection() {
        return this.direction;
    }
}
