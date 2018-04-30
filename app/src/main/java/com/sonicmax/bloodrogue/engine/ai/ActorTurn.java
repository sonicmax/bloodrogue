package com.sonicmax.bloodrogue.engine.ai;

import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Used to store moves for each turn so we can prioritise based on different criteria
 */

public class ActorTurn implements Delayed {
    private final long DEFAULT_DURATION = 1000L;
    private long start;

    private Position positionComponent;

    private Vector2D destination;
    private boolean hasMove;

    private Vector2D collision;
    private boolean hasCollision;

    public ActorTurn(Position positionComponent) {
        this.start = System.currentTimeMillis() + DEFAULT_DURATION;
        this.positionComponent = positionComponent;
        this.hasMove = false;
    }

    public Position getPositionComponent() {
        return this.positionComponent;
    }

    public long getEntity() {
        return this.positionComponent.id;
    }

    public void setCollision(Vector2D collision) {
        this.collision = collision;
        this.hasCollision = true;
    }

    public Vector2D getCollision() {
        return this.collision;
    }

    public boolean hasCollision() {
        return this.hasCollision;
    }

    public void setMove(Vector2D destination) {
        this.destination = destination;
        this.hasMove = true;
    }

    public Vector2D getDestination() {
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
