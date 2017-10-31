package com.sonicmax.bloodrogue.generator;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.factories.EnemyFactory;
import com.sonicmax.bloodrogue.engine.objects.LightSource;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Chest;
import com.sonicmax.bloodrogue.engine.objects.Decoration;
import com.sonicmax.bloodrogue.engine.objects.Floor;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.engine.objects.Room;
import com.sonicmax.bloodrogue.engine.objects.Wall;
import com.sonicmax.bloodrogue.generator.tilesets.All;
import com.sonicmax.bloodrogue.generator.tilesets.Mansion;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class MansionDecorator {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private int mChestChance = 0; // Higher number = less chance that chest will be generated

    // Configuration for enemy placement
    private int mMaxEnemyLevel = 1;
    private int mMinEnemies = 1;
    private int mMaxEnemies = 4;

    private int mOfficeCount = 0;
    private int mLibraryCount = 0;
    private int mGalleryCount = 0;
    private int mBedroomCount = 0;
    private int mBathroomCount = 0;

    private int mMapWidth;
    private int mMapHeight;
    private int mTheme;
    private String mThemeKey;

    private GameObject[][] mMapGrid;
    private ArrayList<GameObject> mObjects;
    private ArrayList<GameObject> mEnemies;
    private ArrayList<GameObject>[][] mObjectGrid;

    private RandomNumberGenerator mRng;

    public MansionDecorator(int mapWidth, int mapHeight, int theme, String key) {
        this.mMapWidth = mapWidth;
        this.mMapHeight = mapHeight;
        this.mTheme = theme;
        this.mThemeKey = key;
        this.mRng = new RandomNumberGenerator();
    }

    public void setGeneratorData(GameObject[][] mapGrid, ArrayList<GameObject> objects,
                                 ArrayList<GameObject>[][] objectGrid, ArrayList<GameObject> enemies) {
        mMapGrid = mapGrid;
        mObjects = objects;
        mObjectGrid = objectGrid;
        mEnemies = enemies;
    }

    public ArrayList<GameObject> getObjects() {
        return mObjects;
    }

    public ArrayList<GameObject>[][] getObjectGrid() {
        return mObjectGrid;
    }

    public ArrayList<GameObject> getEnemies() {
        return mEnemies;
    }

    /**
     * Iterates over array of Room objects and decides how to decorate them.
     * Decorations are added to object grid/object array and retrieved later
     *
     */

    public void decorateRooms(ArrayList<GameObject> rooms) {
        // Make copy of rooms array so we can modify it safely
        ArrayList<GameObject> roomsCopy = new ArrayList<>(rooms);

        // Designate 1 quarter of the map to a specific type of room & attempt to decorate each one.
        // Any rooms which are missed (eg. for being too small/big) will be decorated later
        int sectionSize = mMapWidth / 2;

        Room livingQuartersQuad = new Room(0, 0, sectionSize, sectionSize);

        Iterator it = roomsCopy.iterator();

        int bathroomCount = 0;

        while (it.hasNext()) {
            Room room = (Room) it.next();
            if (AxisAlignedBoxTester.test(livingQuartersQuad, room)) {
                if (bathroomCount <= 2) {
                    // Todo: we should make sure that bathrooms arent connected to each other or other inappropriate rooms
                    convertRoomToBathroom(room);
                    bathroomCount++;
                } else {
                    convertRoomToBedroom(room);
                }

                it.remove();
            }
        }

        Room officeQuad = new Room(sectionSize, 0, sectionSize, sectionSize);

        it = roomsCopy.iterator();

        while (it.hasNext()) {
            Room room = (Room) it.next();

            if (AxisAlignedBoxTester.test(officeQuad, room)) {
                convertRoomToOffice(room);
                it.remove();
            }
        }

        // Now iterate over the remaining rooms and assign random styles to theme
        for (GameObject room : roomsCopy) {
            furnishRoom(room);
        }

        // Iterate over unmodified rooms array and add lighting/chests/enemies/etc
        for (GameObject object : rooms) {

            Room room = (Room) object;

            if (getDoorsFromRoom(room) == 0) {
                Log.v(LOG_TAG, "inaccessible room");
                room.isAccessible = true;
            }

            addNorthWallTiles(room);
            addLightingToRoom(room);
            decorateNorthWall(room);
            addChestsToRoom(room);
            addEnemiesToRoom(room);
        }
    }

    private void furnishRoom(GameObject room) {
        if (mLibraryCount < 2) {
            convertRoomToLibrary((Room) room);
        }

        else if (mGalleryCount < 2) {
            convertRoomToArtGallery((Room) room);
        }

        else {
            convertRoomToOffice((Room) room);
        }
    }

    /**
     *  Art gallery rooms have paintings on north wall, with pedastals in front.
     */

    private void convertRoomToArtGallery(Room room) {
        if (room.width() < 5) return;

        Vector topLeft = new Vector(room.x(), room.y() + room.height());
        Vector topRight = new Vector(room.x() + room.width(), room.y() + room.height());

        ArrayList<String> paintings = new ArrayList<>(Arrays.asList(Mansion.PAINTINGS));

        for (int x = topLeft.x(); x < topRight.x(); x += 2) {
            Vector cell = new Vector(x, room.y() + room.height());
            GameObject object = getMapObjectForCell(cell);

            if (object instanceof Wall && paintings.size() > 0) {
                int random = mRng.getRandomInt(0, paintings.size() - 1);
                Decoration painting = new Decoration(x, cell.y(), paintings.remove(random));

                mObjects.add(painting);

                Decoration pedestal = new Decoration(x, cell.y() - 1, Mansion.PEDESTAL);

                if (!blocksDoorway(new Vector(pedestal.x(), pedestal.y()))) {
                    mObjects.add(pedestal);
                }
            }
        }

        populateObjectGrid();

        ArrayList<Vector> corners = getCornerTiles(room);

        int statueCount = 0;

        for (Vector corner : corners) {
            if (statueCount < 3 && !detectCollisions(corner) && !blocksDoorway(corner)) {
                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.STATUES[mRng.getRandomInt(0, Mansion.STATUES.length - 1)]));
                statueCount++;
            }
        }

        retextureFloor(room, Mansion.MARBLE_FLOOR_1);

        populateObjectGrid();
        mGalleryCount++;
    }

    /**
     *  Offices, bedrooms and bathrooms are all essentially the same - just rooms where
     *  specific pieces of furniture are placed at each corner.
     */

    private void convertRoomToOffice(Room room) {
        if (room.width() * room.height() > 30) return;

        // Add/subtract 1 from corner to account for position of wall
        ArrayList<Vector> corners = getCornerTiles(room);

        boolean deskAdded = false;
        boolean filingCabinetAdded = false;
        boolean plantAdded = false;

        // Iterate over each corner and attempt to place decorations in order of importance.

        for (Vector corner : corners) {
            if (!deskAdded) {
                if (blocksDoorway(corner)) continue;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.OFFICE_DESK));

                // Find adjacent space to place office chair. Can be skipped

                for (Vector direction : Directions.Cardinal.values()) {
                    Vector adjacent = corner.add(direction);
                    if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                        mObjects.add(new Decoration(adjacent.x(), adjacent.y(), Mansion.OFFICE_CHAIR));
                        break;
                    }
                }

                deskAdded = true;
                continue;
            }

            if (!filingCabinetAdded) {
                if (blocksDoorway(corner)) return;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.FILING_CABINET));

                filingCabinetAdded = true;
                continue;
            }

            if (!plantAdded) {
                if (blocksDoorway(corner)) return;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.OFFICE_PLANT));

                String[] floorTiles = new String[] {Mansion.WOOD_FLOOR_1, Mansion.WOOD_FLOOR_2, Mansion.WOOD_FLOOR_3, Mansion.TILED_FLOOR_1};
                retextureFloor(room, floorTiles[mRng.getRandomInt(0, floorTiles.length - 1)]);

                plantAdded = true;
            }
        }

        populateObjectGrid();
        mOfficeCount++;
    }

    private void convertRoomToBedroom(Room room) {
        if (room.width() * room.height() > 30) return;

        ArrayList<Vector> corners = getCornerTiles(room);

        boolean bedAdded = false;
        boolean wardrobeAdded = false;
        boolean plantAdded = false;

        // Iterate over each corner and attempt to place decorations in order of importance.

        for (Vector corner : corners) {
            if (!bedAdded) {
                if (blocksDoorway(corner)) continue;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.BEDS[mRng.getRandomInt(0, 1)]));

                // Find adjacent space to place bedside cabinet. Can be skipped

                for (Vector direction : Directions.Cardinal.values()) {
                    Vector adjacent = corner.add(direction);
                    if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                        mObjects.add(new Decoration(adjacent.x(), adjacent.y(), Mansion.BEDSIDE_CABINET));
                        break;
                    }
                }

                bedAdded = true;
                continue;
            }

            if (!wardrobeAdded) {
                if (blocksDoorway(corner)) continue;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.WARDROBE));
                wardrobeAdded = true;
                continue;
            }

            if (!plantAdded) {
                if (blocksDoorway(corner)) continue;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.OFFICE_PLANT));
                plantAdded = true;
            }
        }

        // We want bedrooms to have wooden floors
        String[] floor = new String[] {Mansion.WOOD_FLOOR_1, Mansion.WOOD_FLOOR_2, Mansion.WOOD_FLOOR_3};
        retextureFloor(room, floor[mRng.getRandomInt(0, floor.length - 1)]);

        populateObjectGrid();
        mBedroomCount++;
    }

    private void convertRoomToBathroom(Room room) {
        if (room.width() * room.height() > 25) return;

        ArrayList<Vector> corners = getCornerTiles(room);

        boolean toiletAdded = false;
        boolean sinkAdded = false;
        boolean bathAdded = false;

        // Iterate over each corner and attempt to place decorations in order of importance.

        for (Vector corner : corners) {
            if (!toiletAdded) {
                if (blocksDoorway(corner)) continue;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.TOILET));
                toiletAdded = true;
                continue;
            }

            if (!sinkAdded) {
                if (blocksDoorway(corner)) return;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.SINK));
                sinkAdded = true;
                continue;
            }

            if (!bathAdded) {
                if (blocksDoorway(corner)) return;

                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.BATH));
                bathAdded = true;
            }
        }

        // Give bathrooms a marble tiled floor
        String[] floor = new String[] {Mansion.MARBLE_FLOOR_2, Mansion.MARBLE_FLOOR_3, Mansion.MARBLE_FLOOR_4, Mansion.MARBLE_FLOOR_5};
        retextureFloor(room, floor[mRng.getRandomInt(0, floor.length - 1)]);

        populateObjectGrid();
        mBathroomCount++;
    }

    private ArrayList<Vector> getCornerTiles(Room room) {
        ArrayList<Vector> corners = new ArrayList<>();

        corners.add(new Vector(room.x(), room.y()));
        corners.add(new Vector(room.x() + room.width() - 1, room.y()));
        corners.add(new Vector(room.x(), room.y() + room.height() - 1));
        corners.add(new Vector(room.x() + room.width() - 1, room.y() + room.height() - 1));

        return corners;
    }

    private boolean blocksDoorway(Vector cell) {
        for (Vector direction : Directions.Cardinal.values()) {
            Vector adjacent = cell.add(direction);
            GameObject object = getMapObjectForCell(adjacent);
            if (object instanceof Floor && ((Floor) object).isDoorway()) {
                return true;
            }
        }

        return false;
    }

    private void addNorthWallTiles(Room room) {
        /*Vector topLeft = new Vector(room.x(), room.y() + room.height());
        Vector topRight = new Vector(room.x() + room.width(), room.y() + room.height());

        for (int x = topLeft.x(); x < topRight.x(); x++) {
            Vector cell = new Vector(x, room.y() + room.height());
            GameObject object = getMapObjectForCell(cell);

            if (object instanceof Wall && adjacentCellsAreCarvable(cell)) {
                object.setSprite(Mansion.WALLPAPER_1);
            }
        }*/
    }

    /**
     *  Finds appropriate place to add decoration/s and adds random decoration to mapData.
     *  50% possibility that it will add a decoration. Ignores rooms where the north wall
     *  only takes up a single block
     */

    private void decorateNorthWall(Room room) {
        Vector topLeft = new Vector(room.x(), room.y() + room.height());
        Vector topRight = new Vector(room.x() + room.width(), room.y() + room.height());
        Vector doorPosition = null;
        Vector itemCoord;

        int numberOfDoors = 0;

        // Check for doors
        for (int x = topLeft.x(); x < topRight.x(); x++) {
            Vector cell = new Vector(x, room.y() + room.height());
            GameObject object = getMapObjectForCell(cell);
            if (object instanceof Floor && ((Floor) object).isDoorway()) {
                numberOfDoors++;
                doorPosition = cell;

                if (numberOfDoors > 1) {
                    // Ignore rooms with multiple doorways on north wall
                    return;
                }
            }
        }

        if (doorPosition != null) {
            // Find largest section of wall and place a decoration roughly in the centre of this section.
            int lengthA = doorPosition.x() - topLeft.x();
            int lengthB = topRight.x() - doorPosition.x();

            if (lengthA > lengthB) {
                itemCoord = new Vector(topLeft.x() + (int) Math.floor(lengthA / 2), topLeft.y());
            }

            else {
                itemCoord = new Vector(doorPosition.x() + (int) Math.floor(lengthB / 2), topLeft.y());
            }

            if (getMapObjectForCell(itemCoord.add(Directions.Cardinal.get("NORTH"))) instanceof Floor) {
                // Ignore single-tiled walls
                return;
            }
        }

        else {
            // Place item in centre. TODO: multiple decorations, or multi-tile decorations
            itemCoord = new Vector(room.x() + (int) Math.floor(room.width() / 2), topLeft.y());
        }

        mObjects.add(new Decoration(itemCoord.x(), itemCoord.y(), Mansion.DECORATIONS[mRng.getRandomInt(0, Mansion.DECORATIONS.length - 1)]));
    }

    /**
     *  Replaces texture of Floor objects contained in room.
     */

    private void retextureFloor(Room room, String imgPath) {
        int right = room.x() + room.width();
        int bottom = room.y() + room.height();

        for (int x = room.x(); x < right; x++) {
            for (int y = room.y(); y < bottom; y++) {
                Vector pos = new Vector(x, y);
                if (inBounds(pos) && mMapGrid[x][y] instanceof Floor) {
                    mMapGrid[x][y] = new Floor(x, y, imgPath);
                }
            }
        }
    }

    /**
     *  Library rooms are full of rows/columns of bookshelves.
     *  Otherwise they are treated the same as any other room
     */

    private void convertRoomToLibrary(Room room) {
        if (room.height() < 5) return;

        if (mRng.getRandomInt(0, 1) == 0) { // Add rows of bookshelves

            // Check whether height is odd/even to decide where to start placing bookshelf rows
            int startPositionY;
            boolean isEven = false;

            if (room.height() % 2 == 1) {
                // Start against wall
                startPositionY = room.y();
            }
            else {
                // Start at square in front of wall
                startPositionY = room.y() + 1;
                isEven = true;
            }

            for (int y = startPositionY; y < startPositionY + room.height() + 1; y += 2) {
                Vector cell = new Vector(room.x(), y);
                addBookshelfRow(cell, isEven);
            }
        }

        else {
            // Check whether height is odd/even to decide where to start placing bookshelf rows
            int startPositionX;
            boolean isEven = false;

            if (room.width() % 2 == 1) {
                // Start against wall
                startPositionX = room.x();
            }
            else {
                // Start at square in front of wall
                startPositionX = room.x() + 1;
                isEven = true;
            }

            for (int x = startPositionX; x < startPositionX + room.width(); x += 2) {
                Vector cell = new Vector(x, room.y());
                addBookshelfColumn(cell, isEven);
            }
        }

        retextureFloor(room, Mansion.MARBLE_FLOOR_1);

        populateObjectGrid();
        mLibraryCount++;
    }

    private void addBookshelfColumn(Vector cell, boolean isEven) {
        while (inBounds(cell) && getMapObjectForCell(cell) instanceof Floor) {

            Floor floor = (Floor) getMapObjectForCell(cell);

            if (floor.isDoorway()) return;

            Vector lookahead = cell.add(Directions.Cardinal.get("NORTH"));

            if (!inBounds(lookahead) || getMapObjectForCell(lookahead) instanceof Floor) {

                floor = (Floor) getMapObjectForCell(lookahead);

                if (floor.isDoorway()) return;

                if (!cellBlocksDoorway(cell, isEven)) {
                    mObjects.add(new Decoration(cell.x(), cell.y(), Mansion.BOOKSHELVES[mRng.getRandomInt(0, Mansion.BOOKSHELVES.length - 1)]));
                }
            }

            else {
                return;
            }

            cell = cell.add(Directions.Cardinal.get("NORTH"));
        }
    }

    private void addBookshelfRow(Vector cell, boolean isEven) {

        while (inBounds(cell) && getMapObjectForCell(cell) instanceof Floor) {

            Floor floor = (Floor) getMapObjectForCell(cell);

            if (floor.isDoorway()) return;

            Vector lookahead = cell.add(Directions.Cardinal.get("EAST"));

            if (inBounds(lookahead) && getMapObjectForCell(lookahead) instanceof Floor) {

                floor = (Floor) getMapObjectForCell(lookahead);

                if (floor.isDoorway()) return;

                else if (!cellBlocksDoorway(cell, isEven)) {
                    mObjects.add(new Decoration(cell.x(), cell.y(), Mansion.BOOKSHELVES[mRng.getRandomInt(0, Mansion.BOOKSHELVES.length - 1)]));
                }
            }

            else {
                return;
            }

            cell = cell.add(Directions.Cardinal.get("EAST"));
        }
    }

    private boolean cellBlocksDoorway(Vector cell, boolean isEven) {

        for (Vector direction : Directions.Cardinal.values()) {
            Vector vector = cell.add(direction);

            if (!inBounds(vector)) continue;

            GameObject object = getMapObjectForCell(vector);

            if (object instanceof Floor && ((Floor) object).isDoorway()) {
                return true;
            }

            // We should check two cells ahead to make sure.
            // If cell is out of bounds we don't have to worry

            Vector newCell = cell.add(direction.scale(2));

            if (inBounds(newCell)) {
                object = getMapObjectForCell(cell.add(direction.scale(2)));
                if (isEven && object instanceof Floor && ((Floor) object).isDoorway()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addLightingToRoom(Room room) {
        String[] bearings = new String[] {"NORTH", "EAST", "WEST"};
        String bearing = bearings[mRng.getRandomInt(0, 2)];
        Vector direction = Directions.Cardinal.get(bearing);
        Vector cell = room.roundedCentre();

        while (inBounds(cell) && getMapObjectForCell(cell) instanceof Floor) {
            cell = cell.add(direction);
        }

        if (!inBounds(cell)) {
            return;
        }

        Vector fov = cell.subtract(direction);
        GameObject object = getMapObjectForCell(cell);

        if (!(object instanceof Floor) || !((Floor) object).isDoorway()) {
            LightSource lightSource = new LightSource(cell.x(), cell.y(), fov.x(), fov.y(), getLightSourceTile(bearing));
            room.addObject(lightSource);
        }
    }

    private String getLightSourceTile(String bearing) {
        switch (mThemeKey) {
            case Mansion.KEY:
                if (bearing.equals("EAST")) {
                    return Mansion.WALL_LANTERN_EAST;
                }

                else return Mansion.WALL_LANTERN;

            default:
                return All.LIGHT_SOURCE;
        }
    }

    private void addChestsToRoom(Room room) {
        int chanceToAdd = mRng.getRandomInt(0, mChestChance);

        if (chanceToAdd != 0) return;

        // Attempt to place the chest 8 times.
        // If this fails, just skip it

        for (Vector direction : Directions.All.values()) {
            Vector cell = room.roundedCentre();

            while (inBounds(cell) && getMapObjectForCell(cell) instanceof Floor) {
                cell = cell.add(direction);
            }

            cell = cell.subtract(direction);

            GameObject object = getMapObjectForCell(cell);

            if (!(object instanceof Floor) || ((Floor) object).isDoorway()) continue;

            if (detectCollisions(cell) || blocksDoorway(cell)) continue;

            Chest chest = new Chest(cell.x(), cell.y());

            if (objectNotBlockingPath(cell)) {
                mObjects.add(chest);
                populateObjectGrid();
                return;
            }
        }
    }

    /**
     *  Tests paths around object to make sure that it's not blocking corridor.
     *  Returns true if object can be placed
     */

    private boolean objectNotBlockingPath(Vector cell) {
        final int PATH_LIMIT = 8;

        // As player can move diagonally, we only have to test two paths: north-south and east-west.
        ArrayList<ArrayList<Vector>> paths = new ArrayList<>();

        paths.add(new ArrayList<Vector>());
        paths.get(0).add(Directions.Cardinal.get("NORTH"));
        paths.get(0).add(Directions.Cardinal.get("SOUTH"));

        paths.add(new ArrayList<Vector>());
        paths.get(1).add(Directions.Cardinal.get("EAST"));
        paths.get(1).add(Directions.Cardinal.get("WEST"));

        // Test paths to make sure that player can move around object.
        for (ArrayList<Vector> path : paths) {
            Vector start = cell.add(path.get(0));
            Vector end = cell.add(path.get(1));

            if (!inBounds(start) || !inBounds(end)) continue;

            if (detectCollisions(start) || detectCollisions(end)) continue;

            ArrayList<Vector> bestPath = findShortestPathAroundObstacle(start, end, cell);

            // If path length === 0, no safe paths were found.
            // If path length exceeds PATH_LIMIT, the object is blocking player's path in a detrimental way
            if (bestPath.size() == 0 || bestPath.size() > PATH_LIMIT) {
                return true;
            }
        }

        return false;
    }

    private ArrayList<Vector> findShortestPathAroundObstacle(Vector startNode, Vector goalNode, Vector obstacle) {
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
                ;       // This probably shouldn't happen
                Log.w(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector direction : Directions.All.values()) {
                Vector adjacentNode = currentNode.add(direction);

                if (!inBounds(adjacentNode)) continue;

                if (checkedNodes.contains(adjacentNode.toString())) continue;

                if (adjacentNode.equals(obstacle) || detectCollisions(adjacentNode)) continue;

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

    private void addEnemiesToRoom(Room room) {
        // First, decide whether we should place enemies at all
        if (mRng.getRandomInt(0, 2) == 0) return;

        // Divide room size by 9 to find how many enemies would reasonably fit in room.
        // (eg. 1 enemy for 3x3, 2 for 5x5, etc)
        // Compare this value to mMaxEnemies and use smallest value.
        // Then choose random number between minimum enemy count + this value

        int enemyFit = (room.width() * room.height()) / 9;
        int numberOfEnemies = mRng.getRandomInt(mMinEnemies, Math.min(enemyFit, mMaxEnemies));

        // We don't want player to be mobbed by several enemies when starting a new floor.
        if (room.isEntrance() && numberOfEnemies > 1) {
            numberOfEnemies = 1;
        }

        // Iterate over each tile in room and find empty positions.
        // Avoid rounded centre because that is where player character would be placed

        ArrayList<Vector> empty = new ArrayList<>();
        Vector centre = room.roundedCentre();

        for (int x = room.x() + 1; x < room.x() + room.width() - 1; x++) {
            for (int y = room.y() + 1; y < room.y() + room.height() - 1; y++) {
                Vector pos = new Vector(x, y);
                if (!pos.equals(centre) && !detectCollisions(new Vector(x, y))) {
                    empty.add(pos);
                }
            }
        }

        // Now pick random positions until enemy count or empty tile array is exhausted.
        // This will distribute enemies somewhat randomly (compared to deploying in rows or columns)

        int count = 0;

        while (count <= numberOfEnemies && empty.size() > 0) {
            int random = mRng.getRandomInt(0, empty.size() - 1);
            Vector pos = empty.remove(random);

            int level = mRng.getRandomInt(1, mMaxEnemyLevel);
            mEnemies.add(EnemyFactory.getRandomEnemy(pos.x(), pos.y(), level));
            populateObjectGrid();
            count++;
        }
    }

    /**
     *  Iterates over bounds of room and returns number of doorways.
     */

    private int getDoorsFromRoom(Room room) {
        int doorCount = 0;

        int right = room.x() + room.width() + 1;
        int top = room.y() + room.height();

        for (int x = room.x() - 1; x <= right; x++) {
            Vector north = new Vector(x, top);
            Vector south = new Vector(x, room.y() - 1);

            if (inBounds(north)) {
                if (getMapObjectForCell(north) instanceof Floor) {
                    Floor floor = (Floor) getMapObjectForCell(north);
                    if (floor.isDoorway()) {
                        doorCount++;
                    }
                }
            }

            if (inBounds(south)) {
                if (getMapObjectForCell(south) instanceof Floor) {
                    Floor floor = (Floor) getMapObjectForCell(south);
                    if (floor.isDoorway()) {
                        doorCount++;
                    }
                }
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector east = new Vector(room.x() - 1, y);
            Vector west = new Vector(room.x() + room.width(), y);

            if (inBounds(east)) {
                if (getMapObjectForCell(east) instanceof Floor) {
                    Floor floor = (Floor) getMapObjectForCell(east);
                    if (floor.isDoorway()) {
                        doorCount++;
                    }
                }
            }

            if (inBounds(west)) {
                if (getMapObjectForCell(west) instanceof Floor) {
                    Floor floor = (Floor) getMapObjectForCell(west);
                    if (floor.isDoorway()) {
                        doorCount++;
                    }
                }
            }
        }

        return doorCount;
    }

    /*
    ---------------------------------------------
    Object grid manipulation and testing
    ---------------------------------------------
    */

    private void addObjectToStack(int x, int y, GameObject object) {
        mObjectGrid[x][y].add(object);
    }

    private GameObject getMapObjectForCell(Vector coords) {
        if (inBounds(coords)) {
            return mMapGrid[coords.x()][coords.y()];
        }
        else {
            throw new Error("Coords (" + coords.x() + ", " + coords.y() + ") are not in bounds");
        }
    }

    /**
     * Tests grid square to see if there are any objects that can be collided with
     *
     * @param position Vector to check
     * @return True if collisions would occur
     */

    private boolean detectCollisions(Vector position) {
        int x = position.x();
        int y = position.y();

        GameObject mapTile = mMapGrid[x][y];

        if (mapTile.isBlocking()) {
            // Log.v("log", "map getSprite " + mapTile.getSprite() + " was blocking");
            return true;
        }

        else if (!mapTile.isTraversable()) {
            // Log.v("log", "map getSprite " + mapTile.getSprite() + " was not traversable");
            return true;
        }

        ArrayList<GameObject> objectStack = mObjectGrid[x][y];

        if (objectStack == null) return false;

        for (GameObject object : objectStack) {

            if (object.isBlocking()) {
                // Log.v("log", "object getSprite " + object.getSprite() + " was blocking");
                return true;
            }

            else if (!object.isTraversable()) {
                // Log.v("log", "object getSprite " + object.getSprite() + " was not traversable");
                return true;
            }
        }

        return false;
    }

    private void populateObjectGrid() {
        mObjectGrid = Array2DHelper.create(mMapWidth, mMapHeight);

        for (GameObject d : mObjects) {
            addObjectToStack(d.x(), d.y(), d);
        }

        for (GameObject e : mEnemies) {
            addObjectToStack(e.x(), e.y(), e);
        }
    }

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < mMapWidth && cell.y() >= 0 && cell.y() < mMapHeight);
    }
}
