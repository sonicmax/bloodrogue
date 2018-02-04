package com.sonicmax.bloodrogue.generator;

import android.content.res.AssetManager;
import android.util.Log;

import com.sonicmax.bloodrogue.data.BlueprintParser;
import com.sonicmax.bloodrogue.data.JSONLoader;
import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Portal;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Terrain;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.generator.factories.TerrainFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.generator.tools.CellularAutomata;
import com.sonicmax.bloodrogue.generator.tools.MazeGenerator;
import com.sonicmax.bloodrogue.generator.tools.PoissonDiskSampler;
import com.sonicmax.bloodrogue.tilesets.BuildingTileset;
import com.sonicmax.bloodrogue.tilesets.ExteriorTileset;
import com.sonicmax.bloodrogue.tilesets.RuinsTileset;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.generator.mansion.Room;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ProceduralGenerator {
    private final String LOG_TAG = this.getClass().getSimpleName();

    public final static int DUNGEON = 0;
    public final static int CAVERN = 1;
    public final static int RUINS = 2;
    public final static int MANSION = 3;
    public final static int EXTERIOR = 4;

    public final static int MAX_COMPONENTS = 18;

    // Border tile arrays are stored in this order
    private final int NORTH = 0;
    private final int NORTH_EAST = 1;
    private final int EAST = 2;
    private final int SOUTH_EAST = 3;
    private final int SOUTH = 4;
    private final int SOUTH_WEST = 5;
    private final int WEST = 6;
    private final int NORTH_WEST = 7;

    private ArrayList<Room> rooms;
    private HashMap<String, Component[]> doors;

    private Vector floorEntrance;
    private Vector floorExit;
    private int type;

    private int mapWidth;
    private int mapHeight;
    private int[][] mapRegions;

    // For region connecting method
    private int currentRegion = -1;

    // Room generation defaults
    private int maxHallLimit = 8;
    private int minChunkSize = 7;

    // Street block generation defaults
    private int maxRoadLimit = 8;
    private int minBlockSize = 16;

    // Random building generation defaults
    private int buildingDensity = 2000;
    private int minBuildingWidth = 10;
    private int maxBuildingWidth = 20;
    private int minBuildingHeight = 10;
    private int maxBuildingHeight = 20;

    // Random room generation defaults
    private int minRoomWidth = 3;
    private int maxRoomWidth = 9;
    private int minRoomHeight = 3;
    private int maxRoomHeight = 9;
    private int roomDensity = 2000;

    private int floorType;
    private int theme;
    private String themeKey;
    private int currentRoomTheme;

    private MansionDecorator decorator;
    private Tiler tiler;
    private RandomNumberGenerator rng;
    private AssetManager assetManager;
    private JSONObject furnitureBlueprints;
    private ComponentManager componentManager;

    private MazeGenerator mazeGenerator;
    private CellularAutomata automata;

    private boolean[][] blockedTiles;

    private long[][] terrainEntities;
    private ArrayList<Long>[][] objectEntities;
    private int currentFloor;

    public ProceduralGenerator(int width, int height, AssetManager assetManager) {
        this.mapWidth = width;
        this.mapHeight = height;
        this.assetManager = assetManager;
        this.furnitureBlueprints = JSONLoader.loadFurniture(assetManager);
        this.mazeGenerator = new MazeGenerator();
        this.automata = new CellularAutomata();

        this.doors = new HashMap<>();
        this.rng = new RandomNumberGenerator();
        this.currentFloor = 1;

        // Setup grids used to store terrain and object entities. We will use these in conjunction
        // with component manager to detect collisions and other interactions when placing terrain/objects.
        // This also means that we can simply reuse instance of ComponentManager in GameEngine, rather
        // than having to return raw arrays of components

        this.terrainEntities = new long[width][height];
        this.objectEntities = Array2DHelper.create2dLongStack(width, height);

        this.componentManager = ComponentManager.getInstance();

        // Make sure that component manager is clear before generating new terrain.
        // (it probably is, but need to be certain)

        this.componentManager.clear();
    }

	/*
		---------------------------------------------
		Initialisation
		---------------------------------------------
	*/

    private void initGrids() {
        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {

                if (x == 0 || x == mapWidth - 1 || y == 0 || y == mapHeight - 1) {
                    Component[] border = TerrainFactory.createBorder(x, y, tiler.getBorderTilePath());

                    terrainEntities[x][y] = border[0].id;
                    componentManager.sortComponentArray(border);

                    if (floorType == ProceduralGenerator.EXTERIOR) {
                        Component[] borderObject = DecalFactory.createDecal(x, y, tiler.getBorderObjectPath());

                        objectEntities[x][y].add(borderObject[0].id);
                        componentManager.sortComponentArray(borderObject);
                    }
                }
                else {
                    if (floorType == ProceduralGenerator.EXTERIOR) {
                        setTerrain(new Vector(x, y), FLOOR, tiler.getFloorTile(x, y, theme));
                    }
                    else {
                        setTerrain(new Vector(x, y), WALL, tiler.getWallTile(x, y, theme));
                    }
                }

            }
        }

        mapRegions = Array2DHelper.fillIntArray(mapWidth, mapHeight, -1);
    }

    public void setFloor(int floor) {
        this.currentFloor = floor;
    }

    public MapData getMapData() {
        return new MapData(terrainEntities, objectEntities, floorEntrance, floorExit, type);
    }

    /*
    ---------------------------------------------
    Theme setting
    ---------------------------------------------
*/

    private void setThemeAsExterior() {
        theme = RoomStyles.MANSION;
        themeKey = ExteriorTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 10;
    }

    private void setThemeAsDungeon() {
        theme = RoomStyles.MANSION;
        themeKey = BuildingTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 1000;
    }

    private void setThemeAsMansion() {
        theme = RoomStyles.MANSION;
        themeKey = BuildingTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 4000;
    }

    private void setThemeAsRuins() {
        theme = RoomStyles.RUINS;
        themeKey = RuinsTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 2000;
    }

/*
    ---------------------------------------------
    Top-level methods for generation
    ---------------------------------------------
*/

    /**
     * Generates a floor for the provided type. Results can be accessed using getMapData()
     * and getMapGrid() methods.
     *
     * @param type ProceduralGenerator.MANSION, ProceduralGenerator.RUINS, etc
     */

    public void generate(int type) {
        floorType = type;

        switch(type) {
            case EXTERIOR:
                setThemeAsExterior();
                tiler = new Tiler(themeKey);
                initGrids();
                generateExterior();
                break;

            case DUNGEON:
                initGrids();
                generateDungeon();
                break;

            case MANSION:
                setThemeAsMansion();
                tiler = new Tiler(themeKey);
                initGrids();
                generateMansion();
                break;

            case RUINS:
                setThemeAsRuins();
                initGrids();
                generateRuins();
                break;

            default:
                throw new Error("Undefined map type: " + type);
        }
    }

    public void generateDungeon() {
        Chunk chunk = new Chunk(0, 0, mapWidth, mapHeight);
        generateRandomRooms(chunk);
        carveRooms();

        // generateCorridors(chunk);
        // connectRegions();
        // removeDeadEnds();

        checkForBrokenDoors();

        calculateGoals();

        decorator = new MansionDecorator(mapWidth, mapHeight, theme, themeKey, assetManager);
        decorator.setGeneratorData(terrainEntities, objectEntities);
        decorator.decorateRooms(rooms);

        removeHiddenWalls();
        removeInaccessibleCells();
    }

    private void generateMansion() {
        // Ignore border tiles
        Chunk map = new Chunk(1, 1, mapWidth - 2, mapHeight - 2);

        ArrayList<Chunk> mapHalves = splitChunkInHalf(map);

        rooms = new ArrayList<>();

        ArrayList<Chunk> chunks = getHallwayChunks(map);
        // chunks.addAll(getHallwayChunks(mapHalves.get(1)));
        splitChunksIntoRooms(chunks);
        carveRooms();

        // generateCorridors(map);
        // connectRegions();
        // removeDeadEnds();
        checkForBrokenDoors();
        calculateGoals();

        /*roomDensity = 100;
        generateRandomRooms(mapHalves.get(1));
        generateCorridors(mapHalves.get(1));

        carveRooms();
        connectRegions();
        removeDeadEnds();
        checkForBrokenDoors();
        calculateGoals();

        if (rng.getRandomFloat(0.0f, 1.0f) < mansionRuinChance) {
            birthLimit = 4;
            deathLimit = 3;
            numberOfSmoothingSteps = 2;
            chanceToStartAlive = 0.4F;
            generateCaverns(mapHalves.get(1));
        }*/

        decorator = new MansionDecorator(mapWidth, mapHeight, theme, themeKey, assetManager);
        decorator.setGeneratorData(terrainEntities, objectEntities);
        decorator.decorateRooms(rooms);
    }

    private void generateRuins() {
        Chunk chunk = new Chunk(0, 0, mapWidth, mapHeight);
        generateRandomRooms(chunk);
        carveRooms();
        // generateCorridors(chunk);
        // removeDeadEnds();
        checkForBrokenDoors();
        // decorateRooms();

        generateCaverns(new Chunk(0, 0, mapWidth, mapHeight));
        removeHiddenWalls();
        removeInaccessibleCells();
        calculateGoals();
    }

