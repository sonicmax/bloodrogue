package com.sonicmax.bloodrogue.generator;

import android.util.Log;
import android.util.SparseIntArray;

import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Border;
import com.sonicmax.bloodrogue.engine.objects.Decoration;
import com.sonicmax.bloodrogue.engine.objects.Door;
import com.sonicmax.bloodrogue.engine.objects.Floor;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.engine.objects.Room;
import com.sonicmax.bloodrogue.engine.objects.Wall;
import com.sonicmax.bloodrogue.renderer.tilesets.All;
import com.sonicmax.bloodrogue.renderer.tilesets.Mansion;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

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

    private final static boolean CARVABLE = true;
    private final static boolean NOT_CARVABLE = false;

    private ArrayList<GameObject> mRooms;
    private HashMap<String, GameObject> mDoors;
    private ArrayList<GameObject> mObjects;
    private ArrayList<GameObject> mEnemies;
    private Vector mStartPosition;

    private int mMapWidth;
    private int mMapHeight;
    private int mType;

    private GameObject[][] mMapGrid;
    private int[][] mMapRegions;
    private ArrayList<GameObject>[][] mObjectGrid;

    private int mCurrentRegion = -1;
    private Set mRegions;

    // Configuration for maze generator
    private int mExtraConnectorChance = 20;
    private int mWindingPercent = 50;

    // Configuration for cavern generator
    private int mBirthLimit = 4;
    private int mDeathLimit = 3;
    private int mNumberOfSteps = 2;
    private double mChanceToStartAlive = 0.45;

    // Configuration for room generation
    private int mMinRoomWidth = 4;
    private int mMaxRoomWidth = 10;
    private int mMinRoomHeight = 4;
    private int mMaxRoomHeight = 10;
    private int mRoomDensity = 2000; // Higher value = more attempts to place non-colliding rooms

    private boolean mGeneratingCorridors = false;
    private int mTheme;
    private String mThemeKey;
    private int mCurrentRoomTheme;

    private MansionDecorator mDecorator;

    public ProceduralGenerator(int width, int height) {
        mMapWidth = width;
        mMapHeight = height;
        mRegions = new HashSet();
        mObjects = new ArrayList<>();
        mEnemies = new ArrayList<>();
        mDoors = new HashMap<>();
    }

	/*
		---------------------------------------------
		Initialisation
		---------------------------------------------
	*/

    public void initGrids() {
        mMapGrid = new GameObject[mMapWidth][mMapHeight];

        for (int x = 0; x < mMapWidth; x++) {
            for (int y = 0; y < mMapHeight; y++) {
                if (x == 0 || x == mMapWidth - 1 || y == 0 || y == mMapHeight - 1) {
                    mMapGrid[x][y] = new Border(x, y, getBorderTile(Mansion.KEY));
                }
                else {
                    mMapGrid[x][y] = getWallTile(x, y, Mansion.KEY);
                }

            }
        }

        mMapRegions = Array2DHelper.fillIntArray(mMapWidth, mMapHeight, -1);
        mObjectGrid = Array2DHelper.create(mMapWidth, mMapHeight);
    }

    public MapData getMapData() {
        return new MapData(mRooms, mDoors, mObjects, mEnemies, mStartPosition, mType);
    }

    public ArrayList<GameObject>[][] getObjects() {
        return mObjectGrid;
    }

    public GameObject[][] getMapGrid() {
        return mMapGrid;
    }

