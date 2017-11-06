package com.sonicmax.bloodrogue.engine;

import android.util.Log;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.engine.ai.AffinityManager;
import com.sonicmax.bloodrogue.engine.ai.EnemyState;
import com.sonicmax.bloodrogue.engine.ai.PlayerState;
import com.sonicmax.bloodrogue.engine.factories.AnimationFactory;
import com.sonicmax.bloodrogue.engine.factories.DecalFactory;
import com.sonicmax.bloodrogue.generator.MapData;
import com.sonicmax.bloodrogue.generator.ProceduralGenerator;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.engine.objects.Actor;
import com.sonicmax.bloodrogue.engine.factories.CorpseFactory;
import com.sonicmax.bloodrogue.engine.objects.LightSource;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.engine.factories.PlayerFactory;
import com.sonicmax.bloodrogue.engine.objects.Room;
import com.sonicmax.bloodrogue.engine.objects.Wall;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.utils.Array2DHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.DelayQueue;

public class GameEngine {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final int DIJKSTRA_MAX = 20;

    private GameInterface gameInterface;
    private FieldOfVisionCalculator fovCalculator;
    private AffinityManager affinityManager;

    private MapData mapData;
    private ArrayList<GameObject>[][] objectGrid;
    private GameObject[][] mapGrid;
    private ArrayList<GameObject> enemies;
    private ArrayList<GameObject>[][] animations;
    private int[][] playerDesireMap;
    private double[][] lightMap;
    private double[][] fieldOfVision;
    private ArrayList<GameObject> lightSources;
    private GameObject player;

    private ArrayList<ActorTurn> turnQueue;
    private ArrayList<GameObject> objectQueue;

    private int score;
    private int totalKilled;
    private int totalAttack;
    private int totalDefence;

    private int mapWidth;
    private int mapHeight;
    private int sightRadius;
    private int currentFloor;

    private boolean playerMoveLock;
    private boolean inventoryOpen;

    private GameState gameState;

    public GameEngine(GameInterface gameInterface) {
        this.playerMoveLock = false;
        this.inventoryOpen = false;
        this.sightRadius = 10;
        this.mapWidth = 32;
        this.mapHeight = 32;
        this.currentFloor = 1;
        this.gameInterface = gameInterface;
        this.fovCalculator = new FieldOfVisionCalculator();
        this.affinityManager = new AffinityManager();
        this.turnQueue = new ArrayList<>();
        this.objectQueue = new ArrayList<>();
    }

    /**
     *  Generates new dungeon floor, updates game state and advances frame.
     *  This is only called when first starting game.
     */

    public void startFromScratch() {
        ProceduralGenerator generator = new ProceduralGenerator(mapWidth, mapHeight);
        generator.setFloor(currentFloor);
        generator.generate(ProceduralGenerator.MANSION);

        mapGrid = generator.getMapGrid();
        mapData = generator.getMapData();
        enemies = mapData.getEnemies();

        Vector startPosition = mapData.getStartPosition();
        player = PlayerFactory.getPlayer(startPosition.x(), startPosition.y());

        objectGrid = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
        animations = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
        populateUsingNewData();

        // Once we have populated object grid and placed player object, we can save data.
        gameState = new GameState(player, getCurrentFloorData());

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        turnQueue = new ArrayList<>();

        advanceFrame();
    }

    private void moveToNextFloor() {
        currentFloor++;

        if (!gameState.hasFloor(currentFloor)) {

            ProceduralGenerator generator = new ProceduralGenerator(mapWidth, mapHeight);
            generator.setFloor(currentFloor);
            generator.generate(ProceduralGenerator.MANSION);

            mapGrid = generator.getMapGrid();
            mapData = generator.getMapData();
            enemies = mapData.getEnemies();

            Vector startPosition = mapData.getStartPosition();
            player = PlayerFactory.getPlayer(startPosition.x(), startPosition.y());
            // moveObjectToNewStack(player, player.getVector(), startPosition);

            objectGrid = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
            animations = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
            populateUsingNewData();

            gameState.updateFloorIndex(currentFloor);
            gameState.addFloor(getCurrentFloorData());
        }

        else {
            gameState.updateFloorIndex(currentFloor);
            FloorData nextFloor = gameState.getCurrentFloor();

            mapGrid = nextFloor.getTerrain();
            objectGrid = nextFloor.getObjects();
            animations = nextFloor.getAnimations();
            fieldOfVision = nextFloor.getFov();
            lightMap = nextFloor.getLightMap();
            player = nextFloor.getPlayer();

            populateUsingSaveState();
        }

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        turnQueue = new ArrayList<>();
    }

