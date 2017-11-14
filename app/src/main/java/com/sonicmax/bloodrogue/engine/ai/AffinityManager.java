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

    public boolean entitiesAreAggressive(AI attacker, AI defender) {
        if (attacker.state == EnemyState.INACTIVE || defender.state == EnemyState.INACTIVE) {
            // Probably a mistake - don't let entities engage in combat
            return false;
        }

        int attackerAffinity = attacker.affinity;
        int defenderAffinity = defender.affinity;

        // Entities with the same affinity will not target each other
        if (attackerAffinity == defenderAffinity) {
            return false;
        }

        // Entities with neutral affinity won't attack others, but will become aggressive when attacked
        if (attackerAffinity == AI.NEUTRAL) {
            return false;
        }

        switch (attackerAffinity) {
            case AI.PLAYER:
                if (defenderAffinity == AI.ENEMY) {
                    return true;
                }

            case AI.ENEMY:
                if (defenderAffinity == AI.PLAYER) {
                    return true;
                }

            default:
                return false;

        }
    }
}
