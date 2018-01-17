package com.sonicmax.bloodrogue.generator;

import android.content.res.AssetManager;
import android.util.Log;
import android.util.SparseIntArray;

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
import com.sonicmax.bloodrogue.engine.components.Stationary;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.generator.factories.TerrainFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.generator.mansion.Chunk;
import com.sonicmax.bloodrogue.tilesets.ExteriorTileset;
import com.sonicmax.bloodrogue.tilesets.MansionTileset;
import com.sonicmax.bloodrogue.tilesets.RuinsTileset;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.generator.mansion.Room;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProceduralGenerator {
    private final String LOG_TAG = this.getClass().getSimpleName();

    public final static int DUNGEON = 0;
    public final static int CAVERN = 1;
    public final static int RUINS = 2;
    public final static int MANSION = 3;
    public final static int EXTERIOR = 4;

    public final static int MAX_COMPONENTS = 18;

    private final static boolean CARVABLE = true;
    private final static boolean NOT_CARVABLE = false;

    private ArrayList<Room> rooms;
    private HashMap<String, Component[]> doors;
    private ArrayList<Component[]> objects;
    private ArrayList<Component[]> enemies;
    private Vector floorEntrance;
    private Vector floorExit;
    private int type;

    private int mapWidth;
    private int mapHeight;

    private Component[][][] mapGrid;
    private int[][] mapRegions;
    private ArrayList<Component[]>[][] objectGrid;

    // For region connecting method
    private int currentRegion = -1;
    private Set regions;

    // BSP room generation defaults
    private int maxHallLimit = 8;
    private int minChunkSize = 7;

    // Corridor (maze) generation defaults
    private int extraConnectorChance = 40;
    private int windingPercent = 35;

    // Configuration for room generation
    private int minRoomWidth = 3;
    private int maxRoomWidth = 9;
    private int minRoomHeight = 3;
    private int maxRoomHeight = 9;
    private int roomDensity = 2000; // Higher value = more attempts to place non-colliding rooms

    private int floorType;
    private int theme;
    private String themeKey;
    private int currentRoomTheme;

    private MansionDecorator decorator;
    private Tiler tiler;
    private RandomNumberGenerator rng;
    private AssetManager assetManager;
    private JSONObject furnitureBlueprints;

    private int currentFloor;

    public ProceduralGenerator(int width, int height, AssetManager assetManager) {
        this.mapWidth = width;
        this.mapHeight = height;
        this.assetManager = assetManager;
        this.furnitureBlueprints = JSONLoader.loadFurniture(assetManager);

        this.regions = new HashSet();
        this.objects = new ArrayList<>();
        this.enemies = new ArrayList<>();
        this.doors = new HashMap<>();
        this.rng = new RandomNumberGenerator();
        this.currentFloor = 1;
    }

	/*
		---------------------------------------------
		Initialisation
		---------------------------------------------
	*/

    private void initGrids() {
        mapGrid = new Component[mapWidth][mapHeight][ComponentManager.MAX_COMPONENTS];

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {

                if (x == 0 || x == mapWidth - 1 || y == 0 || y == mapHeight - 1) {
                    mapGrid[x][y] = TerrainFactory.createBorder(x, y, tiler.getBorderTilePath());
                }
                else {
                    if (floorType == ProceduralGenerator.EXTERIOR) {
                        mapGrid[x][y] = tiler.getFloorTile(x, y, theme);
                    }
                    else {
                        mapGrid[x][y] = tiler.getWallTile(x, y);
                    }
                }

            }
        }

        mapRegions = Array2DHelper.fillIntArray(mapWidth, mapHeight, -1);
        objectGrid = Array2DHelper.createComponentGrid(mapWidth, mapHeight);
    }

    public void setFloor(int floor) {
        this.currentFloor = floor;
    }

    public MapData getMapData() {
        return new MapData(rooms, doors, objects, enemies, floorEntrance, floorExit, type);
    }

    public ArrayList<Component[]>[][] getObjects() {
        return objectGrid;
    }

    public Component[][][] getMapGrid() {
        return mapGrid;
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
        windingPercent = 20;
    }

    private void setThemeAsDungeon() {
        theme = RoomStyles.MANSION;
        themeKey = MansionTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 1000;
        windingPercent = 50;
    }

    private void setThemeAsMansion() {
        theme = RoomStyles.MANSION;
        themeKey = MansionTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 4000;
        windingPercent = 30;
    }

    private void setThemeAsRuins() {
        theme = RoomStyles.RUINS;
        themeKey = RuinsTileset.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 2000;
        windingPercent = 20;
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

        generateCorridors(chunk);
        connectRegions();
        removeDeadEnds();
        checkForBrokenDoors();

        calculateGoals();

        decorator = new MansionDecorator(mapWidth, mapHeight, theme, themeKey, assetManager);
        decorator.setGeneratorData(mapGrid, objects, objectGrid, enemies);
        decorator.decorateRooms(rooms);
        objects = decorator.getObjects();
        objectGrid = decorator.getObjectGrid();
        enemies = decorator.getEnemies();
        Log.v("log", "initial enemies size: " + enemies.size());

        removeHiddenWalls();
        removeInaccessibleCells();
    }

    private float mansionRuinChance = 0.1f;

    private void generateMansion() {
        // Ignore border tiles
        Chunk map = new Chunk(1, 1, mapWidth - 2, mapHeight - 2);

        ArrayList<Chunk> mapHalves = splitChunkInHalf(map);

        rooms = new ArrayList<>();

        ArrayList<Chunk> chunks = getHallwayChunks(map);
        // chunks.addAll(getHallwayChunks(mapHalves.get(1)));
        splitChunksIntoRooms(chunks);
        carveRooms();

        generateCorridors(map);
        connectRegions();
        removeDeadEnds();
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
        decorator.setGeneratorData(mapGrid, objects, objectGrid, enemies);
        decorator.decorateRooms(rooms);
        objects = decorator.getObjects();
        objectGrid = decorator.getObjectGrid();
        enemies = decorator.getEnemies();
        Log.v("log", "initial enemies size: " + enemies.size());
    }

    private void generateRuins() {
        Chunk chunk = new Chunk(0, 0, mapWidth, mapHeight);
        generateRandomRooms(chunk);
        carveRooms();
        generateCorridors(chunk);
        removeDeadEnds();
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
        Chunk map = new Chunk(1, 1, mapWidth - 2, mapHeight - 2);
        floorEntrance = new Vector(1, 1);
        floorExit = new Vector(3, 3);

        PoissonDiskSampler sampler = new PoissonDiskSampler();
        int minDistance = 3;
        int pointCount = 5;
        ArrayList<Vector> treePositions = sampler.generatePoisson(map.width, map.height, minDistance, pointCount);
        for (Vector cell : treePositions) {
            objects.add(DecalFactory.createDecoration(cell.x, cell.y, ExteriorTileset.TREES[rng.getRandomInt(0, ExteriorTileset.TREES.length - 1)]));
        }

        CellularAutomata automata = new CellularAutomata();
        automata.setParams(4, 3, 2, 0.3f);
        boolean[][] forest = automata.generate(map);

        for (int x = 0; x < forest.length; x++) {
            for (int y = 0; y < forest[0].length; y++) {
                if (forest[x][y]) {
                    objects.add(DecalFactory.createDecoration(x, y, ExteriorTileset.TREES[rng.getRandomInt(0, ExteriorTileset.TREES.length - 1)]));
                }
            }
        }

        automata.setParams(4, 3, 2, 0.3f);
        boolean[][] lakes = automata.generate(map);

        for (int x = 0; x < lakes.length; x++) {
            for (int y = 0; y < lakes[0].length; y++) {
                if (lakes[x][y]) {
                    objects.add(DecalFactory.createDecoration(x, y, ExteriorTileset.WATER_1));
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
                    carve(new Vector(x, splitY), MansionTileset.WOOD_FLOOR_1);
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
                    carve(new Vector(splitX, y), MansionTileset.WOOD_FLOOR_1);
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

    private void splitChunksIntoRooms(ArrayList<Chunk> chunks) {
        ArrayList<Chunk> secondPass = new ArrayList<>();

        // First, split the chunks into multiple smaller ones
        for (Chunk chunk : chunks) {
            ArrayList<Chunk> splitRooms = splitRoomChunk(chunk);

            for (Chunk room : splitRooms) {
                if (room.width * room.height > 80) {
                    secondPass.add(room);
                }
                else {
                    rooms.add(new Room(room.x + 1, room.y + 1, room.width - 2, room.height - 2));
                }
            }
        }

        Log.v(LOG_TAG, "Second pass: " + secondPass.size());

        // Now do a second pass where we try to split bigger rooms into smaller ones using brute force.
        // It's not a huge problem to have larger rooms, but ideally these would be rare
        for (Chunk chunk : secondPass) {
            ArrayList<Chunk> splitRooms = splitChunkInHalf(chunk);

            for (Chunk room : splitRooms) {
                rooms.add(new Room(room.x + 1, room.y + 1, room.width - 2, room.height - 2));
            }
        }

        Log.v(LOG_TAG, "Split chunks into " + rooms.size() + " rooms");
    }
/*
    ------------------------------------------------------------------------------------------
    Room generation
    ------------------------------------------------------------------------------------------
*/

    private void generateRandomRooms(Chunk chunk) {
        for (int i = 0; i < roomDensity; i++) {
            Room newRoom = generateRandomRoom(chunk);
            if (newRoom != null) {
               rooms.add(newRoom);
            }
        }
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
                mapGrid[north.x()][north.y()] = TerrainFactory.createWall(north.x, north.y, themedTile);
            }

            if (inBounds(south)) {
                mapGrid[south.x()][south.y()] = TerrainFactory.createWall(south.x, south.y, themedTile);
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector east = new Vector(room.x() - 1, y);
            Vector west = new Vector(room.x() + room.width(), y);

            if (!adjacentCellsAreCarvable(east) || !adjacentCellsAreCarvable(west)) break;

            if (inBounds(east)) {
                mapGrid[east.x()][east.y()] = TerrainFactory.createWall(east.x, east.y, themedTile);
            }

            if (inBounds(west)) {
                mapGrid[west.x()][west.y()] = TerrainFactory.createWall(west.x, west.y, themedTile);
            }
        }
    }

    private void carveRoomFloor(Room room) {
        int right = room.x() + room.width();
        int bottom = room.y() + room.height();

        int carved = 0;

        for (int x = room.x(); x < right; x++) {
            for (int y = room.y(); y < bottom; y++) {
                carve(new Vector(x, y), tiler.getFloorTile(x, y, currentRoomTheme));
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
            Component[] cell = getMapObjectForCell(position);
            Stationary stat = ComponentFinder.getStaticComponent(cell);
            if (stat.type == Stationary.WALL) {
                it.remove();
            }
        }
    }

    private void calculateGoals() {
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

        objects.add(entrance);

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
            objects.add(exit);

            Log.v(LOG_TAG, "path from start to finish was " + furthest + " moves");
        }

        else {
            Log.e(LOG_TAG, "Couldn't find room to place exit");
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
                Log.d(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector direction : Directions.All.values()) {
                Vector adjacentNode = currentNode.add(direction);

                if (!inBounds(adjacentNode)) continue;

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

    private boolean detectCollisions(Vector position) {
        int x = position.x();
        int y = position.y();

        Component[] terrain = mapGrid[x][y];
        Physics physics = ComponentFinder.getPhysicsComponent(terrain);

        if (physics.isBlocking || !physics.isTraversable) {
            return true;
        }

        ArrayList<Component[]> objectStack = objectGrid[x][y];

        if (objectStack == null || objectStack.size() == 0) {
            return false;
        }

        for (Component[] object : objectStack) {

            physics = ComponentFinder.getPhysicsComponent(object);

            if (physics.isBlocking || !physics.isTraversable) {
                return true;
            }
        }

        return false;
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
                    carve(new Vector(x + chunk.x, y + chunk.y), RuinsTileset.FLOOR);
                }
            }
        }
    }

/*
    ---------------------------------------------
    Corridor generation
    ---------------------------------------------
*/

    private void generateCorridors(Chunk chunk) {
        int right = chunk.x + chunk.width;
        int top = chunk.y + chunk.height;

        for (int x = chunk.x + 1; x < right - 1; x++) {
            for (int y = chunk.y - 1; y < top - 1; y++) {
                Vector coords = new Vector(x, y);
                Stationary stat = ComponentFinder.getStaticComponent(getMapObjectForCell(coords));
                if (stat.type == Stationary.WALL && adjacentCellsAreCarvable(coords)) {
                    carveMaze(coords);
                }
            }
        }
    }

    private void carveMaze(Vector start) {
        ArrayList<Vector> cells = new ArrayList<>();
        Vector lastCell = null;
        startRegion();
        carve(start, MansionTileset.WOOD_FLOOR_1);
        cells.add(start);

        while (cells.size() > 0) {
            Vector cell = cells.remove(cells.size() - 1);

            // See which adjacent cells are open.
            HashMap<String, Vector> unmadeCells = new HashMap<>();

            HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

            for (Map.Entry pair : adjacentCells.entrySet()) {
                String direction = (String) pair.getKey();
                Vector adjacentCell = (Vector) pair.getValue();

                if (canCarve(adjacentCell, getVectorForDirection(direction))) {
                    unmadeCells.put(adjacentCell.toString(), adjacentCell);
                }
            }

            if (unmadeCells.size() > 0) {
                Vector firstCarve;

                if (lastCell != null && unmadeCells.containsKey(lastCell.toString()) && rng.getRandomInt(0, 100) > windingPercent) {
                    firstCarve = lastCell;
                } else {
                    firstCarve = (Vector) unmadeCells.values().toArray()[rng.getRandomInt(0, unmadeCells.size() - 1)];
                }

                Vector secondCarve = firstCarve.add(getVectorForDirection(firstCarve.getDirection()));

                carve(firstCarve, MansionTileset.WOOD_FLOOR_1);
                carve(secondCarve, MansionTileset.WOOD_FLOOR_1);

                cells.add(secondCarve);
                lastCell = firstCarve;
            }

            else {
                // No adjacent uncarved cells.
                if (cells.size() > 0) {
                    cells.remove(cells.size() - 1);
                }

                // This path has ended.
                lastCell = null;
            }
        }
    }

    private void connectRegions() {
        // Find all of the tiles that can connect two (or more) regions.
        HashMap<Vector, Set> connectorRegions = new HashMap<>();

        for (int x = 1; x < mapWidth; x++) {
            for (int y = 1; y < mapHeight; y++) {
                Vector cell = new Vector(x, y, "");

                // Ignore everything but walls
                Stationary stat = ComponentFinder.getStaticComponent(getMapObjectForCell(cell));
                if (stat.type != Stationary.WALL) continue;

                Set<Integer> regions = new HashSet<>();

                HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

                for (Vector adjacentCell : adjacentCells.values()) {
                    if (!inBounds(adjacentCell)) continue;

                    int region = mapRegions[adjacentCell.x()][adjacentCell.y()];
                    if (region > -1) {
                        regions.add(region);
                    }
                }

                if (regions.size() < 2) continue;

                connectorRegions.put(cell, regions);
            }
        }

        // Get array of connecting vectors from connectorRegions
        ArrayList<Vector> connectors = new ArrayList<>(connectorRegions.keySet());

        // Keep track of which regions have been merged. This maps an original region index to the one it has been merged to.
        SparseIntArray merged = new SparseIntArray();
        Set<Integer> openRegions = new HashSet<>();

        for (int i = 0; i <= currentRegion; i++) {
            merged.put(i, i);
            openRegions.add(i);
        }

        // Keep connecting regions until we're down to one.
        while (openRegions.size() > 1 && connectors.size() > 0) {
            Vector connector = connectors.get(rng.getRandomInt(0, connectors.size() - 1));

            addJunction(connector);

            // Merge the connected regions. We'll pick one region (arbitrarily) and map all of the other regions to its index.
            ArrayList<Integer> arrayFromConnector = new ArrayList<>(connectorRegions.get(connector));
            ArrayList<Integer> regions = new ArrayList<>();

            for (int region : arrayFromConnector) {
                regions.add(merged.get(region));
            }

            int dest = regions.get(0);
            List<Integer> sources = regions.subList(1, regions.size());

            // Merge all of the affected regions. We have to look at *all* of the
            // regions because other regions may have previously been merged with
            // some of the ones we're merging now.
            for (int i = 0; i <= currentRegion; i++) {
                if (sources.contains(merged.get(i))) {
                    merged.put(i, dest);
                }
            }

            // The sources are no longer in use.
            for (int source : sources) {
                openRegions.remove(source);
            }

            // Remove any connectors that aren't needed anymore
            Iterator<Vector> it = connectors.iterator();

            while (it.hasNext()) {
                Vector pos = it.next();

                // Don't allow connectors right next to each other.
                if (connector.subtract(pos).getMagnitude() < 2) {
                    it.remove();
                    continue;
                }

                // If the connector no long spans different regions, we don't need it.

                ArrayList<Integer> regionsArray = new ArrayList<>(connectorRegions.get(pos));
                HashSet<Integer> spannedRegions = new HashSet<>();

                for (int region : regionsArray) {
                    spannedRegions.add(merged.get(region));
                }

                if (spannedRegions.size() <= 1)  {
                    // This connecter isn't needed, but connect it occasionally so that the
                    // dungeon isn't singly-connected.
                    if (rng.getRandomInt(0, extraConnectorChance) == 0) {
                        addJunction(pos);
                    }

                    it.remove();
                }
            }
        }
    }

    private void removeDeadEnds() {
        boolean done = false;

        while (!done) {
            done = true;

            for (int x = 1; x < mapWidth - 1; x++) {
                for (int y = 1; y < mapHeight - 1; y++) {
                    Vector cell = new Vector(x, y);
                    Stationary stat = ComponentFinder.getStaticComponent(getMapObjectForCell(cell));
                    if (stat.type == Stationary.WALL) continue;

                    // If it only has one exit, it's a dead end.
                    int exits = 0;

                    HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

                    for (Vector adjacentCell : adjacentCells.values()) {
                        stat = ComponentFinder.getStaticComponent(getMapObjectForCell(adjacentCell));
                        if (stat.type != Stationary.WALL) {
                            exits++;
                        }
                    }

                    if (exits != 1) continue;

                    done = false;

                    setTile(cell, MansionTileset.WALL);
                }
            }
        }
    }

    private void addJunction(Vector cell) {
        setTile(cell, MansionTileset.DOORWAY);

        // Todo: chance to do something else here?

        Component[] door = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "door");

        if (door == null) {
            Log.e(LOG_TAG, "Error when creating door");
            return;
        }

        Position position = ComponentFinder.getPositionComponent(door);
        position.x = cell.x;
        position.y = cell.y;
        Sprite sprite = ComponentFinder.getSpriteComponent(door);
        sprite.path = tiler.getClosedDoorTilePath();

        doors.put(cell.toString(), door);
    }

    private void startRegion() {
        currentRegion++;
    }

    private void carve(Vector pos, String type) {
        setTile(pos, type);
        mapRegions[pos.x()][pos.y()] = currentRegion;
    }

    private void carve(Vector pos, Component[] tile) {
        setTile(pos, tile);
        mapRegions[pos.x()][pos.y()] = currentRegion;
    }

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < mapWidth && cell.y() >= 0 && cell.y() < mapHeight);
    }

    private boolean canCarve(Vector cell, Vector direction) {
        return inBounds(cell)
                && adjacentCellsAreCarvable(cell)
                && adjacentCellsAreCarvable(cell.add(direction));
    }

    private void removeInaccessibleCells() {
        HashMap<String, Boolean> checked = new HashMap<>();

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.containsKey(cell.toString())) {
                    Component[] tile = getMapObjectForCell(cell);
                    Stationary stat = ComponentFinder.getStaticComponent(tile);

                    if (stat == null) continue;

                    if (stat.type == Stationary.FLOOR && cellIsInaccessible(cell)) {
                        setTile(cell, MansionTileset.WALL);
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
     *  This is mainly for aesthetic reaons
     */

    private void removeHiddenWalls() {
        Set<String> checked = new HashSet<>();

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.contains(cell.toString())) {

                    Stationary stat = ComponentFinder.getStaticComponent(getMapObjectForCell(cell));

                    if (stat == null) continue;

                    if (stat.type == Stationary.WALL && cellIsInaccessible(cell)) {
                        setTile(cell, TerrainFactory.createBorder(x, y, GenericTileset.DEFAULT_BORDER));
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

    private void setTile(Vector pos, String type) {
        switch(type) {
            case MansionTileset.FLOOR:
                mapGrid[pos.x()][pos.y()] = tiler.getFloorTile(pos.x(), pos.y(), currentRoomTheme);
                break;

            case MansionTileset.WALL:
                mapGrid[pos.x()][pos.y()] = tiler.getWallTile(pos.x(), pos.y());
                break;

            case MansionTileset.DOORWAY:
                mapGrid[pos.x()][pos.y()] = tiler.getDoorwayTile(pos.x(), pos.y());
                break;

            default:
                mapGrid[pos.x()][pos.y()] = DecalFactory.createDecoration(pos.x, pos.y, type);
        }
    }

    private void setTile(Vector pos, Component[] tile) {
        mapGrid[pos.x()][pos.y()] = tile;
    }

    private Component[] getMapObjectForCell(Vector coords) {
        if (inBounds(coords)) {
            return mapGrid[coords.x()][coords.y()];
        }
        else {
            throw new Error("Coords (" + coords.x() + ", " + coords.y() + ") are not in bounds");
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
     * Adjacent cell is "carvable" if all adjacent cells are wall tiles (shader == Stationary.WALL)
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
                Stationary stat = ComponentFinder.getStaticComponent(getMapObjectForCell(adjacent));

                if (stat.type != Stationary.WALL) {
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

            Component[] tile = getMapObjectForCell(direction);
            Stationary stat = ComponentFinder.getStaticComponent(tile);

            if (stat.type != Stationary.WALL && stat.type != Stationary.BORDER) {
                return false;
            }
        }

        return true;
    }

    private Vector getVectorForDirection(String direction) {
        switch (direction) {
            case "up":
                return new Vector(0, 1);

            case "right":
                return new Vector(1, 0);

            case "down":
                return new Vector(0, -1);

            case "left":
                return new Vector(-1, 0);

            default:
                return new Vector(0, 0);
        }
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
