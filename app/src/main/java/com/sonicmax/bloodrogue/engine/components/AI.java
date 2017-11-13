package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;

/**
 *  Entities with this component will typically be computer-controlled (but not necessarily enemies - also
 *  potentially allies, projectiles, etc). It is also used to store values for player-controlled entities.
 *  Typically the game engine will look at possible moves and assign them to path field, or move
 *  entity directly to new position. These entities should also have dynamic component (or otherwise will never move).
 */

public class AI extends Component {
    public static final int PLAYER = 0;
    public static final int ENEMY = 1;
    public static final int NEUTRAL = 2;

    public boolean computerControlled;
    public ArrayList<Vector> path;
    public boolean canInteract;
    public int dijkstra;
    public int playerInterest;
    public int state;
    public int affinity;

    public AI(long id) {
        super(id);
        this.path = new ArrayList<>();
        this.computerControlled = true;
        this.dijkstra = 0;
    }
}