    private void moveToPreviousFloor() {
        currentFloor--;

        // We can assume that game state will have data for previous floor
        gameState.updateFloorIndex(currentFloor);
        FloorData previousFloor = gameState.getCurrentFloor();

        mapGrid = previousFloor.getTerrain();
        objectGrid = previousFloor.getObjects();
        animations = previousFloor.getAnimations();
        fieldOfVision = previousFloor.getFov();
        lightMap = previousFloor.getLightMap();
        player = previousFloor.getPlayer();

        populateUsingSaveState();

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        turnQueue = new ArrayList<>();
    }

    /*
    ---------------------------------------------
     Getters and setters
    ---------------------------------------------
    */

    public FloorData getCurrentFloorData() {
        return new FloorData(currentFloor, mapGrid, objectGrid, animations, fieldOfVision, lightMap, player);
    }

    public GameState getGameState() {
        return this.gameState;
    }

    public void loadState(GameState state) {
        currentFloor = state.getFloorIndex();
        FloorData floor = state.getCurrentFloor();
        mapGrid = floor.getTerrain();
        objectGrid = floor.getObjects();
        animations = floor.getAnimations();
        fieldOfVision = floor.getFov();
        lightMap = floor.getLightMap();
        player = floor.getPlayer();
        populateUsingSaveState();
    }

    public int[] getMapSize() {
        return new int[] {mapWidth, mapHeight};
    }

    public ArrayList<GameObject>[][] getObjects() {
        return objectGrid;
    }

    public GameObject getPlayer() {
        return player;
    }

    private Vector pathDestination;

    public void setPathDestination(Vector dest) {
        this.pathDestination = dest;
    }

    /*
    ---------------------------------------------
     Main game loop
    ---------------------------------------------
    */

    public void advanceFrame() {
        updatePreCombatData();
        determineEnemyMoves();
        gameInterface.passDataToRenderer();
    }

    private void updatePreCombatData() {
        fovCalculator.setValues(mapGrid, objectGrid, player.x(), player.y(), sightRadius);
        fieldOfVision = fovCalculator.calculate();
        generateDesireMaps();
        lightMap = getVisibleLight();
        generateDesireMaps();
        handlePlayerMetabolism();
    }

    private void determineEnemyMoves() {
        if (player.getState() == PlayerState.DEAD) {
            return;
        }

        int enemySize = enemies.size();

        ArrayList<GameObject> newObjects = new ArrayList<>();

        for (int i = 0; i < enemySize; i++) {
            GameObject enemy = enemies.get(i);
            switch (enemy.getState()) {

                case EnemyState.IDLE:
                    takeComputerTurn(enemy);
                    break;

                case EnemyState.SEEKING:
                    takeComputerTurn(enemy);
                    break;

                case EnemyState.PATHFINDING:
                    seekActorWhilePathBlocked(enemy, player);
                    break;

                default:
                    takeComputerTurn(enemy);
                    break;
            }

            if (enemy.getSelfReplicateChance() > 0f) {
                GameObject clone = handleSelfReplication(enemy);
                if (clone != null) {
                    newObjects.add(clone);
                }
            }
        }

        enemies.addAll(newObjects);

        playerMoveLock = false;
    }

    private GameObject handleSelfReplication(GameObject object) {
        float random = new RandomNumberGenerator().getRandomFloat(0f, 1f);
        if (random < object.getSelfReplicateChance()) {

            // Find nearest adjacent free square
            Vector pos = object.getVector();
            Vector newPos = null;
            for (Vector direction : Directions.All.values()) {
                Vector test = pos.add(direction);
                if (!detectCollisions(test)) {
                    newPos = test;
                    break;
                }
            }

            // Return null if we couldn't find an empty space for clone
            if (newPos == null) return null;


            // Make sure we clone right type of object.
            if (object instanceof Actor) {
                // Make sure Actor is in field of vision before cloning.
                // (this is to prevent the map from filling up with cloned Actors)
                if (fieldOfVision[object.x()][object.y()] > 0) {
                    Actor actor = (Actor) object;
                    Actor clone = new Actor(newPos.x(), newPos.y(), actor);
                    // Set last move as original position, so renderer will animate creation of new clone
                    clone.setLastMove(actor.getVector());
                    addObjectToStack(clone.x(), clone.y(), clone);
                    return clone;
                }
            }

            else {
                GameObject clone = new GameObject(newPos.x(), newPos.y(), object);
                addObjectToStack(clone.x(), clone.y(), clone);
                return clone;
            }
        }

        return null;
    }

