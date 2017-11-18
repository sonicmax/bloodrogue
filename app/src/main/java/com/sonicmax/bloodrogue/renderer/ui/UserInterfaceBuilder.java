package com.sonicmax.bloodrogue.renderer.ui;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.generator.factories.AnimationFactory;
import com.sonicmax.bloodrogue.renderer.GameRenderer;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.renderer.text.TextRenderer;
import com.sonicmax.bloodrogue.tilesets.UserInterfaceTileset;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.ArrayList;
import java.util.HashMap;

public class UserInterfaceBuilder {
    public static final int INVENTORY_OPEN = 0;
    public static final int INVENTORY_CLOSED = 1;

    private final String LOG_TAG = this.getClass().getSimpleName();

    private final float DEFAULT_OFFSET_X = 0f;
    private final float DEFAULT_OFFSET_Y = 0f;
    private final float DEFAULT_LIGHTING = 1f;

    private int bottomLeft;
    private int bottom;
    private int bottomRight;
    private int left;
    private int middle;
    private int right;
    private int topLeft;
    private int top;
    private int topRight;
    private int openInventoryIcon;
    private int closedInventoryIcon;
    private int inventorySelected;
    private int inventoryCancel;
    private int inventoryOk;

    // Inventory state
    private boolean inventoryOpen;
    private boolean inventoryClosed;
    private boolean inventoryOpenAnim;
    private boolean inventoryCloseAnim;
    private Animation inventoryAnimation;

    private HashMap<String, Integer> spriteIndexes;

    private final int INVENTORY_WINDOW_BORDER = 1;

    private int gridWidth;
    private int gridHeight;
    private int inventoryWindowWidth;
    private int inventoryWindowHeight;

    private final Sprite okButton;
    private final Sprite cancelButton;

    private Container inventory;
    private Dexterity equipment;

    private InventoryCard inventoryCard;
    private String itemDetailName;
    private String itemDetailDescription;
    private String itemDetailAttribs;
    private String itemDetailWeight;

    private float windowLeft;
    private float windowRight;
    private float scaleFactor;

    private long selectedItem = -1;

    private ArrayList<String> itemDescriptionLines;

    public boolean itemDetailTransitionComplete;
    public boolean inventoryTransitionComplete;

    public UserInterfaceBuilder(HashMap<String, Integer> spriteIndexes, int width, int height) {
        this.spriteIndexes = spriteIndexes;

        this.gridWidth = width;
        this.gridHeight = height;

        this.inventoryWindowWidth = this.gridWidth - 2;
        this.inventoryWindowHeight = this.gridHeight - 2;

        getSpriteIndexesForUi(spriteIndexes);
        setDefaultIconState();

        // Create sprite components for UI buttons so we can use transition methods
        this.okButton = new Sprite(-1);
        this.okButton.spriteIndex = inventoryOk;

        this.cancelButton = new Sprite(-1);
        this.cancelButton.spriteIndex = inventoryCancel;

        this.itemDescriptionLines = new ArrayList<>();

        this.itemDetailName = "";
        this.itemDetailDescription = "";
        this.itemDetailAttribs = "";
        this.itemDetailWeight = "";
    }

    private void setDefaultIconState() {
        inventoryClosed = true;
        inventoryOpen = false;
        inventoryCloseAnim = false;
        inventoryOpenAnim = false;
    }

    /**
     *  Caches relevant sprite indexes to this instance so we don't have to look them up each render
     */

    public void addUiIcons(SpriteRenderer renderer) {
        addIcons(renderer);
    }

    private void getSpriteIndexesForUi(HashMap<String, Integer> spriteIndexes) {
        bottomLeft = spriteIndexes.get(UserInterfaceTileset.WINDOW_BOTTOM_LEFT);
        bottom = spriteIndexes.get(UserInterfaceTileset.WINDOW_BOTTOM);
        bottomRight = spriteIndexes.get(UserInterfaceTileset.WINDOW_BOTTOM_RIGHT);
        left = spriteIndexes.get(UserInterfaceTileset.WINDOW_LEFT);
        middle = spriteIndexes.get(UserInterfaceTileset.WINDOW_MIDDLE);
        right = spriteIndexes.get(UserInterfaceTileset.WINDOW_RIGHT);
        topLeft = spriteIndexes.get(UserInterfaceTileset.WINDOW_TOP_LEFT);
        top = spriteIndexes.get(UserInterfaceTileset.WINDOW_TOP);
        topRight =  spriteIndexes.get(UserInterfaceTileset.WINDOW_TOP_RIGHT);
        openInventoryIcon = spriteIndexes.get(UserInterfaceTileset.INVENTORY_ICON_OPEN);
        closedInventoryIcon = spriteIndexes.get(UserInterfaceTileset.INVENTORY_ICON);
        inventorySelected = spriteIndexes.get(UserInterfaceTileset.WINDOW_SELECTED);
        inventoryCancel = spriteIndexes.get("sprites/inventory_cancel.png");
        inventoryOk = spriteIndexes.get("sprites/inventory_ok.png");
    }

