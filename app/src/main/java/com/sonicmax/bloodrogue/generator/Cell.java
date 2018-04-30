package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.utils.maths.Vector2D;

public class Cell extends Vector2D {
    private String direction;

    public Cell(int x, int y, String direction) {
        super(x, y);
        this.direction = direction;
    }

    public String getDirection() {
        return this.direction;
    }
}
