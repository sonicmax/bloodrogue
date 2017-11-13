package com.sonicmax.bloodrogue.generator;

import android.content.res.AssetManager;
import android.util.Log;
import android.util.SparseIntArray;

import com.sonicmax.bloodrogue.data.BlueprintParser;
import com.sonicmax.bloodrogue.data.JSONLoader;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Portal;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Stationary;
import com.sonicmax.bloodrogue.engine.factories.DecalFactory;
import com.sonicmax.bloodrogue.engine.factories.TerrainFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.tilesets.Ruins;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Room;
import com.sonicmax.bloodrogue.tilesets.All;
import com.sonicmax.bloodrogue.tilesets.Mansion;
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

    private final String FLOOR_TILE = "f";
    private final String WALL_TILE = "w";
    private final String DOORWAY_TILE = "d";

    private final static boolean CARVABLE = true;
    private final static boolean NOT_CARVABLE = false;

    private ArrayList<Room> rooms;
    private HashMap<String, Component[]> doors;
    private ArrayList<Component[]> objects;
    private ArrayList<Component[]> enemies;
    private Vector startPosition;
    private int type;

    private int mapWidth;
    private int mapHeight;

    private Component[][][] mapGrid;
    private int[][] mapRegions;
    private ArrayList<Component[]>[][] objectGrid;

    private int currentRegion = -1;
    private Set regions;

    // Configuration for maze generator
    private int extraConnectorChance = 40;
    private int windingPercent = 35;

    // Configuration for cavern generator
    private int birthLimit = 4;
    private int deathLimit = 3;
    private int numberOfSteps = 2;
    private float chanceToStartAlive = 0.4F;

    // Configuration for room generation
    private int minRoomWidth = 3;
    private int maxRoomWidth = 9;
    private int minRoomHeight = 3;
    private int maxRoomHeight = 9;
    private int roomDensity = 2000; // Higher value = more attempts to place non-colliding rooms

    private boolean generatingCorridors;
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
        this.generatingCorridors = false;
        this.currentFloor = 1;
    }

	/*
		---------------------------------------------
		Initialisation
		---------------------------------------------
	*/

	private final int MAX_COMPONENTS = 18;

    private void initGrids() {
        mapGrid = new Component[mapWidth][mapHeight][MAX_COMPONENTS];

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {

                if (x == 0 || x == mapWidth - 1 || y == 0 || y == mapHeight - 1) {
                    mapGrid[x][y] = TerrainFactory.createBorder(x, y, tiler.getBorderTilePath());
                }
                else {
                    mapGrid[x][y] = tiler.getWallTile(x, y);
                }

            }
        }

        mapRegions = Array2DHelper.fillIntArray(mapWidth, mapHeight, -1);
        objectGrid = Array2DHelper.createArrayList2D(mapWidth, mapHeight);
    }

    public void setFloor(int floor) {
        this.currentFloor = floor;
    }

    public MapData getMapData() {
        return new MapData(rooms, doors, objects, enemies, startPosition, type);
    }

    public ArrayList<Component[]>[][] getObjects() {
        return objectGrid;
    }

    public Component[][][] getMapGrid() {
        return mapGrid;
    }