    public void addIcons(SpriteRenderer uiRenderer) {
        if (inventoryOpenAnim) {
            int frame = processAnimation(inventoryAnimation);

            if (inventoryAnimation.isFinished()) {
                inventoryOpen = true;
                inventoryOpenAnim = false;
            }

            else {
                uiRenderer.addSpriteData(gridWidth - 1, 0, frame, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            }
        }

        else if (inventoryCloseAnim) {
            int frame = processAnimation(inventoryAnimation);

            if (inventoryAnimation.isFinished()) {
                inventoryClosed = true;
                inventoryCloseAnim = false;
            }

            else {
                uiRenderer.addSpriteData(gridWidth - 1, 0, frame, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            }
        }

        if (inventoryOpen) {
            uiRenderer.addSpriteData(gridWidth - 1, 0, openInventoryIcon, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
        }
        else if (inventoryClosed) {
            uiRenderer.addSpriteData(gridWidth - 1, 0, closedInventoryIcon, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
        }
    }

    public void animateIcon(int type) {
        switch (type) {
            case INVENTORY_OPEN:
                inventoryOpenAnim = true;
                inventoryClosed = false;
                inventoryAnimation = AnimationFactory.getInventoryOpenAnimation(gridWidth - 1, 0);
                break;

            case INVENTORY_CLOSED:
                inventoryCloseAnim = true;
                inventoryOpen = false;
                inventoryAnimation = AnimationFactory.getInventoryCloseAnimation(gridWidth - 1, 0);
                break;

            default:
                break;
        }
    }

    private int processAnimation(Animation animation) {
        final int TRANSPARENT = 164;

        try {
            if (animation.isFinished()) {
                return TRANSPARENT;
            }

        } catch (NullPointerException e) {
            Log.e(LOG_TAG, "Error while rendering animation");
            return TRANSPARENT;
        }

        return spriteIndexes.get(animation.getNextFrame());
    }


    /**
     *   Adds empty window to screen.
     */

    public void addWindow(SpriteRenderer uiRenderer) {
        int x = 0;
        int y = 1;

        // Add bottom row sprites

        uiRenderer.addSpriteData(x, y, bottomLeft, DEFAULT_LIGHTING);
        x++;

        while (x < gridWidth - 1) {
            uiRenderer.addSpriteData(x, y, bottom, DEFAULT_LIGHTING);
            x++;
        }

        uiRenderer.addSpriteData(x, y, bottomRight, DEFAULT_LIGHTING);

        // Add middle row sprites
        x = 0;
        y++;

        while (y < gridHeight - 1) {
            uiRenderer.addSpriteData(x, y, left, DEFAULT_LIGHTING);
            x++;

            while (x < gridWidth - 1) {
                uiRenderer.addSpriteData(x, y, middle, DEFAULT_LIGHTING);
                x++;
            }

            uiRenderer.addSpriteData(x, y, right, DEFAULT_LIGHTING);

            // Reset x after each row
            x = 0;
            y++;
        }

        // Add bottom row sprites

        uiRenderer.addSpriteData(x, y, topLeft, DEFAULT_LIGHTING);
        x++;

        while (x < gridWidth - 1) {
            uiRenderer.addSpriteData(x, y, top, DEFAULT_LIGHTING);
            x++;
        }

        uiRenderer.addSpriteData(x, y, topRight, DEFAULT_LIGHTING);
    }

    public void populateInventory(SpriteRenderer uiRenderer) {
        populateInventory(uiRenderer, 1f);
    }

    public void setPlayerComponents(Container container, Dexterity dexterity) {
        this.inventory = container;
        this.equipment = dexterity;
    }

    public void populateInventory(SpriteRenderer uiRenderer, float alpha) {
        ArrayList<Sprite> items = inventory.contents;
        int itemSize = items.size();
        int itemIndex = 0;

        if (itemSize == 0) return;

        int x = INVENTORY_WINDOW_BORDER;
        int width = gridWidth;
        int y = gridHeight - (INVENTORY_WINDOW_BORDER * 2);

        while (itemIndex < itemSize && y > 3) {
            while (itemIndex < itemSize && x < width - INVENTORY_WINDOW_BORDER) {
                Sprite item = items.get(itemIndex);

                if (item.spriteIndex == -1) {
                    item.spriteIndex = spriteIndexes.get(item.path);
                }

                if (item.id == selectedItem) {
                    if (alpha == 1) {
                        uiRenderer.addSpriteData(x, y, item.spriteIndex, DEFAULT_LIGHTING, alpha);
                    }
                }

                else if (item.id == equipment.weaponEntity) {
                    uiRenderer.addSpriteData(x, y, inventorySelected, DEFAULT_LIGHTING, alpha);
                    uiRenderer.addSpriteData(x, y, item.spriteIndex, DEFAULT_LIGHTING, alpha);
                }

                else {
                    uiRenderer.addSpriteData(x, y, item.spriteIndex, DEFAULT_LIGHTING, alpha);
                }

                item.x = x;
                item.y = y;
                item.lastX = x;
                item.lastY = y;

                x++;
                itemIndex++;
            }

            // Reset x after each row
            x = 1;
            y--;
        }
    }

    public void animateItemDetailTransition(GameRenderer renderer, SpriteRenderer uiRenderer, TextRenderer uiTextRenderer, Sprite sprite) {
        selectedItem = sprite.id;
        sprite.x = gridWidth - (INVENTORY_WINDOW_BORDER * 2);
        sprite.y = gridHeight - (INVENTORY_WINDOW_BORDER * 2);

        float fraction = getTransitionProgress(sprite);
        float offsetX = (sprite.x - sprite.lastX) * fraction;
        float offsetY = (sprite.y - sprite.lastY) * fraction;
        float lighting = 1f;

        if (fraction == 1) {
            sprite.movementStep = 0;
            uiRenderer.addSpriteData(sprite.x, sprite.y, sprite.spriteIndex, lighting);
            uiRenderer.addSpriteData(gridWidth - 3, 2, inventoryOk, 1f);
            uiRenderer.addSpriteData(gridWidth - 2, 2, inventoryCancel, 1f);
            renderDetailText(renderer, uiTextRenderer);

            itemDetailTransitionComplete = true;

        } else {
            uiRenderer.addSpriteData(sprite.lastX, sprite.lastY, sprite.spriteIndex, lighting, offsetX, offsetY);
            uiRenderer.addSpriteData(gridWidth - 3, 2, inventoryOk, 1f, fraction);
            uiRenderer.addSpriteData(gridWidth - 2, 2, inventoryCancel, 1f, fraction);
            populateInventory(uiRenderer, 1f - fraction);
            renderDetailText(renderer, uiTextRenderer, 1f - fraction);
        }
    }

    public void processInventoryCard(InventoryCard card, TextRenderer textRenderer) {
        this.inventoryCard = card;
        this.itemDetailName = inventoryCard.name;
        this.itemDetailDescription = inventoryCard.desc;
        this.itemDetailAttribs = inventoryCard.attributes;
        this.itemDetailWeight = inventoryCard.weight;

        // Split description string into lines so we can display full string
        // in inventory detail view

        if (itemDetailDescription != null && !itemDetailDescription.equals("")
                && itemDescriptionLines.size() == 0) {

            splitDescription(textRenderer);
        }
    }

    public int getDetailTextSize() {
        return itemDetailName.length() + itemDetailDescription.length()
                + itemDetailAttribs.length() + itemDetailWeight.length();
    }

    public void clearInventoryCard() {
        itemDetailName = "";
        itemDetailDescription = "";
        itemDetailAttribs = "";
        itemDetailWeight = "";
        itemDescriptionLines.clear();
    }

    /**
     *  Iterates over words in itemDetailDescription string and splits into multiple lines
     *  for displaying in inventory detail view
     */

    private void splitDescription(TextRenderer textRenderer) {
        final float SPRITE_SIZE = 64;
        String[] split = itemDetailDescription.split(" ");

        StringBuilder stringBuilder = new StringBuilder();

        float inventoryWidth = (windowRight - windowLeft) / scaleFactor;

        for (int i = 0; i < split.length; i++) {
            String word = split[i] + " ";

            if (textRenderer.getExpectedTextWidth(stringBuilder.toString() + word) <= inventoryWidth + SPRITE_SIZE) {
                stringBuilder.append(word);
            } else {
                // Add new line to array, empty StringBuilder and start next line with current word
                itemDescriptionLines.add(stringBuilder.toString());
                stringBuilder.setLength(0);
                stringBuilder.append(word);
            }
        }

        itemDescriptionLines.add(stringBuilder.toString());
    }

    public void setBoundsAndScale(float left, float right, float scaleFactor) {
        this.windowLeft = left;
        this.windowRight = right;
        this.scaleFactor = scaleFactor;
    }

    private float getTransitionProgress(Sprite sprite) {
        if (sprite.movementStep >= 15) {
            return 1;
        }

        sprite.movementStep++;

        // Find fraction that we should move by
        float fraction = 1f / 15 * sprite.movementStep;
        // Return squared value to provide simple easing effect on movement
        return (fraction * fraction);
    }

    public void showItemDetailView(SpriteRenderer uiRenderer, InventoryCard details) {
        // Todo: move text stuff from GameRenderer to here. oioioi
        uiRenderer.addSpriteData(details.sprite.x, details.sprite.y, details.sprite.spriteIndex, 1f);
        uiRenderer.addSpriteData(gridWidth - 3, 2, inventoryOk, 1f);
        uiRenderer.addSpriteData(gridWidth - 2, 2, inventoryCancel, 1f);
    }

    public void renderDetailText(GameRenderer renderer, TextRenderer uiTextRenderer) {
        renderDetailText(renderer, uiTextRenderer, 0f);
    }

    public void renderDetailText(GameRenderer renderer, TextRenderer uiTextRenderer, float alphaModifier) {
        float[] offset;

        offset = renderer.getRenderCoordsForObject(new Vector(1, gridHeight - 3), false);
        uiTextRenderer.addTextData(offset[0], offset[1], DEFAULT_OFFSET_Y, 1f, itemDetailName, TextColours.WHITE, alphaModifier);


        offset = renderer.getRenderCoordsForObject(new Vector(1, gridHeight - 6), false);

        for (int i = 0; i < itemDescriptionLines.size(); i++) {
            uiTextRenderer.addTextRowData(itemDescriptionLines.size() - i, offset[0], offset[1],  itemDescriptionLines.get(i), TextColours.YELLOW, alphaModifier);
        }

        offset = renderer.getRenderCoordsForObject(new Vector(1, gridHeight - 7), false);

        if (selectedItem == equipment.weaponEntity || selectedItem == equipment.armourEntity) {
            uiTextRenderer.addTextRowData(0, offset[0], offset[1], "(equipped)", TextColours.ROYAL_BLUE, alphaModifier);
        }

        offset = renderer.getRenderCoordsForObject(new Vector(1, 2), false);
        uiTextRenderer.addTextRowData(1, offset[0], offset[1],  itemDetailAttribs, TextColours.ROYAL_BLUE, alphaModifier);
        uiTextRenderer.addTextRowData(0, offset[0], offset[1],  itemDetailWeight, TextColours.WHITE, alphaModifier);
    }

    public void animateInventoryTransition(GameRenderer renderer, SpriteRenderer uiRenderer, TextRenderer uiTextRenderer, Sprite sprite) {
        selectedItem = sprite.id;
        sprite.x = gridWidth - (INVENTORY_WINDOW_BORDER * 2);
        sprite.y = gridHeight - (INVENTORY_WINDOW_BORDER * 2);

        float fraction = getTransitionProgress(sprite);
        float offsetX = (sprite.lastX - sprite.x) * fraction;
        float offsetY = (sprite.lastY - sprite.y) * fraction;
        float lighting = 1f;

        if (fraction == 1) {
            sprite.movementStep = 0;
            populateInventory(uiRenderer, fraction);
            inventoryTransitionComplete = true;

        } else {
            uiRenderer.addSpriteData(sprite.x, sprite.y, sprite.spriteIndex, lighting, offsetX, offsetY);
            uiRenderer.addSpriteData(gridWidth - 3, 2, inventoryOk, 1f, 1f - fraction);
            uiRenderer.addSpriteData(gridWidth - 2, 2, inventoryCancel, 1f, 1f - fraction);
            populateInventory(uiRenderer, fraction);
            renderDetailText(renderer, uiTextRenderer, fraction);
        }
    }
}
