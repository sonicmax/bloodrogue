package com.sonicmax.bloodrogue.engine.ai;

import com.sonicmax.bloodrogue.engine.components.AI;

/**
 *  Manages relationships between Actors with different affinities.
 *  At the most basic level, makes sure that enemies and player are aggressive to each other.
 *  Also handles aggression between different types of enemy and
 */

public class AffinityManager {
    public AffinityManager() {

    }

    public boolean actorsAreAggressive(AI a, AI b) {
        int affinityA = a.affinity;
        int affinityB = b.affinity;

        // Actors with the same affinity will not target each other
        if (affinityA == affinityB) {
            return false;
        }

        // Actors with neutral affinity will not be targeted, and won't target others.
        // (however, their affinity can be changed if accidentaly attacked)
        if (affinityA == AI.NEUTRAL || affinityB == AI.NEUTRAL) {
            return false;
        }

        switch (affinityA) {
            case AI.PLAYER:
                if (affinityB == AI.ENEMY) {
                    return true;
                }

            case AI.ENEMY:
                if (affinityB == AI.PLAYER) {
                    return true;
                }

            default:
                return false;

        }
    }
}
