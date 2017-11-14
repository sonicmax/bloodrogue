package com.sonicmax.bloodrogue.engine;

import android.util.Log;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.data.JSONLoader;
import com.sonicmax.bloodrogue.engine.ai.AffinityManager;
import com.sonicmax.bloodrogue.engine.ai.EnemyState;
import com.sonicmax.bloodrogue.engine.collisions.FieldOfVisionCalculator;
import com.sonicmax.bloodrogue.engine.components.AI;
import com.sonicmax.bloodrogue.engine.components.Barrier;
import com.sonicmax.bloodrogue.engine.components.Blood;
import com.sonicmax.bloodrogue.engine.components.Collectable;
import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Damage;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Energy;
import com.sonicmax.bloodrogue.engine.components.Experience;
import com.sonicmax.bloodrogue.engine.components.Input;
import com.sonicmax.bloodrogue.engine.components.Name;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Stationary;
import com.sonicmax.bloodrogue.engine.components.Trap;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.generator.factories.AnimationFactory;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.generator.MapData;
import com.sonicmax.bloodrogue.generator.ProceduralGenerator;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.renderer.ui.Animation;
import com.sonicmax.bloodrogue.tilesets.Mansion;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.generator.factories.PlayerFactory;
import com.sonicmax.bloodrogue.utils.Array2DHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.DelayQueue;

public class GameEngine {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final int DIJKSTRA_MAX = 20;
    private final int HIGH_PRIORITY = 0;
    private final int MEDIUM_PRIORITY = 1;
    private final int LOW_PRIORITY = 2;

    private GameInterface gameInterface;
    private FieldOfVisionCalculator fovCalculator;
    private AffinityManager affinityManager;
    private ComponentManager componentManager;

    private GameState gameState;
    private MapData mapData;
    private ArrayList<Component[]>[][] rawObjectComponents;
    private Component[][][] rawTerrainComponents;
    private ArrayList<Component[]>[][] rawAnimationComponents;
    private int[][] playerDesireMap;
    private double[][] fieldOfVision;
    private Component[] player;

    private ArrayList<EntityTurn>[] priorityQueue;
    private ArrayList<Component[]> objectQueue;

    private int mapWidth;
    private int mapHeight;
    private int sightRadius;
    private int currentFloor;

    private boolean playerMoveLock;

    private Vector pathDestination;

    private Sprite[][] terrain;
    private ArrayList<Sprite> objects;
    private ArrayList<Animation> animations;

    // ECS storage and management
    private long [][] terrainEntities;
    private ArrayList<Long>[][] objectEntities;
    private ArrayList<Long> computerEntities;
    private long playerEntity;

    public GameEngine(GameInterface gameInterface) {
        this.playerMoveLock = false;

        this.sightRadius = 10;
        this.mapWidth = 32;
        this.mapHeight = 32;
        this.currentFloor = 1;

        this.gameInterface = gameInterface;
        this.fovCalculator = new FieldOfVisionCalculator();
        this.affinityManager = new AffinityManager();
        this.componentManager = new ComponentManager();

        this.terrain = new Sprite[mapWidth][mapHeight];
        this.objects = new ArrayList<>();
        this.animations = new ArrayList<>();

        this.terrainEntities = new long[mapWidth][mapHeight];
        this.computerEntities = new ArrayList<>();
        this.objectEntities = Array2DHelper.create2dLongStack(mapWidth, mapHeight);

        initPriorityQueue();
        this.objectQueue = new ArrayList<>();

        JSONLoader.loadEnemies(gameInterface.getAssets());
    }

    /*
    ---------------------------------------------
     Game initialisation
    ---------------------------------------------
    */

    private void initPriorityQueue() {
        this.priorityQueue = new ArrayList[3];
        this.priorityQueue[0] = new ArrayList<>();
        this.priorityQueue[1] = new ArrayList<>();
        this.priorityQueue[2] = new ArrayList<>();
    }

    public void startFromScratch() {
        ProceduralGenerator generator = new ProceduralGenerator(mapWidth, mapHeight, gameInterface.getAssets());
        generator.setFloor(currentFloor);
        generator.generate(ProceduralGenerator.MANSION);

        rawTerrainComponents = generator.getMapGrid();
        mapData = generator.getMapData();

        Vector startPosition = mapData.getStartPosition();
        player = PlayerFactory.getPlayer(startPosition.x(), startPosition.y());
        playerEntity = player[0].id;
        componentManager.sortComponentArray(player);

        // For saving purposes, we keep a 2d array of component arrays (organised by grid position).
        // For game logic, we use the entities to look up specific components using ComponentManager
        rawObjectComponents = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
        rawAnimationComponents = Array2DHelper.createArrayList2D(mapWidth, mapHeight);

        populateUsingNewData();

        // Once we have populated object grid and placed player object, we can save data.
        gameState = new GameState(player, getCurrentFloorData());

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);

        sortComponentsAndStoreEntities();
        prebuildSprites();