/*
    ------------------------------------------------------------------------------------------
    Exterior generation

    Uses random sampling & cellular automata to generate terrain features
    (eg. swamps, lakes, bushes, etc)
    ------------------------------------------------------------------------------------------
*/

    public void generateExterior() {
        this.rooms = new ArrayList<>();
        setThemeAsMansion();
        this.tiler = new Tiler(themeKey);
        this.blockedTiles = new boolean[mapWidth][mapHeight];

        Chunk map = new Chunk(0, 0, mapWidth, mapHeight);
        Chunk mapWithoutBorders = new Chunk(1, 1, mapWidth - 2, mapHeight - 2);

        startRegion();

        // First, generate all the organic terrain - trees, flowers, lakes, etc
        placeRandomTreesInChunk(mapWithoutBorders);

        growLakes(mapWithoutBorders);

        addForestRegions(mapWithoutBorders);

        addFloralRegions(mapWithoutBorders);

        // Now we can start to add man made terrain - roads, buildings, etc

        ArrayList<Chunk> buildings = new ArrayList<>();
        ArrayList<Chunk> streetBlocks = getStreetBlocks(map);

        Log.v(LOG_TAG, "street block count: " + streetBlocks.size());

        for (Chunk block : streetBlocks) {
            // Add pavement to border of block
            addBorderToChunk(block, FLOOR, ExteriorTileset.SIDEWALK_BORDER);
            clearObjectsFromBorder(block);

            // Place buildings in each block
            buildings.addAll(addBuildingsToChunk(block));
        }

        removeHiddenWalls();
        checkForBrokenDoors();

        Log.v(LOG_TAG, "street block with bs: " + chunkswithbs);

        // Now use maze generator to add paths to map?
        /*mazeGenerator.setChunk(mapWithoutBorders);

        for (Chunk building : buildings) {
            mazeGenerator.excludeChunkFromMaze(building);
        }

        mazeGenerator.setWindingPercent(90);
        boolean[][] carvedPath = mazeGenerator.generate();

        for (int x = 0; x < mapWithoutBorders.width; x++) {
            for (int y = 0; y < mapWithoutBorders.height; y++) {
                if (carvedPath[x][y]) {
                    Vector cell = new Vector(x, y);
                    if (lakes[x][y]) {
                        setTerrain(cell, FLOOR, ExteriorTileset.BRIDGE_1);
                    }
                    else {
                        // The DIRT_PATH array contains slightly different textures to give organic effect
                        String texture = ExteriorTileset.DIRT_PATH[rng.getRandomInt(0, ExteriorTileset.DIRT_PATH.length - 1)];
                        setTerrain(cell, FLOOR, texture);
                    }

                    clearObjects(x, y);
                }
            }
        }*/
    }

    private void placeRandomTreesInChunk(Chunk chunk) {
        PoissonDiskSampler sampler = new PoissonDiskSampler();
        int minDistance = 3;
        int pointCount = 5;
        ArrayList<Vector> treePositions = sampler.generatePoisson(chunk.width, chunk.height, minDistance, pointCount);
        for (Vector cell : treePositions) {
            Component[] tree = DecalFactory.createDecal(cell.x, cell.y, ExteriorTileset.TREES[rng.getRandomInt(0, ExteriorTileset.TREES.length - 1)]);
            objectEntities[cell.x][cell.y].add(tree[0].id);
            componentManager.sortComponentArray(tree);
        }
    }

    private boolean[][] growLakes(Chunk chunk) {
        automata.setParams(4, 3, 3, 0.3f);
        boolean[][] lakes = automata.generate(chunk);

        int waterTextureIndex = 0;

        for (int x = 0; x < lakes.length; x++) {
            for (int y = 0; y < lakes[0].length; y++) {
                if (lakes[x][y] && !detectCollisions(new Vector(x, y))) {
                    String texture = ExteriorTileset.WATER[waterTextureIndex];

                    setTerrain(new Vector(x, y), BACKGROUND, ExteriorTileset.DIRT_PATH_1);

                    // Water texture array is designed to be iterated over in this order to align the tiles
                    // in a certain way (to try and create a semi random water texture)

                    waterTextureIndex++;

                    if (waterTextureIndex > ExteriorTileset.WATER.length - 1) {
                        waterTextureIndex = 0;
                    }

                    Component[] water = DecalFactory.createLiquid(x, y, texture);
                    objectEntities[x][y].add(water[0].id);
                    componentManager.sortComponentArray(water);


                }
            }
        }

        return lakes;
    }

    private void addForestRegions(Chunk chunk) {
        automata.setParams(4, 3, 2, 0.3f);
        boolean[][] forest = automata.generate(chunk);

        for (int x = 0; x < forest.length; x++) {
            for (int y = 0; y < forest[0].length; y++) {
                if (forest[x][y] && !detectCollisions(new Vector(x, y))) {

                    Component[] tree = DecalFactory.createFovBlockingDecal(x, y, ExteriorTileset.TREES[rng.getRandomInt(0, ExteriorTileset.TREES.length - 1)]);
                    objectEntities[x][y].add(tree[0].id);
                    componentManager.sortComponentArray(tree);
                }
            }
        }
    }

    private void addFloralRegions(Chunk chunk) {
        boolean[][] flowers = automata.generate(chunk);

        for (int x = 0; x < flowers.length; x++) {
            for (int y = 0; y < flowers[0].length; y++) {
                if (flowers[x][y] && !detectCollisions(new Vector(x, y))) {
                    String texture = ExteriorTileset.DECALS[rng.getRandomInt(0, ExteriorTileset.DECALS.length - 1)];
                    Component[] flower = DecalFactory.createTraversableDecoration(x, y, texture);
                    objectEntities[x][y].add(flower[0].id);
                    componentManager.sortComponentArray(flower);
                }
            }
        }
    }