/*
    ---------------------------------------------
    Dungeon floor generators
    ---------------------------------------------
*/
    public void generate(int type) {
        switch(type) {
            case DUNGEON:
                generateDungeon();
                break;

            case MANSION:
                setThemeAsMansion();
                generateDungeon();
                break;

            default:
                throw new Error("Undefined map type");
        }
    }

    private void setThemeAsMansion() {
        mTheme = RoomStyles.MANSION;
        mThemeKey = Mansion.KEY;
        mMinRoomWidth = 3;
        mMaxRoomWidth = 7;
        mMinRoomHeight = 3;
        mMaxRoomHeight = 7;
        mRoomDensity = 1000;
        mWindingPercent = 20;
        initGrids();
    }

    public void generateDungeon() {
        generateRooms();
        carveRooms();

        mGeneratingCorridors = true;
        generateCorridors();
        connectRegions();
        removeDeadEnds();
        checkForBrokenDoors();
        mGeneratingCorridors = false;

        mDecorator = new MansionDecorator(mMapWidth, mMapHeight, mTheme, mThemeKey);
        mDecorator.setGeneratorData(mMapGrid, mObjects, mObjectGrid, mEnemies);
        mDecorator.decorateRooms(mRooms);
        mObjects = mDecorator.getObjects();
        mObjectGrid = mDecorator.getObjectGrid();
        mEnemies = mDecorator.getEnemies();
        Log.v("log", "initial enemies size: " + mEnemies.size());

        removeHiddenWalls();
        removeInaccessibleCells();
        calculateGoals();
    }


    /*var generateRuins() {
        mapData.mapType = MapTypes.RUINS;

        setThemeAsMansion();

        generateRooms();
        carveRooms();
        generateCorridors();
        removeDeadEnds();
        checkForBrokenDoors();
        decorateRooms();

        generateCaverns();
        removeHiddenWalls();
        removeInaccessibleCells();
        calculateGoals();
    }*/

