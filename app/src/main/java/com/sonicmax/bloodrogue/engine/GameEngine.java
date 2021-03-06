package com.sonicmax.bloodrogue.engine;

import android.util.Log;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.audio.FxFilePaths;
import com.sonicmax.bloodrogue.engine.ai.ActorTurn;
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
import com.sonicmax.bloodrogue.engine.components.Knowledge;
import com.sonicmax.bloodrogue.engine.components.Name;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Portal;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Terrain;
import com.sonicmax.bloodrogue.engine.components.Trap;
import com.sonicmax.bloodrogue.engine.components.Usable;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.engine.components.Wieldable;
import com.sonicmax.bloodrogue.engine.environment.TimeManager;
import com.sonicmax.bloodrogue.engine.environment.WeatherManager;
import com.sonicmax.bloodrogue.engine.systems.EntitySystem;
import com.sonicmax.bloodrogue.engine.systems.PotionSystem;
import com.sonicmax.bloodrogue.engine.systems.WeaponsSystem;
import com.sonicmax.bloodrogue.generator.Chunk;
import com.sonicmax.bloodrogue.generator.ProceduralGenerator;
import com.sonicmax.bloodrogue.generator.factories.AnimationFactory;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.generator.MapData;
import com.sonicmax.bloodrogue.generator.tools.CellularAutomata;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.renderer.Animation;
import com.sonicmax.bloodrogue.renderer.ui.InventoryCard;
import com.sonicmax.bloodrogue.tilesets.BuildingTileset;
import com.sonicmax.bloodrogue.tilesets.CorpseTileset;
import com.sonicmax.bloodrogue.tilesets.ExteriorTileset;
import com.sonicmax.bloodrogue.utils.maths.GeometryHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.generator.factories.PlayerFactory;
import com.sonicmax.bloodrogue.utils.Array2DHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.DelayQueue;

public class GameEngine {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final int DIJKSTRA_MAX = 20;
    private final int HIGH_PRIORITY = 0;
    private final int MEDIUM_PRIORITY = 1;
    private final int LOW_PRIORITY = 2;
    private final int ONE_HOUR = 60;

    private GameInterface gameInterface;
    private FieldOfVisionCalculator fovCalculator;
    private AffinityManager affinityManager;
    private ComponentManager componentManager;
    private WeatherManager weatherManager;
    private RandomNumberGenerator rng;

    private GameState gameState;
    private int[][] playerDesireMap;
    private double[][] fieldOfVision;
    private Component[] player;

    private ArrayList<ActorTurn>[] priorityQueue;
    private ArrayList<Component[]> objectQueue;

    private int mapWidth;
    private int mapHeight;
    private int sightRadius;
    private int currentFloor;

    private boolean playerMoveLock;

    private Vector2D pathDestination;

    private Sprite[][] terrainSpriteGrid;
    private ArrayList<Sprite>[][] objectSpriteGrid;
    private ArrayList<Animation> animations;

    // ECS storage and management
    private int[][] terrainEntities;
    private ArrayList<Long>[][] objectEntities;
    private ArrayList<Long> treeEntities;
    private ArrayList<Long> aiEntities;
    private long playerEntity;

    // Game world information
    private boolean[][] indoorRegions;
    private boolean[][] waterRegions;
    private CellularAutomata snowCoverGenerator;
    private int[][] snowCover;
    private int lastSnowCheck = 0;
    private int lastRainCheck = 0;

    // Game turn data
    private ArrayList<Long> inventoryPickupGroup;
    private boolean footstepAlternator;

    public GameEngine(GameInterface gameInterface) {
        this.playerMoveLock = false;
        this.footstepAlternator = true;

        this.sightRadius = 10;
        this.mapWidth = 128;
        this.mapHeight = 128;
        this.currentFloor = 1;

        this.gameInterface = gameInterface;
        this.fovCalculator = new FieldOfVisionCalculator();
        this.affinityManager = new AffinityManager();
        this.componentManager = ComponentManager.getInstance();
        this.weatherManager = new WeatherManager();
        this.rng = new RandomNumberGenerator();

        initCollections();
    }

    /*
    ---------------------------------------------
     Getters/setters
    ---------------------------------------------
    */

    public GameState getGameState() {
        return this.gameState;
    }

    public Frame getCurrentFrameData() {
        return new Frame(currentFloor, terrainEntities, objectSpriteGrid, animations,
                fieldOfVision, fovCalculator.getVisitedTiles(), indoorRegions, waterRegions, snowCover, heightMap, player);
    }

    public FloorData getCurrentFloorData(int[][] rawTerrainComponents, ArrayList<Component[]>[][] rawObjectComponents) {
        return new FloorData(currentFloor, rawTerrainComponents, rawObjectComponents, player);
    }

