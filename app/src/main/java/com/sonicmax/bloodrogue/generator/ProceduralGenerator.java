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
import com.sonicmax.bloodrogue.generator.buildings.HouseWithYard;
import com.sonicmax.bloodrogue.generator.enemies.EnemyBlueprintKeys;
import com.sonicmax.bloodrogue.generator.enemies.EnemyPlacer;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.generator.tools.CellularAutomata;
import com.sonicmax.bloodrogue.generator.tools.GridGeometryHelper;
import com.sonicmax.bloodrogue.generator.tools.MazeGenerator;
import com.sonicmax.bloodrogue.generator.tools.PoissonDiskSampler;
import com.sonicmax.bloodrogue.generator.tools.SimplexNoiseGenerator;
import com.sonicmax.bloodrogue.tilesets.BuildingTileset;
import com.sonicmax.bloodrogue.tilesets.ExteriorTileset;
import com.sonicmax.bloodrogue.tilesets.RuinsTileset;
import com.sonicmax.bloodrogue.tilesets.TileCategorySorter;
import com.sonicmax.bloodrogue.utils.maths.GeometryHelper;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.generator.buildings.Room;
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
    private int maxRoadLimit = 16;
    private int minBlockSize = 16;

    // Random building generation defaults
    private int buildingDensity = 2000;
    private int minBuildingWidth = 15;
    private int maxBuildingWidth = 25;
    private int minBuildingHeight = 15;
    private int maxBuildingHeight = 25;

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
    private EnemyPlacer enemyPlacer;

    private boolean[][] blockedTiles;

    private String[][] terrainTiles;
    private ArrayList<Long>[][] objectEntities;
    private int currentFloor;
    private boolean[][] indoorRegions;
    private boolean[][] waterRegions;

    private final long DEBUG_SEED = 42L;

    private HashMap<String, Integer> spriteIndexes;

    public ProceduralGenerator(int width, int height, AssetManager assetManager) {
        this.mapWidth = width;
        this.mapHeight = height;

        // Setup grids used to store terrain and object entities. We will use these in conjunction
        // with component manager to detect collisions and other interactions when placing terrain/objects.
        this.terrainTiles = new String[width][height];
        this.objectEntities = Array2DHelper.create2dLongStack(width, height);

        this.assetManager = assetManager;
        this.furnitureBlueprints = JSONLoader.loadFurniture(assetManager);
        this.mazeGenerator = new MazeGenerator();
        this.automata = new CellularAutomata();
        this.enemyPlacer = new EnemyPlacer(objectEntities, assetManager);
        this.componentManager = ComponentManager.getInstance();

        // We should have emptied the component manager already, but just to be sure:
        this.componentManager.clear();

        this.doors = new HashMap<>();
        this.rng = new RandomNumberGenerator(DEBUG_SEED);
        this.currentFloor = 1;
    }

    public boolean[][] getIndoorRegions() {
        return indoorRegions;
    }

    public boolean[][] getWaterRegions() {
        return waterRegions;
    }

    public void setSpriteIndexes(HashMap<String, Integer> spriteIndexes) {
        this.spriteIndexes = spriteIndexes;
    }

	/*
		---------------------------------------------
		Initialisation
		---------------------------------------------
	*/

	private void initGrids() {
        mapRegions = Array2DHelper.fillIntArray(mapWidth, mapHeight, -1);
        indoorRegions = new boolean[mapWidth][mapHeight];
        waterRegions = new boolean[mapWidth][mapHeight];
    }

    public void setFloor(int floor) {
        this.currentFloor = floor;
    }

    public MapData getMapData() {
	    // Todo: this won't really work for saving purposes as sprite indexes will change whenever sprites are added to sprite folder
	    int[][] terrainIndices = Array2DHelper.fillIntArray(mapWidth, mapHeight, -1);

	    for (int x = 0; x < mapWidth; x++) {
	        for (int y = 0; y < mapHeight; y++) {
	            if (terrainTiles[x][y] != null) {
                    terrainIndices[x][y] = spriteIndexes.get(terrainTiles[x][y]);
                }
            }
        }

        return new MapData(terrainIndices, objectEntities, new Vector(0, 30), floorExit, type);
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
        decorator.setGeneratorData(terrainTiles, objectEntities);
        // decorator.decorateRooms(rooms);

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
        decorator.setGeneratorData(terrainTiles, objectEntities);
        // decorator.decorateRooms(rooms);
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

    private void generateExterior() {
        this.rooms = new ArrayList<>();
        setThemeAsMansion();
        this.tiler = new Tiler(themeKey);
        this.blockedTiles = new boolean[mapWidth][mapHeight];

        // The first stage of terrain generation is to add roads and place buildings and other landmarks.
        // We can afford to generate this for the whole map

        Chunk world = new Chunk(0, 0, mapWidth, mapHeight);

        addRoadsAndBuildingLocations(world);

        // The second stage of terrain generation is to add grass tiles and other vegetation, water regions,
        // enemies, etc. This is more costly and should only be generated for a section of the map at a time
        // Todo: 64x64 or 128x128?
        // Todo: accounting for z axis, maybe 32 * 32 * 8?

        Chunk startingChunk = new Chunk(0, 0, 128, 128);

        heightMap = generateIslandHeightMap(world);
        addWorldDetails(startingChunk);
        setElevationSpecificTerrain(startingChunk);

        checkForBrokenDoors();
        calculateGoals();
    }

    private void addRoadsAndBuildingLocations(Chunk chunk) {
        ArrayList<Chunk> buildings = new ArrayList<>();
        ArrayList<Chunk> streetBlocks = getStreetBlocks(chunk);

        for (Chunk block : streetBlocks) {
            // Add pavement to border of block
            addBorderToChunk(block, ExteriorTileset.SIDEWALK_BORDER);

            // Place buildings in each block
            if (block.width > 24 && block.height > 24) {
                addHousesToChunk(block);
            }
            else {
                addBuildingsToChunk(block);
            }
        }
    }

    private void addWorldDetails(Chunk chunk) {
        tiler.setTileset(ExteriorTileset.KEY);

        // Replace empty terrain tiles with random grass
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
                if (terrainTiles[x][y] == null) {
                    setTerrain(x, y, tiler.getFloorTile(theme));
                }
            }
        }

        placeRandomTreesInChunk(chunk);
        addForestRegions(chunk);
        addFloralRegions(chunk);
        addOutdoorMobs(chunk);
    }

    private float[][] heightMap;

    private float[][] generateIslandHeightMap(Chunk chunk) {
        /*
            Development notes:
            - Good results setting BE to 0.5, CH to 1.13 and DOF to 3.4, using Euclidian distance
         */

        // Todo: find range of acceptable values for these parameters.

        final float baseElevation = 0.5f;
        final float coastHeight = 1.13f;
        final float dropOffFactor = 3.4f;

        return generateIslandHeightMap(chunk, DEBUG_SEED, baseElevation, coastHeight, dropOffFactor);
    }

    /**
     * Generates height values for terrain mesh using simplex noise.
     * Heights generated using same seed will be identical, provided that
     * other parameters are also the same (chunk, baseElevation, coastHeight, and dropOffFactor).
     * If parameters differ, then terrain will likely be similar (but potentially not)
     *
     * @param chunk Map chunk to operate on
     * @param seed Seed for simplex noise generator
     * @return 2d float array containing heights for terrain mesh
     */

    private float[][] generateIslandHeightMap(Chunk chunk, long seed, float baseElevation, float coastHeight, float dropOffFactor) {
        // For terrain mesh we need to add 1 to grid width/height
        int width = chunk.width + 1;
        int height = chunk.height + 1;

        float[][] heightMap = new float[width][height];

        SimplexNoiseGenerator generator = new SimplexNoiseGenerator(seed);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float nx = (float) x / width - 0.5f;
                float ny = (float) y / height - 0.5f;

                // Generate elevation using three octaves of noise
                float noise = (1 * generator.noise2D(1 * nx, 1 * ny))
                        + (0.5f * generator.noise2D(2 * nx, 2 * ny))
                        + (0.25f * generator.noise2D(4 * nx, 2 * ny));

                // Convert simplex noise to be within range of 0 to 1
                // (add 1 to convert from -1,1 to 0,2, then halve to get 0,1)
                // noise = (noise + 1) / 2;

                float distance;

                // Use Manhattan distance from centre of map to determine coastline
                // distance = 2 * (Math.max(Math.abs(nx), Math.abs(ny)));

                // Use Euclidian distance from centre of map to determine coastline
                distance = 2 * (float) (Math.sqrt(nx * nx + ny * ny));

                // float elevation = (baseElevation + noise) * (1 - coastHeight * (float) Math.pow(distance, dropOffFactor));
                float elevation = (baseElevation + noise) - coastHeight * (float) Math.pow(distance, dropOffFactor);

                // Add 1 to convert from range of [-1,1] to [0, 2], then halve to get [0, 1]
                elevation = (elevation + 1) / 2;

                // For terracing?
                // elevation = Math.round(elevation * 4) / 4;

                heightMap[x][y] = elevation;
            }
        }

        return heightMap;
    }

    public float[][] getHeightMap() {
        return heightMap;
    }

    private void generateExterior2() {
        this.rooms = new ArrayList<>();
        setThemeAsMansion();
        this.tiler = new Tiler(themeKey);
        this.blockedTiles = new boolean[mapWidth][mapHeight];

        Chunk map = new Chunk(0, 0, mapWidth, mapHeight);
        Chunk mapWithoutBorders = new Chunk(1, 1, mapWidth - 2, mapHeight - 2);

        startRegion();

        // First, generate all the organic terrain (trees, flowers, lakes, etc) and add outdoor mobs
        placeRandomTreesInChunk(mapWithoutBorders);
        addForestRegions(mapWithoutBorders);
        addFloralRegions(mapWithoutBorders);
        addOutdoorMobs(mapWithoutBorders);
        addRandomLakes(mapWithoutBorders);

        // Now we can start to add man made terrain - roads, buildings, etc
        ArrayList<Chunk> buildings = new ArrayList<>();
        ArrayList<Chunk> streetBlocks = getStreetBlocks(map);

        for (Chunk block : streetBlocks) {
            // Add pavement to border of block
            addBorderToChunk(block, ExteriorTileset.SIDEWALK_BORDER);
            clearObjectsFromBorder(block);

            // Place buildings in each block
            if (block.width > 24 && block.height > 24) {
                addHousesToChunk(block);
            }
            else {
                addBuildingsToChunk(block);
            }
        }

        // removeHiddenWalls();
        checkForBrokenDoors();

        for (Chunk seed : debugSeedslol) {
            Component[] tree = DecalFactory.createDecal(seed.x, seed.y, GenericTileset.LIGHT_SOURCE);
            objectEntities[seed.x][seed.y].add(tree[0].id);
            componentManager.sortComponentArray(tree);
        }

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

    private ArrayList<Long> treeEntities = new ArrayList<>();

    public ArrayList<Long> getTreeEntities() {
        return treeEntities;
    }

    private void placeRandomTreesInChunk(Chunk chunk) {
        PoissonDiskSampler sampler = new PoissonDiskSampler();
        int minDistance = 3;
        // Roughly guess how many trees will fit, and reduce this amount by a percentage
        // (as we are also placing forest regions)
        double filter = 0.7;
        int treeCount = (mapWidth / minDistance) * (mapHeight / minDistance);
        treeCount *= filter;
        Log.v(LOG_TAG, "Placing " + treeCount + " trees");
        ArrayList<Vector> treePositions = sampler.generateNoise(chunk.width, chunk.height, minDistance, treeCount);
        for (Vector cell : treePositions) {
            if (!inBounds(cell)) continue;
            if (indoorRegions[cell.x][cell.y]) continue;

            Component[] tree = DecalFactory.createDecal(cell.x, cell.y, ExteriorTileset.TREES[rng.getRandomInt(0, ExteriorTileset.TREES.length - 1)]);
            treeEntities.add(tree[0].id);
            objectEntities[cell.x][cell.y].add(tree[0].id);
            componentManager.sortComponentArray(tree);
        }
    }

    /**
     * Sets terrain type based on elevation.
     * @param chunk Map chunk to process
     */

    private void setElevationSpecificTerrain(Chunk chunk) {
        // To add lakes we will use the height map to find regions that are below sea level

        final float SEA_LEVEL = 0.25f;
        final float GRASS_LEVEL = 0.4f;

        // TODO: handle smaller chunks that don't equal size of whole map

        for (int x = 0; x < chunk.width; x++) {
            for (int y = 0; y < chunk.height; y++) {
                // Todo: we want to prevent z-fighting, but should sample surrounding heights
                if (heightMap[x][y] == SEA_LEVEL) {
                    heightMap[x][y] -= 0.001f;
                }

                float elevation = heightMap[x][y];

                if (elevation < SEA_LEVEL) {
                    // Water is rendered as large quad, so we don't need to worry about that.
                    // We need to clear any objects and change the terrain to be sand

                    clearObjects(x, y);
                    setTerrain(x, y, ExteriorTileset.SAND_1);
                    waterRegions[x][y] = true;
                }

                else if (elevation > SEA_LEVEL && elevation <= GRASS_LEVEL) {
                    setTerrain(x, y, ExteriorTileset.SAND_1);
                }
            }
        }
    }

    private void addRandomLakes(Chunk chunk) {
        Vector offset = new Vector(chunk.x, chunk.y);

        automata.setParams(4, 3, 3, 0.3f);
        boolean[][] lakes = automata.generate(chunk);

        ArrayList<Vector> lakeVectors = new ArrayList<>();

        for (int x = 0; x < lakes.length; x++) {
            for (int y = 0; y < lakes[0].length; y++) {
                Vector vector = new Vector(x, y);
                // Remember to translate position on grid to position in world
                Vector translatedVec = vector.subtract(offset);
                if (lakes[x][y] && !detectCollisions(translatedVec)) {
                    lakeVectors.add(vector);
                }
            }
        }

        // Now we should locate adjacent lake tiles and separate into regions

        if (lakeVectors.size() == 0) {
            return;
        }

        ArrayList<MapRegion> lakeRegions = new ArrayList<>();
        ArrayList<Vector> checkedVectors = new ArrayList<>();

        for (Vector vector : lakeVectors) {

            for (Vector direction : Directions.All.values()) {
                Vector adjacent = vector.add(direction);

                if (checkedVectors.contains(adjacent)) {
                    continue;
                }

                if (inBounds(adjacent, 0, 0, lakes.length, lakes[0].length)) {
                    if (lakes[adjacent.x][adjacent.y]) {
                        MapRegion region = new MapRegion();
                        ArrayList<Vector> regionVecs = getRegionVectors(adjacent, lakes);
                        region.addAll(regionVecs);
                        checkedVectors.addAll(regionVecs);
                        lakeRegions.add(region);
                    }
                    else {
                        checkedVectors.add(adjacent);
                    }
                }
                else {
                    checkedVectors.add(adjacent);
                }
            }
        }

        // Find lake regions that are big enough, fill with water and add any special terrain/mobs

        Iterator<MapRegion> it = lakeRegions.iterator();

        while (it.hasNext()) {
            MapRegion region = it.next();
            if (region.getVectors().size() > 2) {
                fillLakeRegion(region, offset);
                addLakeDecorations(region, offset);
            }
            else {
                it.remove();
            }
        }
    }

    private ArrayList<Vector> translateVectors(ArrayList<Vector> vectors, Vector offset) {
        ArrayList<Vector> translated = new ArrayList<>();
        for (Vector vector : vectors) {
            translated.add(vector.subtract(offset));
        }
        return translated;
    }

    private void fillLakeRegion(MapRegion lake, Vector offset) {
        int waterTextureIndex = 0;

        for (Vector vector : lake.getVectors()) {
            if (indoorRegions[vector.x][vector.y]) continue;

            Vector translatedVec = vector.subtract(offset);
            clearObjects(translatedVec.x, translatedVec.y);

            String texture = ExteriorTileset.WATER[waterTextureIndex];

            // Water texture array is designed to be iterated over in this order to align the tiles
            // in a certain way (to try and create a semi random water texture)

            waterTextureIndex++;

            if (waterTextureIndex > ExteriorTileset.WATER.length - 1) {
                waterTextureIndex = 0;
            }

            setTerrain(translatedVec.x, translatedVec.y, texture);

            waterRegions[translatedVec.x][translatedVec.y] = true;
        }
    }

    private void addLakeDecorations(MapRegion lake, Vector offset) {
        ArrayList<Vector> lakeVectors = lake.getVectors();
        if (lakeVectors.size() > 5) {
            int random = rng.getRandomInt(0, lakeVectors.size() - 1);
            Vector location = lakeVectors.get(random).subtract(offset);
            String texture = rng.getRandomItemFromStringArray(ExteriorTileset.POND_LILIES);
            Component[] flower = DecalFactory.createTraversableDecoration(location.x, location.y, texture);
            objectEntities[location.x][location.y].add(flower[0].id);
            componentManager.sortComponentArray(flower);
        }

        ArrayList<Vector> borderVectors = GridGeometryHelper.getBorderVectorsFromRegion(lake);

        if (borderVectors.size() > 0) {
            int random = rng.getRandomInt(0, borderVectors.size() - 1);
            Vector location = borderVectors.get(random).subtract(offset);
            if (inBounds(location) && !detectCollisions(location)) {
                enemyPlacer.placeEnemy(location.x, location.y, EnemyBlueprintKeys.TOAD);
            }
        }
    }

    private ArrayList<Vector> getRegionVectors(Vector start, boolean[][] tiles) {
        ArrayList<Vector> region = new ArrayList<>();
        ArrayList<Vector> queue = new ArrayList<>();
        ArrayList<Vector> checked = new ArrayList<>();

        queue.add(start);
        region.add(start);

        while (queue.size() > 0) {
            Vector vector = queue.remove(0);

            if (checked.contains(vector)) continue;

            for (Vector direction : Directions.Cardinal.values()) {
                Vector adjacent = vector.add(direction);

                if (checked.contains(adjacent)) continue;

                if (inBounds(adjacent, 0, 0, tiles.length, tiles[0].length) && tiles[adjacent.x][adjacent.y]) {
                    region.add(adjacent);
                    queue.add(adjacent);
                }
                else {
                    checked.add(adjacent);
                }
            }

            checked.add(vector);
        }

        return region;
    }

    private boolean chunkContainsVector(Vector vector, Chunk chunk) {
        return (vector.x >= chunk.x && vector.x < chunk.x + chunk.width)
                && (vector.y >= chunk.y && vector.y < chunk.y + chunk.height);
    }

    private boolean inBounds(Vector vector, int x, int y, int width, int height) {
        return (vector.x >= x && vector.x < x + width)
                && (vector.y >= y && vector.y < y + height);
    }

    private void addForestRegions(Chunk chunk) {
        automata.setParams(4, 3, 2, 0.3f);
        boolean[][] forest = automata.generate(chunk);


        for (int x = 0; x < forest.length; x++) {
            for (int y = 0; y < forest[0].length; y++) {
                if (forest[x][y] && !indoorRegions[x][y] && !detectCollisions(new Vector(x, y))) {

                    Component[] tree = DecalFactory.createFovBlockingDecal(x, y, ExteriorTileset.TREES[rng.getRandomInt(0, ExteriorTileset.TREES.length - 1)]);
                    treeEntities.add(tree[0].id);
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
                if (flowers[x][y] && !indoorRegions[x][y] && !detectCollisions(new Vector(x, y))) {
                    String texture = ExteriorTileset.DECALS[rng.getRandomInt(0, ExteriorTileset.DECALS.length - 1)];
                    Component[] flower = DecalFactory.createTraversableDecoration(x, y, texture);
                    objectEntities[x][y].add(flower[0].id);
                    componentManager.sortComponentArray(flower);
                }
            }
        }
    }

    private void addOutdoorMobs(Chunk chunk) {
        // Add herds of bad goats

        automata.setParams(4, 3, 2, 0.3f);
        boolean[][] badGoats = automata.generate(chunk);

        for (int x = 0; x < badGoats.length; x++) {
            for (int y = 0; y < badGoats[0].length; y++) {
                if (badGoats[x][y] && !indoorRegions[x][y] && !detectCollisions(new Vector(x, y))) {
                    enemyPlacer.placeEnemy(x, y, EnemyBlueprintKeys.GOAT);
                }
            }
        }

        // Add mama bears
        PoissonDiskSampler sampler = new PoissonDiskSampler();
        int minDistance = mapWidth / 2;
        int bearCount = 4;
        ArrayList<Vector> treePositions = sampler.generateNoise(chunk.width, chunk.height, minDistance, bearCount);
        for (Vector cell : treePositions) {
            if (!inBounds(cell)) continue;
            if (indoorRegions[cell.x][cell.y]) continue;

            enemyPlacer.placeEnemy(cell.x, cell.y, EnemyBlueprintKeys.BEAR);
        }
    }

/*
    ------------------------------------------------------------------------------------------
    Building generation
    ------------------------------------------------------------------------------------------
*/


    private final int MIN_ROOM_SEEDS = 3;
    private final int MINIMUM_ROOM_AREA = 9;
    private final float MAX_RECT_RATIO = 2f;

    private ArrayList<Chunk> debugSeedslol = new ArrayList<>();
    private ArrayList<MapRegion> debugRegionsLol = new ArrayList<>();
    private ArrayList<MapRegion> roomRegions = new ArrayList<>();
    private ArrayList<Vector> carvedWallslol = new ArrayList<>();

    private ArrayList<MapRegion> generateRoomRegions(Chunk building) {
        ArrayList<Chunk> seeds = generateRoomSeeds(building);
        ArrayList<Chunk> discardedSeeds = new ArrayList<>();
        ArrayList<MapRegion> regions = growRooms(building, seeds, discardedSeeds);

        // Now we want to make sure all space is filled by combining the discarded seeds with existing regions
        Iterator<Chunk> it = discardedSeeds.iterator();

        while (it.hasNext()) {
            Chunk seed = it.next();
            MapRegion mergedRegion = mergeChunkWithRegion(seed, regions);
            if (mergedRegion != null) {
                it.remove();
            }
        }

        return regions;
    }

    private ArrayList<Chunk> generateRoomSeeds(Chunk chunk) {
        int maxRoomSeeds = chunk.area() / MINIMUM_ROOM_AREA;
        int roomSeedCount = rng.getRandomInt(MIN_ROOM_SEEDS, maxRoomSeeds);

        ArrayList<Chunk> seeds = new ArrayList<>();
        PoissonDiskSampler sampler = new PoissonDiskSampler();
        sampler.setDensity(100);
        int minDistance = chunk.width / minRoomWidth;

        int offset = minRoomWidth / 2;

        // Place random room seeds in building
        ArrayList<Vector> seedPositions = sampler.generateNoise(chunk.width - minRoomWidth, chunk.height - minRoomWidth, minDistance, roomSeedCount);

        for (Vector cell : seedPositions) {
            Chunk seed = new Chunk(cell.x + chunk.x + offset, cell.y + chunk.y + offset, 1, 1);
            seeds.add(seed);
            // debugSeedslol.add(seed);
        }

        return seeds;
    }

    private ArrayList<Chunk> addSeedsToCorners(Chunk chunk) {
        ArrayList<Chunk> seeds = new ArrayList<>();

        seeds.add(new Chunk(chunk.x + 1, chunk.y + 1, 1, 1));
        seeds.add(new Chunk(chunk.x + chunk.width - 2, chunk.y + 1, 1, 1));
        seeds.add(new Chunk(chunk.x + 1, chunk.y + chunk.height - 2, 1, 1));
        seeds.add(new Chunk(chunk.x + chunk.width - 2, chunk.y + chunk.height - 2, 1, 1));

        // debugSeedslol.addAll(seeds);

        return seeds;
    }

    private ArrayList<MapRegion> growRooms(Chunk building, ArrayList<Chunk> seeds,
                                           ArrayList<Chunk> discardedSeeds) {

        // Iterate over seeds in queue and expand them outwards until we collide with other rooms
        // or exceed the building bounds. Stop iterating once it's impossible for seeds to expand

        ArrayList<MapRegion> output = new ArrayList<>();
        ArrayList<Chunk> growQueue = new ArrayList<>();
        growQueue.addAll(seeds);

        while (growQueue.size() > 0) {
            Chunk seed = growQueue.remove(0);
            Chunk growth;

            boolean canGrow = true;
            int blockedCount = 0;

            // Expand bottom
            growth = new Chunk(seed.x, seed.y - 1, seed.width, seed.height + 1);

            if (!chunkContainsChunk(growth, building)) {
                canGrow = false;
                blockedCount++;

            } else {
                // Check whether expanded seed collides with any of the rooms in grow queue.
                for (Chunk room : seeds) {
                    if (!room.equals(seed) && AxisAlignedBoxTester.test(room, growth)) {
                        canGrow = false;
                        blockedCount++;
                        break;
                    }
                }
            }

            if (!canGrow) {
                growth.y += 1;
                growth.height -= 1;
            }

            canGrow = true;

            // Expand top
            growth.height += 1;

            if (!chunkContainsChunk(growth, building)) {
                canGrow = false;
                blockedCount++;

            } else {
                for (Chunk room : seeds) {
                    if (!room.equals(seed) && AxisAlignedBoxTester.test(room, growth)) {
                        canGrow = false;
                        blockedCount++;
                        break;
                    }
                }
            }

            if (!canGrow) {
                growth.height -= 1;
            }

            canGrow = true;

            // Expand left
            growth.x -= 1;
            growth.width += 1;

            if (!chunkContainsChunk(growth, building)) {
                canGrow = false;
                blockedCount++;

            } else {
                for (Chunk room : seeds) {
                    if (!room.equals(seed) && AxisAlignedBoxTester.test(room, growth)) {
                        canGrow = false;
                        blockedCount++;
                        break;
                    }
                }
            }

            if (!canGrow) {
                growth.x += 1;
                growth.width -= 1;
            }

            canGrow = true;

            // Expand right
            growth.width += 1;

            if (!chunkContainsChunk(growth, building)) {
                canGrow = false;
                blockedCount++;

            } else {
                for (Chunk room : seeds) {
                    if (!room.equals(seed) && AxisAlignedBoxTester.test(room, growth)) {
                        canGrow = false;
                        blockedCount++;
                        break;
                    }
                }
            }

            if (!canGrow) {
                growth.width -= 1;
            }

            // We don't want rooms to be overly tall or overly wide, so make sure the ratio
            // of longest side to shortest side doesn't exceed 2

            float largestSide = Math.max(seed.width, seed.height);
            float smallestSide = Math.min(seed.width, seed.height);
            float ratio = largestSide / smallestSide;

            if (ratio > MAX_RECT_RATIO) {
                MapRegion region = new MapRegion();
                region.addChunk(seed);
                output.add(region);

                // Todo: remove reliance on rooms array amnd use mapregions
                rooms.add(new Room(seed.x, seed.y, seed.width, seed.height));
                continue;
            }

            // Update seed size
            seed.x = growth.x;
            seed.y = growth.y;
            seed.width = growth.width;
            seed.height = growth.height;

            // If there are still directions that we could expand into, add seed back into queue.
            // Once seed has finished expanding we either add it to the output, or add it
            // to discarded seeds pile for more processing

            if (blockedCount < 4) {
                growQueue.add(seed);

            } else if (seed.width > 2 && seed.height > 2 && seed.width * seed.height >= MINIMUM_ROOM_AREA) {
                MapRegion region = new MapRegion();
                region.addChunk(seed);
                output.add(region);

                // Todo: remove reliance on rooms array amnd use mapregions
                rooms.add(new Room(seed.x, seed.y, seed.width, seed.height));

            } else {
                discardedSeeds.add(seed);
            }
        }

        return output;
    }

    private ArrayList<Integer> mergedRegions = new ArrayList<>();

    private MapRegion mergeChunkWithRegion(Chunk chunk, ArrayList<MapRegion> regions) {
        // Ignore failed seeds for now.
        if (chunk.area() == 1) {
            return null;
        }

        MapRegion seedRegion = new MapRegion();
        seedRegion.addChunk(chunk);

        for (int i = 0; i < regions.size(); i++) {
            if (mergedRegions.contains(i)) continue;

            MapRegion region = regions.get(i);

            for (Vector[] seedSide : seedRegion.getSides()) {
                Vector seedA = seedSide[0];
                Vector seedB = seedSide[1];

                int seedLeft = Math.min(seedA.x, seedB.x);
                int seedRight = Math.max(seedA.x, seedB.x);
                int seedBottom = Math.min(seedA.y, seedB.y);
                int seedTop = Math.max(seedA.y, seedB.y);

                // We need to check whether any sides of chunk are adjacent to an existing region
                // so we can merge them together

                for (Vector[] side : region.getSides()) {
                    Vector pointA = side[0];
                    Vector pointB = side[1];

                    int regionLeft = Math.min(pointA.x, pointB.x);
                    int regionRight = Math.max(pointA.x, pointB.x);
                    int regionBottom = Math.min(pointA.y, pointB.y);
                    int regionTop = Math.max(pointA.y, pointB.y);

                    // Because we are only dealing with right angles (relative to grid)
                    // we can just check whether the x/y coords are equal in both regions
                    // to check whether the sides are parallel

                    if (seedLeft == seedRight && regionLeft == regionRight) {
                        // Parallel on vertical plane
                        region.addChunk(chunk);

                        // Make sure that chunk is adjacent to region
                        if (seedLeft - 1 == regionRight + 1) {
                            // Todo: we only want to remove section of wall that is shared between rooms

                            // Remove separating wall and merge seed with room
                            for (int y = seedBottom; y <= seedTop; y++) {
                                if (y < regionBottom || y > regionTop) continue;
                                region.add(new Vector(seedLeft - 1, y));
                                carvedWallslol.add(new Vector(seedLeft - 1, y));
                            }

                            region.addChunk(chunk);
                            debugRegionsLol.add(seedRegion);

                            mergedRegions.add(i);

                            return region;
                        }

                        else if (regionLeft - 1 == seedRight + 1) {
                            for (int y = seedBottom; y <= seedTop; y++) {
                                if (y < regionBottom || y > regionTop) continue;
                                region.add(new Vector(regionLeft - 1, y));
                                carvedWallslol.add(new Vector(regionLeft - 1, y));
                            }
                            region.addChunk(chunk);
                            debugRegionsLol.add(seedRegion);
                            mergedRegions.add(i);
                            return region;
                        }
                    }

                    if (seedTop == seedBottom && regionTop == regionBottom) {
                        // Parallel on horizontal plane

                        if (seedBottom - 1 == regionTop + 1) {
                            for (int x = seedLeft; x <= seedRight; x++) {
                                if (x < regionLeft || x > regionRight) continue;
                                region.add(new Vector(x, seedBottom - 1));
                                carvedWallslol.add(new Vector(x, seedBottom - 1));
                            }

                            region.addChunk(chunk);
                            debugRegionsLol.add(seedRegion);
                            mergedRegions.add(i);
                            return region;
                        }

                        else if (regionBottom - 1 == seedTop + 1) {
                            for (int x = seedLeft; x <= seedRight; x++) {
                                if (x < regionLeft || x > regionRight) continue;
                                region.add(new Vector(x, regionBottom - 1));
                                carvedWallslol.add(new Vector(x, regionBottom - 1));
                            }

                            region.addChunk(chunk);
                            debugRegionsLol.add(seedRegion);
                            mergedRegions.add(i);
                            return region;
                        }
                    }
                }
            }
        }

        // Chunk wasn't adjacent to any regions
        Log.d(LOG_TAG, "Couldn't merge chunk " + chunk + " with any regions");
        return null;
    }

    private ArrayList<Chunk> getHallwayChunks(Chunk start) {
        ArrayList<Chunk> generatedChunks = new ArrayList<>();
        int totalHalls = 0;

        ArrayList<Chunk> chunkQueue = new ArrayList<>();

        // Ignore border tiles when defining starting chunk
        chunkQueue.add(start);

        // This boolean is inverted on each step so we carveRegion alternating horizontal/vertical hallways
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
                    setTerrain(x, splitY, BuildingTileset.WOOD_FLOOR_1);
                }

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, chunk.width, splitY - chunk.y);
                Chunk splitChunkB = new Chunk(chunk.x, splitY, chunk.width, (chunk.y + chunk.height) - splitY);

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
                    setTerrain(splitX, y, BuildingTileset.WOOD_FLOOR_1);
                }

                Chunk splitChunkA = new Chunk(chunk.x, chunk.y, splitX - chunk.x, chunk.height);
                Chunk splitChunkB = new Chunk(splitX, chunk.y, (chunk.x + chunk.width) - splitX, chunk.height);

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

        // This boolean is inverted on each step so we carveRegion alternating horizontal/vertical hallways
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

                    setTerrain(x, splitY - 1, ExteriorTileset.ROAD_PLAIN);

                    if (verticalRoads[x][splitY]) {
                        setTerrain(x, splitY, ExteriorTileset.ROAD_PLAIN);
                    }
                    else {
                        setTerrain(x, splitY, ExteriorTileset.ROAD_MIDDLE_H);
                    }

                    setTerrain(x, splitY + 1, ExteriorTileset.ROAD_PLAIN);

                    // Either clear objects from the road, or replace a road tile with a dirt tile.
                    for (int y = splitY - 1; y <= splitY + 1; y++) {
                        if (objectEntities[x][y].size() > 0) {
                            waterRegions[x][y] = false;

                            if (rng.coinflip()) {
                                clearObjects(x, y);
                            }
                            else {
                                setTerrain(x, y, ExteriorTileset.ROAD_DAMAGE);
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

                    setTerrain(splitX - 1, y, ExteriorTileset.ROAD_PLAIN);

                    if (horizontalRoads[splitX][y]) {
                        setTerrain(splitX, y, ExteriorTileset.ROAD_PLAIN);
                    }
                    else {
                        setTerrain(splitX, y, ExteriorTileset.ROAD_MIDDLE_V);
                    }

                    setTerrain(splitX + 1, y, ExteriorTileset.ROAD_PLAIN);

                    // Either clear objects from the road, or replace a road tile with a dirt tile
                    for (int x = splitX - 1; x <= splitX + 1; x++) {
                        if (objectEntities[x][y].size() > 0) {
                            waterRegions[x][y] = false;

                            if (rng.coinflip()) {
                                clearObjects(x, y);
                            }
                            else {
                                setTerrain(x, y, ExteriorTileset.ROAD_DAMAGE);
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

    private final int HOUSE_TAG = 1;
    private final int BACKYARD_TAG = 2;

    private void addHousesToChunk(Chunk chunk) {
        ArrayList<HouseWithYard> houses = new ArrayList<>();

        // Todo: th is should be handled when deciding what to do with chunk
        // Just return empty array list if chunk is too small
        // if (chunk.width < minBuildingWidth || chunk.height < minBuildingHeight) return buildings;

        int sizeVariance = 2;
        int gapSize = 4;
        int halfGap = gapSize / 2;
        int targetSize = Math.min(chunk.width, chunk.height) / 2;

        if (chunk.width > chunk.height) {
            int columns = chunk.width / (targetSize + gapSize);
            for (int i = 0; i < columns; i++) {
                int x = chunk.x + (i * targetSize + gapSize) + (i * halfGap);
                Chunk house = new Chunk(x, chunk.y + targetSize - halfGap, targetSize, targetSize - gapSize, HOUSE_TAG);
                Chunk yard = new Chunk(x, chunk.y + 1, targetSize, targetSize - gapSize, BACKYARD_TAG);
                houses.add(new HouseWithYard(house, yard));
            }
        }

        else {
            int rows = chunk.height / (targetSize + gapSize);
            for (int i = 0; i < rows; i++) {
                int y = chunk.y + (i * targetSize + gapSize) + (i * halfGap);
                Chunk house = new Chunk(chunk.x + targetSize - halfGap, y, targetSize - gapSize, targetSize, HOUSE_TAG);
                Chunk yard = new Chunk(chunk.x + 1, y, targetSize - gapSize, targetSize, BACKYARD_TAG);
                houses.add(new HouseWithYard(house, yard));
            }
        }

        for (HouseWithYard houseWithYard : houses) {
            Chunk house = houseWithYard.house;
            Chunk yard = houseWithYard.yard;

            for (int x = house.x; x < house.x + house.width; x++) {
                for (int y = house.y; y < house.y + house.height; y++) {
                    blockedTiles[x][y] = true;
                }
            }

            for (int x = yard.x; x < yard.x + yard.width; x++) {
                for (int y = yard.y; y < yard.y + yard.height; y++) {
                    blockedTiles[x][y] = true;
                }
            }

            clearObjectsFromBorder(yard);
            addBorderObjectToChunk(yard, ExteriorTileset.WOOD_FENCE);

            clearObjectsFromBorder(house);
            carveBuildingFoundation(house);
            // addRoomsToBuilding(houseWithYard);
            createDamagedWalls(house);
        }

        // calculateGoals();

        // addBuildingPaths();
    }

    private ArrayList<Chunk> addBuildingsToChunk(Chunk chunk) {
        ArrayList<Chunk> buildings = new ArrayList<>();

        // Todo: th is should be handled when deciding what to do with chunk
        // Just return empty array list if chunk is too small
        // if (chunk.width < minBuildingWidth || chunk.height < minBuildingHeight) return buildings;

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

            clearObjectsFromBorder(building);
            addBorderObjectToChunk(building, ExteriorTileset.WOOD_FENCE);

            carveBuildingFoundation(building);
            // addRoomsToBuilding(building);
            createDamagedWalls(building);
        }

        // calculateGoals();

        // addBuildingPaths();

        return buildings;
    }

    private ArrayList<Vector> buildingEntrances = new ArrayList<>();

    private void addRoomsToBuilding(HouseWithYard houseWithYard) {
        Chunk building = houseWithYard.house;

        MapRegion yard = new MapRegion();
        yard.addChunk(houseWithYard.yard);

        ArrayList<MapRegion> rooms = generateRoomRegions(building);
        roomRegions.addAll(rooms);
        carveRoomFloor(rooms);
        addExteriorWallFeatures(rooms, building);

        tiler.setTileset(BuildingTileset.KEY);
        decorator = new MansionDecorator(mapWidth, mapHeight, theme, themeKey, assetManager);
        decorator.setGeneratorData(terrainTiles, objectEntities);
        decorator.decorateRooms(rooms, building.width, building.height);

        // Use MazeGenerator to add corridors to building and connect regions
        mazeGenerator.setChunk(building);
        mazeGenerator.setExtraConnectorChance(0);

        // (exclude room regions)
        for (MapRegion room : rooms) {
            mazeGenerator.addExistingRegion(room);
        }

        mazeGenerator.addExistingRegion(yard);

        boolean[][] carvedTiles = mazeGenerator.generate();

        for (int x = 0; x < building.width; x++) {
            for (int y = 0; y < building.height; y++) {
                if (carvedTiles[x][y]) {
                    Vector translatedCell = new Vector(building.x + x, building.y + y);

                    if (TileCategorySorter.isWall(terrainTiles[translatedCell.x][translatedCell.y])) {
                        setTerrain(translatedCell.x, translatedCell.y, BuildingTileset.WOOD_FLOOR_1);
                    }
                }
            }
        }

        for (Vector cell : mazeGenerator.getJunctions()) {
            addJunction(new Vector(building.x + cell.x, building.y + cell.y));
        }

        checkForBrokenDoors();
    }

    private void addRoomsToBuilding(Chunk building) {
        ArrayList<MapRegion> rooms = generateRoomRegions(building);
        roomRegions.addAll(rooms);
        carveRoomFloor(rooms);
        addExteriorWallFeatures(rooms, building);

        tiler.setTileset(BuildingTileset.KEY);
        decorator = new MansionDecorator(mapWidth, mapHeight, theme, themeKey, assetManager);
        decorator.setGeneratorData(terrainTiles, objectEntities);
        decorator.decorateRooms(rooms, building.width, building.height);

        // Use MazeGenerator to add corridors to building and connect regions
        mazeGenerator.setChunk(building);
        mazeGenerator.setExtraConnectorChance(0);

        // (exclude room regions)
        for (MapRegion room : rooms) {
            mazeGenerator.addExistingRegion(room);
        }

        boolean[][] carvedTiles = mazeGenerator.generate();

        for (int x = 0; x < building.width; x++) {
            for (int y = 0; y < building.height; y++) {
                if (carvedTiles[x][y]) {
                    Vector translatedCell = new Vector(building.x + x, building.y + y);

                    // We only want to carve tiles that haven't already been carved
                    if (TileCategorySorter.isWall(terrainTiles[translatedCell.x][translatedCell.y])) {
                        setTerrain(translatedCell.x, translatedCell.y, BuildingTileset.WOOD_FLOOR_1);
                    }
                }
            }
        }

        for (Vector cell : mazeGenerator.getJunctions()) {
            addJunction(new Vector(building.x + cell.x, building.y + cell.y));
        }

        checkForBrokenDoors();
    }

    private void addExteriorWallFeatures(ArrayList<MapRegion> rooms, Chunk building) {
        int exteriorDoors = 0;

        for (MapRegion room : roomRegions) {
            int windowsInRoom = 0;

            ArrayList<Vector> freeTiles = new ArrayList<>();

            ArrayList<Vector[]> sides = GridGeometryHelper.findSides(room.getVectors());

            // Iterate over each side and check if aligned with exterior wall.
            for (Vector[] side : sides) {
                Vector a = side[0];
                Vector b = side[1];

                // Vertical
                if (a.x == b.x) {
                    int top = Math.max(a.y, b.y);
                    int bottom = Math.min(a.y, b.y);
                    int height = top - bottom;

                    if (a.x == building.x + 1) {
                        // Place window on left wall
                        int x = a.x - 1;
                        int y = bottom + (height / 2);

                        freeTiles.add(new Vector(x, y));
                    }
                    else if (a.x == building.x + building.width - 1) {
                        // Place window on right wall
                        int x = a.x + 1;
                        int y = bottom + (height / 2);

                        freeTiles.add(new Vector(x, y));
                    }

                }

                // Horizontal
                else if (a.y == b.y) {
                    int left = Math.min(a.x, b.x);
                    int right = Math.max(a.x, b.x);
                    int width = right - left;

                    if (a.y == building.y + 1) {
                        // Place window on bottom wall
                        int x = left + (width / 2);
                        int y = a.y - 1;

                        freeTiles.add(new Vector(x, y));
                    }
                    else if (a.y == building.y + building.height - 1) {
                        // Place window on top wall
                        int x = left + (width / 2);
                        int y = a.y + 1;

                        freeTiles.add(new Vector(x, y));
                    }
                }
            }

            int z = 1;

            for (Vector freeTile : freeTiles) {
                int x = freeTile.x;
                int y = freeTile.y;

                tiler.setTileset(ExteriorTileset.KEY);

                // All rooms with exterior access should have at least 1 window
                if (windowsInRoom < 1) {
                    setTerrain(x, y, tiler.getFloorTile(currentRoomTheme));

                    String windowTex = getMatchingWindowTexture(x, y);

                    Component[] window = DecalFactory.createCubeDecal(x, y, z, windowTex, false, false);
                    objectEntities[x][y].add(window[0].id);
                    componentManager.sortComponentArray(window);

                    windowsInRoom++;
                    continue;
                }

                // One of these rooms will be used as entrance to building
                if (exteriorDoors < 1) {
                    setTerrain(x, y, tiler.getFloorTile(currentRoomTheme));

                    tiler.setTileset(BuildingTileset.KEY);
                    addJunction(freeTile);
                    buildingEntrances.add(freeTile);
                    exteriorDoors++;
                    continue;
                }

                // 50/50 chance that room will have 2 windows
                if (windowsInRoom < 2 && rng.coinflip()) {
                    setTerrain(x, y, tiler.getFloorTile(currentRoomTheme));
                    String windowTex = getMatchingWindowTexture(x, y);
                    Component[] window = DecalFactory.createCubeDecal(x, y, z, windowTex, false, false);
                    objectEntities[x][y].add(window[0].id);
                    componentManager.sortComponentArray(window);

                    windowsInRoom++;
                }
            }
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
                    setTerrain(path.x, path.y, texture);

                    // Other entities occupying this tile can be safely removed
                    clearObjects(path.x, path.y);
                }

            }
        }
    }

    public String getMatchingWindowTexture(int x, int y) {
        String window = ExteriorTileset.getWindowForWallTile(terrainTiles[x][y]);
        if (window != null) {
            return window;
        }

        Collection<Vector> adjacent = getAdjacentCells(new Vector(x, y), 1, false).values();

        for (Vector cell : adjacent) {
            window = ExteriorTileset.getWindowForWallTile(terrainTiles[cell.x][cell.y]);
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
                // Mark all these tiles as indoor regions. This is mainly used for weather effects
                indoorRegions[x][y] = true;
            }
        }

        int brickIndex = 0;
        String[] brickTiles = ExteriorTileset.WALLS[rng.getRandomInt(0, ExteriorTileset.WALLS.length - 1)];

        // Carve the walls. We defer the interior terrain generation until player enters building,
        // or interior tiles enter their field of vision (eg. through windows).
        
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            Vector bottom = new Vector(x, chunk.y);

            setTerrain(bottom.x, bottom.y, brickTiles[brickIndex]);

            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }

            Component[] wall = DecalFactory.createCubeDecal(x, chunk.y, 1, brickTiles[brickIndex], true, true);
            objectEntities[x][chunk.y].add(wall[0].id);
            componentManager.sortComponentArray(wall);
        }

        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            Vector top = new Vector(x, chunk.y + chunk.height - 1);
            setTerrain(top.x, top.y, brickTiles[brickIndex]);

            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }

            Component[] wall = DecalFactory.createCubeDecal(x, chunk.y + chunk.height - 1, 1, brickTiles[brickIndex], true, true);
            objectEntities[x][chunk.y + chunk.height - 1].add(wall[0].id);
            componentManager.sortComponentArray(wall);
        }

        Vector left = new Vector(chunk.x, 0);

        for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
            left.y = y;
            setTerrain(left.x, left.y, brickTiles[brickIndex]);
            
            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }

            Component[] wall = DecalFactory.createCubeDecal(left.x, left.y, 1, brickTiles[brickIndex], true, true);
            objectEntities[left.x][left.y].add(wall[0].id);
            componentManager.sortComponentArray(wall);
        }

        Vector right = new Vector(chunk.x + chunk.width - 1, 0);

        for (int y = chunk.y; y < chunk.y + chunk.height; y++) {
            right.y = y;
            setTerrain(right.x, right.y, brickTiles[brickIndex]);

            brickIndex++;

            if (brickIndex > brickTiles.length - 1) {
                brickIndex = 0;
            }

            Component[] wall = DecalFactory.createCubeDecal(right.x, right.y, 1, brickTiles[brickIndex], true, true);
            objectEntities[right.x][right.y].add(wall[0].id);
            componentManager.sortComponentArray(wall);
        }
    }

    private void createDamagedWalls(Chunk chunk) {
        String[] damagedWall = ExteriorTileset.getDamagedWallTiles(terrainTiles[chunk.x][chunk.y]);

        // Ignore corner tiles when placing damaged walls

        int z = 1;

        for (int x = chunk.x + 1; x < chunk.x + chunk.width - 1; x++) {
            Vector bottom = new Vector(x, chunk.y);

            if (rng.d6(2) == 2) {
                addObject(bottom, DecalFactory.createCubeDecal(bottom.x, bottom.y, z, rng.getRandomItemFromStringArray(damagedWall), false, false), true);
                copyTerrain(bottom.x, bottom.y + 1, bottom.x, bottom.y);

                // if we modify a tile, skip ahead to prevent from modifying consecutive tiles
                x++;
            }
        }

        for (int x = chunk.x + 1; x < chunk.x + chunk.width - 1; x++) {
            Vector top = new Vector(x, chunk.y + chunk.height - 1);
            if (rng.d6(2) == 2) {
                addObject(top, DecalFactory.createCubeDecal(top.x, top.y, z, rng.getRandomItemFromStringArray(damagedWall), false, false), true);
                copyTerrain(top.x, top.y + 1, top.x, top.y);
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
        for (MapRegion room : roomRegions) {
            currentRoomTheme = rng.getRandomInt(0, 3);
            startRegion();
            // retextureWalls(room);
            carveRoomFloor(room);
        }
    }

    private void retextureWalls(Room room) {
        int right = room.x + room.width + 1;
        int top = room.y + room.height + 1;

        String themedTile = tiler.getMansionWallTilePath(currentRoomTheme);

        for (int x = room.x - 1; x < right; x++) {
            Vector north = new Vector(x, top);
            Vector south = new Vector(x, room.y);

            if (!adjacentCellsAreCarvable(north) || !adjacentCellsAreCarvable(south)) break;

            if (inBounds(north)) {
                terrainTiles[north.x][north.y] = themedTile;
            }

            if (inBounds(south)) {
                terrainTiles[south.x][south.y] = themedTile;
            }
        }

        for (int y = room.y - 1; y < top; y++) {
            Vector east = new Vector(room.x - 1, y);
            Vector west = new Vector(room.x + room.width + 1, y);

            if (!adjacentCellsAreCarvable(east) || !adjacentCellsAreCarvable(west)) break;

            if (inBounds(east)) {
                terrainTiles[east.x][east.y] = themedTile;
            }

            if (inBounds(west)) {
                terrainTiles[west.x][west.y] = themedTile;
            }
        }
    }

    private void carveRoomFloor(ArrayList<MapRegion> rooms) {
        for (MapRegion room : rooms) {
            carveRoomFloor(room);
        }
    }

    private void carveRoomFloor(MapRegion room) {
        currentRoomTheme = rng.getRandomInt(0, 3);
        String floorTexture = tiler.getFloorTile(currentRoomTheme);

        for (Vector cell : room.getVectors()) {
            carveRegion(new Vector(cell.x, cell.y), floorTexture);
        }
    }

    /**
     *  Prevents issue where doors are placed in wall tiles with no destination
     */

    private void checkForBrokenDoors() {
        Iterator it = doors.values().iterator();

        while (it.hasNext()) {
            Component[] door = (Component[]) it.next();
            Position pComp = ComponentFinder.getPositionComponent(door);

            if (pComp == null) continue;

            Vector position = new Vector(pComp.x, pComp.y);

            if (TileCategorySorter.isWall(terrainTiles[position.x][position.y])) {
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

                double distanceToGoal = GeometryHelper.getDistance(adjacentNode, goalNode);

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

        // If terrain tile is null, then we assume it is not blocking and continue to check objects
        if (terrainTiles[x][y] != null) {
            if (isBlocking(terrainTiles[x][y]) || !isTraversable(terrainTiles[x][y])) {
                return true;
            }
        }

        ArrayList<Long> objectStack = objectEntities[x][y];

        if (objectStack == null || objectStack.size() == 0) {
            return false;
        }

        for (Long entity : objectStack) {

            Physics physics = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());

            if (physics.isBlocking || !physics.isTraversable) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlocking(String tile) {
        if (TileCategorySorter.isWall(tile)) return true;

        return false;
    }

    private boolean isTraversable(String tile) {
        if (TileCategorySorter.isWall(tile)) return false;

        return true;
    }

    private boolean canBuildPath(Vector position) {
        int x = position.x();
        int y = position.y();

        /*if (!physics.isTraversable && !physics.isDestructable) {
            return false;
        }*/

        ArrayList<Long> objectStack = objectEntities[x][y];

        if (objectStack == null || objectStack.size() == 0) {
            // At this point, we have already determined that terrain entity can be built over,
            // and there are no objects to check
            return true;
        }

        for (Long entity : objectStack) {
            Physics physics = (Physics) componentManager.getEntityComponent(entity, Physics.class.getSimpleName());

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
                    setTerrain(x + chunk.x, y + chunk.y, RuinsTileset.FLOOR);
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
        // Sometimes maze generator messes up and adds junctions between regions that should not be
        // joined. We can prevent this by making sure that cell has at least two floor tiles in
        // directly adjacent spaces

        int adjacentFloorTiles = 0;

        for (Vector direction : Directions.Cardinal.values()) {
            Vector adjacent = cell.add(direction);
            if (inBounds(adjacent)) {
                if (TileCategorySorter.isFloor(terrainTiles[adjacent.x][adjacent.y])) {
                    adjacentFloorTiles++;
                }
            }
        }

        // If there are fewer than 2 adjacent floor tiles, then proposed junction location isn't connecting
        // any regions. We can safely discard it
        if (adjacentFloorTiles < 2) {
            return;
        }

        setTerrain(cell.x, cell.y, BuildingTileset.WOOD_FLOOR_1);

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
        sprite.wrapToCube = true;

        doors.put(cell.toString(), door);
    }

    private void removeInaccessibleCells() {
        HashMap<String, Boolean> checked = new HashMap<>();

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.containsKey(cell.toString())) {
                    if (TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y]) && cellIsInaccessible(cell)) {
                        setTerrain(cell.x, cell.y, BuildingTileset.WALL);
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

                    if (TileCategorySorter.isWall(terrainTiles[cell.x][cell.y]) && cellIsInaccessible(cell)) {
                        setTerrain(cell.x, cell.y, GenericTileset.TRANSPARENT);
                        checked.add(cell.toString());
                    }
                }
            }
        }
    }

/*
    ---------------------------------------------
    Methods for creating and modifying entities
    ---------------------------------------------
*/

    private void startRegion() {
        currentRegion++;
    }

    private void carveRegion(Vector cell, String texture) {
        mapRegions[cell.x][cell.y] = currentRegion;
        setTerrain(cell.x, cell.y, texture);
    }

    private void setTerrain(int x, int y, String texture) {
        terrainTiles[x][y] = texture;
    }

    private void copyTerrain(int srcX, int srcY, int destX, int destY) {
        terrainTiles[srcX][srcY] = terrainTiles[destX][destY];
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

    private void addBorderToChunk(Chunk chunk, String[] borderTiles) {
        // Todo: for now we can just remove water regions, but need to keep this in mind when adding water borders?

        for (int x = chunk.x; x < chunk.x + chunk.width - 1; x++) {
            setTerrain(x, chunk.y + chunk.height - 1, borderTiles[NORTH]);
            waterRegions[x][chunk.y + chunk.height - 1] = false;
        }

        setTerrain(chunk.x + chunk.width - 1, chunk.y + chunk.height - 1, borderTiles[NORTH_EAST]);
        waterRegions[chunk.x + chunk.width - 1][chunk.y + chunk.height - 1] = false;

        for (int y = chunk.y + chunk.height - 2; y > chunk.y; y--) {
            setTerrain(chunk.x + chunk.width - 1, y, borderTiles[EAST]);
            waterRegions[chunk.x + chunk.width - 1][y] = false;
        }

        setTerrain(chunk.x + chunk.width - 1, chunk.y, borderTiles[SOUTH_EAST]);
        waterRegions[chunk.x + chunk.width - 1][chunk.y] = false;

        for (int x = chunk.x + chunk.width - 2; x > chunk.x; x--) {
            setTerrain(x, chunk.y, borderTiles[SOUTH]);
            waterRegions[x][chunk.y] = false;
        }

        setTerrain(chunk.x, chunk.y, borderTiles[SOUTH_WEST]);
        waterRegions[chunk.x][chunk.y] = false;

        for (int y = chunk.y + 1; y < chunk.y + chunk.height - 1; y++) {
            setTerrain(chunk.x, y, borderTiles[WEST]);
            waterRegions[chunk.x][y] = false;
        }

        setTerrain(chunk.x, chunk.y + chunk.height - 1, borderTiles[NORTH_WEST]);
        waterRegions[chunk.x][chunk.y + chunk.height - 1] = false;
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

    private void addBorderToChunk(Chunk chunk, String tile) {
        for (int x = chunk.x; x < chunk.x + chunk.width; x++) {
            for (int y = chunk.y; y < chunk.y + chunk.height; y++) {

                if (x == chunk.x || x == chunk.x + chunk.width - 1 || y == chunk.y || y == chunk.y + chunk.height - 1) {
                    setTerrain(x, y, tile);
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

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < mapWidth && cell.y() >= 0 && cell.y() < mapHeight);
    }

    private boolean chunkContainsChunk(Chunk chunk, Chunk container) {
        return (chunk.x > container.x && chunk.x + chunk.width < container.x + container.width
                && chunk.y > container.y && chunk.y + chunk.height < container.y + container.height);
    }

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
     * Adjacent cell is "carvable" if all adjacent cells are wall tiles (renderState == Terrain.WALL)
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
                if (!TileCategorySorter.isWall(terrainTiles[adjacent.x][adjacent.y])) {
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

            String tile = terrainTiles[direction.x][direction.y];

            if (!TileCategorySorter.isWall(tile) && !TileCategorySorter.isBorder(tile)) {
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