/*
    ------------------------------------------------------------------------------------------
    Mansion generation

    These types of floor are generated using binary space partition to place the hallways and
    define chunks of rooms, which are then populated using a similar algorithm (with some
    alterations to make sure the generated rooms meet certain parameters). At this point we can
    connect the regions and place doors, and perform any other terrain generation we require
    (ruined rooms, bodies of water, etcetc).
    ------------------------------------------------------------------------------------------
*/

    private ArrayList<Chunk> getHallwayChunks(Chunk start) {
        ArrayList<Chunk> generatedChunks = new ArrayList<>();
        int totalHalls = 0;

        ArrayList<Chunk> chunkQueue = new ArrayList<>();

        // Ignore border tiles when defining starting chunk
        chunkQueue.add(start);

        // This boolean is inverted on each step so we carve alternating horizontal/vertical hallways
        boolean horizontal = true;

        while (chunkQueue.size() > 0 && totalHalls < maxHallLimit) {
            Chunk chunk = chunkQueue.remove(0);

            if (horizontal) {
                // Horizontal split
                int bottom = chunk.bottomLeft()[1] + minChunkSize;
                int top = chunk.topLeft()[1] - minChunkSize;

                if (bottom > top) {
                    // Chunk was too small to split
                    generatedChunks.add(chunk);
                    continue;
                }

                int splitY = rng.getRandomInt(bottom, top);

                startRegion();

                for (int x = chunk.x; x <= chunk.width; x++) {
                    setTerrain(new Vector(x, splitY), FLOOR, BuildingTileset.WOOD_FLOOR_1);
                }

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, chunk.width, splitY - chunk.y);
                Chunk splitChunkB = new Chunk(chunk.x, splitY + 1, chunk.width, (chunk.y + chunk.height) - splitY - 1);

                if (splitChunkA.width >= minChunkSize && splitChunkA.height >= minChunkSize) {
                    // Add chunk to queue to continue splitting
                    chunkQueue.add(splitChunkA);
                }
                else {
                    // Finished with chunk
                    generatedChunks.add(splitChunkA);
                }

                if (splitChunkB.width >= minChunkSize && splitChunkB.height >= minChunkSize) {
                    chunkQueue.add(splitChunkB);
                }
                else {
                    generatedChunks.add(splitChunkB);
                }

                totalHalls++;
            }

            else {
                // Vertical split
                int left = chunk.bottomLeft()[0] + minChunkSize;
                int right = chunk.bottomRight()[0] - minChunkSize;

                if (left > right) {
                    generatedChunks.add(chunk);
                    continue;
                }

                int splitX = rng.getRandomInt(left, right);

                for (int y = chunk.y; y <= chunk.height; y++) {
                    setTerrain(new Vector(splitX, y), FLOOR, BuildingTileset.WOOD_FLOOR_1);
                }

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, splitX - chunk.x, chunk.height);
                Chunk splitChunkB = new Chunk(splitX + 1, chunk.y, (chunk.x + chunk.width) - splitX - 1, chunk.height);

                if (splitChunkA.width > minChunkSize && splitChunkA.height > minChunkSize) {
                    chunkQueue.add(splitChunkA);
                }
                else {
                    generatedChunks.add(splitChunkA);
                }

                if (splitChunkB.width > minChunkSize && splitChunkB.height > minChunkSize) {
                    chunkQueue.add(splitChunkB);
                }
                else {
                    generatedChunks.add(splitChunkB);
                }

                totalHalls++;
            }

            horizontal = !horizontal;
        }

        return generatedChunks;
    }

    /**
     * Splits chunk into smaller street block chunks using binary space partition and carves roads between them.
     * The roads are three grid squares wide and are not considered to be part of the split chunks.
     */

    private ArrayList<Chunk> getStreetBlocks(Chunk start) {
        ArrayList<Chunk> generatedChunks = new ArrayList<>();
        int totalRoads = 0;

        ArrayList<Chunk> chunkQueue = new ArrayList<>();

        // Ignore border tiles when defining starting chunk
        chunkQueue.add(start);

        // This boolean is inverted on each step so we carve alternating horizontal/vertical hallways
        boolean horizontal = true;

        boolean[][] horizontalRoads = new boolean[start.width][start.height];
        boolean[][] verticalRoads = new boolean[start.width][start.height];

        while (chunkQueue.size() > 0 && totalRoads < maxRoadLimit) {
            Chunk chunk = chunkQueue.remove(0);

            if (horizontal) {
                // Horizontal split
                int bottom = chunk.bottomLeft()[1] + minBlockSize;
                int top = chunk.topLeft()[1] - minBlockSize;

                if (bottom > top) {
                    // Chunk was too small to split
                    generatedChunks.add(chunk);
                    continue;
                }

                int splitY = rng.getRandomInt(bottom, top);

                for (int x = chunk.x; x < chunk.x + chunk.width; x++) {

                    setTerrain(new Vector(x, splitY - 1), FLOOR, ExteriorTileset.ROAD_PLAIN);

                    if (verticalRoads[x][splitY]) {
                        setTerrain(new Vector(x, splitY), FLOOR, ExteriorTileset.ROAD_PLAIN);
                    }
                    else {
                        setTerrain(new Vector(x, splitY), FLOOR, ExteriorTileset.ROAD_MIDDLE_H);
                    }

                    setTerrain(new Vector(x, splitY + 1), FLOOR, ExteriorTileset.ROAD_PLAIN);

                    // Either clear objects from the road, or replace a road tile with a dirt tile
                    for (int y = splitY - 1; y <= splitY + 1; y++) {
                        if (objectEntities[x][y].size() > 0) {
                            if (rng.coinflip()) {
                                clearObjects(x, y);
                            }
                            else {
                                setTerrain(new Vector(x, y), FLOOR, ExteriorTileset.ROAD_DAMAGE);
                            }
                        }
                    }

                    horizontalRoads[x][splitY - 1] = true;
                    horizontalRoads[x][splitY] = true;
                    horizontalRoads[x][splitY + 1] = true;
                }

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, chunk.width, splitY - chunk.y - 1);
                Chunk splitChunkB = new Chunk(chunk.x, splitY + 2, chunk.width, (chunk.y + chunk.height) - splitY - 2);

                if (splitChunkA.width > minBlockSize * 2 && splitChunkA.height > minBlockSize * 2) {
                    // Add chunk to queue to continue splitting
                    chunkQueue.add(splitChunkA);
                }
                else {
                    // Finished with chunk
                    generatedChunks.add(splitChunkA);
                }

                if (splitChunkB.width > minBlockSize * 2 && splitChunkB.height > minBlockSize * 2) {
                    chunkQueue.add(splitChunkB);
                }
                else {
                    generatedChunks.add(splitChunkB);
                }

                totalRoads++;
            }

            else {
                // Vertical split
                int left = chunk.bottomLeft()[0] + minBlockSize;
                int right = chunk.bottomRight()[0] - minBlockSize;

                if (left > right) {
                    generatedChunks.add(chunk);
                    continue;
                }

                int splitX = rng.getRandomInt(left, right);

                for (int y = chunk.y; y < chunk.y + chunk.height; y++) {

                    setTerrain(new Vector(splitX - 1, y), FLOOR, ExteriorTileset.ROAD_PLAIN);

                    if (horizontalRoads[splitX][y]) {
                        setTerrain(new Vector(splitX, y), FLOOR, ExteriorTileset.ROAD_PLAIN);
                    }
                    else {
                        setTerrain(new Vector(splitX, y), FLOOR, ExteriorTileset.ROAD_MIDDLE_V);
                    }

                    setTerrain(new Vector(splitX + 1, y), FLOOR, ExteriorTileset.ROAD_PLAIN);

                    // Either clear objects from the road, or replace a road tile with a dirt tile
                    for (int x = splitX - 1; x <= splitX + 1; x++) {
                        if (objectEntities[x][y].size() > 0) {
                            if (rng.coinflip()) {
                                clearObjects(x, y);
                            }
                            else {
                                setTerrain(new Vector(x, y), FLOOR, ExteriorTileset.ROAD_DAMAGE);
                            }
                        }
                    }

                    verticalRoads[splitX - 1][y] = true;
                    verticalRoads[splitX][y] = true;
                    verticalRoads[splitX + 1][y] = true;
                }

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, splitX - chunk.x - 1, chunk.height);
                Chunk splitChunkB = new Chunk(splitX + 2, chunk.y, (chunk.x + chunk.width) - splitX - 2, chunk.height);

                if (splitChunkA.width > minBlockSize * 2 && splitChunkA.height > minBlockSize * 2) {
                    chunkQueue.add(splitChunkA);
                }
                else {
                    generatedChunks.add(splitChunkA);
                }

                if (splitChunkB.width > minBlockSize * 2 && splitChunkB.height > minBlockSize * 2) {
                    chunkQueue.add(splitChunkB);
                }
                else {
                    generatedChunks.add(splitChunkB);
                }

                totalRoads++;
            }

            horizontal = !horizontal;
        }

        return generatedChunks;
    }

    private ArrayList<Chunk> splitRoomChunk(Chunk start) {
        final int MIN_ROOM_SIZE = 6;
        ArrayList<Chunk> generatedChunks = new ArrayList<>();

        ArrayList<Chunk> chunkQueue = new ArrayList<>();
        chunkQueue.add(start);

        while (chunkQueue.size() > 0) {
            Chunk chunk = chunkQueue.remove(0);

            boolean horizontal = true;

            if (chunk.width == chunk.height) {
                // Probably doesn't matter which way we align the rooms
                horizontal = rng.coinflip();
            }

            else if (chunk.width > chunk.height) {
                horizontal = false;
            }

            if (horizontal) {
                // Horizontal split
                int bottom = chunk.bottomLeft()[1] + MIN_ROOM_SIZE;
                int top = chunk.topLeft()[1] - MIN_ROOM_SIZE;

                if (bottom > top) {
                    generatedChunks.add(chunk);
                    continue;
                }

                int splitY = rng.getRandomInt(bottom, top);

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, chunk.width, splitY - chunk.y);
                Chunk splitChunkB = new Chunk(chunk.x, splitY + 1, chunk.width, (chunk.y + chunk.height) - splitY - 1);

                if (splitChunkA.width >= MIN_ROOM_SIZE && splitChunkA.height >= MIN_ROOM_SIZE) {
                    // Add chunk to queue to continue splitting
                    chunkQueue.add(splitChunkA);
                }
                else {
                    // Finished with chunk
                    generatedChunks.add(splitChunkA);
                }

                if (splitChunkB.width >= MIN_ROOM_SIZE && splitChunkB.height >= MIN_ROOM_SIZE) {
                    chunkQueue.add(splitChunkB);
                }
                else {
                    generatedChunks.add(splitChunkB);
                }
            }

            else {
                // Vertical split
                int left = chunk.bottomLeft()[0] + minChunkSize;
                int right = chunk.bottomRight()[0] - minChunkSize;

                if (left > right) {
                    generatedChunks.add(chunk);
                    continue;
                }

                int splitX = rng.getRandomInt(left, right);

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, splitX - chunk.x, chunk.height);
                Chunk splitChunkB = new Chunk(splitX + 1, chunk.y, (chunk.x + chunk.width) - splitX - 1, chunk.height);

                if (splitChunkA.width >= minChunkSize && splitChunkA.height >= minChunkSize) {
                    // Add chunk to queue to continue splitting
                    chunkQueue.add(splitChunkA);
                }
                else {
                    // Finished with chunk
                    generatedChunks.add(splitChunkA);
                }

                if (splitChunkB.width >= minChunkSize && splitChunkB.height >= minChunkSize) {
                    chunkQueue.add(splitChunkB);
                }
                else {
                    generatedChunks.add(splitChunkB);
                }
            }
        }

        return generatedChunks;
    }

    private ArrayList<Chunk> splitChunkInHalf(Chunk chunk) {
        ArrayList<Chunk> generatedChunks = new ArrayList<>();

        boolean horizontal = true;

        if (chunk.width == chunk.height) {
            // Probably doesn't matter which way we align the rooms
            horizontal = rng.coinflip();
        }

        else if (chunk.width > chunk.height) {
            horizontal = false;
        }

        if (horizontal) {
            // Horizontal split
            int bottom = chunk.bottomLeft()[1];

            int splitY = bottom + (chunk.height / 2);

            Chunk splitChunkA = new Chunk(chunk.x, chunk.y, chunk.width, splitY - chunk.y);
            Chunk splitChunkB = new Chunk(chunk.x, splitY + 1, chunk.width, (chunk.y + chunk.height) - splitY - 1);

            // Finished with chunk
            generatedChunks.add(splitChunkA);
            generatedChunks.add(splitChunkB);
        }

        else {
            // Vertical split
            int left = chunk.bottomLeft()[0];

            int splitX = left + (chunk.width / 2);

            Chunk splitChunkA = new Chunk(chunk.x, chunk.y, splitX - chunk.x, chunk.height);
            Chunk splitChunkB = new Chunk(splitX + 1, chunk.y, (chunk.x + chunk.width) - splitX - 1, chunk.height);

            generatedChunks.add(splitChunkA);
            generatedChunks.add(splitChunkB);
        }

        return generatedChunks;
    }

    private ArrayList<Room> splitChunksIntoRooms(ArrayList<Chunk> chunks) {
        ArrayList<Room> roomChunks = new ArrayList<>();
        ArrayList<Chunk> secondPass = new ArrayList<>();

        // First, split the chunks into multiple smaller ones
        for (Chunk chunk : chunks) {
            ArrayList<Chunk> splitRooms = splitRoomChunk(chunk);

            for (Chunk room : splitRooms) {
                if (room.width * room.height > 80) {
                    secondPass.add(room);
                }
                else {
                    Room r = new Room(room.x + 1, room.y + 1, room.width - 2, room.height - 2);
                    roomChunks.add(r);
                }
            }
        }

        // Now do a second pass where we try to split bigger rooms into smaller ones using brute force.
        // It's not a huge problem to have larger rooms, but ideally these would be rare
        for (Chunk chunk : secondPass) {
            ArrayList<Chunk> splitRooms = splitChunkInHalf(chunk);

            for (Chunk room : splitRooms) {
                Room r = new Room(room.x + 1, room.y + 1, room.width - 2, room.height - 2);
                roomChunks.add(r);
            }
        }

        return roomChunks;
    }

    private int chunkswithbs = 0;

    private ArrayList<Chunk> addBuildingsToChunk(Chunk chunk) {
        chunkswithbs++;
        Log.v(LOG_TAG, "chunks with bs: " + chunkswithbs);
        Log.v(LOG_TAG, "placing buildings in chunk " + chunk);
        ArrayList<Chunk> buildings = new ArrayList<>();

        final int areaCutoff = minBuildingWidth * minBuildingHeight;

        int chunkArea = chunk.width * chunk.height;
        int occupiedArea = 0;

        for (int i = 0; i < buildingDensity; i++) {

            if (occupiedArea > chunkArea - areaCutoff) {
                Log.d(LOG_TAG, "Ran out of space for buildings in (" + chunk + ") at iteration " + i);
                break;
            }

            Chunk building = generateRandomBuilding(chunk, minBuildingWidth, maxBuildingWidth,
                    minBuildingHeight, maxBuildingHeight);

            // Make sure that new building doesn't collide with any existing ones.

            boolean placeable = true;

            for (Chunk existingBuilding : buildings) {
                if (AxisAlignedBoxTester.test(building, existingBuilding)) {
                    placeable = false;
                    break;
                }
            }

            if (!placeable) continue;

            if (building.x + building.width >= mapWidth || building.y + building.height >= mapHeight) {
                Log.e(LOG_TAG, "Why is this happening? " + building);
                continue;
            }

            occupiedArea += building.width * building.height;

            for (int x = building.x; x < building.x + building.width; x++) {
                for (int y = building.y; y < building.y + building.height; y++) {
                    blockedTiles[x][y] = true;
                }
            }

            buildings.add(building);

            // clearObjectsFromBorder(building);
            // addBorderObjectToChunk(building, ExteriorTileset.WOOD_FENCE);

            carveBuildingFoundation(building);
            addRoomsToBuilding(building);
            createDamagedWalls(building);
        }

        calculateGoals();

        decorator = new MansionDecorator(mapWidth, mapHeight, theme, themeKey, assetManager);
        decorator.setGeneratorData(terrainEntities, objectEntities);
        decorator.decorateRooms(rooms);

        // addBuildingPaths();

        return buildings;
    }

    private ArrayList<Vector> buildingEntrances = new ArrayList<>();

    private void addRoomsToBuilding(Chunk building) {
        ArrayList<Chunk> chunks = getHallwayChunks(building);
        ArrayList<Room> roomChunks = splitChunksIntoRooms(chunks);
        rooms.addAll(roomChunks);

        int exteriorDoors = 0;

        for (Room room : roomChunks) {
            int windowsInRoom = 0;

            ArrayList<Vector> freeTiles = new ArrayList<>();

            // Check if room is aligned with exterior wall and add window

            if (room.x == building.x + 1) {
                // Place window on left wall
                int x = room.x - 1;
                int y = room.y + (room.height / 2);

                freeTiles.add(new Vector(x, y));
            }

            if (room.x + room.width == building.x + building.width - 1) {
                // Place window on right wall
                int x = room.x + room.width;
                int y = room.y + (room.height / 2);

                freeTiles.add(new Vector(x, y));
            }

            if (room.y == building.y + 1) {
                // Place window on bottom wall
                int x = room.x + (room.width / 2);
                int y = room.y - 1;

                freeTiles.add(new Vector(x, y));
            }

            if (room.y + room.height == building.y + building.height - 1) {
                // Place window on top wall
                int x = room.x + (room.width / 2);
                int y = room.y + room.height;

                freeTiles.add(new Vector(x, y));
            }

            for (Vector freeTile : freeTiles) {
                int x = freeTile.x;
                int y = freeTile.y;

                tiler.setTileset(ExteriorTileset.KEY);

                // All rooms with exterior access should have at least 1 window
                if (windowsInRoom < 1) {
                    setTerrain(new Vector(x, y), FLOOR, tiler.getFloorTile(x, y, currentRoomTheme));

                    String windowTex = getMatchingWindowTexture(x, y);

                    Component[] window = DecalFactory.createTransparentDecal(x, y, windowTex);
                    objectEntities[x][y].add(window[0].id);
                    componentManager.sortComponentArray(window);

                    windowsInRoom++;
                    continue;
                }

                // One of these rooms will be used as entrance to building
                if (exteriorDoors < 1) {
                    setTerrain(new Vector(x, y), FLOOR, tiler.getFloorTile(x, y, currentRoomTheme));

                    tiler.setTileset(BuildingTileset.KEY);
                    addJunction(freeTile);
                    buildingEntrances.add(freeTile);
                    exteriorDoors++;
                    continue;
                }

                // 50/50 chance that room will have 2 windows
                if (windowsInRoom < 2 && rng.coinflip()) {
                    setTerrain(new Vector(x, y), FLOOR, tiler.getFloorTile(x, y, currentRoomTheme));

                    Component[] window = DecalFactory.createTransparentDecal(x, y, ExteriorTileset.RED_BRICKS_WINDOW);
                    objectEntities[x][y].add(window[0].id);
                    componentManager.sortComponentArray(window);

                    windowsInRoom++;
                }
            }
        }

        tiler.setTileset(BuildingTileset.KEY);

        // Use MazeGenerator to add corridors to building
        mazeGenerator.setChunk(building);

        for (Room room : rooms) {
            mazeGenerator.carveChunkFromMaze(room);
        }

        boolean[][] carvedTiles = mazeGenerator.generate();

        for (int x = 0; x < building.width; x++) {
            for (int y = 0; y < building.height; y++) {
                if (carvedTiles[x][y]) {
                    Vector translatedCell = new Vector(building.x + x, building.y + y);
                    setTerrain(translatedCell, FLOOR, BuildingTileset.WOOD_FLOOR_1);
                }
            }
        }

        for (Vector cell : mazeGenerator.getJunctions()) {
            addJunction(new Vector(building.x + cell.x, building.y + cell.y));
        }
    }

    private void addBuildingPaths() {
        // Find paths between each building entrance and link together

        if (buildingEntrances.size() > 1) {

            for (int i = 0; i < buildingEntrances.size(); i++) {
                Vector a = buildingEntrances.get(i);
                Vector b;

                if (i < buildingEntrances.size() - 1) {
                    b = buildingEntrances.get(i + 1);
                }
                else {
                    b = buildingEntrances.get(0);
                }

                for (Vector path : buildPath(a, b, blockedTiles)) {
                    String texture = ExteriorTileset.DIRT_PATH[rng.getRandomInt(0, ExteriorTileset.DIRT_PATH.length - 1)];
                    setTerrain(path, FLOOR, texture);

                    // Other entities occupying this tile can be safely removed
                    clearObjects(path.x, path.y);
                }

            }
        }
    }

    public String getMatchingWindowTexture(int x, int y) {
        Sprite sprite = (Sprite) componentManager.getEntityComponent(terrainEntities[x][y], Sprite.class.getSimpleName());
        String window = ExteriorTileset.getWindowForWallTile(sprite.path);
        if (window != null) {
            return window;
        }

        Collection<Vector> adjacent = getAdjacentCells(new Vector(x, y), 1, false).values();

        for (Vector cell : adjacent) {
            sprite = (Sprite) componentManager.getEntityComponent(terrainEntities[cell.x][cell.y], Sprite.class.getSimpleName());
            window = ExteriorTileset.getWindowForWallTile(sprite.path);
            if (window != null) {
                return window;
            }
        }

        return ExteriorTileset.GREY_BRICK_RUBBLE;
    }

    /*
    ------------------------------------------------------------------------------------------
    Building generation
    ------------------------------------------------------------------------------------------
*/

    /**
     * Builds brick wall around edges of chunk and fills with wall tiles.
     * Warning: this will also erase any objects inside chunk.
     *
     * @param chunk Chunk to build on
     */

    private void carveBuildingFoundation(Chunk chunk) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
                clearObjects(x, y);
            }
        }

        int brickIndex = 0;
        String[] brickTiles = ExteriorTileset.WALLS[rng.getRandomInt(0, ExteriorTileset.WALLS.length - 1)];

        // First, carve the inside
        for (int x = chunk.x + 1; x < chunk.x + chunk.width - 1; x++) {
            for (int y = chunk.y + 1; y < chunk.y + chunk.height - 1; y++) {
                setTerrain(new Vector(x, y), WALL, BuildingTileset.WOOD_WALL);
            }
        }
        
        // Then carve the walls
        
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            Vector bottom = new Vector(x, chunk.y);

            setTerrain(bottom, BORDER, brickTiles[brickIndex]);

            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }
        }

        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            Vector top = new Vector(x, chunk.y + chunk.height - 1);
            setTerrain(top, BORDER, brickTiles[brickIndex]);

            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }
        }

        for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
            Vector left = new Vector(chunk.x, y);
            setTerrain(left, BORDER, brickTiles[brickIndex]);
            
            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }
        }

        for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
            Vector right = new Vector(chunk.x + chunk.width - 1, y);
            setTerrain(right, BORDER, brickTiles[brickIndex]);

            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }
        }
    }

    private void createDamagedWalls(Chunk chunk) {
        Sprite wall = (Sprite) componentManager.getEntityComponent(terrainEntities[chunk.x][chunk.y], Sprite.class.getSimpleName());
        String[] damagedWall = ExteriorTileset.getDamagedWallTiles(wall.path);

        // Ignore corner tiles when placing damaged walls

        for (int x = chunk.x + 1; x < chunk.x + chunk.width - 1; x++) {
            Vector bottom = new Vector(x, chunk.y);

            if (rng.d6(1) == 1) {
                addObject(bottom, DecalFactory.createTransparentDecal(bottom.x, bottom.y, rng.getRandomItemFromStringArray(damagedWall)), true);
                copyTerrain(bottom.add(new Vector(0, 1)), bottom);

                // if we modify a tile, skip ahead to prevent from modifying consecutive tiles
                x++;
            }
        }

        for (int x = chunk.x + 1; x < chunk.x + chunk.width - 1; x++) {
            Vector top = new Vector(x, chunk.y + chunk.height - 1);
            if (rng.d6(1) == 1) {
                addObject(top, DecalFactory.createTransparentDecal(top.x, top.y, rng.getRandomItemFromStringArray(damagedWall)), true);
                copyTerrain(top.add(new Vector(0, 1)), top);
                x++;
            }
        }

        // Todo: figure out best way to add ruined walls to vertical sides

        /*for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
            Vector left = new Vector(chunk.x, y);
            if (rng.d6(1) == 1) {
                addObject(left, DecalFactory.createTransparentDecal(left.x, left.y, rng.getRandomItemFromStringArray(ExteriorTileset.RUBBLE)), true);
                copyTerrain(left.add(new Vector(0, 1)), left);
                y++;
            }
        }

        for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
            Vector right = new Vector(chunk.x + chunk.width - 1, y);
            if (rng.d6(1) == 1) {
                addObject(right, DecalFactory.createTransparentDecal(right.x, right.y, rng.getRandomItemFromStringArray(ExteriorTileset.RUBBLE)), true);
                y++;
                copyTerrain(right.add(new Vector(0, 1)), right);
            }
        }*/
    }

    private void generateRandomRooms(Chunk chunk, int roomDensity) {
        for (int i = 0; i < roomDensity; i++) {
            Room newRoom = generateRandomRoom(chunk);
            if (newRoom != null) {
                rooms.add(newRoom);
            }
        }
    }

    private void generateRandomRooms(Chunk chunk) {
        generateRandomRooms(chunk, roomDensity);
    }

    private Room generateRandomRoom(Chunk chunk) {
        int width = rng.getRandomInt(minRoomWidth, maxRoomWidth);
        int height = rng.getRandomInt(minRoomHeight, maxRoomHeight);
        int x = rng.getRandomInt(chunk.x + 1, chunk.x + chunk.width - width - 2);
        int y = rng.getRandomInt(chunk.y + 1, chunk.y + chunk.height - height - 2);

        Room newRoom = new Room(x, y, width, height);

        // If room is colliding with any existing rooms, return null.
        // Otherwise, return newly generated room

        // Todo: this is really inefficient. But probably not a big deal
        for (Room room : rooms) {
            if (AxisAlignedBoxTester.test(room, newRoom)) {
                return null;
            }
        }

        return newRoom;
    }

    /**
     * Generates random building inside target, using given parameters to define its potential size.
     * If the parameters exceed the size of the chunk, this method will automatically trim
     * the generated building
     */

    private Chunk generateRandomBuilding(Chunk target, int minWidth, int maxWidth, int minHeight, int maxHeight) {
        int width = rng.getRandomInt(minWidth, maxWidth);
        int height = rng.getRandomInt(minHeight, maxHeight);

        // Shrink dimensions if generated chunk would never fit inside target
        if (width > target.width - 4) {
            width = target.width - 4;
        }

        if (height > target.height - 4) {
            height = target.height - 4;
        }

        // Remember to account for width/height of generated chunk when placing in target area
        int xLimit = target.x + target.width - 2 - width;
        int yLimit = target.y + target.height - 2 - height;

        int x, y;

        if (target.x + 2 >= xLimit) {
            x = target.x + 2;
        } else {
            x = rng.getRandomInt(target.x + 2, xLimit);
        }

        if (target.y + 2 >= yLimit) {
            y = target.y + 2;
        } else {
            y = rng.getRandomInt(target.y + 2, yLimit);
        }

        return new Chunk(x, y, width, height);
    }

    private void carveRooms() {
        for (Room room : rooms) {
            startRegion();
            currentRoomTheme = new RandomNumberGenerator().getRandomInt(0, 3);
            retextureWalls(room);
            currentRoomTheme = new RandomNumberGenerator().getRandomInt(0, 3);
            carveRoomFloor(room);
        }
    }

    private void retextureWalls(Room room) {
        int right = room.x() + room.width() + 1;
        int top = room.y() + room.height();

        String themedTile = tiler.getMansionWallTilePath(currentRoomTheme);

        for (int x = room.x() - 1; x <= right; x++) {
            Vector north = new Vector(x, top);
            Vector south = new Vector(x, room.y() - 1);

            if (!adjacentCellsAreCarvable(north) || !adjacentCellsAreCarvable(south)) break;

            if (inBounds(north)) {
                Component[] wall = TerrainFactory.createWall(north.x, north.y, themedTile);
                terrainEntities[north.x][north.y] = wall[0].id;
                componentManager.sortComponentArray(wall);
            }

            if (inBounds(south)) {
                Component[] wall = TerrainFactory.createWall(south.x, south.y, themedTile);
                terrainEntities[south.x][south.y] = wall[0].id;
                componentManager.sortComponentArray(wall);
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector east = new Vector(room.x() - 1, y);
            Vector west = new Vector(room.x() + room.width(), y);

            if (!adjacentCellsAreCarvable(east) || !adjacentCellsAreCarvable(west)) break;

            if (inBounds(east)) {
                Component[] wall = TerrainFactory.createWall(east.x, east.y, themedTile);
                terrainEntities[east.x][east.y] = wall[0].id;
                componentManager.sortComponentArray(wall);
            }

            if (inBounds(west)) {
                Component[] wall = TerrainFactory.createWall(west.x, west.y, themedTile);
                terrainEntities[west.x][west.y] = wall[0].id;
                componentManager.sortComponentArray(wall);
            }
        }
    }

    private void carveRoomFloor(Room room) {
        int right = room.x() + room.width();
        int bottom = room.y() + room.height();

        int carved = 0;

        for (int x = room.x(); x < right; x++) {
            for (int y = room.y(); y < bottom; y++) {
                setTerrain(new Vector(x, y), FLOOR, tiler.getFloorTile(x, y, currentRoomTheme));
                carved++;
            }
        }

        if (carved == 0) {
            Log.d(LOG_TAG, "Couldn't carve chunk: " + room.toString());
        }
    }

    /**
     *  Prevents issue where doors are sometimes placed over wall tiles.
     *  Should really figure out the cause of this... oh well
     */

    private void checkForBrokenDoors() {
        Iterator it = doors.values().iterator();

        while (it.hasNext()) {
            Component[] door = (Component[]) it.next();
            Position pComp = ComponentFinder.getPositionComponent(door);
            Vector position = new Vector(pComp.x, pComp.y);

            long entity = terrainEntities[position.x][position.y];
            Terrain stat = (Terrain) componentManager.getEntityComponent(entity, Terrain.class.getSimpleName());

            if (stat.type == Terrain.WALL) {
                it.remove();
            }
        }
    }

    private void calculateGoals() {
        if (rooms.size() < 2) {
            floorEntrance = new Vector(1, 1);
            floorExit = new Vector(3, 3);
        }
        else {
            Room startRoom = rooms.get(rng.getRandomInt(0, rooms.size() - 1));

            int count = 0;
            int roomCount = rooms.size();

            // Make sure that starting room is accessible
            while (!startRoom.isAccessible && count < roomCount) {
                startRoom = rooms.get(rng.getRandomInt(0, rooms.size() - 1));
                count++;
            }

            if (!startRoom.isAccessible) {
                // Todo: to handle this error, we should regenerate the terrain
                throw new Error("Start room was inaccessible!");
            }

            startRoom.setEntrance();
            floorEntrance = startRoom.roundedCentre();

            Component[] entrance = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "entranceStairs");
            Position position = ComponentFinder.getPositionComponent(entrance);
            position.x = floorEntrance.x;
            position.y = floorEntrance.y;
            Portal portal = ComponentFinder.getPortalComponent(entrance);
            portal.destFloor = currentFloor - 1;

            objectEntities[position.x][position.y].add(position.id);
            componentManager.sortComponentArray(entrance);

            int furthest = 0;
            Vector furthestRoomCentre = null;

            for (Room room : rooms) {
                Vector centre = room.roundedCentre();
                ArrayList<Vector> path = findShortestPath(floorEntrance, centre);
                int distance = path.size();
                if (distance > furthest) {
                    furthestRoomCentre = centre;
                    furthest = distance;
                }
            }

            if (furthestRoomCentre != null) {
                Component[] exit = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "exitStairs");

                position = ComponentFinder.getPositionComponent(exit);
                position.x = furthestRoomCentre.x;
                position.y = furthestRoomCentre.y;
                portal = ComponentFinder.getPortalComponent(exit);
                portal.destFloor = currentFloor + 1;

                floorExit = furthestRoomCentre;

                objectEntities[position.x][position.y].add(position.id);
                componentManager.sortComponentArray(exit);

                Log.v(LOG_TAG, "path from start to finish was " + furthest + " moves");
            } else {
                Log.e(LOG_TAG, "Couldn't find room to place exit");
            }
        }
    }

    private ArrayList<Vector> findShortestPath(Vector startNode, Vector goalNode) {
        return findShortestPath(startNode, goalNode, Directions.All.values(), new boolean[mapWidth][mapHeight]);
    }

    private ArrayList<Vector> buildPath(Vector startNode, Vector goalNode, boolean[][] exclusions) {
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
                Log.d(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector direction : Directions.Cardinal.values()) {
                Vector adjacentNode = currentNode.add(direction);

                // Avoid checking nodes that are out of bounds, nodes that have already been checked,
                // nodes in excluded array and nodes that contain non-traversable and indestructable entities

                if (!inBounds(adjacentNode)) continue;
                if (checkedNodes.contains(adjacentNode.toString())) continue;
                if (exclusions[adjacentNode.x][adjacentNode.y]) continue;
                if (!canBuildPath(adjacentNode)) continue;

                double distanceToGoal = Calculator.getDistance(adjacentNode, goalNode);

                if (distanceToGoal < bestDistance) {
                    bestDistance = distanceToGoal;
                    closestNode = adjacentNode;
                }
            }

            if (closestNode != null) {
                if (closestNode.equals(goalNode)) {
                    return optimalPath;
                }
                else {
                    checkedNodes.add(currentNode.toString());
                    lastNode = currentNode;
                    optimalPath.add(closestNode);
                    openNodes.add(closestNode);
                }
            }

            else {
                if (lastNode != null && lastNode.equals(goalNode)) {
                    return optimalPath;
                }
                else {
                    Log.e(LOG_TAG, "Couldn't find path between nodes: " + startNode.toString() + " to " + goalNode.toString() + " (Closest and last nodes == null) ");
                    return new ArrayList<>();
                }
            }
        }

        if (lastNode != null && !lastNode.equals(goalNode)) {
            Log.e(LOG_TAG, "Couldn't find path between nodes: " + startNode.toString() + " to " + goalNode.toString() + " (finished iterating - didn't reach goal)");
            return new ArrayList<>();
        }

        else {
            return optimalPath;
        }
    }

    private ArrayList<Vector> findShortestPath(Vector startNode, Vector goalNode, Collection<Vector> directions, boolean[][] exclusions) {
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
                Log.d(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector direction : directions) {
                Vector adjacentNode = currentNode.add(direction);

                if (!inBounds(adjacentNode)) continue;

                if (checkedNodes.contains(adjacentNode.toString())) continue;

                if (detectCollisions(adjacentNode)) continue;

                if (exclusions[adjacentNode.x][adjacentNode.y]) continue;

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

        if (lastNode == null || !lastNode.equals(goalNode)) {
            Log.e(LOG_TAG, "Couldn't find path between nodes: " + startNode.toString() + " to " + goalNode.toString());
            return new ArrayList<>();
        }

        else {
            return optimalPath;
        }
    }

    private boolean detectCollisions(Vector position) {
        int x = position.x();
        int y = position.y();

        long terrainEntity = terrainEntities[x][y];
        Physics physics = (Physics) componentManager.getEntityComponent(terrainEntity, Physics.class.getSimpleName());

        if (physics.isBlocking || !physics.isTraversable) {
            return true;
        }

        ArrayList<Long> objectStack = objectEntities[x][y];

        if (objectStack == null || objectStack.size() == 0) {
            return false;
        }

        for (Long entity : objectStack) {

            physics = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());

            if (physics.isBlocking || !physics.isTraversable) {
                return true;
            }
        }

        return false;
    }

    private boolean canBuildPath(Vector position) {
        int x = position.x();
        int y = position.y();

        long terrainEntity = terrainEntities[x][y];
        Physics physics = (Physics) componentManager.getEntityComponent(terrainEntity, Physics.class.getSimpleName());

        if (!physics.isTraversable && !physics.isDestructable) {
            return false;
        }

        ArrayList<Long> objectStack = objectEntities[x][y];

        if (objectStack == null || objectStack.size() == 0) {
            // At this point, we have already determined that terrain entity can be built over,
            // and there are no objects to check
            return true;
        }

        for (Long entity : objectStack) {
            physics = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());

            if (!physics.isTraversable && !physics.isDestructable) {
                return false;
            }
        }

        return true;
    }

