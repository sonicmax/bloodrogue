package com.sonicmax.bloodrogue.generator;

import com.sonicmax.bloodrogue.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.objects.EnemyFactory;
import com.sonicmax.bloodrogue.objects.LightSource;
import com.sonicmax.bloodrogue.maths.Vector;
import com.sonicmax.bloodrogue.objects.Chest;
import com.sonicmax.bloodrogue.objects.Decoration;
import com.sonicmax.bloodrogue.objects.Floor;
import com.sonicmax.bloodrogue.objects.GameObject;
import com.sonicmax.bloodrogue.objects.Room;
import com.sonicmax.bloodrogue.objects.Wall;
import com.sonicmax.bloodrogue.tilesets.All;
import com.sonicmax.bloodrogue.tilesets.Mansion;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.NumberGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

public class Decorator {
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

    public Decorator(int mapWidth, int mapHeight, int theme, String key) {
        this.mMapWidth = mapWidth;
        this.mMapHeight = mapHeight;
        this.mTheme = theme;
        this.mThemeKey = key;
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
     * @param oldRooms
     */

    public void decorateRooms(ArrayList<GameObject> oldRooms) {
        // Make copy of rooms array so we can modify it safely
        ArrayList<GameObject> rooms = new ArrayList<>(oldRooms);

        // Designate 1 quarter of the map to a specific type of room & attempt to decorate each one.
        // Any rooms which are missed (eg. for being too small/big) will be decorated later
        int sectionSize = mMapWidth / 2;

        Room livingQuartersQuad = new Room(0, 0, sectionSize, sectionSize);

        Iterator it = rooms.iterator();

        while (it.hasNext()) {
            Room room = (Room) it.next();

            if (AxisAlignedBoxTester.test(livingQuartersQuad, room)) {
                if (NumberGenerator.getRandomInt(0, 2) > 0) {
                    convertRoomToBedroom(room);
                } else {
                    convertRoomToBathroom(room);
                }

                it.remove();
            }
        }

        Room officeQuad = new Room(sectionSize, 0, sectionSize, sectionSize);

        it = rooms.iterator();

        while (it.hasNext()) {
            Room room = (Room) it.next();

            if (AxisAlignedBoxTester.test(officeQuad, room)) {
                convertRoomToOffice(room);
                it.remove();
            }
        }

        // Now iterate over the remaining rooms and assign random styles to theme
        for (GameObject room : rooms) {
            furnishRoom(room);
        }

        // Iterate over unmodified rooms array and add lighting/chests/enemies/etc
        for (GameObject object : oldRooms) {

            Room room = (Room) object;

            if (getDoorsFromArea(room.roundedCentre()) == 0) {
                room.isAccessible = true;
            }

            addNorthWallTiles(room);
            addLightingToRoom(room);
            decorateNorthWall(room);
            addChestsToRoom(room);
            addEnemiesToRoom(room);
        }
    }

    public void furnishRoom(GameObject room) {
        int roomChance = NumberGenerator.getRandomInt(0, 2);

        if (mOfficeCount < 3 && roomChance == 0) {
            convertRoomToOffice((Room) room);
        }

        if (mLibraryCount < 2 && roomChance == 1) {
            convertRoomToLibrary((Room) room);
        }

        if (mGalleryCount < 2 && roomChance == 2) {
            convertRoomToArtGallery((Room) room);
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
                int random = NumberGenerator.getRandomInt(0, paintings.size() - 1);
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
                mObjects.add(new Decoration(corner.x(), corner.y(), Mansion.STATUES[NumberGenerator.getRandomInt(0, Mansion.STATUES.length - 1)]));
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
        if (room.width() * room.height() > 25) return;

        // Add/subtract 1 from corner to account for position of wall
        ArrayList<Vector> corners = new ArrayList<>();
        corners.add(new Vector(room.x(), room.y()));
        corners.add(new Vector(room.x() + room.width() - 1, room.y()));
        corners.add(new Vector(room.x(), room.y() + room.height() - 1));
        corners.add(new Vector(room.x() + room.width() - 1, room.y() + room.height() - 1));

        // Pick corner to place desk. Make sure it's not blocking doorway
        Vector deskLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(deskLocation)) return;

        mObjects.add(new Decoration(deskLocation.x(), deskLocation.y(), Mansion.OFFICE_DESK));

        // Find adjacent space to place office chair. Can be skipped

        for (Vector direction : Directions.Cardinal.values()) {
            Vector adjacent = deskLocation.add(direction);
            if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                mObjects.add(new Decoration(adjacent.x(), adjacent.y(), Mansion.OFFICE_CHAIR));
                break;
            }
        }

        // Find corner to add filing cabinets. If we placed table but failed to place cabinet, skip
        Vector filingCabinetLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(filingCabinetLocation)) return;