    /*
    ---------------------------------------------
     Input checkers
    ---------------------------------------------
    */

    public void checkUserInput(Vector destination) {
        boolean adjacent = isAdjacent(destination, player.getVector());

        if (adjacent) {
            ActorTurn turn = new ActorTurn(player);
            turn.setMove(destination);
            turnQueue.add(turn);

        } else {
            /*if (fieldOfVision[destination.x()][destination.y()] > 0) {
                ArrayList<Vector> path = findShortestPath(player.getVector(), destination);
                path.add(destination);
                queueAndFollowPath(path);
            }*/
        }

        takeTurns();
        advanceFrame();
    }

    private boolean isAdjacent(Vector original, Vector adjacent) {
        Vector difference = original.subtract(adjacent);
        return (difference.x() >= -1 && difference.x() <= 1) && (difference.y() >= -1 && difference.y() <= 1);
    }

    /*
    ---------------------------------------------
     Object manipulation
    ---------------------------------------------
    */

    private void addObjectToStack(int x, int y, GameObject object) {
        // Note: we can assume that x and y will be in bounds
        objectGrid[x][y].add(object);
    }

    private void moveObjectToNewStack(GameObject object, Vector oldPos, Vector newPos) {
        ArrayList<GameObject> oldStack = objectGrid[oldPos.x()][oldPos.y()];
        ArrayList<GameObject> newStack = objectGrid[newPos.x()][newPos.y()];

        if (oldStack.contains(object)) {
            oldStack.remove(object);
            newStack.add(object);
        }
        else {
            Log.e(LOG_TAG, "Object " + object.getSprite() + " not present in stack");
            newStack.add(object);
        }

        object.setLastMove(new Vector(oldPos.x(), oldPos.y()));
    }