/*
    ------------------------------------------------------------------------------------------
    Cavern generation

    Uses cellular automata to generate a cave-like structure in the chunk provided to
    generateCaverns(). The result can be tweaked using various parameters, such as
    numberOfSmoothingSteps (creates a more rounded cavern) and chanceToStartAlive (creates a
    messier cavern).
    ------------------------------------------------------------------------------------------
*/

    private void generateCaverns(Chunk chunk) {
        CellularAutomata automata = new CellularAutomata();
        boolean[][] cavern = automata.generate(chunk);

        for (int x = 1; x < chunk.width - 1; x++) {
            for (int y = 1; y < chunk.height - 1; y++) {
                if (cavern[x][y]) {
                    // Note: as cell map origin is 0,0, we have to translate coordinates
                    setTerrain(new Vector(x + chunk.x, y + chunk.y), FLOOR, RuinsTileset.FLOOR);
                }
            }
        }
    }

/*
    ---------------------------------------------
    Corridor generation
    ---------------------------------------------
*/

    /**
     * Sets terrain at cell to doorway tile and adds door to component manager.
     * We also have to update the position component and switch the sprite to a non-default one
     *
     * @param cell Grid position to place door
     */

    private void addJunction(Vector cell) {
        setTerrain(cell, DOORWAY, BuildingTileset.WOOD_FLOOR_1);

        Component[] door = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "door");

        if (door == null) {
            Log.e(LOG_TAG, "Error when creating door");
            return;
        }

        objectEntities[cell.x][cell.y].add(door[0].id);
        componentManager.sortComponentArray(door);

        Position position = (Position) componentManager.getEntityComponent(door[0].id, Position.class.getSimpleName());
        position.x = cell.x;
        position.y = cell.y;
        Sprite sprite = (Sprite) componentManager.getEntityComponent(door[0].id, Sprite.class.getSimpleName());
        sprite.path = tiler.getClosedDoorTilePath();

        doors.put(cell.toString(), door);
    }

    private void startRegion() {
        currentRegion++;
    }

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < mapWidth && cell.y() >= 0 && cell.y() < mapHeight);
    }

    private void removeInaccessibleCells() {
        HashMap<String, Boolean> checked = new HashMap<>();

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.containsKey(cell.toString())) {
                    long entity = terrainEntities[cell.x][cell.y];
                    Terrain stat = (Terrain) componentManager.getEntityComponent(entity, Terrain.class.getSimpleName());

                    if (stat == null) continue;

                    if (stat.type == Terrain.FLOOR && cellIsInaccessible(cell)) {
                        setTerrain(cell, WALL, BuildingTileset.WALL);
                        checked.put(cell.toString(), true);
                        HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, true);
                        for (Vector adjacentCell : adjacentCells.values()) {
                            checked.put(adjacentCell.toString(), true);
                        }
                    }
                }
            }
        }
    }

    /**
     *  Removes any walls which are not visible to player (ie. surrounded by other walls)
     *  This is mainly for aesthetic reasons
     */

    private void removeHiddenWalls() {
        Set<String> checked = new HashSet<>();

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.contains(cell.toString())) {

                    long entity = terrainEntities[cell.x][cell.y];
                    Terrain stat = (Terrain) componentManager.getEntityComponent(entity, Terrain.class.getSimpleName());

                    if (stat == null) continue;

                    if (stat.type == Terrain.WALL && cellIsInaccessible(cell)) {
                        setTerrain(cell, BORDER, GenericTileset.TRANSPARENT);
                        checked.add(cell.toString());
                    }
                }
            }
        }
    }