    private Position getPlayerPosition() {
        return (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
    }

    public Vector2D getPlayerVector() {
        Position position = (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
        return new Vector2D(position.x, position.y);
    }


    /*
    ---------------------------------------------
     Game initialisation
    ---------------------------------------------
    */

    /**
     * Initialises any arrays/queues/etc used to store floor data.
     */

    private void initCollections() {
        terrainSpriteGrid = new Sprite[mapWidth][mapHeight];
        objectSpriteGrid = Array2DHelper.create2DSpriteArray(mapWidth, mapHeight);
        animations = new ArrayList<>();
        snowCover = Array2DHelper.fillIntArray(mapWidth, mapHeight, -1);

        terrainEntities = new int[mapWidth][mapHeight];
        aiEntities = new ArrayList<>();
        treeEntities = new ArrayList<>();
        objectEntities = Array2DHelper.create2dLongStack(mapWidth, mapHeight);

        inventoryPickupGroup = new ArrayList<>();

        initPriorityQueue();
        objectQueue = new ArrayList<>();

        snowCoverGenerator = null;

        componentManager.clear();
    }

    private void initPriorityQueue() {
        this.priorityQueue = new ArrayList[3];
        this.priorityQueue[HIGH_PRIORITY] = new ArrayList<>();
        this.priorityQueue[MEDIUM_PRIORITY] = new ArrayList<>();
        this.priorityQueue[LOW_PRIORITY] = new ArrayList<>();
    }

    public void startNewGame() {
        gameState = new GameState();
        player = PlayerFactory.getPlayer(0, 0);
        playerEntity = player[0].id;
        gameState.setPlayer(player);

        generateNewFloor(this.currentFloor);
        saveCurrentFloor();
        gameInterface.saveState(gameState);
        advanceFrame();
    }

    private HashMap<String, Integer> spriteIndexes;

    public void setSpriteIndexes(HashMap<String, Integer> spriteIndexes) {
        this.spriteIndexes = spriteIndexes;
    }

    /**
     * Generates terrain/objects/enemies/etc for a new floor and instantiates a new GameState object
     * to hold components + any other data required for gameplay.
     */

    private ProceduralGenerator proceduralGenerator;

    private float[][] heightMap;

    private void generateNewFloor(int floorIndex) {
        // Retrieve player equipment from component manager and remove components from previous floor.
        ArrayList<Component> playerEquipment = getPlayerEquipment();

        initCollections();

        // Generate data for new floor
        proceduralGenerator = new ProceduralGenerator(mapWidth, mapHeight, gameInterface.getAssets());
        proceduralGenerator.setFloor(floorIndex);
        proceduralGenerator.setSpriteIndexes(spriteIndexes);
        proceduralGenerator.generate(ProceduralGenerator.EXTERIOR);

        // Generated data has already been sorted into ComponentManager instance, so we just have to
        // grab the arrays of terrainSpriteGrid/object entities
        MapData mapData = proceduralGenerator.getMapData();
        terrainEntities = mapData.getTerrainEntities();
        objectEntities = mapData.getObjectEntities();
        indoorRegions = proceduralGenerator.getIndoorRegions();
        waterRegions = proceduralGenerator.getWaterRegions();
        treeEntities = proceduralGenerator.getTreeEntities();
        heightMap = proceduralGenerator.getHeightMap();

        // Now we need to either create new player entity (if this was the first floor) or
        // sort the existing components and move player to new start position
        Vector2D entrance = mapData.getEntrancePosition();

        componentManager.sortComponentArray(player);

        // Add any equipment to component manager. This effectively carries the equipment to
        // the newly generated floor.
        for (Component item : playerEquipment) {
            componentManager.sortComponent(item);
        }

        // Get references to enemy entities
        ArrayList<Component> enemies = componentManager.getComponents(AI.class.getSimpleName());

        for (int i = 0; i < enemies.size(); i++) {
            this.aiEntities.add(enemies.get(i).id);
        }

        addPlayer(entrance);
        prebuildSprites();

        weatherManager = gameInterface.getWeatherManager();

        weatherManager.setWeatherState(WeatherManager.SNOWING,
                gameInterface.getTimeManager().getTotalTimeInMinutes());

        proceduralGenerator = null;
    }

    private void addPlayer(Vector2D startPosition) {
        ArrayList<Long> newStack = objectEntities[startPosition.x][startPosition.y];

        newStack.add(playerEntity);

        Sprite sprite = (Sprite) componentManager.getEntityComponent(playerEntity, Sprite.class.getSimpleName());
        sprite.x = startPosition.x;
        sprite.y = startPosition.y;

        Position playerPosition = (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
        playerPosition.x = startPosition.x;
        playerPosition.y = startPosition.y;
    }

    /**
     * Creates an ArrayList containing all the components of each entity in player's inventory
     * and any currently equipped items. To avoid duplicating them we also remove them from the
     * component manager before saving state
     */

    private ArrayList<Component> getPlayerEquipment() {
        Container inventory = (Container) componentManager.getEntityComponent(playerEntity, Container.class.getSimpleName());
        ArrayList<Component> equipment = new ArrayList<>();

        if (inventory != null) {
            Iterator<Sprite> contents = inventory.contents.iterator();

            while (contents.hasNext()) {
                Component item = contents.next();
                ArrayList<Component> components = componentManager.getEntityComponents(item.id);
                equipment.addAll(components);
                componentManager.removeEntityComponents(item.id);
            }
        }
        else {
            Log.w(LOG_TAG, "Player entity didn't have container component");
        }

        Dexterity dex = (Dexterity) componentManager.getEntityComponent(playerEntity, Dexterity.class.getSimpleName());

        if (dex != null) {
            equipment.addAll(componentManager.getEntityComponents(dex.weaponEntity));
            componentManager.removeEntityComponents(dex.weaponEntity);

            equipment.addAll(componentManager.getEntityComponents(dex.armourEntity));
            componentManager.removeEntityComponents(dex.armourEntity);
        }
        else {
            Log.w(LOG_TAG, "Player entity didn't have dexterity component");
        }

        return equipment;
    }

    /**
     * Called when player wants to move to a different floor. Handles generation of new floors and
     * loading of existing floor data.
     *
     * @param floorIndex index of floor we want to move to
     * @param direction Use Directions.UP or Directions.DOWN
     */

    private void changeFloor(int floorIndex, int direction) {
        // Make sure components for current floor are updated
        saveCurrentFloor();

        currentFloor = floorIndex;
        gameState.updateFloorIndex(currentFloor);

        gameInterface.saveState(gameState);

        if (gameState.hasFloor(floorIndex)) {
            gameState.updateFloorIndex(floorIndex);
            FloorData floor = gameState.getCurrentFloor();
            loadFloor(floor, direction);
        }

        else {
            generateNewFloor(floorIndex);
            saveCurrentFloor();
        }

        gameInterface.saveState(gameState);

        playerDesireMap = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        initPriorityQueue();
    }

    private void saveCurrentFloor() {
        // Reinitialize raw component arrays.
        Component[][][] rawTerrainComponents = new Component[mapWidth][mapHeight][ProceduralGenerator.MAX_COMPONENTS];
        ArrayList<Component[]>[][] rawObjectComponents = Array2DHelper.createComponentGrid(mapWidth, mapHeight);

        // Todo: should we pause/resume animations? Probably not
        // ArrayList<Component[]>[][] rawAnimationComponents = Array2DHelper.createComponentGrid(mapWidth, mapHeight);

        // Reverse the process of sorting components
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                long terrainEntity = terrainEntities[x][y];
                ArrayList<Component> terrain = componentManager.getEntityComponents(terrainEntity);
                rawTerrainComponents[x][y] = terrain.toArray(new Component[terrain.size()]);

                for (long objectEntity : objectEntities[x][y]) {
                    ArrayList<Component> objects = componentManager.getEntityComponents(objectEntity);
                    rawObjectComponents[x][y].add(objects.toArray(new Component[objects.size()]));
                }
            }
        }

        // Now we can update floor data
        FloorData floor = getCurrentFloorData(terrainEntities, rawObjectComponents);

        FloorData existingFloor = gameState.getCurrentFloor();

        if (existingFloor != null) {
            // Update existing floor
            floor.setEntrance(existingFloor.entrancePosition);
            floor.setExit(existingFloor.exitPosition);

            gameState.updateFloor(currentFloor, floor);
        }
        else {
            // Add new floor
            gameState.addFloor(floor);
        }
    }

    /**
     * Restores game state from an instance of the GameState class (probably loaded from disk).
     * The end result of this method is the same as if we had generated the floor from scratch
     *
     * @param state GameState to restore
     */

    public void restoreGameState(GameState state) {
        this.gameState = state;
        this.currentFloor = state.getCurrentFloorIndex();
        this.player = state.getPlayer();

        loadFloor(state.getCurrentFloor(), 0);
    }

    private void loadFloor(FloorData floor, int direction) {
        // Get components for player's inventory so we can move them to new floor
        ArrayList<Component> playerEquipment = getPlayerEquipment();

        // Clear components and other data attached to last floor
        initCollections();

        // First, we can sort the components that should be carried to next floor
        componentManager.sortComponentArray(player);

        for (Component item : playerEquipment) {
            componentManager.sortComponent(item);
        }

        // Get raw components from floor data
        int[][] rawTerrainComponents = floor.getTerrain();
        ArrayList<Component[]>[][] rawObjectComponents = floor.getObjects();

        int width = rawTerrainComponents.length;
        int height = rawTerrainComponents[0].length;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (Component[] object : rawObjectComponents[x][y]) {
                    sortComponentsAndStoreEntities(object);
                }
            }
        }