/*
    ---------------------------------------------
    Dungeon floor generators
    ---------------------------------------------
*/
    public void generate(int type) {
        switch(type) {
            case DUNGEON:
                initGrids();
                generateDungeon();
                break;

            case MANSION:
                setThemeAsMansion();
                tiler = new Tiler(themeKey);
                initGrids();
                generateDungeon();
                break;

            case RUINS:
                setThemeAsRuins();
                initGrids();
                generateRuins();
                break;

            default:
                throw new Error("Undefined map shader");
        }
    }

    private void setThemeAsDungeon() {
        theme = RoomStyles.MANSION;
        themeKey = Mansion.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 1000;
        windingPercent = 50;
    }

    private void setThemeAsMansion() {
        theme = RoomStyles.MANSION;
        themeKey = Mansion.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 4000;
        windingPercent = 30;
    }

    private void setThemeAsRuins() {
        theme = RoomStyles.RUINS;
        themeKey = Ruins.KEY;
        minRoomWidth = 3;
        maxRoomWidth = 7;
        minRoomHeight = 3;
        maxRoomHeight = 7;
        roomDensity = 2000;
        windingPercent = 20;
    }

    public void generateDungeon() {
        generateRooms();
        carveRooms();

        generatingCorridors = true;
        generateCorridors();
        connectRegions();
        removeDeadEnds();
        checkForBrokenDoors();
        generatingCorridors = false;

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

    private void generateRuins() {
        generateRooms();
        carveRooms();
        generateCorridors();
        removeDeadEnds();
        checkForBrokenDoors();
        // decorateRooms();

        generateCaverns();
        removeHiddenWalls();
        removeInaccessibleCells();
        calculateGoals();
    }

/*
    ---------------------------------------------
    Room generation
    ---------------------------------------------
*/

    private void generateRooms() {
        rooms = new ArrayList<>();

        for (int i = 0; i < roomDensity; i++) {
            Room newRoom = generateRoom();
            if (newRoom != null) {
               rooms.add(newRoom);
            }
        }
    }

    private Room generateRoom() {
        int width = rng.getRandomInt(minRoomWidth, maxRoomWidth);
        int height = rng.getRandomInt(minRoomHeight, maxRoomHeight);
        int x = rng.getRandomInt(1, mapWidth - width - 2);
        int y = rng.getRandomInt(1, mapHeight - height - 2);

        Room newRoom = new Room(x, y, width, height);

        // If room is colliding with any existing rooms, return null.
        // Otherwise, return newly generated room

        // Todo: this is really inefficient.

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

    private ArrayList<Vector> getWallVectors(Room room) {
        ArrayList<Vector> walls = new ArrayList<>();

        int right = room.x() + room.width() + 1;
        int top = room.y() + room.height();

        for (int x = room.x() - 1; x <= right; x++) {
            Vector north = new Vector(x, top);
            Vector south = new Vector(x, room.y() - 1);

            if (inBounds(north)) {
                walls.add(north);
            }

            if (inBounds(south)) {
                walls.add(south);
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector east = new Vector(room.x() - 1, y);
            Vector west = new Vector(room.x() + room.width(), y);

            if (inBounds(east)) {
                walls.add(east);
            }

            if (inBounds(west)) {
                walls.add(west);
            }
        }

        return walls;
    }

    private void carveRoomFloor(Room room) {
        int right = room.x() + room.width();
        int bottom = room.y() + room.height();

        for (int x = room.x(); x < right; x++) {
            for (int y = room.y(); y < bottom; y++) {
                carve(new Vector(x, y), tiler.getFloorTile(x, y, currentRoomTheme));
            }
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
        Room startRoom = (Room) rooms.get(rng.getRandomInt(0, rooms.size() - 1));

        int count = 0;
        int roomCount = rooms.size();

        // Make sure that starting room is accessible
        while (!startRoom.isAccessible && count < roomCount) {
            startRoom = (Room) rooms.get(rng.getRandomInt(0, rooms.size() - 1));
            count++;
        }

        if (!startRoom.isAccessible) {
            // Todo: to handle this error, we should regenerate the terrain
            throw new Error("Start room was inaccessible!");
        }

        startRoom.setEntrance();
        startPosition = startRoom.roundedCentre();

        Component[] entrance = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "entranceStairs");
        Position position = (Position) entrance[0];
        position.x = startPosition.x;
        position.y = startPosition.y;
        Portal portal = (Portal) entrance[3];
        portal.destFloor = currentFloor - 1;

        objects.add(entrance);

        int furthest = 0;
        Vector furthestRoom = null;

        for (Room room : rooms) {
            Vector centre = room.roundedCentre();
            ArrayList<Vector> path = findShortestPath(startPosition, centre);
            int distance = path.size();
            if (distance > furthest) {
                furthestRoom = centre;
                furthest = distance;
            }
        }

        if (furthestRoom != null) {
            Component[] exit = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "exitStairs");

            position = (Position) exit[0];
            position.x = furthestRoom.x;
            position.y = furthestRoom.y;
            portal = (Portal) exit[3];
            portal.destFloor = currentFloor + 1;

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
    ---------------------------------------------
    Cavern generation
    ---------------------------------------------
*/

    private boolean[][] cellMap;

    private void generateCaverns() {
        cellMap = new boolean[mapWidth][mapHeight];

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                cellMap[x][y] = false;
            }
        }

        initialiseCellMap();

        for (int i = 0; i < numberOfSteps; i++) {
            cellMap = doSimulationStep();
        }

        for (int x = 1; x < mapWidth - 1; x++) {
            for (int y = 1; y < mapHeight - 1; y++) {
                if (cellMap[x][y]) {
                    carve(new Vector(x, y), Ruins.FLOOR);
                }
            }
        }

        removeHiddenWalls();
    }

    private void initialiseCellMap() {
        for (int x = 1; x < mapWidth - 1; x++) {
            for (int y = 1; y < mapHeight - 1; y++) {
                if (rng.getRandomFloat(0F, 1F) < chanceToStartAlive){
                    cellMap[x][y] = true;
                }
            }
        }
    }

    private boolean[][] doSimulationStep() {
        boolean[][] newMap = new boolean[mapWidth][mapHeight];

        for (int x = 0; x < mapWidth; x++) {
            for (int y = 0; y < mapHeight; y++) {
                newMap[x][y] = false;
            }
        }

        //Loop over each row and column of the map
        for (int x = 1; x < mapWidth - 1; x++) {
            for (int y = 1; y < mapHeight - 1; y++) {

                int neighbours = countAliveNeighbours(x, y);

                //The new value is based on our simulation rules
                //First, if a cell is alive but has too few neighbours, kill it.

                if (cellMap[x][y]) {
                    if (neighbours < deathLimit) {
                        newMap[x][y] = false;
                    }
                    else {
                        newMap[x][y] = true;
                    }
                }

                //Otherwise, if the cell is dead now, check if it has the right number of neighbours to be 'born'
                else {
                    if (neighbours > birthLimit) {
                        newMap[x][y] = true;
                    }
                    else {
                        newMap[x][y] = false;
                    }
                }
            }
        }

        return newMap;
    }

    private int countAliveNeighbours(int x, int y) {
        int count = 0;

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                int neighbourX = x + i;
                int neighbourY = y + j;

                // Do nothing if we're looking at the middle point
                if (i == 0 && j == 0) continue;

                    //In case the index we're looking at it off the edge of the map
                else if (neighbourX < 0 || neighbourY < 0
                        || neighbourX >= mapWidth || neighbourY >= mapHeight) {

                    count++;
                }

                // Otherwise, a normal check of the neighbour
                else if (cellMap[neighbourX][neighbourY]) {
                    count++;
                }
            }
        }

        return count;
    }