        // We should nullify arrays of raw components returned from generator once they've been sorted.
        // It would be a nightmare to try and manage entity lifecycle with so many references
        rawTerrainComponents = null;
        mapData = null;
        rawObjectComponents = null;
        rawAnimationComponents = null;

        advanceFrame();
    }

    private void moveToNextFloor() {
        currentFloor++;

        if (!gameState.hasFloor(currentFloor)) {

            ProceduralGenerator generator = new ProceduralGenerator(mapWidth, mapHeight, gameInterface.getAssets());
            generator.setFloor(currentFloor);
            generator.generate(ProceduralGenerator.MANSION);

            rawTerrainComponents = generator.getMapGrid();
            mapData = generator.getMapData();

            Vector startPosition = mapData.getStartPosition();
            player = PlayerFactory.getPlayer(startPosition.x(), startPosition.y());
            // moveObjectToNewStack(player, player.getVector(), startPosition);

            rawObjectComponents = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
            rawAnimationComponents = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
            populateUsingNewData();

            gameState.updateFloorIndex(currentFloor);
            gameState.addFloor(getCurrentFloorData());
        }

        else {
            gameState.updateFloorIndex(currentFloor);
            FloorData nextFloor = gameState.getCurrentFloor();

            rawTerrainComponents = nextFloor.getTerrain();
            rawObjectComponents = nextFloor.getObjects();
            // rawAnimationComponents = nextFloor.getAnimations();
            fieldOfVision = nextFloor.getFov();
            player = nextFloor.getPlayer();

            populateUsingSaveState();
        }

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        initPriorityQueue();
    }

    private void moveToPreviousFloor() {
        currentFloor--;

        // We can assume that game state will have data for previous floor
        gameState.updateFloorIndex(currentFloor);
        FloorData previousFloor = gameState.getCurrentFloor();

        rawTerrainComponents = previousFloor.getTerrain();
        rawObjectComponents = previousFloor.getObjects();
        // rawAnimationComponents = previousFloor.getAnimations();
        fieldOfVision = previousFloor.getFov();
        player = previousFloor.getPlayer();

        populateUsingSaveState();

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        initPriorityQueue();
    }

    public Frame getCurrentFrameData() {
        return new Frame(currentFloor, terrain, objects, animations, fieldOfVision, player);
    }

    public FloorData getCurrentFloorData() {
        return new FloorData(currentFloor, rawTerrainComponents, rawObjectComponents, rawAnimationComponents, fieldOfVision, player);
    }

    public GameState getGameState() {
        return this.gameState;
    }

    public void loadState(GameState state) {
        currentFloor = state.getFloorIndex();
        FloorData floor = state.getCurrentFloor();
        rawTerrainComponents = floor.getTerrain();
        rawObjectComponents = floor.getObjects();
        rawAnimationComponents = floor.getAnimations();
        fieldOfVision = floor.getFov();
        player = floor.getPlayer();
        populateUsingSaveState();
    }

    public int[] getMapSize() {
        return new int[] {mapWidth, mapHeight};
    }

    public Component[] getPlayer() {
        return player;
    }

    public void setPathDestination(Vector dest) {
        this.pathDestination = dest;
    }

    private void populateUsingSaveState() {
        /*enemies = new ArrayList<>();

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                ArrayList<Component[]> objects = rawObjectComponents[x][y];

                for (Component[] object : objects) {
                    if (object instanceof Actor && !object.isPlayerControlled()) {
                        enemies.add(object);
                    }
                }
            }
        }*/
    }

    private void populateUsingNewData() {
        for (Component[] object : mapData.getObjects()) {
            Position pos = ComponentFinder.getPositionComponent(object);
            addObjectToStack(pos.x, pos.y, object);
        }

        for (Component[] door : mapData.getDoors()) {
            Position pos = ComponentFinder.getPositionComponent(door);
            addObjectToStack(pos.x, pos.y, door);
        }

        for (Component[] enemy : mapData.getEnemies()) {
            Position pos = ComponentFinder.getPositionComponent(enemy);
            addObjectToStack(pos.x, pos.y, enemy);
            computerEntities.add(pos.id);
        }

        Position playerPosition = (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
        addObjectToStack(playerPosition.x, playerPosition.y, player);
    }

    private void prebuildSprites() {
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                Component[] t = rawTerrainComponents[x][y];
                terrain[x][y] = ComponentFinder.getSpriteComponent(t);

                ArrayList<Long> entities = objectEntities[x][y];

                for (Long entity : entities) {
                    Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
                    sprite.x = x;
                    sprite.y = y;
                    objects.add(sprite);
                }
            }
        }
    }

    /*
    ---------------------------------------------
     Main game loop
    ---------------------------------------------
    */

    public void advanceFrame() {
        updatePreCombatData();
        determineAiMoves();
        gameInterface.passDataToRenderer();
    }

    private Position getPlayerPosition() {
        return (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
    }

    public Vector getPlayerVector() {
        Position position = (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
        return new Vector(position.x, position.y);
    }

    private void updatePreCombatData() {
        Position playerPosition = getPlayerPosition();
        fovCalculator.setValues(terrainEntities, objectEntities, componentManager, playerPosition.x, playerPosition.y, sightRadius);
        fieldOfVision = fovCalculator.calculate();
        generatePlayerDesireMap();
        handleMetabolism();
    }

    private void determineAiMoves() {
        int enemySize = computerEntities.size();

        for (int i = 0; i < enemySize; i++) {
            long entity = computerEntities.get(i);
            AI ai = (AI) componentManager.getEntityComponent(entity, AI.class.getSimpleName());

            if (ai == null) {
                // Mark for deletion?
                continue;
            }

            switch (ai.state) {
                case EnemyState.INACTIVE:
                    break;

                case EnemyState.IDLE:
                    takeAiTurn(entity);
                    break;

                case EnemyState.SEEKING:
                    takeAiTurn(entity);
                    break;

                case EnemyState.PATHFINDING:
                    seekActorWhilePathBlocked(entity, playerEntity);
                    break;

                default:
                    takeAiTurn(entity);
                    break;
            }

            // Todo: handle self-replicating enemies by operating on SelfReplicate components
        }

        playerMoveLock = false;
    }

    private GameObject handleSelfReplication(GameObject object) {
        /*float random = new RandomNumberGenerator().getRandomFloat(0f, 1f);
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


            // Make sure we clone right shader of object.
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
        }*/

        return null;
    }

    /*
    ---------------------------------------------
     Input checkers
    ---------------------------------------------
    */

    public void checkUserInput(Vector destination) {
        Position playerPosition = (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
        boolean adjacent = isAdjacent(destination, new Vector(playerPosition.x, playerPosition.y));

        if (adjacent) {
            EntityTurn turn = new EntityTurn(playerPosition);
            turn.setMove(destination);
            priorityQueue[MEDIUM_PRIORITY].add(turn);

        } else {
            /*if (fieldOfVision[destination.x()][destination.y()] > 0) {
                ArrayList<Vector> path = findShortestPath(player.getVector(), destination);
                path.add(destination);
                queueAndFollowPath(path);
            }*/
        }

        takeQueuedTurns();
        advanceFrame();
    }

    private boolean isAdjacent(Vector original, Vector adjacent) {
        Vector difference = original.subtract(adjacent);
        return (difference.x() >= -1 && difference.x() <= 1) && (difference.y() >= -1 && difference.y() <= 1);
    }

    /*
    ---------------------------------------------
     Entity and component management
    ---------------------------------------------
    */

    /**
     *  In ProceduralGenerator, we kept components for each entity in a Component[] sorted by grid.
     *
     *  To improve performance in GameEngine, we should sort the components by class into HashMaps
     *  associated with each entity, and keep 2D arrays of the entities in GameEngine: one for
     *  terrain (ie. single entity per cell), and one for objects (multiple entities per cell)
     */

    private void sortComponentsAndStoreEntities() {
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                Component[] terrain = rawTerrainComponents[x][y];

                terrainEntities[x][y] = terrain[0].id;
                componentManager.sortComponentArray(terrain);

                ArrayList<Component[]> objects = rawObjectComponents[x][y];

                for (Component[] object : objects) {
                    if (object != null) {
                        objectEntities[x][y].add(object[0].id);
                        componentManager.sortComponentArray(object);
                    }
                }

            }
        }
    }

    private void sortComponentsAndStoreEntities(Component[] components) {
        long entity = components[0].id;
        componentManager.sortComponentArray(components);
        Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
        objectEntities[position.x][position.y].add(entity);
    }

    private void addObjectToStack(int x, int y, Component[] object) {
        // Note: we can assume that x and y will be in bounds
        rawObjectComponents[x][y].add(object);
    }

    private void addEntityToStack(int x, int y, long entity) {
        objectEntities[x][y].add(entity);
    }

    private void moveObjectToNewStack(long entity, int oldX, int oldY, int newX, int newY) {
        ArrayList<Long> oldStack = objectEntities[oldX][oldY];
        ArrayList<Long> newStack = objectEntities[newX][newY];

        if (oldStack.contains(entity)) {
            oldStack.remove(entity);
            newStack.add(entity);
        }
        else {
            Log.e(LOG_TAG, "Object not present in old stack. Adding to new stack");
            newStack.add(entity);
        }

        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        sprite.x = newX;
        sprite.y = newY;
        sprite.lastX = oldX;
        sprite.lastY = oldY;
    }

    private void changeSpritePath(Sprite sprite, String path) {
        sprite.path = path;
        sprite.spriteIndex = -1;
    }

    /*
    ---------------------------------------------
     Dijkstra stuff
    ---------------------------------------------
    */

    private void generatePlayerDesireMap() {
        int[] tilesToCheck = {Stationary.WALL, Stationary.FLOOR};
        int[][] desireGrid = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        ArrayList<Vector> desireLocations = new ArrayList<>();

        Position playerPosition = getPlayerPosition();
        AI ai = (AI) componentManager.getEntityComponent(playerEntity, AI.class.getSimpleName());

        desireGrid[playerPosition.x][playerPosition.y] = ai.dijkstra;
        desireLocations.add(new Vector(playerPosition.x, playerPosition.y));

        playerDesireMap = populateDijkstraGrid(Directions.All.values(), desireGrid, desireLocations, tilesToCheck, false);
    }

    private int[][] populateDijkstraGrid(Collection<Vector> directions, int[][] desireGrid, ArrayList<Vector> desireLocations, int[] typesToCheck, boolean ignoreCollisions) {
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

                    long terrainEntity = terrainEntities[neighbour.x][neighbour.y];
                    Stationary stat = (Stationary) componentManager.getEntityComponent(terrainEntity, Stationary.class.getSimpleName());

                    int value = desireGrid[neighbour.x()][neighbour.y()];

                    if (!ignoreCollisions && detectCollisions(neighbour)) continue;

                    else if (value >= initialValue + 2 && (entityTypeInArray(stat, typesToCheck))) {
                        desireGrid[neighbour.x()][neighbour.y()] = initialValue + 1;

                        if (entityTypeInArray(stat, typesToCheck)) {
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

    private void takeAiTurn(long entity) {
        Position actorPosComp = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
        Vector position = new Vector(actorPosComp.x, actorPosComp.y);

        Position playerPosComp = getPlayerPosition();
        Vector playerPos = new Vector(playerPosComp.x, playerPosComp.y);

        int bestDesire = DIJKSTRA_MAX + 1;

        // Check adjacent tiles and find one with best desire score
        for (Vector direction : Directions.All.values()) {
            Vector adjacent = position.add(direction);
            int desire = playerDesireMap[adjacent.x()][adjacent.y()];
            if (desire < bestDesire) {
                bestDesire = desire;
            }
        }

        AI ai = (AI) componentManager.getEntityComponent(entity, AI.class.getSimpleName());

        if (ai.state == EnemyState.INACTIVE) {
            return;
        }

        if (ai.state == EnemyState.IDLE) {
            if (bestDesire > ai.playerInterest) {
                // Ignore until player is closer
                return;
            } else {
                Name nameComponent = (Name) componentManager.getEntityComponent(entity, Name.class.getSimpleName());
                gameInterface.addNarration(nameComponent.value + " is looking for blood!", TextColours.RED);
                ai.state = EnemyState.SEEKING;
            }
        }


        // Check whether actor is directly adjacent to player & queue attack for next turn
        if (bestDesire == 0) {
            EntityTurn turn = new EntityTurn(actorPosComp);
            turn.setMove(playerPos);
            priorityQueue[HIGH_PRIORITY].add(turn);
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
            ai.state = EnemyState.PATHFINDING;
        }

        else if (closestTile != null) {
            EntityTurn turn = new EntityTurn(actorPosComp);
            turn.setMove(closestTile);
            priorityQueue[LOW_PRIORITY].add(turn);
        }
    }

    private void seekActorWhilePathBlocked(long seekingEntity, long targetEntity) {
        Position position = (Position) componentManager.getEntityComponent(seekingEntity, Position.class.getSimpleName());
        AI ai = (AI) componentManager.getEntityComponent(seekingEntity, AI.class.getSimpleName());

        ArrayList<Vector> path = ai.path;

        if (path == null || path.size() == 0) {
            ai.path = findShortestPath(seekingEntity, targetEntity);
        }

        else if (path.size() > 0) {

            // Todo: generate desire map for target here

            Vector nextCell = path.get(0);
            int nextDesire = playerDesireMap[nextCell.x()][nextCell.y()];
            int bestDesire = Integer.MAX_VALUE;

            Vector posVec = new Vector(position.x, position.y);

            // Check adjacent tiles and find one with lowest desire score
            for (Vector direction : Directions.All.values()) {
                Vector adjacent = posVec.add(direction);
                int desire = playerDesireMap[adjacent.x()][adjacent.y()];
                if (desire < bestDesire) {
                    bestDesire = desire;
                }
            }

            if (bestDesire <= nextDesire) {
                // We should stop following this path & go back to following dijkstra map
                ai.path.clear();
                ai.state = EnemyState.SEEKING;
                takeAiTurn(seekingEntity);
            }

            else {
                nextCell = ai.path.remove(0);
                EntityTurn turn = new EntityTurn(position);
                turn.setMove(nextCell);
                priorityQueue[LOW_PRIORITY].add(turn);
                generatePlayerDesireMap();
            }
        }

        else {
            ai.state = EnemyState.SEEKING;
            takeAiTurn(seekingEntity);
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

    private ArrayList<Vector> findShortestPath(long entityA, long entityB) {
        Position posA = (Position) componentManager.getEntityComponent(entityA, Position.class.getSimpleName());
        Position posB = (Position) componentManager.getEntityComponent(entityB, Position.class.getSimpleName());

        return findShortestPath(new Vector(posA.x, posA.y), new Vector(posB.x, posB.y));
    }

    public ArrayList<Vector> onTouchPathComplete() {
        if (this.pathDestination == null || !inBounds(this.pathDestination) || fieldOfVision[pathDestination.x()][pathDestination.y()] == 0
                || detectCollisions(this.pathDestination)) {

            return new ArrayList<>();
        }

        else {
            Position playerPos = (Position) componentManager.getEntityComponent(player[0].id,
                    Position.class.getSimpleName());

            return findShortestPath(new Vector(playerPos.x, playerPos.y), this.pathDestination);
        }
    }

    public void queueAndFollowPath(ArrayList<Vector> path) {
        DelayQueue<EntityTurn> queue = new DelayQueue<>();
        long start = 500L;

        Position playerPos = (Position) componentManager.getEntityComponent(player[0].id,
                Position.class.getSimpleName());

        for (int i = 0; i < path.size(); i++) {
            EntityTurn turn = new EntityTurn(playerPos);
            Vector vector = path.get(i);
            turn.setMove(vector);
            turn.setStart(start * (long) i);
            queue.put(turn);
        }

        gameInterface.setMoveLock(true);

        while (!queue.isEmpty()) {

            try {
                EntityTurn turn = queue.take();
                priorityQueue[MEDIUM_PRIORITY].add(turn);
                takeQueuedTurns();
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

    private void takeQueuedTurns() {
        updatePreCombatData();

        for (int i = HIGH_PRIORITY; i <= LOW_PRIORITY; i++) {
            Iterator<EntityTurn> iterator = priorityQueue[i].iterator();

        while (iterator.hasNext()) {
            EntityTurn turn = iterator.next();

            if (turn.hasMove()) {
                Vector destination = turn.getDestination();
                Position actor = turn.getPositionComponent();
                long entity = turn.getEntity();

                if (!detectCollisions(destination)) {
                    moveObjectToNewStack(entity, actor.x, actor.y, destination.x, destination.y);
                    // Update position component after messing around with stacks to make sure that
                    // lastX and lastY in Sprite are set correctly
                    actor.x = destination.x;
                    actor.y = destination.y;

                    handleMovementInteractions(entity, destination);
                    } else {
                    checkCollisions(entity, destination);
                }
            }

                // Make sure to remove turns from queue after we're finished
            iterator.remove();
        }
        }

        addQueuedObjects();
    }

    /**
     *  Called before entity moves to a new tile. Iterates over entities occupying position and
     *  checks for potential collisions.
     */

    private void checkCollisions(long initiator, Vector position) {
        ArrayList<Long> entityStack = objectEntities[position.x()][position.y()];

        Iterator<Long> it = entityStack.iterator();

        while (it.hasNext()) {
            long target = it.next();

            Physics targetPhysics = (Physics) componentManager.getEntityComponent(target, Physics.class.getSimpleName());

            if (targetPhysics != null && targetPhysics.activateOnCollide) {
                int result = doCollision(target, initiator);

                if (result == Actions.REMOVE_FROM_ITERATOR) {
                    it.remove();
                    continue;
                }

                performEntityAction(initiator, target, result);
            }
        }
    }

    /**
     *  Checks components of colliding entities and performs all possible actions.
     */

    private int doCollision(long target, long actor) {
        // Check for relevant components and perform actions on them
        Collectable collectableComponent = (Collectable) componentManager.getEntityComponent(target, Collectable.class.getSimpleName());

        if (collectableComponent != null) {
            Container actorContainer = (Container) componentManager.getEntityComponent(actor, Container.class.getSimpleName());
            if (actorContainer != null) {
                boolean success = addToContainer(collectableComponent, actorContainer);
                if (success) {
                    // Remove entity from object stack - it is now located inside container.
                    // Otherwise, continue with execution
                    return Actions.REMOVE_FROM_ITERATOR;
                }
            }
        }

        Container containerComponent = (Container) componentManager.getEntityComponent(target, Container.class.getSimpleName());

        if (containerComponent != null) {
            checkContainer(target, actor, containerComponent);
        }

        Barrier barrierComponent = (Barrier) componentManager.getEntityComponent(target, Barrier.class.getSimpleName());

        if (barrierComponent != null) {
            openBarrier(target, barrierComponent);
        }

        Trap trapComponent = (Trap) componentManager.getEntityComponent(target, Trap.class.getSimpleName());

        if (trapComponent != null) {
            float chance = new RandomNumberGenerator().getRandomFloat(0f, 1f);
            if (chance < trapComponent.chanceToActivate) {
                activateTrap(target, actor);
            }
        }

        AI attackerAi = (AI) componentManager.getEntityComponent(actor, AI.class.getSimpleName());
        AI defenderAi = (AI) componentManager.getEntityComponent(target, AI.class.getSimpleName());

        if (attackerAi != null && defenderAi != null) {
            if (affinityManager.entitiesAreAggressive(attackerAi, defenderAi)) {
                engageInCombat(actor, target);
            }
        }

        return Actions.NONE;
    }

    private boolean addToContainer(Collectable collectable, Container container) {
        if (container.totalWeight + collectable.weight <= container.capacity) {
            Sprite sprite = (Sprite) componentManager.getEntityComponent(collectable.id, Sprite.class.getSimpleName());
            container.contents.add(sprite);
            container.totalWeight += collectable.weight;
            hideEntity(collectable.id);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * To hide an entity, we need to tell the renderer to ignore the sprite component, move the entity to [0, 0]
     * and disable the AI component. To reactivate we just have to reverse these changes
     */

    private void hideEntity(long entity) {
        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        sprite.shader = Sprite.NONE;

        Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
        position.x = 0;
        position.y = 0;

        AI ai = (AI) componentManager.getEntityComponent(entity, AI.class.getSimpleName());
        if (ai != null) {
            ai.state = EnemyState.INACTIVE;
        }
    }

    private void activateTrap(long trap, long victim) {
        Damage damage = (Damage) componentManager.getEntityComponent(trap, Damage.class.getSimpleName());
        Vitality vitality = (Vitality) componentManager.getEntityComponent(victim, Vitality.class.getSimpleName());

        if (damage == null) {
            Log.e(LOG_TAG, "Trap activated but had no damage component. Skipping");
            return;
        }
        if (vitality == null) {
            Log.e(LOG_TAG, "Trap activated but victim had no vitality component. Skipping");
            return;
        }

        int damageDealt = doDamage(damage, vitality);

        Position defenderPosition = (Position) componentManager.getEntityComponent(victim, Position.class.getSimpleName());
        int x = defenderPosition.x;
        int y = defenderPosition.y;

        Blood blood = (Blood) componentManager.getEntityComponent(victim, Blood.class.getSimpleName());
        Component[] splat = DecalFactory.createBloodSplat(defenderPosition, blood, terrainEntities, componentManager);
        if (splat != null) {
            objectQueue.add(splat);
        }

        gameInterface.displayStatus(defenderPosition, Integer.toString(damageDealt), TextColours.STATUS_RED);

        if (vitality.hp <= 0) {
            Name defenderName = (Name) componentManager.getEntityComponent(victim, Name.class.getSimpleName());

            animations.add(AnimationFactory.getDeathAnimation(blood, x, y));

            ArrayList<Component[]> spray = DecalFactory.createBloodSpray(defenderPosition, blood, terrainEntities, componentManager);
            for (Component[] components : spray) {
                sortComponentsAndStoreEntities(components);
            }

            gameInterface.addNarration(defenderName.value + " killed by trap!", TextColours.RED);

            makeDead(victim);

            AI defenderAi = (AI) componentManager.getEntityComponent(victim, AI.class.getSimpleName());

            if (defenderAi == null || !defenderAi.computerControlled) {
                // Check for other controllable characters, or end game
            }
        }

        else {
            animations.add(AnimationFactory.getHitAnimation(blood, x, y));
        }
    }

    private void openBarrier(long target, Barrier barrier) {
        switch (barrier.type) {
            case Barrier.DOOR:
                if (!barrier.open) {
                    barrier.open = true;

                    // We also have to update physics component and sprite
                    Physics physicsComponent = (Physics) componentManager.getEntityComponent(target, Physics.class.getSimpleName());
                    physicsComponent.isBlocking = false;
                    physicsComponent.isTraversable = true;

                    Sprite spriteComponent = (Sprite) componentManager.getEntityComponent(target, Sprite.class.getSimpleName());
                    spriteComponent.path = Mansion.DOUBLE_DOORS_OPEN;
                    spriteComponent.spriteIndex = -1;
                }
                break;

            default:
                Log.e(LOG_TAG, "No shader associated with Barrier - can't act");
                break;
        }
    }

    private void checkContainer(long target, long actor, Container containerComponent) {
        switch (containerComponent.type) {
            case Container.CHEST:
                if (!containerComponent.open) {
                    Sprite sprite = (Sprite) componentManager.getEntityComponent(target, Sprite.class.getSimpleName());

                    if (containerComponent.contents.size() > 0) {
                        changeSpritePath(sprite, "sprites/chest_open.png");
                    } else {
                        changeSpritePath(sprite, "sprites/chest_empty.png");
                    }

                    containerComponent.open = true;
                }
                break;

            default:
                break;
        }
    }

    /**
     * Called after entity moves to a new tile. Checks whether any of the entities occupying
     * this position have a movement action that needs to be performed (eg. moving to new floor,
     * traps, environment damage, etc)
     */

    private void handleMovementInteractions(long actorEntity, Vector position) {
        ArrayList<Long> targetStack = objectEntities[position.x()][position.y()];

        Iterator<Long> it = targetStack.iterator();

        while (it.hasNext()) {
            long target = it.next();

            Physics targetPhysics = (Physics) componentManager.getEntityComponent(target, Physics.class.getSimpleName());

            if (targetPhysics != null && targetPhysics.activateOnMove) {
                int result = activateTarget(target, actorEntity);
                performEntityAction(actorEntity, target, result);

                if (result == Actions.EXIT_FLOOR || result == Actions.EXIT_PREVIOUS_FLOOR) {
                    // At this point, continuing iteration will cause exception.
                    // (and even if we could, we shouldn't)
                    break;
                }
            }
        }
    }

    private int activateTarget(long activatedEntity, long targetEntity) {
        // Check known activation behaviours and perform result on actor.
        return -1;
    }

    /**
     *  Checks result of activateTarget() method to see if we need to do anything.
     *  This will generally be things that change the state of the game (eg. changing floors) rather
     *  than more simple actions (eg. activating traps)
     */

    private void performEntityAction(long activatedEntity, long targetEntity, int code) {
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

    /**
     *  Iterates over objectQueue and adds entities and components to game.
     */

    private void addQueuedObjects() {
        for (Component[] object : objectQueue) {
            long entity = object[0].id;
            sortComponentsAndStoreEntities(object);

            Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());

            if (position != null) {
                addEntityToStack(position.x, position.y, entity);

                // If entity has sprite component, we should add it to rendering grid
                Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
                sprite.x = position.x;
                sprite.y = position.y;
                objects.add(sprite);
            }

            else {
                Log.d(LOG_TAG, "Couldn't add queued entity - no position component");
            }
        }

        objectQueue.clear();
    }

    /**
     *  Iterates over components which act on metabolism (Energy, Poison, etc) and makes whatever
     *  modifications we need for this turn.
     */

    private void handleMetabolism() {}

    /**
     *  Calculates results of combat between two entities. Does some sanity checks to make sure
     *  that entities should be fighting, gets damage and checks whether entity is still alive.
     *  Also adds blood decals and handles UI stuff (displaying damage, narrations, etc)
     */

    private void engageInCombat(long aggressor, long defender) {
        // Prevent entities from engaging in combat with themselves.
        if (aggressor == defender) return;

        AI defenderAi = (AI) componentManager.getEntityComponent(defender, AI.class.getSimpleName());

        Damage damage = (Damage) componentManager.getEntityComponent(aggressor, Damage.class.getSimpleName());
        Vitality vitality = (Vitality) componentManager.getEntityComponent(defender, Vitality.class.getSimpleName());

        if (damage == null || vitality == null) {
            Log.e(LOG_TAG, aggressor + " attempted combat with " + defender + " but required components were missing.");
            return;
        }

        Dexterity aggressorDex = (Dexterity) componentManager.getEntityComponent(aggressor, Dexterity.class.getSimpleName());

        int damageDealt;

        if (aggressorDex != null && aggressorDex.weaponEntity != -1) {
            // Apply weapon modifier to base strength
            Damage weaponDamage = (Damage) componentManager.getEntityComponent(aggressorDex.weaponEntity, Damage.class.getSimpleName());
            damageDealt = doDamage(weaponDamage.strength + damage.strength, vitality);
        }

        else {
            damageDealt = doDamage(damage, vitality);
        }

        Position defenderPosition = (Position) componentManager.getEntityComponent(defender, Position.class.getSimpleName());
        int x = defenderPosition.x;
        int y = defenderPosition.y;

        Blood blood = (Blood) componentManager.getEntityComponent(defender, Blood.class.getSimpleName());
        Component[] splat = DecalFactory.createBloodSplat(defenderPosition, blood, terrainEntities, componentManager);
        if (splat != null) {
            objectQueue.add(splat);
        }

        // Update combat log and display hit rawAnimationComponents
        if (defenderAi != null && defenderAi.computerControlled) {
            gameInterface.displayStatus(defenderPosition, Integer.toString(damageDealt), TextColours.STATUS_RED);
        }

        if (vitality.hp <= 0) {
            Name attackerName = (Name) componentManager.getEntityComponent(aggressor, Name.class.getSimpleName());
            Name defenderName = (Name) componentManager.getEntityComponent(defender, Name.class.getSimpleName());

            animations.add(AnimationFactory.getDeathAnimation(blood, x, y));

            ArrayList<Component[]> spray = DecalFactory.createBloodSpray(defenderPosition, blood, terrainEntities, componentManager);
            for (Component[] components : spray) {
                sortComponentsAndStoreEntities(components);
            }

            gameInterface.addNarration(attackerName.value + " killed " + defenderName.value + "!", TextColours.RED);

            applyXpReward(aggressor, defender);

            makeDead(defender);

            if (defenderAi == null || !defenderAi.computerControlled) {
                // Check for other controllable characters, or end game
            }
        }

        else {
            animations.add(AnimationFactory.getHitAnimation(blood, x, y));
        }
    }

    /**
     *  Calculates effects of Damage component on target Vitality component.
     */

    private int doDamage(Damage attacker, Vitality defender) {
        int damage = getDamage(attacker.strength, defender.endurance);

        int defenderHp = defender.hp;

        // Make sure that hp doesn't drop below 0. (at least for now)
        if (defenderHp - damage >= 0) {
            defender.hp -= damage;
        }
        else {
            defender.hp = 0;
        }

        return damage;
    }

    private int doDamage(int strength, Vitality defender) {
        int damage = getDamage(strength, defender.endurance);

        int defenderHp = defender.hp;

        // Make sure that hp doesn't drop below 0. (at least for now)
        if (defenderHp - damage >= 0) {
            defender.hp -= damage;
        }
        else {
            defender.hp = 0;
        }

        return damage;
    }

    private void makeDead(long entity) {
        // Alter physics of entity so that it does not activate on collisions & is traversable.
        Physics physComponent = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());
        physComponent.activateOnCollide = false;
        physComponent.isTraversable = true;

        // Remove components that make entity "alive".
        componentManager.removeEntityComponent(entity, AI.class.getSimpleName());
        componentManager.removeEntityComponent(entity, Input.class.getSimpleName());
        componentManager.removeEntityComponent(entity, Damage.class.getSimpleName());
        componentManager.removeEntityComponent(entity, Vitality.class.getSimpleName());
        componentManager.removeEntityComponent(entity, Experience.class.getSimpleName());
        componentManager.removeEntityComponent(entity, Energy.class.getSimpleName());

        // Replace current sprite with corpse
        Name nameComponent = (Name) componentManager.getEntityComponent(entity, Name.class.getSimpleName());
        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        sprite.shader = Sprite.DYNAMIC;
        sprite.lastX = -1;
        sprite.lastY = -1;

        switch (nameComponent.value) {
            case "Zombie":
                sprite.path = "sprites/zombie_corpse.png";
                break;

            case "Giant Rat":
                sprite.path = "sprites/giant_rat_corpse.png";
                break;

            case "Ogre":
            case "Great Ogre":
                sprite.path = "sprites/ogre_corpse.png";
                break;

            case "Giant Komodo":
                sprite.path = "sprites/giant_komodo_corpse.png";
                break;

            case "Green Slime":
                sprite.path = "sprites/green_slime_corpse.png";
                break;

            case "Purple Slime":
                sprite.path = "sprites/purple_slime_corpse.png";
                break;

            case "Giant Bug":
                sprite.path = "sprites/cockroach_corpse.png";
                break;

            case "Spirit":
                sprite.path = "sprites/ogre_spirit_corpse.png";
                break;

            default:
                sprite.path = "sprites/transparent.png";
                break;
        }
    }

    private void applyXpReward(long attacker, long defender) {
        Experience attackerExp = (Experience) componentManager.getEntityComponent(attacker, Experience.class.getSimpleName());
        Experience defenderExp = (Experience) componentManager.getEntityComponent(defender, Experience.class.getSimpleName());
        Vitality defenderVitality = (Vitality) componentManager.getEntityComponent(defender, Vitality.class.getSimpleName());
        Damage defenderThreat = (Damage) componentManager.getEntityComponent(defender, Damage.class.getSimpleName());

        int reward = getXpReward(defenderVitality, defenderThreat.strength, defenderExp.level, attackerExp.level);

        attackerExp.xp += reward;

        checkLevel(attackerExp);
    }

    public int getXpReward(Vitality defender, int defenderThreat, int defenderLevel, int attackerLevel) {
        return Math.round((defender.maxHp + defenderThreat + defender.endurance) * defenderLevel / (attackerLevel + 1));
    }

    private int getDamage(int attack, int defence) {
        int criticalChance = 0; // Todo: work out critical hit chance
        int baseDamage = attack * attack / (attack + defence);

        return baseDamage;

        // Todo: need to fix this properly.
        // return Math.max(baseDamage, 1);
    }

    private void checkLevel(Experience xpComponent) {
        if (xpComponent.xp >= xpComponent.xpToNextLevel) {
            xpComponent.level++;
            gameInterface.addNarration("You have now reached level " + xpComponent.level + "!");
        }
    }

    /*
    ---------------------------------------------
     Helper methods
    ---------------------------------------------
    */

    private boolean detectCollisions(Vector position) {
        // Map *should* be surrounded by border tiles which prevent player from moving out of bounds.
        // But just in case;
        if (!inBounds(position)) return true;

        int x = position.x();
        int y = position.y();

        // Check stationary component for blocking types
        long entity = terrainEntities[x][y];
        Stationary stationary = (Stationary) componentManager.getEntityComponent(entity, Stationary.class.getSimpleName());

        // Walls and borders will always prevent movement.
        if (stationary != null && (stationary.type == Stationary.WALL || stationary.type == Stationary.BORDER)) {
            return true;
        }

        // Now iterate over object entities in position and check physics component for properties

        int objectSize = objectEntities[x][y].size();

        for (int i = 0; i < objectSize; i++) {
            long object = objectEntities[x][y].get(i);
            Physics component = (Physics) componentManager.getEntityComponent(object, Physics.class.getSimpleName());
            if (component != null && !component.isTraversable) {
                return true;
            }
        }

        return false;
    }

    private boolean entityTypeInArray(Stationary stat, int[] typesToCheck) {
        for (int i = 0; i < typesToCheck.length; i++) {
            if (typesToCheck[i] == stat.type) {
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
