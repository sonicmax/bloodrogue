package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.HashMap;

/**
 *  Class with static HashMaps that contain vectors for various directions.
 *  eg. Directions.Cardinal.NORTH = (0, 1)
 */

public class Directions {
    public final static HashMap<String, Vector> Cardinal;
    public final static HashMap<String, Vector> All;
    public final static HashMap<String, Vector> Diagonal;

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