/*
    ---------------------------------------------
    Corridor generation
    ---------------------------------------------
*/

    private void generateCorridors() {
        for (int x = 1; x < mapWidth - 1; x++) {
            for (int y = 1; y < mapHeight - 1; y++) {
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
        carve(start, Mansion.WOOD_FLOOR_1);
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

                carve(firstCarve, Mansion.WOOD_FLOOR_1);
                carve(secondCarve, Mansion.WOOD_FLOOR_1);

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

                    setTile(cell, Mansion.WALL);
                }
            }
        }
    }

    private void addJunction(Vector cell) {
        setTile(cell, Mansion.DOORWAY);

        // Todo: chance to do something else here?

        Component[] door = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "door");

        if (door == null) {
            Log.e(LOG_TAG, "Error when creating door");
            return;
        }

        Position position = (Position) door[0];
        position.x = cell.x;
        position.y = cell.y;
        Sprite sprite = (Sprite) door[1];
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
                        setTile(cell, Mansion.WALL);
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
                        setTile(cell, TerrainFactory.createBorder(x, y, All.DEFAULT_BORDER));
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
            case Mansion.FLOOR:
                mapGrid[pos.x()][pos.y()] = tiler.getFloorTile(pos.x(), pos.y(), currentRoomTheme);
                break;

            case Mansion.WALL:
                mapGrid[pos.x()][pos.y()] = tiler.getWallTile(pos.x(), pos.y());
                break;

            case Mansion.DOORWAY:
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
