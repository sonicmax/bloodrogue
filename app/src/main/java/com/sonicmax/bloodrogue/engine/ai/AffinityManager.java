package com.sonicmax.bloodrogue.engine.ai;

import com.sonicmax.bloodrogue.engine.objects.Actor;

/**
 *  Manages relationships between Actors with different affinities.
 *  At the most basic level, makes sure that enemies and player are aggressive to each other.
 *  Also handles aggression between different types of enemy and
 */

public class AffinityManager {
    public AffinityManager() {

    }

    public boolean actorsAreAggressive(Actor a, Actor b) {
        int affinityA = a.getAffinity();
        int affinityB = b.getAffinity();

        // Actors with the same affinity will not target each other
        if (affinityA == affinityB) {
            return false;
        }

        // Actors with neutral affinity will not be targeted, and won't target others.
        // (however, their affinity can be changed if accidentaly attacked)
        if (affinityA == Actor.NEUTRAL || affinityB == Actor.NEUTRAL) {
            return false;
        }

        switch (affinityA) {
            case Actor.PLAYER:
                if (affinityB == Actor.ENEMY) {
                    return true;
                }

            case Actor.ENEMY:
                if (affinityB == Actor.PLAYER) {
                    return true;
                }

            default:
                return false;

        }
    }
}
