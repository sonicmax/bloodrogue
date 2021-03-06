package com.sonicmax.bloodrogue.engine.ai;

public class EnemyState {
    public final static int INACTIVE = -1; // Inactive (needs manual activation)
    public final static int IDLE = 0; // Idle
    public final static int SEEKING = 1; // Seeking player
    public final static int PATHFINDING = 2; // Finding new path
}
