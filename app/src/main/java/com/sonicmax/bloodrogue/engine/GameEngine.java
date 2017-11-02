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

    private MapData mMapData;
    private ArrayList<GameObject>[][] mObjectGrid;
    private GameObject[][] mMapGrid;
    private ArrayList<GameObject> mEnemies;
    private ArrayList<GameObject>[][] mAnimations;
    private int[][] mPlayerDesireMap;
    private double[][] mLightMap;
    private double[][] mFOV;
    private ArrayList<GameObject> mLightSources;
    private GameObject mPlayer;

    private ArrayList<ActorTurn> mTurnQueue;
    private ArrayList<GameObject> mObjectQueue;


    private int mScore;
    private int mTotalKilled;
    private int mTotalAttack;
    private int mTotalDefence;

    private int mMapWidth;
    private int mMapHeight;
    private int mSightRadius;
    private int mCurrentFloor;

    private boolean mPlayerMoveLock;
    private boolean mInventoryOpen;
    private FieldOfVisionCalculator mFovCalculator;
    private AffinityManager mAffinityManager;

    private GameInterface mGameInterface;

    public GameEngine(GameInterface gameInterface) {
        this.mPlayerMoveLock = false;
        this.mInventoryOpen = false;
        this.mSightRadius = 10;
        this.mMapWidth = 32;
        this.mMapHeight = 32;
        this.mCurrentFloor = 1;
        this.mGameInterface = gameInterface;
        this.mFovCalculator = new FieldOfVisionCalculator();
        this.mAffinityManager = new AffinityManager();
        this.mTurnQueue = new ArrayList<>();
        this.mObjectQueue = new ArrayList<>();
    }

    public void initState() {
        ProceduralGenerator generator = new ProceduralGenerator(mMapWidth, mMapHeight);
        generator.setFloor(mCurrentFloor);
        generator.generate(ProceduralGenerator.MANSION);

        mMapGrid = generator.getMapGrid();
        mMapData = generator.getMapData();
        mEnemies = mMapData.getEnemies();

        Vector startPosition = mMapData.getStartPosition();
        mPlayer = PlayerFactory.getPlayer(startPosition.x(), startPosition.y());

        mObjectGrid = Array2DHelper.create(mMapWidth, mMapHeight);
        mAnimations = Array2DHelper.create(mMapWidth, mMapHeight);
        populateObjectGrid();

        mPlayerDesireMap = Array2DHelper.fillIntArray(mMapWidth, mMapHeight, DIJKSTRA_MAX);
        mTurnQueue = new ArrayList<>();

        advanceFrame();
    }

    /*
    ---------------------------------------------
     Getters and setters
    ---------------------------------------------
    */

    public Frame getFrame() {
        return new Frame(mMapGrid, mObjectGrid, mAnimations, mFOV, mLightMap, mPlayer);
    }

    public int[] getMapSize() {
        return new int[] {mMapWidth, mMapHeight};
    }

    public ArrayList<GameObject>[][] getObjects() {
        return mObjectGrid;
    }

    public GameObject getPlayer() {
        return mPlayer;
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
        mGameInterface.passDataToRenderer();
    }

    private void updatePreCombatData() {
        mFovCalculator.setValues(mMapGrid, mObjectGrid, mPlayer.x(), mPlayer.y(), mSightRadius);
        mFOV = mFovCalculator.calculate();
        generateDesireMaps();
        mLightMap = getVisibleLight();
        generateDesireMaps();
        handlePlayerMetabolism();
    }

    private void determineEnemyMoves() {
        if (mPlayer.getState() == PlayerState.DEAD) {
            return;
        }

        int enemySize = mEnemies.size();

        ArrayList<GameObject> newObjects = new ArrayList<>();

        for (int i = 0; i < enemySize; i++) {
            GameObject enemy = mEnemies.get(i);
            switch (enemy.getState()) {

                case EnemyState.IDLE:
                    takeComputerTurn(enemy);
                    break;

                case EnemyState.SEEKING:
                    takeComputerTurn(enemy);
                    break;

                case EnemyState.PATHFINDING:
                    seekActorWhilePathBlocked(enemy, mPlayer);
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

        mEnemies.addAll(newObjects);

        mPlayerMoveLock = false;
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
                if (mFOV[object.x()][object.y()] > 0) {
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
        boolean adjacent = isAdjacent(destination, mPlayer.getVector());

        if (adjacent) {
            ActorTurn turn = new ActorTurn(mPlayer);
            turn.setMove(destination);
            mTurnQueue.add(turn);

        } else {
            /*if (mFOV[destination.x()][destination.y()] > 0) {
                ArrayList<Vector> path = findShortestPath(mPlayer.getVector(), destination);
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
        mObjectGrid[x][y].add(object);
    }

    private void moveObjectToNewStack(GameObject object, Vector oldPos, Vector newPos) {
        ArrayList<GameObject> oldStack = mObjectGrid[oldPos.x()][oldPos.y()];
        ArrayList<GameObject> newStack = mObjectGrid[newPos.x()][newPos.y()];

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

    private void populateObjectGrid() {
        ArrayList<GameObject> objects = mMapData.getObjects();

        for (GameObject object : objects) {
            addObjectToStack(object.x(), object.y(), object);
        }

        ArrayList<GameObject> doors = mMapData.getDoors();

        for (GameObject door : doors) {
            addObjectToStack(door.x(), door.y(), door);
        }

        ArrayList<GameObject> enemies = mMapData.getEnemies();

        for (GameObject enemy : enemies) {
            addObjectToStack(enemy.x(), enemy.y(), enemy);
        }

        addObjectToStack(mPlayer.x(), mPlayer.y(), mPlayer);

        mLightSources = new ArrayList<>();
        addRoomObjects(mMapData.getRooms());
    }

    private void addRoomObjects(ArrayList<GameObject> rooms) {
        // int x = Math.round(mPlayer.x() - (mMapWidth / 2));
        // int y = Math.round(mPlayer.y() - (mMapHeight / 2));
        // Vector scrollOffset = new Vector(x, y);

        // Room visibleArea = renderer.getVisibleArea();

        for (GameObject room : rooms) {
            ArrayList<GameObject> objects = ((Room) room).getObjects();
            for (GameObject object : objects) {

                // axisAlignedBoxTest(room, visibleArea)

                if (object instanceof LightSource) {
                    mLightSources.add(object);
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
        int[][] desireGrid = Array2DHelper.fillIntArray(mMapWidth, mMapHeight, DIJKSTRA_MAX);
        ArrayList<Vector> desireLocations = new ArrayList<>();

        desireGrid[mPlayer.x()][mPlayer.y()] = mPlayer.getDijkstra();
        desireLocations.add(new Vector(mPlayer.x(), mPlayer.y()));

        // Player desire is blocked by collision, player radius goes through walls/etc
        mPlayerDesireMap = populateDijkstraGrid(Directions.All.values(), desireGrid, desireLocations, tilesToCheck, false);
    }

    private double[][] getVisibleLight() {
        final int LIGHT_RADIUS = 5;
        FieldOfVisionCalculator fov = new FieldOfVisionCalculator();
        fov.setDarknessFactor(2);
        double[][] combinedLightMap = null;

        int lightSize = mLightSources.size();
        for (int i = 0; i < lightSize; i++) {
            GameObject source = mLightSources.get(i);

            // Ignore anything outside of player's FOV
            if (mFOV[source.x()][source.y()] > 0) {

                fov.setValues(mMapGrid, mObjectGrid, mPlayer.x(), mPlayer.y(), LIGHT_RADIUS);
                double[][] lightMap = fov.calculate();

                if (combinedLightMap != null) {
                    for (int x = 0; x < mMapWidth; x++) {
                        for (int y = 0; y < mMapHeight; y++) {
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
                    if (mFOV[neighbour.x()][neighbour.y()] == 0) continue;

                    GameObject tile = mMapGrid[neighbour.x()][neighbour.y()];
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
        Vector playerPos = new Vector(mPlayer.x(), mPlayer.y());

        int bestDesire = DIJKSTRA_MAX + 1;

        // Check adjacent tiles and find one with best desire score
        for (Vector direction : Directions.All.values()) {
            Vector adjacent = position.add(direction);
            int desire = mPlayerDesireMap[adjacent.x()][adjacent.y()];
            if (desire < bestDesire) {
                bestDesire = desire;
            }
        }

        if (actor.getState() == EnemyState.IDLE) {
            if (bestDesire > actor.getPlayerInterest()) {
                // Ignore until player is closer
                return;
            } else {
                mGameInterface.addNarration(actor.getName() + " is looking for blood!", TextColours.RED);
                actor.setState(EnemyState.SEEKING);
            }
        }


        // Check whether actor is directly adjacent to player & queue attack for next turn
        if (bestDesire == 0) {
            ActorTurn turn = new ActorTurn(actor);
            turn.setMove(mPlayer.getVector());
            mTurnQueue.add(turn);
            return;
        }

        int bestHeuristic = Integer.MAX_VALUE;
        Vector closestTile = null;
        ArrayList<Vector> blockedTiles = new ArrayList<>();

        for (Vector direction : Directions.All.values()) {
            Vector adjacent = position.add(direction);
            int newDesire = mPlayerDesireMap[adjacent.x()][adjacent.y()];
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
            int newDesire = mPlayerDesireMap[tile.x()][tile.y()];
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
            mTurnQueue.add(turn);
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
            int nextDesire = mPlayerDesireMap[nextCell.x()][nextCell.y()];
            int bestDesire = Integer.MAX_VALUE;

            // Check adjacent tiles and find one with lowest desire score
            for (Vector direction : Directions.All.values()) {
                Vector adjacent = position.add(direction);
                int desire = mPlayerDesireMap[adjacent.x()][adjacent.y()];
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
                mTurnQueue.add(turn);
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
        if (this.pathDestination == null || !inBounds(this.pathDestination) || mFOV[pathDestination.x()][pathDestination.y()] == 0
                || detectCollisions(this.pathDestination)) {

            return new ArrayList<>();
        }

        else {
            return findShortestPath(mPlayer.getVector(), this.pathDestination);
        }
    }

    public void queueAndFollowPath(ArrayList<Vector> path) {
        DelayQueue<ActorTurn> queue = new DelayQueue<>();
        long start = 500L;

        for (int i = 0; i < path.size(); i++) {
            ActorTurn turn = new ActorTurn(mPlayer);
            Vector vector = path.get(i);
            turn.setMove(vector);
            turn.setStart(start * (long) i);
            queue.put(turn);
        }

        mGameInterface.setMoveLock(true);

        while (!queue.isEmpty()) {

            try {
                ActorTurn turn = queue.take();
                mTurnQueue.add(turn);
                takeTurns();
                advanceFrame();

            } catch (InterruptedException e) {

                Log.e(LOG_TAG, "Error in queue", e);
                return;
            }
        }

        mGameInterface.setMoveLock(false);
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

        Iterator<ActorTurn> iterator = mTurnQueue.iterator();

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
        ArrayList<GameObject> objectStack = mObjectGrid[position.x()][position.y()];

        Iterator<GameObject> it = objectStack.iterator();

        while (it.hasNext()) {
            GameObject target = it.next();
            if (target.activateOnMove()) {
                int result = target.collide(actor);
                performActorAction(actor, target, result);
            }
        }
    }

    /**
     * Called after player collides with an object in a tile. Checks whether any of the objects
     * occupying this position have a collision action that needs to be performed (eg opening doors,
     * opening chests, attacking enemies).
     */

    private void handleCollisionInteractions(Actor actor, Vector position) {
        ArrayList<GameObject> objectStack = mObjectGrid[position.x()][position.y()];

        Iterator<GameObject> it = objectStack.iterator();

        while (it.hasNext()) {
            GameObject target = it.next();
            if (target.activateOnCollide()) {
                int result = target.collide(actor);
                performActorAction(actor, target, result);

                // Todo: why did i do this
                if (target.hasDeathAnimation()) {
                    mAnimations[target.x()][target.y()].add(target.getDeathAnimation());
                }

                if (target instanceof Actor) {
                    Actor targetActor = (Actor) target;
                    attack(actor, targetActor);

                    if (targetActor.getHp() <= 0) {
                        it.remove();
                        mEnemies.remove(target);
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
                mCurrentFloor++;
                mGameInterface.initFloorChange();
                initState();
                mGameInterface.startNewFloor();
                break;

            case Actions.EXIT_PREVIOUS_FLOOR:
                break;

            case Actions.NONE:
            default:
                break;
        }
    }

    private void addQueuedObjects() {
        Iterator<GameObject> iterator = mObjectQueue.iterator();

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
        if (!mAffinityManager.actorsAreAggressive(attacker, defender)) return;

        int damage = attacker.attack(defender);

        if (damage == 0) {
            // Todo: figure out why this happens
            Log.v(LOG_TAG, attacker.getSprite() + " attacked " + defender.getSprite() + " but caused no damage");
            Log.v(LOG_TAG, "attacker strength: " + attacker.getStrength() + ", defender endurance: " + defender.getEndurance());
        }

        if (defender.getSprite().equals("sprites/dude.png")) {
            mTotalDefence += damage;
        }

        else {
           mTotalAttack += damage;
        }

        GameObject splat = DecalFactory.createBloodSplat(defender, mMapGrid);

        if (splat != null) {
            addObjectToStack(splat.x(), splat.y(), splat);
        }

        // Update combat log and display hit animations
        mGameInterface.displayStatus(defender, Integer.toString(damage), TextColours.STATUS_RED);

        // renderer.addAnimation(target.getDamageAnimation());

        int x = defender.x();
        int y = defender.y();

        if (defender.getHp() <= 0) {
            mAnimations[x][y].add(AnimationFactory.getDeathAnimation(defender, x, y));
            DecalFactory.createBloodSpray(defender, mMapGrid, mObjectGrid);

            mGameInterface.addNarration(attacker.getName() + " killed " + defender.getName() + "!", TextColours.RED);

            int reward = defender.getXpReward(attacker.getLevel());
            int currentXp = attacker.getXp();
            attacker.setXp(currentXp + reward);

            checkPlayerLevel();

            // We have to add object to stack after we've finished iterating in handleInteractions()
            // otherwise it will throw ConcurrentModificationException
            mObjectQueue.add(CorpseFactory.getCorpse(x, y, defender.getSprite()));

            if (defender.isPlayerControlled()) {
                // Stop gamei guess lol
            }

            else {
                mTotalKilled++;
            }
        }

        else {
            mAnimations[x][y].add(AnimationFactory.getHitAnimation(defender, x, y));
        }
    }

    private void checkPlayerLevel() {
        Actor player = (Actor) mPlayer;

        if (player.getXp() >= player.getXpToNextLevel()) {
            player.levelUp();
            // renderer.addAnimation(new PlayerLevelUp(player.x, player.y));
            mGameInterface.addNarration("You have now reached level " + player.getLevel() + "!");
        }
    }

    /*
    ---------------------------------------------
     Helper methods
    ---------------------------------------------
    */

    private boolean detectCollisions(Vector position) {
        if (!inBounds(position)) return true;

        GameObject mapTile = mMapGrid[position.x()][position.y()];

        if (mapTile instanceof Wall) {
            return true;
        }

        int x = position.x();
        int y = position.y();

        int objectSize = mObjectGrid[x][y].size();

        for (int i = 0; i < objectSize; i++) {
            if (!mObjectGrid[x][y].get(i).isTraversable()) {
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

        return (x >= 0 && x < mMapWidth) && (y >= 0 && y < mMapHeight);
    }
}