        mObjects.add(new Decoration(filingCabinetLocation.x(), filingCabinetLocation.y(), Mansion.FILING_CABINET));

        // Finally, place office plant
        Vector plantLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(plantLocation)) return;

        mObjects.add(new Decoration(plantLocation.x(), plantLocation.y(), Mansion.OFFICE_PLANT));

        String[] floorTiles = new String[] {Mansion.WOOD_FLOOR_1, Mansion.WOOD_FLOOR_2, Mansion.WOOD_FLOOR_3, Mansion.TILED_FLOOR_1};
        retextureFloor(room, floorTiles[NumberGenerator.getRandomInt(0, floorTiles.length - 1)]);

        populateObjectGrid();
        mOfficeCount++;
    }

    private void convertRoomToBedroom(Room room) {
        if (room.width() * room.height() > 30) return;

        ArrayList<Vector> corners = getCornerTiles(room);

        // Pick corner to place desk. Make sure it's not blocking doorway
        Vector bedLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(bedLocation)) return;

        mObjects.add(new Decoration(bedLocation.x(), bedLocation.y(), Mansion.BEDS[NumberGenerator.getRandomInt(0, 1)]));

        // Find adjacent space to place office chair. Can be skipped

        for (Vector direction : Directions.Cardinal.values()) {
            Vector adjacent = bedLocation.add(direction);
            if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                mObjects.add(new Decoration(adjacent.x(), adjacent.y(), Mansion.BEDSIDE_CABINET));
                break;
            }
        }

        Vector wardrobeLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(wardrobeLocation)) return;

        mObjects.add(new Decoration(wardrobeLocation.x(), wardrobeLocation.y(), Mansion.WARDROBE));

        Vector plantLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(plantLocation)) return;

        mObjects.add(new Decoration(plantLocation.x(), plantLocation.y(), Mansion.OFFICE_PLANT));

        String[] floor = new String[] {Mansion.WOOD_FLOOR_1, Mansion.WOOD_FLOOR_2, Mansion.WOOD_FLOOR_3};
        retextureFloor(room, floor[NumberGenerator.getRandomInt(0, floor.length - 1)]);

        populateObjectGrid();
        mBedroomCount++;
    }

    private void convertRoomToBathroom(Room room) {
        if (room.width() * room.height() > 25) return;

        ArrayList<Vector> corners = getCornerTiles(room);

        Vector toiletLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(toiletLocation)) return;

        mObjects.add(new Decoration(toiletLocation.x(), toiletLocation.y(), Mansion.TOILET));

        Vector sinkLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(sinkLocation)) return;

        mObjects.add(new Decoration(sinkLocation.x(), sinkLocation.y(), Mansion.SINK));

        Vector bathLocation = corners.remove(NumberGenerator.getRandomInt(0, corners.size() - 1));

        if (blocksDoorway(bathLocation)) return;

        mObjects.add(new Decoration(bathLocation.x(), bathLocation.y(), Mansion.BATH));

        String[] floor = new String[] {Mansion.MARBLE_FLOOR_2, Mansion.MARBLE_FLOOR_3, Mansion.MARBLE_FLOOR_4, Mansion.MARBLE_FLOOR_5};
        retextureFloor(room, floor[NumberGenerator.getRandomInt(0, floor.length - 1)]);

        populateObjectGrid();
        mBathroomCount++;
        room.furnished = true;
    }

    private ArrayList<Vector> getCornerTiles(Room room) {
        ArrayList<Vector> corners = new ArrayList<>();

        // Add/subtract 1 from corner to account for position of wall
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
        Vector topLeft = new Vector(room.x(), room.y() + room.height());
        Vector topRight = new Vector(room.x() + room.width(), room.y() + room.height());

        for (int x = topLeft.x(); x < topRight.x(); x++) {
            Vector cell = new Vector(x, room.y() + room.height());
            GameObject object = getMapObjectForCell(cell);

            if (object instanceof Wall && adjacentCellsAreCarvable(cell)) {
                object.setTile(Mansion.WALLPAPER_1);
            }
        }
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

        mObjects.add(new Decoration(itemCoord.x(), itemCoord.y(), Mansion.DECORATIONS[NumberGenerator.getRandomInt(0, Mansion.DECORATIONS.length - 1)]));
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

    public boolean themeHasFurniture(String key) {
        switch (key) {
            case Mansion.KEY:
                return true;

            default:
                return false;
        }
    }

    /**
     *  Library rooms are full of rows/columns of bookshelves.
     *  Otherwise they are treated the same as any other room
     */

    private void convertRoomToLibrary(Room room) {
        if (room.height() < 5) return;

        if (NumberGenerator.getRandomInt(0, 1) == 0) { // Add rows of bookshelves

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
                addLibraryRow(cell, y, isEven);
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
                addVerticalLibraryRow(cell, x, isEven);
            }
        }

        retextureFloor(room, Mansion.MARBLE_FLOOR_1);

        populateObjectGrid();
        mLibraryCount++;
    }

    private void addVerticalLibraryRow(Vector cell, int x, boolean isEven) {
        while (inBounds(cell) && getMapObjectForCell(cell) instanceof Floor) {

            Vector lookahead = cell.add(Directions.Cardinal.get("NORTH"));
            boolean inBounds = inBounds(lookahead);

            if (!inBounds || getMapObjectForCell(lookahead) instanceof Floor) {

                if (!cellBlocksDoorway(cell, isEven)) {
                    mObjects.add(new Decoration(cell.x(), cell.y(), Mansion.BOOKSHELVES[NumberGenerator.getRandomInt(0, Mansion.BOOKSHELVES.length - 1)]));
                }
            }

            else {
                break;
            }

            cell = cell.add(Directions.Cardinal.get("NORTH"));
        }
    }

    private void addLibraryRow(Vector cell, int y, boolean isEven) {

        while (inBounds(cell) && getMapObjectForCell(cell) instanceof Floor) {

            Vector lookahead = cell.add(Directions.Cardinal.get("EAST"));
            boolean inBounds = inBounds(lookahead);

            if (!inBounds || getMapObjectForCell(lookahead) instanceof Floor) {

                if (!cellBlocksDoorway(cell, isEven)) {
                    mObjects.add(new Decoration(cell.x(), cell.y(), Mansion.BOOKSHELVES[NumberGenerator.getRandomInt(0, Mansion.BOOKSHELVES.length - 1)]));
                }
            }

            else {
                break;
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
        String bearing = bearings[NumberGenerator.getRandomInt(0, 2)];
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

        if ((object instanceof Floor) && ((Floor) object).isDoorway()) {

        }
        else {
            LightSource lightSource = new LightSource(fov.x(), fov.y(), cell.x(), cell.y(), getLightSourceTile(bearing));
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

    /**
     *  % chance to place chest in front of random wall in room.
     */

    private void addChestsToRoom(Room room) {
        int chanceToAdd = NumberGenerator.getRandomInt(0, mChestChance);

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
        final int PATH_LIMIT = 6;

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

            ArrayList<Vector> bestPath = testPath(start, end, cell);

            // If path length === 0, no safe paths were found.
            // If path length exceeds PATH_LIMIT, the object is blocking player's path in a detrimental way
            if (bestPath.size() == 0 || bestPath.size() > PATH_LIMIT) {
                return true;
            }
        }

        return false;
    }

    private ArrayList<Vector> testPath(Vector startNode, Vector goalNode, Vector obstacle) {
        ArrayList<Vector> optimalPath = new ArrayList<>();
        ArrayList<Vector> openNodes = new ArrayList<>();

        HashMap<String, Boolean> checkedNodes = new HashMap<>();

        openNodes.add(startNode);

        Vector lastNode = null;

        while (openNodes.size() > 0) {
            Vector currentNode = openNodes.remove(openNodes.size() - 1);

            if (lastNode != null && lastNode.equals(currentNode)) {
                break;
            }

            Vector closestNode = null;
            int closestDistance = Integer.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector direction : Directions.Cardinal.values()) {
                Vector adjacentNode = currentNode.add(direction);

                if (!inBounds(adjacentNode)) continue;

                if (checkedNodes.containsKey(adjacentNode.toString())) continue;

                if (adjacentNode.equals(obstacle) || detectCollisions(adjacentNode)) continue;

                int distanceToGoal = getDistance(adjacentNode, goalNode);

                if (distanceToGoal < closestDistance) {
                    closestDistance = distanceToGoal;
                    closestNode = adjacentNode;
                }
            }

            if (closestNode == null) {
                break;
            }

            else if (closestNode.equals(goalNode)) {
                optimalPath.add(closestNode);
                lastNode = closestNode;
            }

            else {
                checkedNodes.put(currentNode.toString(), true);
                lastNode = currentNode;
                optimalPath.add(closestNode);
                openNodes.add(closestNode);
            }
        }

        if (lastNode == null || !lastNode.equals(goalNode)) {
            // Path was blocked
            return new ArrayList<>(0);
        }

        else {
            return optimalPath;
        }
    }

    private int getDistance(Vector a, Vector b) {
        return (int) Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2));
    }

    private void addEnemiesToRoom(Room room) {
        int numberOfEnemies = NumberGenerator.getRandomInt(mMinEnemies, mMaxEnemies);

        if (NumberGenerator.getRandomInt(0, 1) == 0) {

            // We don't want player to be mobbed by several enemies when starting a new floor.
            if (room.isEntrance() && numberOfEnemies > 1) {
                numberOfEnemies = 1;
            }

            for (int i = 0; i < numberOfEnemies; i++) {
                // Account for walls when placing enemies
                int x = NumberGenerator.getRandomInt(room.x() + 1, room.x() + room.width() - 1);
                int y = NumberGenerator.getRandomInt(room.y() + 1, room.y() + room.height() - 1);
                int level = NumberGenerator.getRandomInt(1, mMaxEnemyLevel);

                if (objectNotBlockingPath(new Vector(x, y))) {
                    mEnemies.add(EnemyFactory.getRandomEnemy(x, y, level));
                    populateObjectGrid();
                }
            }
        }
    }

    private int getDoorsFromArea(Vector start) {
        ArrayList<String> checked = new ArrayList<>();
        ArrayList<Vector> queue = new ArrayList<>();

        int doorCount = 0;

        Vector north = new Vector(0, 1);
        Vector south = new Vector(0, -1);
        Vector west = new Vector(-1, 0);
        Vector east = new Vector(1, 0);

        queue.add(start);

        // Use modified flood fill algorithm to find all the door tiles in current room.
        // Add Door objects to array and return
        while (queue.size() > 0) {
            Vector cell = queue.remove(queue.size() - 1);

            checked.add(cell.toString());

            Vector eastCell = cell;
            Vector westCell = cell;
            ArrayList<Vector> scanline = new ArrayList<>();
            GameObject object;

            // Find floor tiles to east of player position
            while (inBounds((eastCell)) && getMapObjectForCell(eastCell) instanceof Floor) {
                checked.add(eastCell.toString());
                scanline.add(eastCell);
                eastCell = eastCell.add(east);
            }

            // Check for doors
            if (inBounds(eastCell)) {
                object = getMapObjectForCell((eastCell));
                if (object instanceof Floor && ((Floor) object).isDoorway()) {
                    doorCount++;
                }
            }

            // Find floor tiles to west of player position.
            while (inBounds(westCell) && getMapObjectForCell(westCell) instanceof Floor) {
                checked.add(westCell.toString());
                scanline.add(westCell);
                westCell = westCell.add(west);
            }

            if (inBounds(westCell)) {
                // Check for doors
                object = getMapObjectForCell((westCell));
                if (object instanceof Floor && ((Floor) object).isDoorway()) {
                    doorCount++;
                }
            }

            // Iterate over each tile in scanline and check north/south for floor tiles.
            // If we find floor tiles, add these to queue and repeat process.

            for (Vector item : scanline) {
                Vector northCell = item.add(north);
                Vector southCell = item.add(south);
                GameObject northTile = getMapObjectForCell(northCell);
                GameObject southTile = getMapObjectForCell(southCell);

                if (!checked.contains(northCell.toString())) {
                    if (northTile instanceof Floor) {
                        Floor floor = (Floor) northTile;
                        if (!floor.isDoorway()) {
                            queue.add(northCell);
                        }
                        else {
                            doorCount++;
                        }

                    }
                }

                if (!checked.contains(southCell.toString())) {
                    if (southTile instanceof Floor) {
                        Floor floor = (Floor) southTile;
                        if (!floor.isDoorway()) {
                            queue.add(southCell);
                        }
                        else {
                            doorCount++;
                        }

                    }
                }
            }
        }

        return doorCount;
    }

    private GameObject getMapObjectForCell(Vector coords) {
        if (inBounds(coords)) {
            return mMapGrid[coords.x()][coords.y()];
        }
        else {
            throw new Error("Coords (" + coords.x() + ", " + coords.y() + ") are not in bounds");
        }
    }

    /*
    ---------------------------------------------
    Object grid manipulation and testing
    ---------------------------------------------
    */

    private void addObjectToStack(int x, int y, GameObject object) {
        mObjectGrid[x][y].add(object);
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
            // Log.v("log", "map tile " + mapTile.tile() + " was blocking");
            return true;
        }

        else if (!mapTile.isTraversable()) {
            // Log.v("log", "map tile " + mapTile.tile() + " was not traversable");
            return true;
        }

        ArrayList<GameObject> objectStack = mObjectGrid[x][y];

        if (objectStack == null) return false;

        for (GameObject object : objectStack) {

            if (object.isBlocking()) {
                // Log.v("log", "object tile " + object.tile() + " was blocking");
                return true;
            }

            else if (!object.isTraversable()) {
                // Log.v("log", "object tile " + object.tile() + " was not traversable");
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

    private boolean adjacentCellsAreCarvable(Vector cell) {
        HashMap<String, Vector> adjacentCells = getAdjacentCells(cell, 1, false);

        for (Vector adjacent : adjacentCells.values()) {

            if (!inBounds(adjacent) || !(getMapObjectForCell(adjacent) instanceof Wall)) {
                return false;
            }
        }

        return true;
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
}
