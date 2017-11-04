package com.sonicmax.bloodrogue.engine;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.objects.GameObject;

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
    private GameObject player;

    public GameState() {}

    /**
     *  GameState is initialised with starting player data and the terrain/objects generated for first floor.
     */

    public GameState(GameObject player, FloorData firstFloor) {
        this.floors = new ArrayList<>();
        this.floors.add(firstFloor);
        this.player = player;
        this.currentFloor = 1;
    }

    /**
     *  Each time player descends to a new floor, we add it to GameState using this method.
     */

    public void addFloor(FloorData floor) {
        floors.add(floor);
    }

    public void addFloors(ArrayList<FloorData> floors) {
        this.floors = floors;
    }

    public boolean hasFloor(int index) {
        return index < floors.size();
    }

    /**
     *  Whenever player changes floor, we update the index here.
     */

    public void updateFloorIndex(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public int getFloorIndex() {
        return this.currentFloor;
    }

    /**
     *  Returns FloorData object for current floor.
     */

    public FloorData getCurrentFloor() {
        return floors.get(currentFloor - 1);
    }

    public GameObject getPlayer() {
        return player;
    }
}
