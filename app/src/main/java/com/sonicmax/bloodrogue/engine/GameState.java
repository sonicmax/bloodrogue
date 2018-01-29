package com.sonicmax.bloodrogue.engine;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *  Holds all data relevant to the current game state - the terrain and objects that populate each floor,
 *  some global flags and statistics, etc. Implements Serializable so we can save and restore data.
 */

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String LOG_TAG = this.getClass().getSimpleName();

    private ArrayList<FloorData> floors;
    private int currentFloor;
    private Component[] player;

    public GameState() {
        this.floors = new ArrayList<>();
        this.currentFloor = 1;
    }

    /**
     *  Each time player descends to a new floor, we add it to GameState using this method.
     */

    public void addFloor(FloorData floor) {
        this.floors.add(floor);
    }

    public void addFloors(ArrayList<FloorData> floors) {
        this.floors = floors;
    }

    public void updateFloor(int floorNumber, FloorData floor) {
        if (hasFloor(floorNumber)) {
            int index = floorNumber - 1;
            floors.set(index, floor);
        }
    }

    public boolean hasFloor(int index) {
        return index <= this.floors.size();
    }

    /**
     *  Whenever player changes floor, we update the index here.
     */

    public void updateFloorIndex(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public int getCurrentFloorIndex() {
        return this.currentFloor;
    }

    /**
     *  Returns FloorData object for current floor. Make sure to handle null response after calling
     */

    public FloorData getCurrentFloor() {
        if (this.floors.size() >= currentFloor) {
            return this.floors.get(currentFloor - 1);
        } else {
            return null;
        }
    }

    public void setPlayer(Component[] player) {
        this.player = player;
    }

    public Component[] getPlayer() {
        return this.player;
    }
}
