package com.sonicmax.bloodrogue.renderer.ui;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.generator.factories.AnimationFactory;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;

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

    // Inventory state
    private boolean inventoryOpen;
    private boolean inventoryClosed;
    private boolean inventoryOpenAnim;
    private boolean inventoryCloseAnim;
    private Animation inventoryAnimation;

    private HashMap<String, Integer> spriteIndexes;

    private int gridWidth;
    private int gridHeight;

    public UserInterfaceBuilder(HashMap<String, Integer> spriteIndexes, int width, int height) {
        this.spriteIndexes = spriteIndexes;
        this.gridWidth = width;
        this.gridHeight = height;
        getSpriteIndexesForUi(spriteIndexes);
        setDefaultIconState();
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

    public void buildUi(SpriteRenderer renderer) {
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
        float lighting = 1f;

        int x = 0;
        int width = gridWidth;
        int y = 1;

        // Add bottom row sprites

        uiRenderer.addSpriteData(x, y, bottomLeft, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
        x++;

        while (x < width - 1) {
            uiRenderer.addSpriteData(x, y, bottom, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            x++;
        }

        uiRenderer.addSpriteData(x, y, bottomRight, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

        // Add middle row sprites
        x = 0;
        y++;

        while (y < gridHeight - 1) {
            uiRenderer.addSpriteData(x, y, left, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            x++;

            while (x < width - 1) {
                uiRenderer.addSpriteData(x, y, middle, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                x++;
            }

            uiRenderer.addSpriteData(x, y, right, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

            // Reset x after each row
            x = 0;
            y++;
        }

        // Add bottom row sprites

        uiRenderer.addSpriteData(x, y, topLeft, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
        x++;

        while (x < width - 1) {
            uiRenderer.addSpriteData(x, y, top, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            x++;
        }

        uiRenderer.addSpriteData(x, y, topRight, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
    }

    public void populateInventory(Container container, SpriteRenderer uiRenderer) {
        ArrayList<Sprite> items = container.contents;
        int itemSize = items.size();
        int itemIndex = 0;

        if (itemSize == 0) return;

        int x = 1;
        int width = gridWidth;
        int y = gridHeight - 2;

        while (itemIndex < itemSize && y > 2) {
            while (itemIndex < itemSize && x < width - 1) {
                if (items.get(itemIndex).spriteIndex == -1) {
                    items.get(itemIndex).spriteIndex = spriteIndexes.get(items.get(itemIndex).path);
                }
                uiRenderer.addSpriteData(x, y, items.get(itemIndex).spriteIndex, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                x++;
                itemIndex++;
            }

            // Reset x after each row
            x = 1;
            y--;
        }
    }
}
