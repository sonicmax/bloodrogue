package com.sonicmax.bloodrogue.engine;

import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Actor;
import com.sonicmax.bloodrogue.engine.objects.GameObject;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class ActorTurn implements Delayed {
    private final long DEFAULT_DURATION = 1000L;
    private long start;

    private Actor actor;

    private Vector destination;
    private boolean hasMove;

    private Vector collision;
    private boolean hasCollision;

    public ActorTurn(GameObject actor) {
        this.start = System.currentTimeMillis() + DEFAULT_DURATION;
        this.actor = (Actor) actor;
        this.hasMove = false;
    }

    public Actor getActor() {
        return this.actor;
    }

    public void setCollision(Vector collision) {
        this.collision = collision;
        this.hasCollision = true;
    }

    public Vector getCollision() {
        return this.collision;
    }

    public boolean hasCollision() {
        return this.hasCollision;
    }

    public void setMove(Vector destination) {
        this.destination = destination;
        this.hasMove = true;
    }

    public Vector getDestination() {
        return this.destination;
    }

    public boolean hasMove() {
        return this.hasMove;
    }

    @Override
    public int compareTo(Delayed o) {
        long diff = getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
        diff = Math.min(diff, 1);
        diff = Math.max(diff, -1);
        return (int) diff;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = start - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = System.currentTimeMillis() + start;
    }
}
