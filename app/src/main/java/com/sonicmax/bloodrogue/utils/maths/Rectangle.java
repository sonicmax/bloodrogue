package com.sonicmax.bloodrogue.utils.maths;

import java.io.Serializable;

public class Rectangle implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Rectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
