package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.HashMap;

/**
 *  Class with various constants to help deal with directions in calculations/etc.
 */

public class Directions {
    public final static HashMap<String, Vector> Cardinal;
    public final static HashMap<String, Vector> All;
    public final static HashMap<String, Vector> Diagonal;

    // These are used as values to indicate which direction player is travelling when they change
    // floors - the actual numbers are not important.
    public final static int UP = -1;
    public final static int DOWN = 1;

    static {
        Cardinal = new HashMap<>();
        Cardinal.put("NORTH", new Vector(0, 1));
        Cardinal.put("EAST", new Vector(1, 0));
        Cardinal.put("SOUTH", new Vector(0, -1));
        Cardinal.put("WEST", new Vector(-1, 0));

        All = new HashMap<>();
        All.put("NORTH", new Vector(0, 1));
        All.put("NE", new Vector(1, 1));
        All.put("EAST", new Vector(1, 0));
        All.put("SE", new Vector(1, -1));
        All.put("SOUTH", new Vector(0, -1));
        All.put("SW", new Vector(-1, -1));
        All.put("WEST", new Vector(-1, 0));
        All.put("NW", new Vector(-1, 1));

        Diagonal = new HashMap<>();
        Diagonal.put("NE", new Vector(1, 1));
        Diagonal.put("SE", new Vector(1, -1));
        Diagonal.put("SW", new Vector(-1, -1));
        Diagonal.put("NW", new Vector(-1, 1));
    }
}