/*
    ---------------------------------------------
    Methods for setting/getting tiles
    ---------------------------------------------
*/

    private final int FLOOR = 0;
    private final int WALL = 1;
    private final int DOORWAY = 2;
    private final int BORDER = 3;
    private final int BACKGROUND = 4;
    private final int OBJECT = 5;

    private void setTerrain(Vector cell, int type, String texture) {
        // Make sure that we remove any existing components before adding new terrain tile
        componentManager.removeEntityComponents(terrainEntities[cell.x][cell.y]);

        Component[] tile;

        switch (type) {
            case FLOOR:
                tile = TerrainFactory.createFloor(cell.x, cell.y, texture);
                terrainEntities[cell.x][cell.y] = tile[0].id;
                break;

            case WALL:
                tile = TerrainFactory.createWall(cell.x, cell.y, texture);
                terrainEntities[cell.x][cell.y] = tile[0].id;
                break;

            case DOORWAY:
                tile = TerrainFactory.createDoorway(cell.x, cell.y, texture);
                terrainEntities[cell.x][cell.y] = tile[0].id;
                break;

            case BORDER:
                tile = TerrainFactory.createBorder(cell.x, cell.y, texture);
                terrainEntities[cell.x][cell.y] = tile[0].id;
                break;

            case BACKGROUND:
                tile = TerrainFactory.createBackground(cell.x, cell.y, texture);
                terrainEntities[cell.x][cell.y] = tile[0].id;
                break;

            default:
                Log.w(LOG_TAG, "Couldn't find specified type (" + type + "), setting as floor tile ");
                tile = TerrainFactory.createFloor(cell.x, cell.y, texture);
        }

        componentManager.sortComponentArray(tile);
    }

    private void setTerrain(Vector cell, int type, Component[] tile) {
        // Make sure that we remove any existing components before adding new terrain tile
        componentManager.removeEntityComponents(terrainEntities[cell.x][cell.y]);
        terrainEntities[cell.x][cell.y] = tile[0].id;
        componentManager.sortComponentArray(tile);
    }
    
    private void copyTerrain(Vector from, Vector to) {
        long targetEntity = terrainEntities[from.x][from.y];
        Sprite target = (Sprite) componentManager.getEntityComponent(targetEntity, Sprite.class.getSimpleName());
        Terrain targetType = (Terrain) componentManager.getEntityComponent(targetEntity, Terrain.class.getSimpleName());
        
        switch (targetType.type) {
            case Terrain.FLOOR:
                setTerrain(to, FLOOR, target.path);
                break;
                
            case Terrain.WALL:
                setTerrain(to, WALL, target.path);
                break;
                
            case Terrain.BORDER:
                setTerrain(to, BORDER, target.path);
                break;
                
            case Terrain.DOORWAY:
                setTerrain(to, DOORWAY, target.path);
                break;
                
            case Terrain.DEFAULT:
                setTerrain(to, FLOOR, target.path);
                break;
        }
    }
    
    private void addObject(Vector cell, Component[] tile, boolean replace) {
        if (replace) {
            clearObjects(cell.x, cell.y);
        }
        objectEntities[cell.x][cell.y].add(tile[0].id);
        componentManager.sortComponentArray(tile);
    }

    private void clearObjects(int x, int y) {
        for (long entity : objectEntities[x][y]) {
            componentManager.removeEntityComponents(entity);
        }

        objectEntities[x][y].clear();
    }

    private void addBorderToChunk(Chunk chunk, int type, String[] borderTiles) {
        for (int x = chunk.x; x < chunk.x + chunk.width - 1; x++) {
            setTerrain(new Vector(x, chunk.y + chunk.height - 1), type, borderTiles[NORTH]);
        }

        setTerrain(new Vector(chunk.x + chunk.width - 1, chunk.y + chunk.height - 1), type, borderTiles[NORTH_EAST]);

        for (int y = chunk.y + chunk.height - 2; y > chunk.y; y--) {
            setTerrain(new Vector(chunk.x + chunk.width - 1, y), type, borderTiles[EAST]);
        }

        setTerrain(new Vector(chunk.x + chunk.width - 1, chunk.y), type, borderTiles[SOUTH_EAST]);

        for (int x = chunk.x + chunk.width - 2; x > chunk.x; x--) {
            setTerrain(new Vector(x, chunk.y), type, borderTiles[SOUTH]);
        }

        setTerrain(new Vector(chunk.x, chunk.y), type, borderTiles[SOUTH_WEST]);

        for (int y = chunk.y + 1; y < chunk.y + chunk.height - 1; y++) {
            setTerrain(new Vector(chunk.x, y), type, borderTiles[WEST]);
        }

        setTerrain(new Vector(chunk.x, chunk.y + chunk.height - 1), type, borderTiles[NORTH_WEST]);
    }

    private void addBorderObjectToChunk(Chunk chunk, String[] borderTiles) {
        for (int x = chunk.x; x < chunk.x + chunk.width - 1; x++) {
            Vector position = new Vector(x, chunk.y + chunk.height - 1);
            addObject(position, DecalFactory.createFovBlockingDecal(position.x, position.y, borderTiles[NORTH]), true);
        }

        Vector ne = new Vector(chunk.x + chunk.width - 1, chunk.y + chunk.height - 1);
        addObject(ne, DecalFactory.createFovBlockingDecal(ne.x, ne.y, borderTiles[NORTH_EAST]), true);

        for (int y = chunk.y + chunk.height - 2; y > chunk.y; y--) {
            Vector position = new Vector(chunk.x + chunk.width - 1, y);
            addObject(position, DecalFactory.createFovBlockingDecal(position.x, position.y, borderTiles[EAST]), true);
        }

        Vector se = new Vector(chunk.x + chunk.width - 1, chunk.y);
        addObject(se, DecalFactory.createFovBlockingDecal(se.x, se.y, borderTiles[SOUTH_EAST]), true);

        for (int x = chunk.x + chunk.width - 2; x > chunk.x; x--) {
            Vector position = new Vector(x, chunk.y);
            addObject(position, DecalFactory.createFovBlockingDecal(position.x, position.y, borderTiles[SOUTH]), true);
        }

        Vector sw = new Vector(chunk.x, chunk.y);
        addObject(sw, DecalFactory.createFovBlockingDecal(sw.x, sw.y, borderTiles[SOUTH_WEST]), true);

        for (int y = chunk.y + 1; y < chunk.y + chunk.height - 1; y++) {
            Vector position = new Vector(chunk.x, y);
            addObject(position, DecalFactory.createFovBlockingDecal(position.x, position.y, borderTiles[WEST]), true);
        }

        Vector nw = new Vector(chunk.x, chunk.y + chunk.height - 1);
        addObject(nw, DecalFactory.createFovBlockingDecal(nw.x, nw.y, borderTiles[NORTH_WEST]), true);
    }

    private void addBorderObjectToChunk(Chunk chunk, String tile) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {

                if (x == chunk.x || x == chunk.x + chunk.width - 1 || y == chunk.y || y == chunk.y + chunk.height - 1) {
                    addObject(new Vector(x, y), DecalFactory.createFovBlockingDecal(x, y, tile), true);
                }
            }
        }
    }

    private void addBorderToChunk(Chunk chunk, int type, String tile) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {

                if (x == chunk.x || x == chunk.x + chunk.width - 1 || y == chunk.y || y == chunk.y + chunk.height - 1) {
                    setTerrain(new Vector(x, y), type, tile);
                }
            }
        }
    }

    private void clearObjectsFromBorder(Chunk chunk) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {

                if (x == chunk.x || x == chunk.x + chunk.width - 1 || y == chunk.y || y == chunk.y + chunk.height - 1) {
                    clearObjects(x, y);
                }
            }
        }
    }


    /*
    ---------------------------------------------
    Helper methods
    ---------------------------------------------
    */

    private HashMap<String, Vector> getAdjacentCells(Vector coords, int lookahead, boolean directlyAdjacent) {
        int x = coords.x();
        int y = coords.y();

        HashMap<String, Vector> cells = new HashMap<>();

        cells.put("up", new Cell(x, y + lookahead, "up"));
        cells.put("right", new Cell(x + lookahead, y, "right"));
        cells.put("down", new Cell(x, y - lookahead, "down"));
        cells.put("left", new Cell(x - lookahead, y, "left"));

        if (directlyAdjacent) {
            return cells;
        }

        else {
            // Include diagonally adjacent cells
            cells.put("up-right", new Cell(x + lookahead, y + lookahead, "up-right"));
            cells.put("up-left", new Cell(x - lookahead, y + lookahead, "up-left"));
            cells.put("down-right", new Cell(x + lookahead, y - lookahead, "down-right"));
            cells.put("down-left", new Cell(x - lookahead, y - lookahead, "down-left"));
            return cells;
        }
    }

    /**
     * Adjacent cell is "carvable" if all adjacent cells are wall tiles (shader == Terrain.WALL)
     */

    private boolean adjacentCellsAreCarvable(Vector cell) {
        HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, false);

        String[] oppositeDirections = getOppositeWithDiagonals(cell.getDirection());

        // We only want to check squares in direction that maze is being carved in
        for (String direction : oppositeDirections) {
            if (adjacentCells.containsKey(direction)) {
                adjacentCells.remove(direction);
            }
        }

        for (Vector adjacent : adjacentCells.values()) {

            if (inBounds(adjacent)) {
                long entity = terrainEntities[adjacent.x][adjacent.y];
                Terrain stat = (Terrain) componentManager.getEntityComponent(entity, Terrain.class.getSimpleName());

                if (stat.type != Terrain.WALL) {
                    return false;
                }
            }

            else {
                return false;
            }
        }

        return true;
    }

    private boolean cellIsInaccessible(Vector cell) {
        for (Vector direction : Directions.All.values()) {
            direction = direction.add(cell);

            if (!inBounds(direction)) {
                continue;
            }

            long entity = terrainEntities[direction.x][direction.y];
            Terrain stat = (Terrain) componentManager.getEntityComponent(entity, Terrain.class.getSimpleName());

            if (stat.type != Terrain.WALL && stat.type != Terrain.BORDER) {
                return false;
            }
        }

        return true;
    }

    private String[] getOppositeWithDiagonals (String direction) {
        switch (direction) {
            case "up":
                return new String[] {"down-left", "down", "down-right"};

            case "right":
                return new String[] {"up-left", "left", "down-left"};

            case "down":
                return new String[] {"up-left", "up", "up-right"};

            case "left":
                return new String[] {"up-right", "right", "down-right"};

            default:
                return new String[] {};
        }
    }
}