/*
    ---------------------------------------------
    Room generation
    ---------------------------------------------
*/

    private void generateRooms() {
        mRooms = new ArrayList<>();

        for (int i = 0; i < mRoomDensity; i++) {
            GameObject newRoom = generateRoom();
            if (newRoom != null) {
               mRooms.add(newRoom);
            }
        }
    }

    private Room generateRoom() {
        int width = NumberGenerator.getRandomInt(mMinRoomWidth, mMaxRoomWidth);
        int height = NumberGenerator.getRandomInt(mMinRoomHeight, mMaxRoomHeight);
        int x = NumberGenerator.getRandomInt(1, mMapWidth - width - 2);
        int y = NumberGenerator.getRandomInt(1, mMapHeight - height - 2);

        Room newRoom = new Room(x, y, width, height);

        // If room is colliding with any existing rooms, return null.
        // Otherwise, return newly generated room
        for (GameObject room : mRooms) {
            if (AxisAlignedBoxTester.test((Room) room, newRoom)) {
                return null;
            }
        }

        return newRoom;
    }

    private void carveRooms() {
        for (GameObject room : mRooms) {
            startRegion();
            mCurrentRoomTheme = NumberGenerator.getRandomInt(0, 3);
            retextureWalls((Room) room);
            mCurrentRoomTheme = NumberGenerator.getRandomInt(0, 3);
            carveRoomFloor((Room) room);
        }
    }

    private void retextureWalls(Room room) {
        int right = room.x() + room.width() + 1;
        int top = room.y() + room.height();

        String themedTile = getThemedWallTile();

        for (int x = room.x() - 1; x <= right; x++) {
            Vector north = new Vector(x, top);
            Vector south = new Vector(x, room.y() - 1);

            if (!adjacentCellsAreCarvable(north) || !adjacentCellsAreCarvable(south)) break;

            if (inBounds(north)) {
                mMapGrid[north.x()][north.y()] = new Wall(north.x(), north.y(), themedTile);
            }

            if (inBounds(south)) {
                mMapGrid[south.x()][south.y()] = new Wall(south.x(), south.y(), themedTile);
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector east = new Vector(room.x() - 1, y);
            Vector west = new Vector(room.x() + room.width(), y);

            if (!adjacentCellsAreCarvable(east) || !adjacentCellsAreCarvable(west)) break;

            if (inBounds(east)) {
                mMapGrid[east.x()][east.y()] = new Wall(east.x(), east.y(), themedTile);
            }

            if (inBounds(west)) {
                mMapGrid[west.x()][west.y()] = new Wall(west.x(), west.y(), themedTile);
            }
        }
    }

    private String getThemedWallTile() {
        switch (mCurrentRoomTheme) {
            case 0:
                return Mansion.WALLPAPER_1;

            case 1:
                return Mansion.WALLPAPER_2;

            case 2:
                return Mansion.WALLPAPER_3;

            case 3:
                return Mansion.WOOD_WALL;

            default:
                return Mansion.WOOD_WALL;
        }
    }

    private void carveRoomFloor(Room room) {
        int right = room.x() + room.width();
        int bottom = room.y() + room.height();

        for (int x = room.x(); x < right; x++) {
            for (int y = room.y(); y < bottom; y++) {
                carve(new Vector(x, y), getFloorTile(x, y, mThemeKey));
            }
        }
    }

    /**
     *  Prevents issue where doors are sometimes placed over wall tiles.
     *  Should really figure out the cause of this... oh well
     */

    private void checkForBrokenDoors() {
        Iterator it = mDoors.values().iterator();

        while (it.hasNext()) {
            Door door = (Door) it.next();
            Vector position = new Vector(door.x(), door.y());
            if (getMapObjectForCell(position) instanceof Wall) {
                it.remove();
            }
        }
    }

    private void calculateGoals() {
        Room startRoom = (Room) mRooms.get(NumberGenerator.getRandomInt(0, mRooms.size() - 1));

        // Make sure that starting room is accessible
        while (!startRoom.isAccessible) {
            startRoom = (Room) mRooms.get(NumberGenerator.getRandomInt(0, mRooms.size() - 1));
        }

        startRoom.setEntrance();
        mStartPosition = startRoom.roundedCentre();
    }


/*
    ---------------------------------------------
    Cavern generation
    ---------------------------------------------
*/

    /*var generateCaverns() {
        mapData.mapType = MapTypes.CAVERNS;

        var cellMap = initialiseMap(createNewGrid(false));
        for (var i = 0; i < numberOfSteps; i++) {
            cellMap = doSimulationStep(cellMap);
        }

        for (var x = 1; x < mapSize - 1; x++) {
            for (var y = 1; y < mapSize - 1; y++) {
                if (cellMap[x][y]) {
                    carve(new Vector(x, y), Tiles.FLOOR);
                }
            }
        }

        removeHiddenWalls();
    };

    var initialiseMap(cellMap) {
        var width = cellMap.length;
        var height = cellMap[0].length;

        for (var x = 1; x < width - 1; x++) {
            for (var y = 1; y < height - 1; y++) {
                if (getRandomFloat(0, 1) < chanceToStartAlive){
                    cellMap[x][y] = true;
                }
            }
        }

        return cellMap;
    };

    var doSimulationStep(oldMap) {
        var newMap = createNewGrid(false);
        //Loop over each row and column of the map
        for (var x = 1; x < oldMap.length - 1; x++) {
            for (var y = 1; y < oldMap[0].length - 1; y++) {

                var neighbours = countAliveNeighbours(oldMap, x, y);

                //The new value is based on our simulation rules
                //First, if a cell is alive but has too few neighbours, kill it.

                if (oldMap[x][y]) {
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

    var countAliveNeighbours(cells, x, y) {
        var count = 0;

        for (var i = -1; i < 2; i++) {
            for (var j = -1; j < 2; j++) {
                var neighbourX = x + i;
                var neighbourY = y + j;

                // Do nothing if we're looking at the middle point
                if (i == 0 && j == 0) continue;

                    //In case the index we're looking at it off the edge of the map
                else if (neighbourX < 0 || neighbourY < 0
                        || neighbourX >= cells.length || neighbourY >= cells[0].length) {

                    count++;
                }

                // Otherwise, a normal check of the neighbour
                else if (cells[neighbourX][neighbourY]) {
                    count++;
                }
            }
        }

        return count;
    };*/


/*
    ---------------------------------------------
    Corridor generation
    ---------------------------------------------
*/

    private void generateCorridors() {
        for (int x = 1; x < mMapWidth - 1; x++) {
            for (int y = 1; y < mMapHeight - 1; y++) {
                Vector coords = new Vector(x, y);

                if (getMapObjectForCell(coords) instanceof Wall && adjacentCellsAreCarvable(coords)) {
                    carveMaze(coords);
                }
            }
        }
    }

    private void carveMaze(Vector start) {
        ArrayList<Vector> cells = new ArrayList<>();
        Vector lastCell = null;
        startRegion();
        carve(start, Mansion.FLOOR);
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

                if (lastCell != null && unmadeCells.containsKey(lastCell.toString()) && NumberGenerator.getRandomInt(0, 100) > mWindingPercent) {
                    firstCarve = lastCell;
                } else {
                    firstCarve = (Vector) unmadeCells.values().toArray()[NumberGenerator.getRandomInt(0, unmadeCells.size() - 1)];
                }

                Vector secondCarve = firstCarve.add(getVectorForDirection(firstCarve.getDirection()));

                carve(firstCarve, Mansion.FLOOR);
                carve(secondCarve, Mansion.FLOOR);

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

        for (int x = 1; x < mMapWidth; x++) {
            for (int y = 1; y < mMapHeight; y++) {
                Vector cell = new Vector(x, y, "");

                // Ignore everything but walls
                if (!(getMapObjectForCell(cell) instanceof Wall)) continue;

                Set<Integer> regions = new HashSet<>();

                HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

                for (Vector adjacentCell : adjacentCells.values()) {
                    if (!inBounds(adjacentCell)) continue;

                    int region = mMapRegions[adjacentCell.x()][adjacentCell.y()];
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

        for (int i = 0; i <= mCurrentRegion; i++) {
            merged.put(i, i);
            openRegions.add(i);
        }

        // Keep connecting regions until we're down to one.
        while (openRegions.size() > 1 && connectors.size() > 0) {
            Vector connector = connectors.get(NumberGenerator.getRandomInt(0, connectors.size() - 1));

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
            for (int i = 0; i <= mCurrentRegion; i++) {
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
                    if (NumberGenerator.getRandomInt(0, mExtraConnectorChance) == 0) {
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

            for (int x = 1; x < mMapWidth - 1; x++) {
                for (int y = 1; y < mMapHeight - 1; y++) {
                    Vector cell = new Vector(x, y);

                    if (getMapObjectForCell(cell) instanceof Wall) continue;

                    // If it only has one exit, it's a dead end.
                    int exits = 0;

                    HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, CARVABLE);

                    for (Vector adjacentCell : adjacentCells.values()) {
                        if (!(getMapObjectForCell(adjacentCell) instanceof Wall)) {
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

        if (NumberGenerator.getRandomInt(0, 1) == 0) {
            // mapData.doors.set(cell.toString(), new DoorObject(cell.x, cell.y, getOpenDoorTile(), getClosedDoorTile(), true));
        } else {
            mDoors.put(cell.toString(), new Door(cell.x(), cell.y(), getOpenDoorTile(), getClosedDoorTile()));
        }
    }

    private void startRegion() {
        mCurrentRegion++;
    }

    private void carve(Vector pos, String type) {
        setTile(pos, type);
        mMapRegions[pos.x()][pos.y()] = mCurrentRegion;
    }

    private void carve(Vector pos, GameObject tile) {
        setTile(pos, tile);
        mMapRegions[pos.x()][pos.y()] = mCurrentRegion;
    }

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < mMapWidth && cell.y() >= 0 && cell.y() < mMapHeight);
    }

    private boolean canCarve(Vector cell, Vector direction) {
        return inBounds(cell)
                && adjacentCellsAreCarvable(cell)
                && adjacentCellsAreCarvable(cell.add(direction));
    }

    private void removeInaccessibleCells() {
        HashMap<String, Boolean> checked = new HashMap<>();

        for (int x = 0; x < mMapWidth; x++) {
            for (int y = 0; y < mMapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.containsKey(cell.toString())) {
                    GameObject tile = getMapObjectForCell(cell);

                    if (tile instanceof Floor && cellIsInaccessible(cell)) {
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

        for (int x = 0; x < mMapWidth; x++) {
            for (int y = 0; y < mMapHeight; y++) {
                Vector cell = new Vector(x, y);

                if (!checked.contains(cell.toString())) {

                    if (getMapObjectForCell(cell) instanceof Wall && cellIsInaccessible(cell)) {
                        setTile(cell, new Border(x, y, All.DEFAULT_BORDER));
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
                mMapGrid[pos.x()][pos.y()] = getFloorTile(pos.x(), pos.y(), mThemeKey);
                break;

            case Mansion.WALL:
                mMapGrid[pos.x()][pos.y()] = getWallTile(pos.x(), pos.y(), mThemeKey);
                break;

            case Mansion.DOORWAY:
                mMapGrid[pos.x()][pos.y()] = getDoorwayTile(pos.x(), pos.y(), mThemeKey);
                break;

            default:
                mMapGrid[pos.x()][pos.y()] = new Decoration(pos.x(), pos.y(), type);
        }
    }

    private void setTile(Vector pos, GameObject tile) {
        mMapGrid[pos.x()][pos.y()] = tile;
    }

    private GameObject getMapObjectForCell(Vector coords) {
        if (inBounds(coords)) {
            return mMapGrid[coords.x()][coords.y()];
        }
        else {
            throw new Error("Coords (" + coords.x() + ", " + coords.y() + ") are not in bounds");
        }
    }

    private Floor getFloorTile(int x, int y, String theme) {
        switch (theme) {
            case Mansion.KEY:
                return new Floor(x, y, getThemedMansionRoomFloor());

            default:
                return new Floor(x, y, Mansion.FLOOR);
        }
    }

    private String getThemedMansionRoomFloor() {
        switch (mCurrentRoomTheme) {
            case 0:
                return Mansion.MARBLE_FLOOR_1;

            case 1:
                return Mansion.TILED_FLOOR_1;

            case 2:
            case 3:
                return Mansion.WOOD_FLOOR_1;

            default:
                return Mansion.WOOD_FLOOR_1;
        }
    }

    private Wall getWallTile(int x, int y, String theme) {
        switch (theme) {
            case Mansion.KEY:
                return new Wall(x, y, Mansion.WALLPAPER_3);

            default:
                return new Wall(x, y, Mansion.WALL);
        }
    }

    private Floor getDoorwayTile(int x, int y, String theme) {
        switch (theme) {
            case Mansion.KEY:
                if (mGeneratingCorridors) {
                    return new Floor(x, y, Mansion.WOOD_FLOOR_1, Floor.IS_DOORWAY);
                } else {
                    return new Floor(x, y, Mansion.MARBLE_FLOOR_1, Floor.IS_DOORWAY);
                }

            default:
                return new Floor(x, y, Mansion.WOOD_FLOOR_1, Floor.IS_DOORWAY);
        }
    }

    private String getOpenDoorTile() {
        switch (mThemeKey) {
            case Mansion.KEY:
                return Mansion.DOUBLE_DOORS_OPEN;

            default:
                return Mansion.OPEN_DOOR;
        }
    }

    private String getClosedDoorTile() {
        switch (mThemeKey) {
            case Mansion.KEY:
                return Mansion.DOUBLE_DOORS;

            default:
                return Mansion.CLOSED_DOOR;
        }
    }

    private String getBorderTile(String key) {
        switch (key) {
            case Mansion.KEY:
                return Mansion.BRICK_WALL;

            default:
                return All.DEFAULT_BORDER;
        }
    }

/*
    ---------------------------------------------
    Helper methods
    ---------------------------------------------
*/

    /**
     * Legacy method to get adjacent cells. Should replace with something using Directions class
     * Doesn't check whether new cells are in bounds.
     *
     * @param coords Vector of start position
     * @param lookahead Number of cells to move ahead
     * @param directlyAdjacent Whether to exclude diagonals
     * @return HashMap containing adjacent cells
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

            if (!inBounds(adjacent) || !(getMapObjectForCell(adjacent) instanceof Wall)
                    || getMapObjectForCell(adjacent) instanceof Border) {
                return false;
            }
        }

        return true;
    }

    private boolean cellIsInaccessible(Vector cell) {
        // Note: as player can't move diagonally, we don't need to check these cells.
        HashMap<String, Vector> directions = getAdjacentCells(cell, 1, false);

        for (Vector direction : directions.values()) {
            if (!inBounds(direction)) {
                return false;
            }

            GameObject tile = getMapObjectForCell(direction);

            if (!(tile instanceof Wall) || !(tile instanceof Border)) {
                return false;
            }
        }

        return true;
    };

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