        Vector2D startPos;

        if (direction == Directions.DOWN) {
            startPos = floor.entrancePosition;
        } else if (direction == Directions.UP) {
            startPos = floor.exitPosition;
        } else {
            Position pos = getPlayerPosition();
            startPos = new Vector2D(pos.x, pos.y);
        }

        addPlayer(new Vector2D(startPos.x, startPos.y));

        // Sort raw components by type and filter sprite components for renderer
        prebuildSprites();

        // Enemy components have already been sorted, but we should keep a reference to their entities
        ArrayList<Component> enemies = componentManager.getComponents(AI.class.getSimpleName());

        for (int i = 0; i < enemies.size(); i++) {
            this.aiEntities.add(enemies.get(i).id);
        }
    }

    public int[] getMapSize() {
        return new int[] {mapWidth, mapHeight};
    }

    public Component[] getPlayer() {
        return player;
    }

    public void setPathDestination(Vector2D dest) {
        this.pathDestination = dest;
    }

    /**
     * Caches 2d arrays of sprite components to be passed to renderer.
     */

    private void prebuildSprites() {
        this.terrainSpriteGrid = new Sprite[mapWidth][mapHeight];
        this.objectSpriteGrid = Array2DHelper.create2DSpriteArray(mapWidth, mapHeight);
        this.animations.clear();

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                terrainSpriteGrid[x][y] = (Sprite) componentManager.getEntityComponent(terrainEntities[x][y], Sprite.class.getSimpleName());

                for (Long entity : objectEntities[x][y]) {
                    Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());

                    if (sprite == null) {
                        Log.e(LOG_TAG, "Sprite was null: " + entity);
                        continue;
                    }

                    sprite.x = x;
                    sprite.y = y;

                    objectSpriteGrid[x][y].add(sprite);
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
        updatePreTurnData();
        advanceWorldTime();
        determineAiMoves();
        doPostTurnJobs();
        gameInterface.passDataToRenderer();
    }

    private void updatePreTurnData() {
        Position playerPosition = getPlayerPosition();
        fovCalculator.setValues(terrainEntities, objectEntities, playerPosition.x, playerPosition.y, sightRadius);
        fieldOfVision = fovCalculator.calculate();
        generatePlayerDesireMap();
    }

    private void determineAiMoves() {
        int enemySize = aiEntities.size();

        for (int i = 0; i < enemySize; i++) {
            long entity = aiEntities.get(i);
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

    private void doPostTurnJobs() {
        checkEntitiesTakingDamage();
    }

    private void checkEntitiesTakingDamage() {
        Iterator<Long> it = entitiesTakingDamage.iterator();
        while (it.hasNext()) {
            Sprite defenderSprite = (Sprite) componentManager.getEntityComponent(it.next(), Sprite.class.getSimpleName());
            // Make renderer display hit animation and return to previous state on completion
            if (defenderSprite.hasHitAnimation) {

                if (defenderSprite.hasIdleAnimation) {
                    defenderSprite.nextAnimationState = Sprite.IDLE_ANIMATION;
                }
                else {
                    defenderSprite.nextAnimationState = Sprite.NO_ANIMATION;
                }

                defenderSprite.currentAnimationState = Sprite.HIT_ANIMATION;
                defenderSprite.hitAnimation.reset();
            }
            it.remove();
        }
    }

    private GameObject handleSelfReplication(GameObject object) {
        /*float random = rng.getRandomFloat(0f, 1f);
        if (random < object.getSelfReplicateChance()) {

            // Find nearest adjacent free square
            Vector pos = object.getVector();
            Vector newPos = null;
            for (Vector direction : Directions.GenericTileset.values()) {
                Vector test = pos.add(direction);
                if (!detectCollisions(test)) {
                    newPos = test;
                    break;
                }
            }

            // Return null if we couldn't find an empty space for clone
            if (newPos == null) return null;


            // Make sure we clone right renderState of object.
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

    public void checkUserInput(Vector2D destination) {
        Position playerPosition = (Position) componentManager.getEntityComponent(playerEntity, Position.class.getSimpleName());
        boolean adjacent = isAdjacent(destination, new Vector2D(playerPosition.x, playerPosition.y));

        if (adjacent) {
            // Iterate through all player-controlled entities and queue movements
            ArrayList<Input> playerControlled = componentManager.getComponents(Input.class.getSimpleName());
            for (int i = 0; i < playerControlled.size(); i++) {
                long entity = playerControlled.get(i).id;
                Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
                ActorTurn turn = new ActorTurn(position);
                turn.setMove(destination);
                priorityQueue[MEDIUM_PRIORITY].add(turn);
            }

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

    private boolean isAdjacent(Vector2D original, Vector2D adjacent) {
        Vector2D difference = original.subtract(adjacent);
        return (difference.x() >= -1 && difference.x() <= 1) && (difference.y() >= -1 && difference.y() <= 1);
    }

    /*
    ---------------------------------------------
     Entity and component management
    ---------------------------------------------
    */

    private void sortComponentsAndStoreEntities(Component[] components) {
        long entity = components[0].id;
        componentManager.sortComponentArray(components);
        Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
        objectEntities[position.x][position.y].add(entity);
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
            Log.w(LOG_TAG, "Object not present in old stack. Adding to new stack");
            newStack.add(entity);
        }

        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        sprite.x = newX;
        sprite.y = newY;
        sprite.lastX = oldX;
        sprite.lastY = oldY;
        sprite.dirty = true;

        // Update sprite grid for renderer
        if (objectSpriteGrid[oldX][oldY].contains(sprite)) {
            objectSpriteGrid[oldX][oldY].remove(sprite);
        }

        objectSpriteGrid[newX][newY].add(sprite);
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
        int[] tilesToCheck = {Terrain.WALL, Terrain.FLOOR};
        int[][] desireGrid = Array2DHelper.fillIntArray(mapWidth, mapHeight, DIJKSTRA_MAX);
        ArrayList<Vector2D> desireLocations = new ArrayList<>();

        Position playerPosition = getPlayerPosition();
        AI ai = (AI) componentManager.getEntityComponent(playerEntity, AI.class.getSimpleName());
        int dijkstra;

        if (ai == null) {
            Log.w(LOG_TAG, "No AI component for entity: " + playerEntity);
            dijkstra = 20;
        }
        else {
            dijkstra = ai.dijkstra;
        }

        desireGrid[playerPosition.x][playerPosition.y] = dijkstra;
        desireLocations.add(new Vector2D(playerPosition.x, playerPosition.y));

        playerDesireMap = populateDijkstraGrid(Directions.All.values(), desireGrid, desireLocations, tilesToCheck, false);
    }

    private int[][] populateDijkstraGrid(Collection<Vector2D> directions, int[][] desireGrid, ArrayList<Vector2D> desireLocations, int[] typesToCheck, boolean ignoreCollisions) {
        int desireSize = desireLocations.size();
        for (int i = 0; i < desireSize; i++) {
            Vector2D desire = desireLocations.get(i);
            ArrayList<Vector2D> queue = new ArrayList<>();

            queue.add(desire);

            while (queue.size() > 0) {
                Vector2D cell = queue.remove(queue.size() - 1);

                int initialValue = desireGrid[cell.x()][cell.y()];

                for (Vector2D direction : directions) {
                    Vector2D neighbour = cell.add(direction);

                    if (!inBounds(neighbour)) continue;
                    if (fieldOfVision[neighbour.x()][neighbour.y()] == 0) continue;

                    long terrainEntity = terrainEntities[neighbour.x][neighbour.y];
                    Terrain stat = (Terrain) componentManager.getEntityComponent(terrainEntity, Terrain.class.getSimpleName());

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
        Vector2D position = new Vector2D(actorPosComp.x, actorPosComp.y);

        Position playerPosComp = getPlayerPosition();
        Vector2D playerPos = new Vector2D(playerPosComp.x, playerPosComp.y);

        int bestDesire = DIJKSTRA_MAX + 1;

        // Check adjacent tiles and find one with best desire score
        for (Vector2D direction : Directions.All.values()) {
            Vector2D adjacent = position.add(direction);
            if (inBounds(adjacent)) {
                int desire = playerDesireMap[adjacent.x()][adjacent.y()];
                if (desire < bestDesire) {
                    bestDesire = desire;
                }
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
            ActorTurn turn = new ActorTurn(actorPosComp);
            turn.setMove(playerPos);
            priorityQueue[HIGH_PRIORITY].add(turn);
            return;
        }

        int bestHeuristic = Integer.MAX_VALUE;
        Vector2D closestTile = null;
        ArrayList<Vector2D> blockedTiles = new ArrayList<>();

        for (Vector2D direction : Directions.All.values()) {
            Vector2D adjacent = position.add(direction);
            if (inBounds(adjacent)) {
                int newDesire = playerDesireMap[adjacent.x()][adjacent.y()];
                int distance = (int) GeometryHelper.getDistance(adjacent, playerPos);
                // Really simple heuristic (that probably needs improving)
                int heuristic = newDesire + distance;

                if (heuristic < bestHeuristic) {

                    if (!detectCollisions(adjacent)) {
                        bestHeuristic = heuristic;
                        closestTile = adjacent;
                    } else {
                        blockedTiles.add(adjacent);
                    }
                }
            }
        }

        boolean blocked = false;

        // Todo: We should check blocked tiles to see whether any of them were closed doors.
        int blockedSize = blockedTiles.size();
        for (int i = 0; i < blockedSize; i++) {
            Vector2D tile = blockedTiles.get(i);
            int newDesire = playerDesireMap[tile.x()][tile.y()];
            int distance = (int) GeometryHelper.getDistance(tile, playerPos);
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
            ActorTurn turn = new ActorTurn(actorPosComp);
            turn.setMove(closestTile);
            priorityQueue[LOW_PRIORITY].add(turn);
        }
    }

    private void seekActorWhilePathBlocked(long seekingEntity, long targetEntity) {
        Position position = (Position) componentManager.getEntityComponent(seekingEntity, Position.class.getSimpleName());
        AI ai = (AI) componentManager.getEntityComponent(seekingEntity, AI.class.getSimpleName());

        ArrayList<Vector2D> path = ai.path;

        if (path == null || path.size() == 0) {
            ai.path = findShortestPath(seekingEntity, targetEntity);
        }

        else if (path.size() > 0) {

            // Todo: generate desire map for target here

            Vector2D nextCell = path.get(0);
            int nextDesire = playerDesireMap[nextCell.x()][nextCell.y()];
            int bestDesire = Integer.MAX_VALUE;

            Vector2D posVec = new Vector2D(position.x, position.y);

            // Check adjacent tiles and find one with lowest desire score
            for (Vector2D direction : Directions.All.values()) {
                Vector2D adjacent = posVec.add(direction);
                if (inBounds(adjacent)) {
                    int desire = playerDesireMap[adjacent.x()][adjacent.y()];
                    if (desire < bestDesire) {
                        bestDesire = desire;
                    }
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
                ActorTurn turn = new ActorTurn(position);
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

    private ArrayList<Vector2D> findShortestPath(Vector2D startNode, Vector2D goalNode) {
        ArrayList<Vector2D> optimalPath = new ArrayList<>();
        ArrayList<Vector2D> openNodes = new ArrayList<>();
        ArrayList<String> checkedNodes = new ArrayList<>();

        if (startNode.equals(goalNode)) {
            return optimalPath;
        }

        openNodes.add(startNode);

        Vector2D lastNode = null;

        while (openNodes.size() > 0) {
            Vector2D currentNode = openNodes.remove(openNodes.size() - 1);

            if (lastNode != null && lastNode.equals(currentNode)) {
                // This probably shouldn't happen
                Log.w(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector2D closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector2D direction : Directions.All.values()) {
                Vector2D adjacentNode = currentNode.add(direction);

                if (checkedNodes.contains(adjacentNode.toString())) continue;

                if (detectCollisions(adjacentNode)) continue;

                double distanceToGoal = GeometryHelper.getDistance(adjacentNode, goalNode);

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

    private ArrayList<Vector2D> findShortestPath(long entityA, long entityB) {
        Position posA = (Position) componentManager.getEntityComponent(entityA, Position.class.getSimpleName());
        Position posB = (Position) componentManager.getEntityComponent(entityB, Position.class.getSimpleName());

        return findShortestPath(new Vector2D(posA.x, posA.y), new Vector2D(posB.x, posB.y));
    }

    public ArrayList<Vector2D> onTouchPathComplete() {
        if (this.pathDestination == null || !inBounds(this.pathDestination) || fieldOfVision[pathDestination.x()][pathDestination.y()] == 0
                || detectCollisions(this.pathDestination)) {

            return new ArrayList<>();
        }

        else {
            Position playerPos = (Position) componentManager.getEntityComponent(player[0].id,
                    Position.class.getSimpleName());

            return findShortestPath(new Vector2D(playerPos.x, playerPos.y), this.pathDestination);
        }
    }

    public void queueAndFollowPath(ArrayList<Vector2D> path) {
        DelayQueue<ActorTurn> queue = new DelayQueue<>();
        long start = 500L;

        Position playerPos = (Position) componentManager.getEntityComponent(player[0].id,
                Position.class.getSimpleName());

        for (int i = 0; i < path.size(); i++) {
            ActorTurn turn = new ActorTurn(playerPos);
            Vector2D vector = path.get(i);
            turn.setMove(vector);
            turn.setStart(start * (long) i);
            queue.put(turn);
        }

        gameInterface.setMoveLock(true);

        while (!queue.isEmpty()) {

            try {
                ActorTurn turn = queue.take();
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
        updatePreTurnData();

        for (int i = HIGH_PRIORITY; i <= LOW_PRIORITY; i++) {
            Iterator<ActorTurn> iterator = priorityQueue[i].iterator();

            while (iterator.hasNext()) {
                ActorTurn turn = iterator.next();

                if (turn.hasMove()) {
                    Vector2D destination = turn.getDestination();
                    Position actor = turn.getPositionComponent();
                    long entity = turn.getEntity();

                    Physics physics = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());

                    if (physics.isTraversable || !detectCollisions(destination)) {
                        moveObjectToNewStack(entity, actor.x, actor.y, destination.x, destination.y);
                        // Update position component after messing around with stacks to make sure that
                        // lastX and lastY in Sprite are set correctly
                        actor.x = destination.x;
                        actor.y = destination.y;

                        handleMovementInteractions(entity, destination);

                        if (entity == playerEntity) {
                            String fx = (footstepAlternator) ? FxFilePaths.FOOTSTEP_1 : FxFilePaths.FOOTSTEP_2;
                            gameInterface.triggerSoundEffect(fx);
                            footstepAlternator = !footstepAlternator;
                        }

                    } else {
                        checkForCollisions(entity, destination);
                    }
                }

                // Make sure to remove turns from queue after we're finished
                iterator.remove();
            }
        }

        addQueuedObjects();
    }

    /**
     * Checks for collisions between initiating entity and any entitys residing in the target
     * grid square. Only entities with physics components can collide with each other.
     *
     * @param initiator Moving entity
     * @param position Position to check
     */

    private void checkForCollisions(long initiator, Vector2D position) {
        // Early exit if initator entity doesn't have collidable physics
        Physics physics = (Physics) componentManager.getEntityComponent(initiator, Physics.class.getSimpleName());

        if (physics == null || !physics.activateOnCollide) return;

        Iterator<Long> it = objectEntities[position.x()][position.y()].iterator();

        while (it.hasNext()) {
            long target = it.next();

            Physics targetPhysics = (Physics) componentManager.getEntityComponent(target, Physics.class.getSimpleName());

            if (targetPhysics != null && targetPhysics.activateOnCollide) {
                int result = collideEntities(target, initiator);

                if (result == Actions.REMOVE_ENTITY) {
                    it.remove();
                    continue;
                }

                performAction(initiator, target, result);
            }
        }
    }

    /**
     * Generally when actors collide with entities, nothing happens - but in some cases we want to
     * trigger specific actions depending on their components (eg. opening doors, opening chests,
     * attacking enemies, etc)
     *
     * @param target Entity that was collided with
     * @param actor Colliding entity
     * @return Actions constant
     */

    private int collideEntities(long target, long actor) {
        Collectable collectableComponent = (Collectable) componentManager.getEntityComponent(target, Collectable.class.getSimpleName());

        if (collectableComponent != null) {
            Container actorContainer = (Container) componentManager.getEntityComponent(actor, Container.class.getSimpleName());
            if (actorContainer != null) {
                boolean success = addToContainer(collectableComponent, actorContainer);
                if (success) {
                    // Remove entity from object stack - it is now located inside container.
                    // Otherwise, continue with execution
                    return Actions.REMOVE_ENTITY;
                }
            }
        }

        Container containerComponent = (Container) componentManager.getEntityComponent(target, Container.class.getSimpleName());

        if (containerComponent != null) {
            toggleContainerState(target, containerComponent);
        }

        Barrier barrierComponent = (Barrier) componentManager.getEntityComponent(target, Barrier.class.getSimpleName());

        if (barrierComponent != null) {
            toggleBarrierState(target, barrierComponent);
        }

        Trap trapComponent = (Trap) componentManager.getEntityComponent(target, Trap.class.getSimpleName());

        if (trapComponent != null) {
            float chance = rng.getRandomFloat(0f, 1f);
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

    /**
     * Adds collectable entity to container entity and displays feedback in UI
     *
     * @param collectable
     * @param container
     * @return
     */

    private boolean addToContainer(Collectable collectable, Container container) {
        if (container.totalWeight + collectable.weight <= container.capacity) {
            Sprite sprite = (Sprite) componentManager.getEntityComponent(collectable.id, Sprite.class.getSimpleName());
            container.contents.add(sprite);
            container.totalWeight += collectable.weight;
            EntitySystem.hide(componentManager, collectable.id);
            animations.add(AnimationFactory.getPingAnimation(sprite.x, sprite.y));

            // Todo: should this be default behaviour when picking up weapons?
            if (!WeaponsSystem.hasEqupped(componentManager, playerEntity, Wieldable.WEAPON)) {
                WeaponsSystem.wieldWeapon(componentManager, container.id, collectable.id);
            }

            return true;
        }
        else {
            return false;
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

        // Todo: fix DecalFactory methods

        /*Component[] splat = DecalFactory.createBloodSplat(defenderPosition, blood, terrainEntities);
        if (splat != null) {
            objectQueue.add(splat);
        }*/

        gameInterface.displayStatus(defenderPosition, Integer.toString(damageDealt), TextColours.STATUS_RED);

        if (vitality.hp <= 0) {
            Name defenderName = (Name) componentManager.getEntityComponent(victim, Name.class.getSimpleName());

            animations.add(AnimationFactory.getDeathAnimation(blood, x, y));

            /*ArrayList<Component[]> spray = DecalFactory.createBloodSpray(defenderPosition, blood, terrainEntities);
            for (Component[] components : spray) {
                sortComponentsAndStoreEntities(components);
            }*/

            gameInterface.addNarration(defenderName.value + " killed by trap!", TextColours.RED);

            kill(victim);

            AI defenderAi = (AI) componentManager.getEntityComponent(victim, AI.class.getSimpleName());

            if (defenderAi == null || !defenderAi.computerControlled) {
                // Check for other controllable characters, or end game
            }
        }

        else {
            animations.add(AnimationFactory.getHitAnimation(blood, x, y));
        }
    }

    /**
     * Toggles state of barrier components - typically between being open and closed.
     *
     * @param entity
     * @param barrierComponent
     */

    private void toggleBarrierState(long entity, Barrier barrierComponent) {
        switch (barrierComponent.type) {
            case Barrier.DOOR:
                if (!barrierComponent.open) {
                    barrierComponent.open = true;
                    // We also have to update physics component and sprite
                    Physics physicsComponent = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());
                    physicsComponent.isBlocking = false;
                    physicsComponent.isTraversable = true;

                    Sprite spriteComponent = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
                    spriteComponent.path = BuildingTileset.DOUBLE_DOORS_OPEN;
                    spriteComponent.spriteIndex = -1;

                    gameInterface.triggerSoundEffect(FxFilePaths.DOOR_OPEN_1);
                }
                break;

            default:
                Log.e(LOG_TAG, "No renderState associated with Barrier - can't act");
                break;
        }
    }

    /**
     * Contains typically have three states - closed, open, and empty. This method switches between
     * each state depending on whether it has been opened & whether it contains items.
     *
     * @param containerEntity
     * @param containerComponent
     */
    
    private void toggleContainerState(long containerEntity, Container containerComponent) {
        switch (containerComponent.type) {
            case Container.CHEST:
                if (!containerComponent.open) {
                    Sprite sprite = (Sprite) componentManager.getEntityComponent(containerEntity, Sprite.class.getSimpleName());

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

    private void handleMovementInteractions(long entity, Vector2D position) {
        ArrayList<Long> targetStack = objectEntities[position.x()][position.y()];

        Iterator<Long> it = targetStack.iterator();

        while (it.hasNext()) {
            long entityToCheck = it.next();

            Physics targetPhysics = (Physics) componentManager.getEntityComponent(entityToCheck, Physics.class.getSimpleName());

            if (targetPhysics != null && targetPhysics.activateOnMove) {
                int result = checkMovementActions(entityToCheck, entity);

                if (result == Actions.REMOVE_ENTITY) {
                    it.remove();
                    continue;
                }

                performAction(entity, entityToCheck, result);

                if (result == Actions.GO_TO_NEXT_FLOOR || result == Actions.GO_TO_PREV_FLOOR) {
                    // At this point, continuing iteration will cause exception.
                    // (and even if we could, we don't need to)
                    break;
                }
            }
        }

        if (inventoryPickupGroup.size() > 0) {
            addItemPickupNarration();
            gameInterface.triggerSoundEffect(FxFilePaths.ITEM_PICKUP);
        }
    }

    /**
     * Checks whether target entity has any special actions to perform when player/AI characters move onto it
     * and returns int constant from Actions class
     *
     * Example usage: chests, items on floor, traps, stairs, etc
     *
     * @param target Entity to check for actions
     * @param actor Player/AI character
     * @return Constant from Actions class
     */

    private int checkMovementActions(long target, long actor) {
        Collectable collectableComponent = (Collectable) componentManager.getEntityComponent(target, Collectable.class.getSimpleName());

        // Check whether target entity can be picked up
        if (collectableComponent != null) {
            Container actorContainer = (Container) componentManager.getEntityComponent(actor, Container.class.getSimpleName());
            if (actorContainer != null) {
                boolean success = addToContainer(collectableComponent, actorContainer);
                if (success) {
                    inventoryPickupGroup.add(target);

                    // Remove entity from object stack - it is now located inside container.
                    return Actions.REMOVE_ENTITY;
                }
            }
        }

        Portal portalComponent = (Portal) componentManager.getEntityComponent(target, Portal.class.getSimpleName());

        // Check whether target entity can transport actor to different location
        // TODO: figure out sane way to handle this for non-player actors
        if (portalComponent != null && actor == playerEntity) {

            if (portalComponent.destFloor == -1) {
                // Check for destination tile.


            }

            else {
                if (portalComponent.destFloor < currentFloor
                        && currentFloor - portalComponent.destFloor == 1) {

                    return Actions.GO_TO_PREV_FLOOR;
                }

                else if (portalComponent.destFloor > currentFloor
                        && portalComponent.destFloor - currentFloor == 1) {

                    return Actions.GO_TO_NEXT_FLOOR;
                }

                else {
                    Log.w(LOG_TAG, "Weird floor portal. Dest = " + portalComponent.destFloor
                            + ", current = " + currentFloor);
                }
            }
        }

        return Actions.NONE;
    }

    private void addItemPickupNarration() {
        StringBuilder builder = new StringBuilder();
        builder.append("You picked up the ");

        int numberOfItems = inventoryPickupGroup.size();
        long item = inventoryPickupGroup.get(0);

        InventoryCard details = getEntityDetails(item);
        String name = "";

        if (details.name.equals("Unknown")) {
            if (componentManager.has(item, Wieldable.class.getSimpleName())) {
                if (componentManager.has(item, Vitality.class.getSimpleName())) {
                    name = "unidentified armour";
                } else if (componentManager.has(item, Damage.class.getSimpleName())) {
                    name = "unidentified weapon";
                }

            } else if (componentManager.has(item, Usable.class.getSimpleName())) {
                name = "unidentified potion";
            } else {
                name = "something weird";
            }
        } else {
            name = details.name;
        }

        builder.append(name);

        if (numberOfItems > 1) {
            builder.append(" and ");
            builder.append(numberOfItems - 1);

            if (numberOfItems - 1 > 1) {
                builder.append(" other items.");
            } else {
                builder.append(" other item.");
            }

        } else {
            builder.append(".");
        }

        gameInterface.addNarration(builder.toString(), TextColours.YELLOW);
        inventoryPickupGroup.clear();
    }


    /**
     *  Checks result of checkMovementActions() method to see if we need to do anything.
     *  This will generally be things that change the state of the game (eg. changing floors) rather
     *  than more simple actions (eg. activating traps)
     */

    private void performAction(long activatedEntity, long targetEntity, int code) {
        switch (code) {
            case Actions.GO_TO_NEXT_FLOOR:
                // Tell renderer to fade out content and display loading screen, generate
                // terrainSpriteGrid for new floor and fade in with new content
                gameInterface.startFloorChange();
                changeFloor(currentFloor + 1, Directions.DOWN);
                advanceFrame();
                gameInterface.transitionToNewContent();
                break;

            case Actions.GO_TO_PREV_FLOOR:
                if (currentFloor > 1) {
                    gameInterface.startFloorChange();
                    changeFloor(currentFloor - 1, Directions.UP);
                    advanceFrame();
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
                objectSpriteGrid[position.x][position.y].add(sprite);
            }

            else {
                Log.d(LOG_TAG, "Couldn't add queued entity - no position component");
            }
        }

        objectQueue.clear();
    }

    private void advanceWorldTime() {
        checkWeather();
    }

    private void checkWeather() {
        TimeManager timeManager = gameInterface.getTimeManager();
        int currentTime = timeManager.getTotalTimeInMinutes();

        // Perform any time-related weather actions (eg. puddle formation/evaporation, snow, etc)
        switch (weatherManager.getCurrentWeatherState()) {
            // Wet conditions
            case WeatherManager.RAINING:
                handleRainEffects(currentTime);
                handleSnowMelting(currentTime);
                break;

            case WeatherManager.SNOWING:
                // Todo: freeze puddles?
                handleSnowEffects(currentTime);
                break;

            // Dry conditions
            case WeatherManager.FINE:
            case WeatherManager.FOGGY:
                handlePuddleEvaporation(currentTime);
                handleSnowMelting(currentTime);
                break;
        }

        weatherManager.checkWeather(currentTime);
    }

    private void handleSnowEffects(int time) {
        for (Long entity : treeEntities) {
            if (rng.d6(1, 1)) {
                Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
                if (sprite != null) {
                    sprite.overlayPath = ExteriorTileset.TREE_SNOW_TOP;
                    sprite.overlayRenderState = Sprite.DYNAMIC;
                    weatherManager.startTreeSnowTimer(entity);
                }
            }
        }

        if (snowCoverGenerator == null) {
            snowCoverGenerator = new CellularAutomata();
            snowCoverGenerator.setChanceToStartAlive(0.1f);
            snowCoverGenerator.prepareSimulation(new Chunk(0, 0, mapWidth, mapHeight));
        }

        if (time - lastSnowCheck > ONE_HOUR) {
            // Set cellular automata params so that:

            // - Any cell with > 2 neighbours will be brought to life
            // - No cells will be killed (as impossible to have < 0 neighbours)
            // - 0.05% chance that dead cells will be resurrected

            int birthLimit = 2;
            int deathLimit = -1;
            float chanceToRessurect = 0.05f;

            snowCoverGenerator.setBirthLimit(birthLimit)
                    .setDeathLimit(deathLimit)
                    .setChanceToResurrect(chanceToRessurect)
                    .setBringAlive(true)
                    .setKillAlive(false);

            snowCoverGenerator.doSimulationStep();
            snowCover = snowCoverGenerator.getEdgeMap();
            lastSnowCheck = time;
        }
    }

    private void handleRainEffects(int time) {
        // While number of puddles < PUDDLE_LIMIT, attempt to add puddles on each turn
        if (weatherManager.getPuddleEntities().size() >= WeatherManager.PUDDLE_LIMIT) {
            return;
        }

        int tries = rng.getRandomInt(1, weatherManager.getRainIntensity());

        for (int i = 0; i < tries; i++) {
            int x = rng.getRandomInt(0, mapWidth - 1);
            int y = rng.getRandomInt(0, mapHeight - 1);
            if (!indoorRegions[x][y] && !waterRegions[x][y]) {
                String texture = rng.getRandomItemFromStringArray(ExteriorTileset.PUDDLES);
                Component[] puddle = DecalFactory.createTraversableDecoration(x, y, texture);
                long entity = puddle[0].id;

                objectQueue.add(puddle);
                weatherManager.addPuddle(entity);

                waterRegions[x][y] = true;
            }
        }
    }

    private void handleSnowMelting(int time) {
        int elapsedSnowTime = time - lastSnowCheck;

        // Snow will start to melt after 2 hours
        if (elapsedSnowTime > ONE_HOUR) {

            if (snowCoverGenerator != null) {
                // Set cellular automata params so that:

                // No new cells will be generated (impossible to have >9 neighbours)
                // Cells with less than 2 neighbours will be killed
                // 0.05% chance that living cells will be killed

                int birthLimit = 9;
                int deathLimit = 6;
                float chanceToDie = 0.01f;

                snowCoverGenerator.setBirthLimit(birthLimit)
                        .setDeathLimit(deathLimit)
                        .setChanceToDie(chanceToDie)
                        .setBringAlive(false)
                        .setKillAlive(true);

                snowCoverGenerator.doSimulationStep();
                snowCover = snowCoverGenerator.getEdgeMap();
            }

            int ticks = elapsedSnowTime * 3;
            ArrayList<Long> treesToMelt = weatherManager.getMeltedTreeSnow(ticks);

            for (Long entity : treesToMelt) {
                // We can just turn off overlay for this tree
                Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
                sprite.overlayRenderState = Sprite.NONE;
            }

            lastSnowCheck = time;
        }
    }

    private void handlePuddleEvaporation(int time) {
        int elapsedRainTime = time - lastRainCheck;

        // Puddles will last for a maximum of 1 hour, and start to evaporate after 40 minutes.
        if (elapsedRainTime > WeatherManager.PUDDLE_DURATION_MINUTES) {
            if (weatherManager.getPuddleEntities().size() > 0) {
                int ticks = elapsedRainTime * 3;
                ArrayList<Long> puddles = weatherManager.getPuddlesToRemove(ticks);
                for (Long entity : puddles) {
                    removeEntity(entity);
                }
            }

            lastRainCheck = time;
        }
    }

    private void removeEntity(long entity) {
        Position position = (Position) componentManager.getEntityComponent(entity, Position.class.getSimpleName());
        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        objectEntities[position.x][position.y].remove(entity);
        objectSpriteGrid[position.x][position.y].remove(sprite);
        componentManager.removeEntityComponents(entity);
    }

    /**
     *  Calculates results of combat between two entities. Does some sanity checks to make sure
     *  that entities should be fighting, gets damage and checks whether entity is still alive.
     *  Also adds blood decals and handles UI stuff (displaying damage, narrations, etc)
     */

    private ArrayList<Long> entitiesTakingDamage = new ArrayList<>();

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

        /*Component[] splat = DecalFactory.createBloodSplat(defenderPosition, blood, terrainEntities);
        if (splat != null) {
            objectQueue.add(splat);
        }*/

        entitiesTakingDamage.add(defender);

        gameInterface.triggerSoundEffect(FxFilePaths.SMALL_HIT_1);

        // Update combat log and display hit rawAnimationComponents
        if (defenderAi != null && defenderAi.computerControlled) {
            gameInterface.displayStatus(defenderPosition, Integer.toString(damageDealt), TextColours.STATUS_RED);
        }

        if (vitality.hp <= 0) {
            Name attackerName = (Name) componentManager.getEntityComponent(aggressor, Name.class.getSimpleName());
            Name defenderName = (Name) componentManager.getEntityComponent(defender, Name.class.getSimpleName());

            animations.add(AnimationFactory.getDeathAnimation(blood, x, y));

            /*

            ArrayList<Component[]> spray = DecalFactory.createBloodSpray(defenderPosition, blood, terrainEntities);
            for (Component[] components : spray) {
                sortComponentsAndStoreEntities(components);
            }*/

            gameInterface.addNarration(attackerName.value + " killed " + defenderName.value + "!", TextColours.RED);

            applyXpReward(aggressor, defender);

            kill(defender);

            if (defenderAi == null || !defenderAi.computerControlled) {
                // Check for other controllable characters, or end game
            }
        }

        else {
            animations.add(AnimationFactory.getHitAnimation(blood, x, y));
        }
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

    private int doDamage(Damage attacker, Vitality defender) {
        return doDamage(attacker.strength, defender);
    }

    private void kill(long entity) {
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
        sprite.renderState = Sprite.DYNAMIC;
        sprite.lastX = -1;
        sprite.lastY = -1;
        sprite.path = CorpseTileset.getCorpseForEntity(nameComponent.value);

        if (sprite.currentAnimationState == Sprite.HIT_ANIMATION) {
            sprite.nextAnimationState = Sprite.NO_ANIMATION;
        }
        else {
            sprite.currentAnimationState = Sprite.NO_ANIMATION;
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

    public long getInventoryEntity(int index) {
        Container inventory = (Container) componentManager.getEntityComponent(playerEntity, Container.class.getSimpleName());

        if (index >= inventory.contents.size()) return -1;

        Sprite item = inventory.contents.get(index);

        return item.id;
    }

    public InventoryCard getEntityDetails(long entity) {
        Sprite sprite = (Sprite) componentManager.getEntityComponent(entity, Sprite.class.getSimpleName());
        Name nameComponent = (Name) componentManager.getEntityComponent(entity, Name.class.getSimpleName());

        String stats = "";

        if (componentManager.has(entity, Wieldable.class.getSimpleName())) {
            if (componentManager.has(entity, Damage.class.getSimpleName())) {
                Damage damageComponent = (Damage) componentManager.getEntityComponent(entity, Damage.class.getSimpleName());
                stats = "Strength: " + damageComponent.strength;
            }
        }

        else if (componentManager.has(entity, Usable.class.getSimpleName())) {
            Collectable collectable = (Collectable) componentManager.getEntityComponent(entity, Collectable.class.getSimpleName());
            Usable usable = (Usable) componentManager.getEntityComponent(entity, Usable.class.getSimpleName());

            if (collectable.unknown) {
                Knowledge playerKnowledge = (Knowledge) componentManager.getEntityComponent(playerEntity, Knowledge.class.getSimpleName());
                if (playerKnowledge.identifiedItems.containsValue(usable.effectId)) {
                    nameComponent.value = PotionSystem.getIdentifiedPotionName(usable.effectId);
                    stats = getItemAttributes(usable, entity);
                }
                else {
                    stats = "???";
                }
            }

            else {
                stats = getItemAttributes(usable, entity);
            }
        }

        Collectable collectable = (Collectable) componentManager.getEntityComponent(entity, Collectable.class.getSimpleName());

        return new InventoryCard(sprite, nameComponent.value, nameComponent.description, stats, collectable.weight);
    }

    public String getItemAttributes(Usable usable, long entity) {
        if (componentManager.has(entity, Vitality.class.getSimpleName())) {
            Vitality vitalityComponent = (Vitality) componentManager.getEntityComponent(entity, Vitality.class.getSimpleName());
            // Todo: work out how much health this would restore lol
            if (vitalityComponent.endurance < 5) {
                return "Vitality (weak)";
            }
        }

        else if (componentManager.has(entity, Damage.class.getSimpleName())) {
            Damage damageComponent = (Damage) componentManager.getEntityComponent(entity, Damage.class.getSimpleName());
            switch (usable.effect) {
                case "damage":
                    if (damageComponent.strength < 5) {
                        return "Poison (weak)";
                    }

                case "strength":
                    if (damageComponent.strength < 5) {
                        return "Gain strength";
                    }
            }
        }

        return "???";
    }

    public void useEntity(long entity) {
        Name nameComponent = (Name) componentManager.getEntityComponent(entity, Name.class.getSimpleName());
        if (componentManager.has(entity, Wieldable.class.getSimpleName())) {
            WeaponsSystem.wieldWeapon(componentManager, playerEntity, entity);
            gameInterface.addNarration("You equipped the " + nameComponent.value + ".", TextColours.ROYAL_BLUE);
        }

        else if (componentManager.has(entity, Usable.class.getSimpleName())) {
            PotionSystem.quaff(componentManager, playerEntity, entity);
            gameInterface.addNarration("You quaffed the " + nameComponent.value + ".", TextColours.WHITE);
            removeEntityFromInventory(entity);
        }
    }

    public void unequipEntity(long entity) {
        WeaponsSystem.unwieldCurrentWeapon(componentManager, playerEntity);
    }

    public void removeEntityFromInventory(long entity) {
        Container inventory = (Container) componentManager.getEntityComponent(playerEntity, Container.class.getSimpleName());

        Iterator<Sprite> contents = inventory.contents.iterator();

        while (contents.hasNext()) {
            Sprite sprite = contents.next();
            if (sprite.id == entity) {
                contents.remove();
                break;
            }
        }

        componentManager.removeEntityComponents(entity);
    }

    /*
    ---------------------------------------------
     Helper methods
    ---------------------------------------------
    */

    private boolean detectCollisions(Vector2D position) {
        // Map *should* be surrounded by border tiles which prevent player from moving out of bounds.
        // But just in case;
        if (!inBounds(position)) return true;

        int x = position.x();
        int y = position.y();

        // Check terrain component for blocking types
        long entity = terrainEntities[x][y];
        Terrain terrain = (Terrain) componentManager.getEntityComponent(entity, Terrain.class.getSimpleName());

        // Walls and borders will always prevent movement.
        if (terrain != null && (terrain.type == Terrain.WALL || terrain.type == Terrain.BORDER)) {
            return true;
        }

        Physics terrainPhysics = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());
        if (terrainPhysics != null && !terrainPhysics.isTraversable) {
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

    private boolean entityTypeInArray(Terrain stat, int[] typesToCheck) {
        if (stat == null) {
            return false;
        }

        for (int i = 0; i < typesToCheck.length; i++) {
            if (typesToCheck[i] == stat.type) {
                return true;
            }
        }

        return false;
    }

    private boolean inBounds(Vector2D position) {
        int x = position.x();
        int y = position.y();

        return (x >= 0 && x < mapWidth) && (y >= 0 && y < mapHeight);
    }
}