    private void populateUsingSaveState() {
        enemies = new ArrayList<>();
        lightSources = new ArrayList<>();

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                ArrayList<GameObject> objects = objectGrid[x][y];

                for (GameObject object : objects) {
                    if (object instanceof Actor && !object.isPlayerControlled()) {
                        enemies.add(object);
                    }

                    else if (object instanceof LightSource) {
                        lightSources.add(object);
                    }
                }
            }
        }
    }

    private void populateUsingNewData() {
        ArrayList<GameObject> objects = mapData.getObjects();

        for (GameObject object : objects) {
            addObjectToStack(object.x(), object.y(), object);
        }

        ArrayList<GameObject> doors = mapData.getDoors();

        for (GameObject door : doors) {
            addObjectToStack(door.x(), door.y(), door);
        }

        ArrayList<GameObject> enemies = mapData.getEnemies();

        for (GameObject enemy : enemies) {
            addObjectToStack(enemy.x(), enemy.y(), enemy);
        }

        addObjectToStack(player.x(), player.y(), player);

        lightSources = new ArrayList<>();
        addRoomObjects(mapData.getRooms());
    }

    private void addRoomObjects(ArrayList<GameObject> rooms) {
        // int x = Math.round(player.x() - (mapWidth / 2));
        // int y = Math.round(player.y() - (mapHeight / 2));
        // Vector scrollOffset = new Vector(x, y);

        // Room visibleArea = renderer.getVisibleArea();

        for (GameObject room : rooms) {
            ArrayList<GameObject> objects = ((Room) room).getObjects();
            for (GameObject object : objects) {

                // axisAlignedBoxTest(room, visibleArea)

                if (object instanceof LightSource) {
                    lightSources.add(object);
                    addObjectToStack(object.x(), object.y(), object);
                }
            }
        }
    }

    /*
    ---------------------------------------------
     Dijkstra stuff
    ---------------------------------------------
    */

    private void generateDesireMaps() {
        String[] tilesToCheck = {"Wall", "Floor"};
        int[][] desireGrid = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        ArrayList<Vector> desireLocations = new ArrayList<>();

        desireGrid[player.x()][player.y()] = player.getDijkstra();
        desireLocations.add(new Vector(player.x(), player.y()));

        // Player desire is blocked by collision, player radius goes through walls/etc
        playerDesireMap = populateDijkstraGrid(Directions.All.values(), desireGrid, desireLocations, tilesToCheck, false);
    }

    private double[][] getVisibleLight() {
        final int LIGHT_RADIUS = 5;
        FieldOfVisionCalculator fov = new FieldOfVisionCalculator();
        fov.setDarknessFactor(2);
        double[][] combinedLightMap = null;

        int lightSize = lightSources.size();
        for (int i = 0; i < lightSize; i++) {
            GameObject source = lightSources.get(i);

            // Ignore anything outside of player's FOV
            if (fieldOfVision[source.x()][source.y()] > 0) {

                fov.setValues(mapGrid, objectGrid, player.x(), player.y(), LIGHT_RADIUS);
                double[][] lightMap = fov.calculate();

                if (combinedLightMap != null) {
                    for (int x = 0; x < mapWidth; x++) {
                        for (int y = 0; y < mapHeight; y++) {
                            if (lightMap[x][y] > combinedLightMap[x][y]) {
                                combinedLightMap[x][y] = lightMap[x][y];
                            }
                        }
                    }
                }

                else {
                    combinedLightMap = lightMap;
                }

            }
        }

        return combinedLightMap;
    }

    private int[][] populateDijkstraGrid(Collection<Vector> directions, int[][] desireGrid, ArrayList<Vector> desireLocations, String[] tilesToCheck, boolean ignoreCollisions) {
        int desireSize = desireLocations.size();
        for (int i = 0; i < desireSize; i++) {
            Vector desire = desireLocations.get(i);
            ArrayList<Vector> queue = new ArrayList<>();

            queue.add(desire);

            while (queue.size() > 0) {
                Vector cell = queue.remove(queue.size() - 1);

                int initialValue = desireGrid[cell.x()][cell.y()];

                for (Vector direction : directions) {
                    Vector neighbour = cell.add(direction);

                    if (!inBounds(neighbour)) continue;
                    if (fieldOfVision[neighbour.x()][neighbour.y()] == 0) continue;

                    GameObject tile = mapGrid[neighbour.x()][neighbour.y()];
                    int value = desireGrid[neighbour.x()][neighbour.y()];

                    if (!ignoreCollisions && detectCollisions(neighbour)) continue;

                    else if (value >= initialValue + 2 && (objectInstanceInArray(tile, tilesToCheck))) {
                        desireGrid[neighbour.x()][neighbour.y()] = initialValue + 1;

                        if (objectInstanceInArray(tile, tilesToCheck)) {
                            queue.add(neighbour);
                        }
                    }
                }
            }
        }

        return desireGrid;
    }

    /*
    ---------------------------------------------
     Path finding
    ---------------------------------------------
    */

    private void takeComputerTurn(GameObject actor) {
        Vector position = new Vector(actor.x(), actor.y());
        Vector playerPos = new Vector(player.x(), player.y());

        int bestDesire = DIJKSTRA_MAX + 1;

        // Check adjacent tiles and find one with best desire score
        for (Vector direction : Directions.All.values()) {
            Vector adjacent = position.add(direction);
            int desire = playerDesireMap[adjacent.x()][adjacent.y()];
            if (desire < bestDesire) {
                bestDesire = desire;
            }
        }

        if (actor.getState() == EnemyState.IDLE) {
            if (bestDesire > actor.getPlayerInterest()) {
                // Ignore until player is closer
                return;
            } else {
                gameInterface.addNarration(actor.getName() + " is looking for blood!", TextColours.RED);
                actor.setState(EnemyState.SEEKING);
            }
        }


        // Check whether actor is directly adjacent to player & queue attack for next turn
        if (bestDesire == 0) {
            ActorTurn turn = new ActorTurn(actor);
            turn.setMove(player.getVector());
            turnQueue.add(turn);
            return;
        }

        int bestHeuristic = Integer.MAX_VALUE;
        Vector closestTile = null;
        ArrayList<Vector> blockedTiles = new ArrayList<>();

        for (Vector direction : Directions.All.values()) {
            Vector adjacent = position.add(direction);
            int newDesire = playerDesireMap[adjacent.x()][adjacent.y()];
            int distance = (int) Calculator.getDistance(adjacent, playerPos);
            // Really simple heuristic (that probably needs improving)
            int heuristic = newDesire + distance;

            if (heuristic < bestHeuristic) {

                if (!detectCollisions(adjacent)) {
                    bestHeuristic = heuristic;
                    closestTile = adjacent;
                }
                else {
                    blockedTiles.add(adjacent);
                }
            }
        }

        boolean blocked = false;

        // Todo: We should check blocked tiles to see whether any of them were closed doors.
        int blockedSize = blockedTiles.size();
        for (int i = 0; i < blockedSize; i++) {
            Vector tile = blockedTiles.get(i);
            int newDesire = playerDesireMap[tile.x()][tile.y()];
            int distance = (int) Calculator.getDistance(tile, playerPos);
            int heuristic = newDesire + distance;

            // If any of these blocked tiles are closer than the closest empty getSprite, we should
            // probably look for a way around.

            if (closestTile == null || heuristic < bestHeuristic) {
                blocked = true;
                break;
            }
        }

        if (blocked) {
            actor.setState(EnemyState.PATHFINDING);
            return;
        }

        else if (closestTile != null) {
            ActorTurn turn = new ActorTurn(actor);
            turn.setMove(closestTile);
            turnQueue.add(turn);
        }
    }

    private void seekActorWhilePathBlocked(GameObject enemy, GameObject target) {
        Vector position = new Vector(enemy.x(), enemy.y());
        ArrayList<Vector> path = enemy.getPath();

        if (path == null) {
            enemy.setPath(findShortestPath(enemy, target));
        }

        else if (path.size() > 0) {

            // Todo: generate desire map for target here

            Vector nextCell = path.get(0);
            int nextDesire = playerDesireMap[nextCell.x()][nextCell.y()];
            int bestDesire = Integer.MAX_VALUE;

            // Check adjacent tiles and find one with lowest desire score
            for (Vector direction : Directions.All.values()) {
                Vector adjacent = position.add(direction);
                int desire = playerDesireMap[adjacent.x()][adjacent.y()];
                if (desire < bestDesire) {
                    bestDesire = desire;
                }
            }

            if (bestDesire <= nextDesire) {
                // We should stop following this path & go back to following dijkstra map
                enemy.setPath(null);
                enemy.setState(EnemyState.SEEKING);
                takeComputerTurn(enemy);
            }

            else {
                nextCell = enemy.removeFromPath(0);
                ActorTurn turn = new ActorTurn(enemy);
                turn.setMove(nextCell);
                turnQueue.add(turn);
                generateDesireMaps();
            }
        }

        else {
            enemy.setState(EnemyState.SEEKING);
            takeComputerTurn(enemy);
        }
    }

    private ArrayList<Vector> findShortestPath(Vector startNode, Vector goalNode) {
        ArrayList<Vector> optimalPath = new ArrayList<>();
        ArrayList<Vector> openNodes = new ArrayList<>();
        ArrayList<String> checkedNodes = new ArrayList<>();

        if (startNode.equals(goalNode)) {
            return optimalPath;
        }

        openNodes.add(startNode);

        Vector lastNode = null;

        while (openNodes.size() > 0) {
            Vector currentNode = openNodes.remove(openNodes.size() - 1);

            if (lastNode != null && lastNode.equals(currentNode)) {
                // This probably shouldn't happen
                Log.w(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector direction : Directions.All.values()) {
                Vector adjacentNode = currentNode.add(direction);

                if (checkedNodes.contains(adjacentNode.toString())) continue;

                if (detectCollisions(adjacentNode)) continue;

                double distanceToGoal = Calculator.getDistance(adjacentNode, goalNode);

                if (distanceToGoal < bestDistance) {
                    bestDistance = distanceToGoal;
                    closestNode = adjacentNode;
                }
            }

            if (closestNode == null || closestNode.equals(goalNode)) {
                return optimalPath;
            }

            else {
                checkedNodes.add(currentNode.toString());
                lastNode = currentNode;
                optimalPath.add(closestNode);
                openNodes.add(closestNode);
            }
        }

        return optimalPath;
    }

    private ArrayList<Vector> findShortestPath(GameObject a, GameObject b) {
        return findShortestPath(a.getVector(), b.getVector());
    }

    public ArrayList<Vector> onTouchPathComplete() {
        if (this.pathDestination == null || !inBounds(this.pathDestination) || fieldOfVision[pathDestination.x()][pathDestination.y()] == 0
                || detectCollisions(this.pathDestination)) {

            return new ArrayList<>();
        }

        else {
            return findShortestPath(player.getVector(), this.pathDestination);
        }
    }

    public void queueAndFollowPath(ArrayList<Vector> path) {
        DelayQueue<ActorTurn> queue = new DelayQueue<>();
        long start = 500L;

        for (int i = 0; i < path.size(); i++) {
            ActorTurn turn = new ActorTurn(player);
            Vector vector = path.get(i);
            turn.setMove(vector);
            turn.setStart(start * (long) i);
            queue.put(turn);
        }

        gameInterface.setMoveLock(true);

        while (!queue.isEmpty()) {

            try {
                ActorTurn turn = queue.take();
                turnQueue.add(turn);
                takeTurns();
                advanceFrame();

            } catch (InterruptedException e) {

                Log.e(LOG_TAG, "Error in queue", e);
                return;
            }
        }

        gameInterface.setMoveLock(false);
    }

    /*
    ---------------------------------------------
     Gameplay
    ---------------------------------------------
    */

    /**
     *  Iterates over queued turns and decide which actions to take.
     *  Player will be first in queue, followed by enemies.
     */

    private void takeTurns() {
        updatePreCombatData();

        Iterator<ActorTurn> iterator = turnQueue.iterator();

        while (iterator.hasNext()) {
            ActorTurn turn = iterator.next();

            if (turn.hasMove()) {
                Vector destination = turn.getDestination();
                Actor actor = turn.getActor();

                if (!detectCollisions(destination)) {
                    moveObjectToNewStack(actor, actor.getVector(), destination);
                    actor.move(destination);
                    handleMovementInteractions(actor, destination);
                }

                else {
                    handleCollisionInteractions(actor, destination);
                }
            }

            iterator.remove();
        }

        addQueuedObjects();
    }

    /**
     * Called after player moves to a new tile. Checks whether any of the objects occupying
     * this position have a movement action that needs to be performed (eg. moving to new floor,
     * traps, environment damage, etc)
     */

    private void handleMovementInteractions(Actor actor, Vector position) {
        ArrayList<GameObject> objectStack = objectGrid[position.x()][position.y()];

        Iterator<GameObject> it = objectStack.iterator();

        while (it.hasNext()) {
            GameObject target = it.next();
            if (target.activateOnMove()) {
                int result = target.collide(actor);
                performActorAction(actor, target, result);
                if (result == Actions.EXIT_FLOOR || result == Actions.EXIT_PREVIOUS_FLOOR) {
                    // At this point, continuing iteration will cause exception.
                    // (and even if we could, we shouldn't)
                    break;
                }
            }
        }
    }

    /**
     * Called after player collides with an object in a tile. Checks whether any of the objects
     * occupying this position have a collision action that needs to be performed (eg opening doors,
     * opening chests, attacking enemies).
     */

    private void handleCollisionInteractions(Actor actor, Vector position) {
        ArrayList<GameObject> objectStack = objectGrid[position.x()][position.y()];

        Iterator<GameObject> it = objectStack.iterator();

        while (it.hasNext()) {
            GameObject target = it.next();
            if (target.activateOnCollide()) {
                int result = target.collide(actor);
                performActorAction(actor, target, result);

                // Todo: why did i do this
                if (target.hasDeathAnimation()) {
                    animations[target.x()][target.y()].add(target.getDeathAnimation());
                }

                if (target instanceof Actor) {
                    Actor targetActor = (Actor) target;
                    attack(actor, targetActor);

                    if (targetActor.getHp() <= 0) {
                        it.remove();
                        enemies.remove(target);
                    }
                }
            }
        }
    }

    /**
     *  Checks result of GameObject.collide() method to see if we need to perform any extra actions.
     *  Most are handled within the GameObject, but some require a higher scope to work.
     */

    private void performActorAction(Actor actor, GameObject target, int code) {
        switch (code) {

            case Actions.EXIT_FLOOR:
                // Tell renderer to fade out content and display loading screen, generate
                // terrain for new floor and fade in with new content
                gameInterface.initFloorChange();
                moveToNextFloor();
                Log.v(LOG_TAG, "current floor: " + currentFloor);
                gameInterface.transitionToNewContent();
                break;

            case Actions.EXIT_PREVIOUS_FLOOR:
                if (currentFloor > 1) {
                    gameInterface.initFloorChange();
                    moveToPreviousFloor();
                    Log.v(LOG_TAG, "current floor: " + currentFloor);
                    gameInterface.transitionToNewContent();
                }
                break;

            case Actions.NONE:
                break;

            default:
                Log.d(LOG_TAG, "Code " + code + " not present in switch statement");
                break;
        }
    }

    private void addQueuedObjects() {
        Iterator<GameObject> iterator = objectQueue.iterator();

        while (iterator.hasNext()) {
            GameObject object = iterator.next();
            addObjectToStack(object.x(), object.y(), object);
            iterator.remove();
        }
    }

    private void handlePlayerMetabolism() {}

    private void attack(Actor attacker, Actor defender) {
        // Prevent Actors from attacking themselves. (Todo: may implement this later on)
        if (attacker.getId().equals(defender.getId())) return;

        // Prevent Actors from attacking other Actors with the same affinity. (Todo: this should be implemented in AI, not here)
        if (!affinityManager.actorsAreAggressive(attacker, defender)) return;

        int damage = attacker.attack(defender);

        if (defender.getSprite().equals("sprites/dude.png")) {
            totalDefence += damage;
        }

        else {
           totalAttack += damage;
        }

        GameObject splat = DecalFactory.createBloodSplat(defender, mapGrid);

        if (splat != null) {
            addObjectToStack(splat.x(), splat.y(), splat);
        }

        // Update combat log and display hit animations
        if (!defender.isPlayerControlled()) {
            gameInterface.displayStatus(defender, Integer.toString(damage), TextColours.STATUS_RED);
        }

        // renderer.addAnimation(target.getDamageAnimation());

        int x = defender.x();
        int y = defender.y();

        if (defender.getHp() <= 0) {
            animations[x][y].add(AnimationFactory.getDeathAnimation(defender, x, y));
            DecalFactory.createBloodSpray(defender, mapGrid, objectGrid);

            gameInterface.addNarration(attacker.getName() + " killed " + defender.getName() + "!", TextColours.RED);

            int reward = defender.getXpReward(attacker.getLevel());
            int currentXp = attacker.getXp();
            attacker.setXp(currentXp + reward);

            checkPlayerLevel();

            // We have to add object to stack after we've finished iterating in handleInteractions()
            // otherwise it will throw ConcurrentModificationException
            objectQueue.add(CorpseFactory.getCorpse(x, y, defender.getSprite()));

            if (defender.isPlayerControlled()) {
                // Stop gamei guess lol
            }

            else {
                totalKilled++;
            }
        }

        else {
            animations[x][y].add(AnimationFactory.getHitAnimation(defender, x, y));
        }
    }

    private void checkPlayerLevel() {
        Actor player = (Actor) this.player;

        if (player.getXp() >= player.getXpToNextLevel()) {
            player.levelUp();
            // renderer.addAnimation(new PlayerLevelUp(player.x, player.y));
            gameInterface.addNarration("You have now reached level " + player.getLevel() + "!");
        }
    }

    /*
    ---------------------------------------------
     Helper methods
    ---------------------------------------------
    */

    private boolean detectCollisions(Vector position) {
        if (!inBounds(position)) return true;

        GameObject mapTile = mapGrid[position.x()][position.y()];

        if (mapTile instanceof Wall) {
            return true;
        }

        int x = position.x();
        int y = position.y();

        int objectSize = objectGrid[x][y].size();

        for (int i = 0; i < objectSize; i++) {
            if (!objectGrid[x][y].get(i).isTraversable()) {
                return true;
            }
        }

        return false;
    }

    private boolean objectInstanceInArray(GameObject tile, String[] tilesToCheck) {
        for (int i = 0; i < tilesToCheck.length; i++) {
            if (tile.getClass().getSimpleName().equals(tilesToCheck[i])) {
                return true;
            }
        }

        return false;
    }

    private boolean inBounds(Vector position) {
        int x = position.x();
        int y = position.y();

        return (x >= 0 && x < mapWidth) && (y >= 0 && y < mapHeight);
    }
}
