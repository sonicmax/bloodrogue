package com.sonicmax.bloodrogue.generator;

import android.content.res.AssetManager;
import android.util.Log;

import com.sonicmax.bloodrogue.data.BlueprintParser;
import com.sonicmax.bloodrogue.engine.ComponentManager;
import com.sonicmax.bloodrogue.engine.collisions.AxisAlignedBoxTester;
import com.sonicmax.bloodrogue.engine.Directions;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.components.Physics;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.systems.PotionSystem;
import com.sonicmax.bloodrogue.generator.factories.DecalFactory;
import com.sonicmax.bloodrogue.engine.systems.ComponentFinder;
import com.sonicmax.bloodrogue.generator.tools.GridGeometryHelper;
import com.sonicmax.bloodrogue.tilesets.BuildingTileset;
import com.sonicmax.bloodrogue.tilesets.GenericTileset;
import com.sonicmax.bloodrogue.tilesets.TileCategorySorter;
import com.sonicmax.bloodrogue.utils.maths.GeometryHelper;
import com.sonicmax.bloodrogue.data.JSONLoader;
import com.sonicmax.bloodrogue.utils.maths.Vector2D;
import com.sonicmax.bloodrogue.generator.buildings.Room;
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

    private String[][] terrainTiles;
    private ArrayList<Long>[][] objectEntities;

    private JSONObject furnitureBlueprints;
    private JSONObject weaponBlueprints;
    private JSONObject potionBlueprints;

    private RandomNumberGenerator rng;
    private AssetManager assetManager;
    private ComponentManager componentManager;

    public MansionDecorator(int mapWidth, int mapHeight, int theme, String key, AssetManager assetManager) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.theme = theme;
        this.themeKey = key;
        this.assetManager = assetManager;
        this.furnitureBlueprints = JSONLoader.loadFurniture(assetManager);
        this.weaponBlueprints = JSONLoader.loadWeapons(assetManager);
        this.potionBlueprints = PotionSystem.generateRandomPotionEffects(JSONLoader.loadPotions(assetManager));
        this.componentManager = ComponentManager.getInstance();

        this.rng = new RandomNumberGenerator();
    }

    public void setGeneratorData(String[][] terrainEntities, ArrayList<Long>[][] objectEntities) {
        this.terrainTiles = terrainEntities;
        this.objectEntities = objectEntities;
    }

    /**
     * Iterates over array of Room objects and decides how to decorate them.
     * Decorations are added to object grid/object array and retrieved later
     *
     */

    public void decorateRooms(ArrayList<MapRegion> rooms, int buildingWidth, int buildingHeight) {
        // Make copy of rooms array so we can modify it safely
        ArrayList<MapRegion> roomsCopy = new ArrayList<>(rooms);

        // Designate 1 quarter of the map to a specific shader of room & attempt to decorate each one.
        // Any rooms which are missed (eg. for being too small/big) will be decorated later
        int sectionWidth = buildingWidth / 2;
        int sectionHeight = buildingHeight / 2;

        Room livingQuartersQuad = new Room(0, 0, sectionWidth, sectionHeight);

        Iterator<MapRegion> it = roomsCopy.iterator();

        int bathroomCount = 0;

        while (it.hasNext()) {
            MapRegion room = it.next();
            // Todo: this will work for most rooms, but not irregularly shaped ones
            Chunk boundingBox = GridGeometryHelper.getBoundingBox(room.getCorners());
            if (AxisAlignedBoxTester.test(livingQuartersQuad, boundingBox)) {
                if (bathroomCount < 1) {
                    // Todo: bathroom should be room with 1 door
                    convertRoomToBathroom(room);
                    bathroomCount++;
                } else {
                    convertRoomToBedroom(room);
                }

                it.remove();
            }
        }

        Room officeQuad = new Room(sectionWidth, 0, sectionWidth, sectionHeight);

        it = roomsCopy.iterator();

        while (it.hasNext()) {
            MapRegion room = it.next();
            Chunk boundingBox = GridGeometryHelper.getBoundingBox(room.getCorners());
            if (AxisAlignedBoxTester.test(livingQuartersQuad, boundingBox)) {
                convertRoomToOffice(room);
                it.remove();
            }
        }

        // Now iterate over the remaining rooms and assign random styles to theme
        for (MapRegion room : roomsCopy) {
            furnishRoom(room);
        }

        int windowCount = 0;

        // Iterate over unmodified rooms array and add lighting/chests/enemies/etc
        for (MapRegion room : rooms) {

            /*if (getDoorsFromRoom(room) == 0) {
                Log.v(LOG_TAG, "inaccessible room");
            }

            addNorthWallTiles(room);
            addLightingToRoom(room);
            decorateNorthWall(room);*/

            // Todo: we should add items/weapons/enemies separately to decorations
            // addItemsToRoom(room);
            // addWeaponsToRoom(room);
            // addEnemiesToRoom(room);
        }
    }

    private void furnishRoom(MapRegion room) {
        if (libraryCount < 1) {
            convertRoomToLibrary(room);
        }

        else if (galleryCount < 1 && room.getCorners().size() == 4) {
            convertRoomToArtGallery(room);
        }

        else {
            convertRoomToOffice(room);
        }
    }

    /**
     *  Art gallery rooms have paintings on north wall, with pedastals in front. For now we can only
     *  build these in rectangular rooms
     */

    private void convertRoomToArtGallery(MapRegion room) {
        Chunk boundingBox = GridGeometryHelper.getBoundingBox(room.getCorners());

        if (boundingBox.width < 5) return;

        Vector2D topLeft = new Vector2D(boundingBox.x, boundingBox.y + boundingBox.height);
        Vector2D topRight = new Vector2D(boundingBox.x + boundingBox.width, boundingBox.y + boundingBox.height);

        ArrayList<String> paintings = new ArrayList<>(Arrays.asList(BuildingTileset.PAINTINGS));

        for (int x = topLeft.x(); x < topRight.x(); x += 2) {
            Vector2D cell = new Vector2D(x, boundingBox.y + boundingBox.height);

            if (TileCategorySorter.isWall(terrainTiles[cell.x][cell.y]) && paintings.size() > 0) {
                int random = rng.getRandomInt(0, paintings.size() - 1);
                Component[] painting = DecalFactory.createDecal(x, cell.y, paintings.remove(random));

                objectEntities[x][cell.y].add(painting[0].id);
                componentManager.sortComponentArray(painting);

                Component[] pedestal = DecalFactory.createDecal(x, cell.y - 1, BuildingTileset.PEDESTAL);

                if (!blocksDoorway(new Vector2D(x, cell.y - 1))) {
                    objectEntities[x][cell.y - 1].add(pedestal[0].id);
                    componentManager.sortComponentArray(pedestal);
                }
            }
        }

        ArrayList<Vector2D> corners = GridGeometryHelper.findInsideCorners(room.getVectors());

        int statueCount = 0;

        for (Vector2D corner : corners) {
            if (statueCount < 3 && !detectCollisions(corner) && !blocksDoorway(corner)) {
                String tile = BuildingTileset.STATUES[rng.getRandomInt(0, BuildingTileset.STATUES.length - 1)];

                Component[] decal = DecalFactory.createDecal(corner.x, corner.y, tile);
                objectEntities[corner.x][corner.y].add(decal[0].id);
                componentManager.sortComponentArray(decal);

                statueCount++;
            }
        }

        retextureFloor(room, BuildingTileset.MARBLE_FLOOR_1);

        galleryCount++;
    }

    /**
     *  Offices, bedrooms and bathrooms are all essentially the same - just rooms where
     *  specific pieces of furniture are placed at each corner.
     */

    private void convertRoomToOffice(MapRegion room) {
        // Add/subtract 1 from corner to account for position of wall
        ArrayList<Vector2D> corners = GridGeometryHelper.findInsideCorners(room.getVectors());

        boolean deskAdded = false;
        boolean filingCabinetAdded = false;
        boolean plantAdded = false;

        // Iterate over each corner and attempt to place decorations in order of importance.

        for (Vector2D corner : corners) {
            if (!deskAdded) {
                if (blocksDoorway(corner)) continue;

                Component[] desk = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.OFFICE_DESK);
                objectEntities[corner.x][corner.y].add(desk[0].id);
                componentManager.sortComponentArray(desk);

                // Find adjacent space to place office chair. Can be skipped

                for (Vector2D direction : Directions.Cardinal.values()) {
                    Vector2D adjacent = corner.add(direction);
                    if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                        Component[] chair = DecalFactory.createDecal(adjacent.x, adjacent.y, BuildingTileset.OFFICE_CHAIR);
                        objectEntities[adjacent.x][adjacent.y].add(chair[0].id);
                        componentManager.sortComponentArray(chair);
                        break;
                    }
                }

                deskAdded = true;
                continue;
            }

            if (!filingCabinetAdded) {
                if (blocksDoorway(corner)) return;

                Component[] cabinet = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.FILING_CABINET);
                objectEntities[corner.x][corner.y].add(cabinet[0].id);
                componentManager.sortComponentArray(cabinet);

                filingCabinetAdded = true;
                continue;
            }

            if (!plantAdded) {
                if (blocksDoorway(corner)) return;

                Component[] plant = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.OFFICE_PLANT);
                objectEntities[corner.x][corner.y].add(plant[0].id);
                componentManager.sortComponentArray(plant);

                String[] floorTiles = new String[] {BuildingTileset.WOOD_FLOOR_1, BuildingTileset.WOOD_FLOOR_2, BuildingTileset.WOOD_FLOOR_3};
                retextureFloor(room, floorTiles[rng.getRandomInt(0, floorTiles.length - 1)]);

                plantAdded = true;
            }
        }

        officeCount++;
    }

    private void convertRoomToBedroom(MapRegion room) {
        ArrayList<Vector2D> corners = GridGeometryHelper.findInsideCorners(room.getVectors());

        boolean bedAdded = false;
        boolean wardrobeAdded = false;
        boolean plantAdded = false;

        // Iterate over each corner and attempt to place decorations in order of importance.

        for (Vector2D corner : corners) {
            if (!bedAdded) {
                if (blocksDoorway(corner)) continue;

                Component[] bed = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.BEDS[rng.getRandomInt(0, 1)]);
                objectEntities[corner.x][corner.y].add(bed[0].id);
                componentManager.sortComponentArray(bed);

                // Find adjacent space to place bedside cabinet. Can be skipped

                for (Vector2D direction : Directions.Cardinal.values()) {
                    Vector2D adjacent = corner.add(direction);
                    if (!detectCollisions(adjacent) && !blocksDoorway(adjacent)) {
                        Component[] cabinet = DecalFactory.createDecal(adjacent.x, adjacent.y, BuildingTileset.BEDSIDE_CABINET);
                        objectEntities[adjacent.x][adjacent.y].add(cabinet[0].id);
                        componentManager.sortComponentArray(cabinet);
                        break;
                    }
                }

                bedAdded = true;
                continue;
            }

            if (!wardrobeAdded) {
                if (blocksDoorway(corner)) continue;

                Component[] wardrobe = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.WARDROBE);
                objectEntities[corner.x][corner.y].add(wardrobe[0].id);
                componentManager.sortComponentArray(wardrobe);

                wardrobeAdded = true;
                continue;
            }

            if (!plantAdded) {
                if (blocksDoorway(corner)) continue;

                Component[] plant = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.OFFICE_PLANT);
                objectEntities[corner.x][corner.y].add(plant[0].id);
                componentManager.sortComponentArray(plant);

                plantAdded = true;
            }
        }

        // We want bedrooms to have wooden floors
        String[] floor = new String[] {BuildingTileset.WOOD_FLOOR_1, BuildingTileset.WOOD_FLOOR_2, BuildingTileset.WOOD_FLOOR_3};
        retextureFloor(room, floor[rng.getRandomInt(0, floor.length - 1)]);

        bedroomCount++;
    }

    private void convertRoomToBathroom(MapRegion room) {
        ArrayList<Vector2D> corners = GridGeometryHelper.findInsideCorners(room.getVectors());

        boolean toiletAdded = false;
        boolean sinkAdded = false;
        boolean bathAdded = false;

        // Iterate over each corner and attempt to place decorations in order of importance.

        for (Vector2D corner : corners) {
            if (!toiletAdded) {
                if (blocksDoorway(corner)) continue;

                Component[] decal = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.TOILET);
                objectEntities[corner.x][corner.y].add(decal[0].id);
                componentManager.sortComponentArray(decal);

                toiletAdded = true;
                continue;
            }

            if (!sinkAdded) {
                if (blocksDoorway(corner)) return;

                Component[] decal = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.SINK);
                objectEntities[corner.x][corner.y].add(decal[0].id);
                componentManager.sortComponentArray(decal);


                sinkAdded = true;
                continue;
            }

            if (!bathAdded) {
                if (blocksDoorway(corner)) return;

                Component[] decal = DecalFactory.createDecal(corner.x, corner.y, BuildingTileset.BATH);
                objectEntities[corner.x][corner.y].add(decal[0].id);
                componentManager.sortComponentArray(decal);

                bathAdded = true;
            }
        }

        // Give bathrooms a marble tiled floor
        retextureFloor(room, BuildingTileset.MARBLE_FLOOR_2);

        bathroomCount++;
    }

    private boolean blocksDoorway(Vector2D cell) {
        for (Vector2D direction : Directions.Cardinal.values()) {
            Vector2D adjacent = cell.add(direction);
            if (inBounds(adjacent)) {

                if (TileCategorySorter.isDoorway(terrainTiles[adjacent.x][adjacent.y])) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addNorthWallTiles(Room room) {
        /*Vector topLeft = new Vector(room.x(), room.y() + room.height());
        Vector topRight = new Vector(room.x() + room.width(), room.y() + room.height());

        for (int x = topLeft.x(); x < topRight.x(); x++) {
            Vector cell = new Vector(x, room.y() + room.height());
            GameObject object = getTerrainComponent(cell);

            if (object.shader == Terrain.WALL && adjacentCellsAreCarvable(cell)) {
                object.setSprite(BuildingTileset.WALLPAPER_1);
            }
        }*/
    }

    /**
     *  Finds appropriate place to add decoration/s and adds random decoration to mapData.
     *  50% possibility that it will add a decoration. Ignores rooms where the north wall
     *  only takes up a single block
     */

    private void decorateNorthWall(Room room) {
        Vector2D topLeft = new Vector2D(room.x(), room.y() + room.height());
        Vector2D topRight = new Vector2D(room.x() + room.width(), room.y() + room.height());
        Vector2D doorPosition = null;
        Vector2D itemCoord;

        int numberOfDoors = 0;

        // Check for doors
        for (int x = topLeft.x(); x < topRight.x(); x++) {
            Vector2D cell = new Vector2D(x, room.y() + room.height());
            if (TileCategorySorter.isDoorway(terrainTiles[cell.x][cell.y])) {
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
                itemCoord = new Vector2D(topLeft.x() + (int) Math.floor(lengthA / 2), topLeft.y());
            }

            else {
                itemCoord = new Vector2D(doorPosition.x() + (int) Math.floor(lengthB / 2), topLeft.y());
            }

            Vector2D north = itemCoord.add(Directions.Cardinal.get("NORTH"));

            if (TileCategorySorter.isFloor(terrainTiles[north.x][north.y])) {
                // Ignore single-tiled walls
                return;
            }
        }

        else {
            // Place item in centre. TODO: multiple decorations, or multi-getSprite decorations
            itemCoord = new Vector2D(room.x() + (int) Math.floor(room.width() / 2), topLeft.y());
        }

        Component[] decal = DecalFactory.createDecal(itemCoord.x, itemCoord.y,
                BuildingTileset.DECORATIONS[rng.getRandomInt(0, BuildingTileset.DECORATIONS.length - 1)]);
        objectEntities[itemCoord.x][itemCoord.y].add(decal[0].id);
        componentManager.sortComponentArray(decal);
    }

    /**
     *  Replaces texture of floor objects contained in room.
     */

    private void retextureFloor(MapRegion room, String imgPath) {
        for (Vector2D pos : room.getVectors()) {
            terrainTiles[pos.x][pos.y] = imgPath;
        }
    }

    /**
     *  Library rooms are full of rows/columns of bookshelves.
     *  Otherwise they are treated the same as any other room
     */

    private void convertRoomToLibrary(MapRegion room) {
        Chunk boundingBox = GridGeometryHelper.getBoundingBox(room.getCorners());

        if (rng.getRandomInt(0, 1) == 0) { // Add rows of bookshelves

            // Check whether height is odd/even to decide where to start placing bookshelf rows
            int startPositionY;
            boolean isEven = false;

            if (boundingBox.height % 2 == 1) {
                // Start against wall
                startPositionY = boundingBox.y;
            }
            else {
                // Start at square in front of wall
                startPositionY = boundingBox.y + 1;
                isEven = true;
            }

            for (int y = startPositionY; y < startPositionY + boundingBox.height + 1; y += 2) {
                Vector2D cell = new Vector2D(boundingBox.x, y);
                addBookshelfRow(cell, isEven);
            }
        }

        else {
            // Check whether height is odd/even to decide where to start placing bookshelf rows
            int startPositionX;
            boolean isEven = false;

            if (boundingBox.width % 2 == 1) {
                // Start against wall
                startPositionX = boundingBox.x;
            }
            else {
                // Start at square in front of wall
                startPositionX = boundingBox.x + 1;
                isEven = true;
            }

            for (int x = startPositionX; x < startPositionX + boundingBox.width; x += 2) {
                Vector2D cell = new Vector2D(x, boundingBox.y);
                addBookshelfColumn(cell, isEven);
            }
        }

        retextureFloor(room, BuildingTileset.MARBLE_FLOOR_1);

        libraryCount++;
    }

    private void addBookshelfColumn(Vector2D cell, boolean isEven) {
        while (inBounds(cell) && TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) {

            if (TileCategorySorter.isDoorway(terrainTiles[cell.x][cell.y])) return;

            Vector2D lookahead = cell.add(Directions.Cardinal.get("NORTH"));

            if (inBounds(lookahead) && TileCategorySorter.isFloor(terrainTiles[lookahead.x][lookahead.y])) {

                if (TileCategorySorter.isDoorway(terrainTiles[lookahead.x][lookahead.y])) return;

                if (!cellBlocksDoorway(cell, isEven)) {
                    String tile = BuildingTileset.BOOKSHELVES[rng.getRandomInt(0, BuildingTileset.BOOKSHELVES.length - 1)];
                    Component[] decal = DecalFactory.createCubeDecal(cell.x, cell.y, 1, tile, true, true);
                    objectEntities[cell.x][cell.y].add(decal[0].id);
                    componentManager.sortComponentArray(decal);
                }
            }

            else {
                return;
            }

            cell = cell.add(Directions.Cardinal.get("NORTH"));
        }
    }

    private void addBookshelfRow(Vector2D cell, boolean isEven) {
        while (inBounds(cell) && TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) {

            if (TileCategorySorter.isDoorway(terrainTiles[cell.x][cell.y])) return;

            Vector2D lookahead = cell.add(Directions.Cardinal.get("EAST"));

            if (inBounds(lookahead) && TileCategorySorter.isFloor(terrainTiles[lookahead.x][lookahead.y])) {

                if (TileCategorySorter.isDoorway(terrainTiles[lookahead.x][lookahead.y])) return;

                else if (!cellBlocksDoorway(cell, isEven)) {
                    String tile = BuildingTileset.BOOKSHELVES[rng.getRandomInt(0, BuildingTileset.BOOKSHELVES.length - 1)];
                    Component[] decal = DecalFactory.createCubeDecal(cell.x, cell.y, 1, tile, true, true);
                    objectEntities[cell.x][cell.y].add(decal[0].id);
                    componentManager.sortComponentArray(decal);
                }
            }

            else {
                return;
            }

            cell = cell.add(Directions.Cardinal.get("EAST"));
        }
    }

    private boolean cellBlocksDoorway(Vector2D cell, boolean isEven) {

        for (Vector2D direction : Directions.Cardinal.values()) {
            Vector2D vector = cell.add(direction);

            if (!inBounds(vector)) continue;

            if (TileCategorySorter.isDoorway(terrainTiles[vector.x][vector.y])) {
                return true;
            }

            // We should check two cells ahead to make sure.
            // If cell is out of bounds we don't have to worry

            Vector2D newCell = cell.add(direction.scale(2));

            if (inBounds(newCell)) {
                if (isEven && TileCategorySorter.isDoorway(terrainTiles[newCell.x][newCell.y])) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addLightingToRoom(Room room) {
        String[] bearings = new String[] {"NORTH", "EAST", "WEST"};
        String bearing = bearings[rng.getRandomInt(0, 2)];
        Vector2D direction = Directions.Cardinal.get(bearing);
        Vector2D cell = room.roundedCentre();

        while (inBounds(cell) && TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) {
            cell = cell.add(direction);
        }

        if (!inBounds(cell)) {
            return;
        }

        if (!TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y]) && !TileCategorySorter.isDoorway(terrainTiles[cell.x][cell.y])) {
            Component[] lightSource = DecalFactory.createDecal(cell.x, cell.y, getLightSourceTile(bearing));
            objectEntities[cell.x][cell.y].add(lightSource[0].id);
            componentManager.sortComponentArray(lightSource);
        }

    }

    private String getLightSourceTile(String bearing) {
        switch (themeKey) {
            case BuildingTileset.KEY:
                if (bearing.equals("EAST")) {
                    return BuildingTileset.WALL_LANTERN_EAST;
                }

                else return BuildingTileset.WALL_LANTERN;

            default:
                return GenericTileset.LIGHT_SOURCE;
        }
    }

    private void addItemsToRoom(Room room) {
        // float chanceToAdd = rng.getRandomFloat(0f, 1f);
        // if (chanceToAdd > chestChance) return;

        // Attempt to place the chest 8 times.
        // If this fails, just skip it

        for (Vector2D direction : Directions.All.values()) {
            Vector2D cell = room.roundedCentre();

            while (inBounds(cell) && TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) {
                cell = cell.add(direction);
            }

            cell = cell.subtract(direction);

            if (!TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) continue;

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
                objectEntities[cell.x][cell.y].add(chest[0].id);
                componentManager.sortComponentArray(chest);
                return;
            }
        }
    }

    private void addWeaponsToRoom(Room room) {
        // float chanceToAdd = rng.getRandomFloat(0f, 1f);
        // if (chanceToAdd > chestChance) return;

        // Attempt to place the chest 8 times.
        // If this fails, just skip it

        for (Vector2D direction : Directions.All.values()) {
            Vector2D cell = room.roundedCentre();

            while (inBounds(cell) && TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) {
                cell = cell.add(direction);
            }

            cell = cell.subtract(direction);

            if (!TileCategorySorter.isFloor(terrainTiles[cell.x][cell.y])) continue;

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
                objectEntities[cell.x][cell.y].add(chest[0].id);
                componentManager.sortComponentArray(chest);
                return;
            }
        }
    }

    private boolean objectBlockingPath(Vector2D cell) {
        final int PATH_LIMIT = 8;

        // As player can move diagonally, we only have to test two paths: north-south and east-west.
        ArrayList<ArrayList<Vector2D>> paths = new ArrayList<>();

        paths.add(new ArrayList<Vector2D>());
        paths.get(0).add(Directions.Cardinal.get("NORTH"));
        paths.get(0).add(Directions.Cardinal.get("SOUTH"));

        paths.add(new ArrayList<Vector2D>());
        paths.get(1).add(Directions.Cardinal.get("EAST"));
        paths.get(1).add(Directions.Cardinal.get("WEST"));

        // Test paths to make sure that player can move around object.
        for (ArrayList<Vector2D> path : paths) {
            Vector2D start = cell.add(path.get(0));
            Vector2D end = cell.add(path.get(1));

            if (!inBounds(start) || !inBounds(end)) continue;

            if (detectCollisions(start) || detectCollisions(end)) continue;

            ArrayList<Vector2D> bestPath = findShortestPathAroundObstacle(start, end, cell);

            // If path length === 0, no safe paths were found.
            // If path length exceeds PATH_LIMIT, the object is blocking player's path in a detrimental way
            if (bestPath.size() == 0 || bestPath.size() > PATH_LIMIT) {
                return true;
            }
        }

        return false;
    }

    private ArrayList<Vector2D> findShortestPathAroundObstacle(Vector2D startNode, Vector2D goalNode, Vector2D obstacle) {
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
                Log.w(LOG_TAG, "Duplicate node in findShortestPath at " + lastNode.toString());
                break;
            }

            Vector2D closestNode = null;
            double bestDistance = Double.MAX_VALUE;

            // Find adjacent node which is closest to goal
            for (Vector2D direction : Directions.All.values()) {
                Vector2D adjacentNode = currentNode.add(direction);

                if (!inBounds(adjacentNode)) continue;

                if (checkedNodes.contains(adjacentNode.toString())) continue;

                if (adjacentNode.equals(obstacle) || detectCollisions(adjacentNode)) continue;

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

    private void addEnemiesToRoom(Room room) {
        // First, decide whether we should place enemies at all
        if (rng.getRandomInt(0, 2) == 0) return;

        // Divide room size by 9 to find how many enemies would reasonably fit in room.
        // (eg. 1 enemy for 3x3, 2 for 5x5, etc)
        // Compare this value to maxEnemies and use smallest value.
        // Then choose random number between minimum enemy count + this value

        int enemyFit = (room.width() * room.height()) / 9;
        int numberOfEnemies;

        // Todo: why is this happening?
        if (minEnemies > Math.min(enemyFit, maxEnemies)) {
            numberOfEnemies = minEnemies;
        }
        else {
            numberOfEnemies = rng.getRandomInt(minEnemies, Math.min(enemyFit, maxEnemies));
        }

        // We don't want player to be mobbed by several enemies when starting a new floor.
        if (room.isEntrance() && numberOfEnemies > 1) {
            numberOfEnemies = 1;
        }

        // Iterate over each getSprite in room and find empty positions.
        // Avoid rounded centre because that is where player character would be placed

        ArrayList<Vector2D> emptyVectors = new ArrayList<>();
        Vector2D centre = room.roundedCentre();

        for (int x = room.x() + 1; x < room.x() + room.width() - 1; x++) {
            for (int y = room.y() + 1; y < room.y() + room.height() - 1; y++) {
                Vector2D pos = new Vector2D(x, y);
                if (!pos.equals(centre) && !detectCollisions(new Vector2D(x, y))) {
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
            Vector2D vector = emptyVectors.remove(random);
            int type = rng.getRandomInt(0, size - 1);
            int level = rng.getRandomInt(1, maxEnemyLevel);

            Component[] enemy = BlueprintParser.getComponentArrayForBlueprint(enemyBlueprints, keyArray.get(type));

            if (enemy != null) {
                objectEntities[vector.x][vector.y].add(enemy[0].id);
                componentManager.sortComponentArray(enemy);

                Position positionComponent = (Position) componentManager.getEntityComponent(enemy[0].id, Position.class.getSimpleName());

                if (positionComponent != null) {
                    positionComponent.x = vector.x;
                    positionComponent.y = vector.y;
                }
            }

            else {
                Log.e(LOG_TAG, "Enemy Component[] was null - error in blueprint?");
            }

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
            Vector2D north = new Vector2D(x, top);
            Vector2D south = new Vector2D(x, room.y() - 1);

            if (inBounds(north)) {
                if (TileCategorySorter.isDoorway(terrainTiles[north.x][north.y])) {
                    doorCount++;
                }
            }

            if (inBounds(south)) {
                if (TileCategorySorter.isDoorway(terrainTiles[south.x][south.y])) {
                    doorCount++;
                }
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector2D east = new Vector2D(room.x() - 1, y);
            Vector2D west = new Vector2D(room.x() + room.width(), y);

            if (inBounds(east)) {
                if (TileCategorySorter.isDoorway(terrainTiles[east.x][east.y])) {
                    doorCount++;
                }
            }

            if (inBounds(west)) {
                if (TileCategorySorter.isDoorway(terrainTiles[west.x][west.y])) {
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

    /**
     * Tests grid square to see if there are any objects that can be collided with
     *
     * @param position Vector to check
     * @return True if collisions would occur
     */

    private boolean detectCollisions(Vector2D position) {
        int x = position.x();
        int y = position.y();

        String terrain = terrainTiles[x][y];

        if (isBlocking(terrain)) {
            // Log.v("log", "map getSprite " + mapTile.getSprite() + " was blocking");
            return true;
        }

        else if (!isTraversable(terrain)) {
            // Log.v("log", "map getSprite " + mapTile.getSprite() + " was not traversable");
            return true;
        }

        ArrayList<Long> objectStack = objectEntities[x][y];

        for (Long object : objectStack) {

            Physics physics = (Physics) componentManager.getEntityComponent(object, Physics.class.getSimpleName());

            if (physics == null) {
                Log.e(LOG_TAG, "Physics was null");
                return true;
            }

            if (physics.isBlocking) {
                return true;
            }

            else if (!physics.isTraversable) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlocking(String tile) {
        switch (tile) {
            default:
                return false;
        }
    }

    private boolean isTraversable(String tile) {
        switch (tile) {
            default:
                return false;
        }
    }

    private boolean inBounds(Vector2D cell) {
        return (cell.x() >= 0 && cell.x() < mapWidth && cell.y() >= 0 && cell.y() < mapHeight);
    }

    private ArrayList<Vector2D> getWallVectors(Room room) {
        ArrayList<Vector2D> walls = new ArrayList<>();

        int right = room.x() + room.width() + 1;
        int top = room.y() + room.height();

        for (int x = room.x() - 1; x <= right; x++) {
            Vector2D north = new Vector2D(x, top);
            Vector2D south = new Vector2D(x, room.y() - 1);

            if (inBounds(north)) {
                walls.add(north);
            }

            if (inBounds(south)) {
                walls.add(south);
            }
        }

        for (int y = room.y() - 1; y <= top; y++) {
            Vector2D east = new Vector2D(room.x() - 1, y);
            Vector2D west = new Vector2D(room.x() + room.width(), y);

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
