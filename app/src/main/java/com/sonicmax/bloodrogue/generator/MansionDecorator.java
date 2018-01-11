package com.sonicmax.bloodrogue.generator;

import android.content.res.AssetManager;
import android.util.Log;

import com.sonicmax.bloodrogue.data.BlueprintParser;
import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Stationary;
import com.sonicmax.bloodrogue.engine.systems.PotionSystem;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.generator.factories.TerrainFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.tilesets.MansionTileset;
import com.sonicmax.bloodrogue.utils.maths.Calculator;
import com.sonicmax.bloodrogue.data.JSONLoader;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Room;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class MansionDecorator {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private float chestChance = 0.3f;
    private int maxEnemyLevel = 1;
    private int minEnemies = 1;
    private int maxEnemies = 4;

    private int officeCount = 0;
    private int libraryCount = 0;
    private int galleryCount = 0;
    private int bedroomCount = 0;
    private int bathroomCount = 0;

    private int mapWidth;
    private int mapHeight;
    private int theme;
    private String themeKey;

    private Component[][][] mapGrid;
    private ArrayList<Component[]> objects;
    private ArrayList<Component[]> enemies;
    private ArrayList<Component[]>[][] objectGrid;

    private JSONObject furnitureBlueprints;
    private JSONObject weaponBlueprints;
    private JSONObject potionBlueprints;

    private RandomNumberGenerator rng;
    private AssetManager assetManager;

    public MansionDecorator(int mapWidth, int mapHeight, int theme, String key, AssetManager assetManager) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.theme = theme;
        this.themeKey = key;
        this.assetManager = assetManager;
        this.furnitureBlueprints = JSONLoader.loadFurniture(assetManager);
        this.weaponBlueprints = JSONLoader.loadWeapons(assetManager);
        this.potionBlueprints = PotionSystem.generateRandomPotionEffects(JSONLoader.loadPotions(assetManager));

        this.rng = new RandomNumberGenerator();
    }

    public void setGeneratorData(Component[][][] mapGrid, ArrayList<Component[]> objects,
                                 ArrayList<Component[]>[][] objectGrid, ArrayList<Component[]> enemies) {
        this.mapGrid = mapGrid;
        this.objects = objects;
        this.objectGrid = objectGrid;
        this.enemies = enemies;
    }

    public ArrayList<Component[]> getObjects() {
        return objects;
    }

    public ArrayList<Component[]>[][] getObjectGrid() {
        return objectGrid;
    }

    public ArrayList<Component[]> getEnemies() {
        return enemies;
    }

    /**
     * Iterates over array of Room objects and decides how to decorate them.
     * Decorations are added to object grid/object array and retrieved later
     *
     */

    public void decorateRooms(ArrayList<Room> rooms) {
        // Make copy of rooms array so we can modify it safely
        ArrayList<Room> roomsCopy = new ArrayList<>(rooms);

        // Designate 1 quarter of the map to a specific shader of room & attempt to decorate each one.
        // Any rooms which are missed (eg. for being too small/big) will be decorated later
        int sectionSize = mapWidth / 2;

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
        for (Room room : roomsCopy) {
            furnishRoom(room);
        }

        // Iterate over unmodified rooms array and add lighting/chests/enemies/etc
        for (Room room : rooms) {

            if (getDoorsFromRoom(room) == 0) {
                Log.v(LOG_TAG, "inaccessible room");
                room.isAccessible = true;
            }

            addNorthWallTiles(room);
            addLightingToRoom(room);
            decorateNorthWall(room);
            addItemsToRoom(room);
            addWeaponsToRoom(room);
            addEnemiesToRoom(room);
        }
    }

    private void furnishRoom(Room room) {
        if (libraryCount < 2) {
            convertRoomToLibrary(room);
        }

        else if (galleryCount < 2) {
            convertRoomToArtGallery(room);
        }

        else {
            convertRoomToOffice(room);
        }
    }

    /**
     *  Art gallery rooms have paintings on north wall, with pedastals in front.
     */

    private void convertRoomToArtGallery(Room room) {
        if (room.width() < 5) return;

        Vector topLeft = new Vector(room.x(), room.y() + room.height());
        Vector topRight = new Vector(room.x() + room.width(), room.y() + room.height());

        ArrayList<String> paintings = new ArrayList<>(Arrays.asList(MansionTileset.PAINTINGS));

        for (int x = topLeft.x(); x < topRight.x(); x += 2) {
            Vector cell = new Vector(x, room.y() + room.height());
            Component[] object = getMapObjectForCell(cell);

            Stationary stat = ComponentFinder.getStaticComponent(object);

            if (stat.type == Stationary.WALL && paintings.size() > 0) {
                int random = rng.getRandomInt(0, paintings.size() - 1);
                Component[] painting = DecalFactory.createDecoration(x, cell.y, paintings.remove(random));

                objects.add(painting);

                Component[] pedestal = DecalFactory.createDecoration(x, cell.y - 1, MansionTileset.PEDESTAL);

                if (!blocksDoorway(new Vector(x, cell.y - 1))) {
                    objects.add(pedestal);
                }
            }
        }

        populateObjectGrid();

        ArrayList<Vector> corners = getCornerTiles(room);

        int statueCount = 0;

        for (Vector corner : corners) {
            if (statueCount < 3 && !detectCollisions(corner) && !blocksDoorway(corner)) {
                String tile = MansionTileset.STATUES[rng.getRandomInt(0, MansionTileset.STATUES.length - 1)];
                objects.add(DecalFactory.createDecoration(corner.x, corner.y, tile));
                statueCount++;
            }
        }

        retextureFloor(room, MansionTileset.MARBLE_FLOOR_1);

        populateObjectGrid();
        galleryCount++;
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

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.OFFICE_DESK));

                // Find adjacent space to place office chair. Can be skipped

                for (Vector direction : Directions.Cardinal.values()) {
                    Vector adjacent = corner.add(direction);
                    if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                        objects.add(DecalFactory.createDecoration(adjacent.x, adjacent.y, MansionTileset.OFFICE_CHAIR));
                        break;
                    }
                }

                deskAdded = true;
                continue;
            }

            if (!filingCabinetAdded) {
                if (blocksDoorway(corner)) return;

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.FILING_CABINET));

                filingCabinetAdded = true;
                continue;
            }

            if (!plantAdded) {
                if (blocksDoorway(corner)) return;

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.OFFICE_PLANT));

                String[] floorTiles = new String[] {MansionTileset.WOOD_FLOOR_1, MansionTileset.WOOD_FLOOR_2, MansionTileset.WOOD_FLOOR_3};
                retextureFloor(room, floorTiles[rng.getRandomInt(0, floorTiles.length - 1)]);

                plantAdded = true;
            }
        }

        populateObjectGrid();
        officeCount++;
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

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.BEDS[rng.getRandomInt(0, 1)]));

                // Find adjacent space to place bedside cabinet. Can be skipped

                for (Vector direction : Directions.Cardinal.values()) {
                    Vector adjacent = corner.add(direction);
                    if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                        objects.add(DecalFactory.createDecoration(adjacent.x, adjacent.y, MansionTileset.BEDSIDE_CABINET));
                        break;
                    }
                }

                bedAdded = true;
                continue;
            }

            if (!wardrobeAdded) {
                if (blocksDoorway(corner)) continue;

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.WARDROBE));
                wardrobeAdded = true;
                continue;
            }

            if (!plantAdded) {
                if (blocksDoorway(corner)) continue;

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.OFFICE_PLANT));
                plantAdded = true;
            }
        }

        // We want bedrooms to have wooden floors
        String[] floor = new String[] {MansionTileset.WOOD_FLOOR_1, MansionTileset.WOOD_FLOOR_2, MansionTileset.WOOD_FLOOR_3};
        retextureFloor(room, floor[rng.getRandomInt(0, floor.length - 1)]);

        populateObjectGrid();
        bedroomCount++;
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

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.TOILET));
                toiletAdded = true;
                continue;
            }

            if (!sinkAdded) {
                if (blocksDoorway(corner)) return;

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.SINK));
                sinkAdded = true;
                continue;
            }

            if (!bathAdded) {
                if (blocksDoorway(corner)) return;

                objects.add(DecalFactory.createDecoration(corner.x, corner.y, MansionTileset.BATH));
                bathAdded = true;
            }
        }

        // Give bathrooms a marble tiled floor
        retextureFloor(room, MansionTileset.MARBLE_FLOOR_2);

        populateObjectGrid();
        bathroomCount++;
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
            Component[] object = getMapObjectForCell(adjacent);
            Stationary stat = ComponentFinder.getStaticComponent(object);
            if (stat.type == Stationary.DOORWAY) {
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

            if (object.shader == Stationary.WALL && adjacentCellsAreCarvable(cell)) {
                object.setSprite(MansionTileset.WALLPAPER_1);
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
            Component[] object = getMapObjectForCell(cell);
            Stationary stat = ComponentFinder.getStaticComponent(object);
            if (stat.type == Stationary.DOORWAY) {
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

            Component[] tile = getMapObjectForCell(itemCoord.add(Directions.Cardinal.get("NORTH")));
            Stationary stat = ComponentFinder.getStaticComponent(tile);

            if (stat.type == Stationary.FLOOR) {
                // Ignore single-tiled walls
                return;
            }
        }

        else {
            // Place item in centre. TODO: multiple decorations, or multi-getSprite decorations
            itemCoord = new Vector(room.x() + (int) Math.floor(room.width() / 2), topLeft.y());
        }

        objects.add(DecalFactory.createDecoration(itemCoord.x, itemCoord.y,
                MansionTileset.DECORATIONS[rng.getRandomInt(0, MansionTileset.DECORATIONS.length - 1)]));
    }

    /**
     *  Replaces texture of floor objects contained in room.
     */

    private void retextureFloor(Room room, String imgPath) {
        int right = room.x() + room.width();
        int bottom = room.y() + room.height();

        for (int x = room.x(); x < right; x++) {
            for (int y = room.y(); y < bottom; y++) {
                Vector pos = new Vector(x, y);
                Component[] components = mapGrid[x][y];
                Stationary s = ComponentFinder.getStaticComponent(components);

                if (s != null && inBounds(pos) && s.type == Stationary.FLOOR) {
                    mapGrid[x][y] = TerrainFactory.createFloor(x, y, imgPath);
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

        if (rng.getRandomInt(0, 1) == 0) { // Add rows of bookshelves

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

        retextureFloor(room, MansionTileset.MARBLE_FLOOR_1);

        populateObjectGrid();
        libraryCount++;
    }

    private void addBookshelfColumn(Vector cell, boolean isEven) {
        while (inBounds(cell) && ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {

            Component[] floor = getMapObjectForCell(cell);

            if (((Stationary) floor[2]).type == Stationary.DOORWAY) return;

            Vector lookahead = cell.add(Directions.Cardinal.get("NORTH"));

            if (!inBounds(lookahead) || ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {

                floor = getMapObjectForCell(lookahead);

                if (((Stationary) floor[2]).type == Stationary.DOORWAY) return;

                if (!cellBlocksDoorway(cell, isEven)) {
                    String tile = MansionTileset.BOOKSHELVES[rng.getRandomInt(0, MansionTileset.BOOKSHELVES.length - 1)];
                    objects.add(DecalFactory.createDecoration(cell.x, cell.y, tile));
                }
            }

            else {
                return;
            }

            cell = cell.add(Directions.Cardinal.get("NORTH"));
        }
    }

    private void addBookshelfRow(Vector cell, boolean isEven) {

        while (inBounds(cell) && ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {

            Component[] floor = getMapObjectForCell(cell);

            if (((Stationary) floor[2]).type == Stationary.DOORWAY) return;

            Vector lookahead = cell.add(Directions.Cardinal.get("EAST"));

            if (!inBounds(lookahead) || ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {

                floor = getMapObjectForCell(lookahead);

                if (((Stationary) floor[2]).type == Stationary.DOORWAY) return;

                else if (!cellBlocksDoorway(cell, isEven)) {
                    String tile = MansionTileset.BOOKSHELVES[rng.getRandomInt(0, MansionTileset.BOOKSHELVES.length - 1)];
                    objects.add(DecalFactory.createDecoration(cell.x, cell.y, tile));
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

            Component[] object = getMapObjectForCell(vector);

            if (((Stationary) object[2]).type == Stationary.DOORWAY) {
                return true;
            }

            // We should check two cells ahead to make sure.
            // If cell is out of bounds we don't have to worry

            Vector newCell = cell.add(direction.scale(2));

            if (inBounds(newCell)) {
                object = getMapObjectForCell(cell.add(direction.scale(2)));
                if (isEven && ((Stationary) object[2]).type == Stationary.DOORWAY) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addLightingToRoom(Room room) {
        String[] bearings = new String[] {"NORTH", "EAST", "WEST"};
        String bearing = bearings[rng.getRandomInt(0, 2)];
        Vector direction = Directions.Cardinal.get(bearing);
        Vector cell = room.roundedCentre();

        while (inBounds(cell) && ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {
            cell = cell.add(direction);
        }

        if (!inBounds(cell)) {
            return;
        }

        Component[] object = getMapObjectForCell(cell);

        if (((Stationary) object[2]).type != Stationary.FLOOR && ((Stationary) object[2]).type != Stationary.DOORWAY) {
            Component[] lightSource = DecalFactory.createDecoration(cell.x, cell.y, getLightSourceTile(bearing));
            objects.add(lightSource);
        }

        populateObjectGrid();
    }

    private String getLightSourceTile(String bearing) {
        switch (themeKey) {
            case MansionTileset.KEY:
                if (bearing.equals("EAST")) {
                    return MansionTileset.WALL_LANTERN_EAST;
                }

                else return MansionTileset.WALL_LANTERN;

            default:
                return GenericTileset.LIGHT_SOURCE;
        }
    }

    private void addItemsToRoom(Room room) {
        // float chanceToAdd = rng.getRandomFloat(0f, 1f);
        // if (chanceToAdd > chestChance) return;

        // Attempt to place the chest 8 times.
        // If this fails, just skip it

        for (Vector direction : Directions.All.values()) {
            Vector cell = room.roundedCentre();

            while (inBounds(cell) && ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {
                cell = cell.add(direction);
            }

            cell = cell.subtract(direction);

            Component[] object = getMapObjectForCell(cell);

            if (((Stationary) object[2]).type != Stationary.FLOOR || ((Stationary) object[2]).type == Stationary.DOORWAY) continue;

            if (detectCollisions(cell) || blocksDoorway(cell)) continue;

            Iterator<String> keys = potionBlueprints.keys();
            ArrayList<String> keyArray = new ArrayList<>();

            while (keys.hasNext()) {
                keyArray.add(keys.next());
            }

            int rng = new RandomNumberGenerator().getRandomInt(0, keyArray.size() - 1);

            Component[] chest = BlueprintParser.getComponentArrayForBlueprint(potionBlueprints, keyArray.get(rng));
            // Component[] chest = BlueprintParser.getComponentArrayForBlueprint(furnitureBlueprints, "chest");

            if (chest == null) return;

            Position position = ComponentFinder.getPositionComponent(chest);
            position.x = cell.x;
            position.y = cell.y;

            if (!objectBlockingPath(cell)) {
                objects.add(chest);
                populateObjectGrid();
                return;
            }
        }
    }

    private void addWeaponsToRoom(Room room) {
        // float chanceToAdd = rng.getRandomFloat(0f, 1f);
        // if (chanceToAdd > chestChance) return;

        // Attempt to place the chest 8 times.
        // If this fails, just skip it

        for (Vector direction : Directions.All.values()) {
            Vector cell = room.roundedCentre();

            while (inBounds(cell) && ((Stationary) getMapObjectForCell(cell)[2]).type == Stationary.FLOOR) {
                cell = cell.add(direction);
            }

            cell = cell.subtract(direction);

            Component[] object = getMapObjectForCell(cell);

            if (((Stationary) object[2]).type != Stationary.FLOOR || ((Stationary) object[2]).type == Stationary.DOORWAY) continue;

            if (detectCollisions(cell) || blocksDoorway(cell)) continue;

            Iterator<String> keys = weaponBlueprints.keys();
            ArrayList<String> keyArray = new ArrayList<>();

            while (keys.hasNext()) {
                keyArray.add(keys.next());
            }

            int rng = new RandomNumberGenerator().getRandomInt(0, keyArray.size() - 1);

            Component[] chest = BlueprintParser.getComponentArrayForBlueprint(weaponBlueprints, keyArray.get(rng));

            if (chest == null) return;

            Position position = ComponentFinder.getPositionComponent(chest);
            position.x = cell.x;
            position.y = cell.y;

            if (!objectBlockingPath(cell)) {
                objects.add(chest);
                populateObjectGrid();
                return;
            }
        }
    }

    private boolean objectBlockingPath(Vector cell) {
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
        if (rng.getRandomInt(0, 2) == 0) return;

        // Divide room size by 9 to find how many enemies would reasonably fit in room.
        // (eg. 1 enemy for 3x3, 2 for 5x5, etc)
        // Compare this value to maxEnemies and use smallest value.
        // Then choose random number between minimum enemy count + this value

        int enemyFit = (room.width() * room.height()) / 9;
        int numberOfEnemies = rng.getRandomInt(minEnemies, Math.min(enemyFit, maxEnemies));

        // We don't want player to be mobbed by several enemies when starting a new floor.
        if (room.isEntrance() && numberOfEnemies > 1) {
            numberOfEnemies = 1;
        }

        // Iterate over each getSprite in room and find empty positions.
        // Avoid rounded centre because that is where player character would be placed

        ArrayList<Vector> emptyVectors = new ArrayList<>();
        Vector centre = room.roundedCentre();

        for (int x = room.x() + 1; x < room.x() + room.width() - 1; x++) {
            for (int y = room.y() + 1; y < room.y() + room.height() - 1; y++) {
                Vector pos = new Vector(x, y);
                if (!pos.equals(centre) && !detectCollisions(new Vector(x, y))) {
                    emptyVectors.add(pos);
                }
            }
        }

        JSONObject enemyBlueprints = JSONLoader.loadEnemies(assetManager);
        int size = enemyBlueprints.length();

        Iterator<String> keys = enemyBlueprints.keys();
        ArrayList<String> keyArray = new ArrayList<>();

        while (keys.hasNext()) {
            keyArray.add(keys.next());
        }

        // Now pick random positions until enemy count or empty getSprite array is exhausted.
        // This will distribute enemies somewhat randomly (compared to deploying in rows or columns)

        int count = 0;

        while (count <= numberOfEnemies && emptyVectors.size() > 0) {
            int random = rng.getRandomInt(0, emptyVectors.size() - 1);
            Vector vector = emptyVectors.remove(random);
            int type = rng.getRandomInt(0, size - 1);
            int level = rng.getRandomInt(1, maxEnemyLevel);

            Component[] enemy = BlueprintParser.getComponentArrayForBlueprint(enemyBlueprints, keyArray.get(type));
            Position positionComponent = ComponentFinder.getPositionComponent(enemy);

            if (positionComponent != null) {
                positionComponent.x = vector.x;
                positionComponent.y = vector.y;
                enemies.add(enemy);
            }

            else {
                Log.e(LOG_TAG, "No position component found - skipping");
            }

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
                if (((Stationary) getMapObjectForCell(north)[2]).type == Stationary.DOORWAY) {
                    doorCount++;
                }
            }

            if (inBounds(south)) {
                if (((Stationary) getMapObjectForCell(south)[2]).type == Stationary.DOORWAY) {
                    doorCount++;
                }
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector east = new Vector(room.x() - 1, y);
            Vector west = new Vector(room.x() + room.width(), y);

            if (inBounds(east)) {
                if (((Stationary) getMapObjectForCell(east)[2]).type == Stationary.DOORWAY) {
                    doorCount++;
                }
            }

            if (inBounds(west)) {
                if (((Stationary) getMapObjectForCell(west)[2]).type == Stationary.DOORWAY) {
                    doorCount++;
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

    private void addObjectToStack(int x, int y, Component[] object) {
        objectGrid[x][y].add(object);
    }

    private Component[] getMapObjectForCell(Vector coords) {
        if (inBounds(coords)) {
            return mapGrid[coords.x()][coords.y()];
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

        Component[] mapTile = mapGrid[x][y];
        Physics physics = null;

        for (int i = 0; i < mapTile.length; i++) {
            if (mapTile[i] instanceof Physics) {
                physics = (Physics) mapTile[i];
                break;
            }
        }

        if (physics.isBlocking) {
            // Log.v("log", "map getSprite " + mapTile.getSprite() + " was blocking");
            return true;
        }

        else if (!physics.isTraversable) {
            // Log.v("log", "map getSprite " + mapTile.getSprite() + " was not traversable");
            return true;
        }

        ArrayList<Component[]> objectStack = objectGrid[x][y];

        if (objectStack == null) return false;

        for (Component[] object : objectStack) {

            physics = null;

            for (int i = 0; i < object.length; i++) {
                if (object[i] instanceof Physics) {
                    physics = (Physics) object[i];
                    break;
                }
            }

            if (physics == null) {
                Log.e(LOG_TAG, "Physics was null");
                return true;
            }

            if (physics.isBlocking) {
                // Log.v("log", "object getSprite " + object.getSprite() + " was blocking");
                return true;
            }

            else if (!physics.isTraversable) {
                // Log.v("log", "object getSprite " + object.getSprite() + " was not traversable");
                return true;
            }
        }

        return false;
    }

    private void populateObjectGrid() {
        objectGrid = Array2DHelper.createComponentGrid(mapWidth, mapHeight);

        for (Component[] object : objects) {
            Position position = null;

            for (int i = 0; i < object.length; i++) {
                if (object[i] instanceof Position) {
                    position = (Position) object[i];
                    break;
                }
            }

            addObjectToStack(position.x, position.y, object);
        }

        for (Component[] enemy : enemies) {
            Position position = null;

            for (int i = 0; i < enemy.length; i++) {
                if (enemy[i] instanceof Position) {
                    position = (Position) enemy[i];
                    break;
                }
            }

            addObjectToStack(position.x, position.y, enemy);
        }
    }

    private boolean inBounds(Vector cell) {
        return (cell.x() >= 0 && cell.x() < mapWidth && cell.y() >= 0 && cell.y() < mapHeight);
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
}
