package com.sonicmax.bloodrogue.renderer.ui;

import android.util.Log;

import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.generator.factories.AnimationFactory;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;
import com.sonicmax.bloodrogue.tilesets.UserInterfaceTileset;

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

    public boolean itemDetailTransitionComplete;

    public UserInterfaceBuilder(HashMap<String, Integer> spriteIndexes, int width, int height) {
        this.spriteIndexes = spriteIndexes;

        this.gridWidth = width;
        this.gridHeight = height;

        this.inventoryWindowWidth = this.gridWidth - 2;
        this.inventoryWindowHeight = this.gridHeight - 2;

        getSpriteIndexesForUi(spriteIndexes);
        setDefaultIconState();

        // Create sprite components for UI buttons so we can use transition methods
        okButton = new Sprite(-1);
        okButton.spriteIndex = inventoryOk;

        cancelButton = new Sprite(-1);
        cancelButton.spriteIndex = inventoryCancel;

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

        uiRenderer.addSpriteData(x, y, bottomLeft, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
        x++;

        while (x < gridWidth - 1) {
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

            while (x < gridWidth - 1) {
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

        while (x < gridWidth - 1) {
            uiRenderer.addSpriteData(x, y, top, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            x++;
        }

        uiRenderer.addSpriteData(x, y, topRight, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
    }

    public void populateInventory(Container container, Dexterity dex, SpriteRenderer uiRenderer) {
        ArrayList<Sprite> items = container.contents;
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

                if (item.id == dex.weaponEntity) {
                    uiRenderer.addSpriteData(x, y, inventorySelected, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                }

                uiRenderer.addSpriteData(x, y, item.spriteIndex, DEFAULT_LIGHTING, DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

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

    public void animateItemDetailTransition(Sprite sprite, SpriteRenderer uiRenderer) {
        sprite.x = gridWidth - (INVENTORY_WINDOW_BORDER * 2);
        sprite.y = gridHeight - (INVENTORY_WINDOW_BORDER * 2);

        float fraction = getTransitionProgress(sprite);
        float offsetX = (sprite.x - sprite.lastX) * fraction;
        float offsetY = (sprite.y - sprite.lastY) * fraction;
        float lighting = 1f;

        if (fraction == 1) {
            sprite.movementStep = 0;
            sprite.lastX = -1;
            sprite.lastY = -1;
            uiRenderer.addSpriteData(sprite.x, sprite.y, sprite.spriteIndex, lighting);
            itemDetailTransitionComplete = true;

        } else {
            uiRenderer.addSpriteData(sprite.lastX, sprite.lastY, sprite.spriteIndex, lighting, offsetX, offsetY);
        }

        uiRenderer.addSpriteData(gridWidth - 3, 2, inventoryOk, 1f);
        uiRenderer.addSpriteData(gridWidth - 2, 2, inventoryCancel, 1f);
    }

    private float getTransitionProgress(Sprite sprite) {
        if (sprite.movementStep >= 15) {
            return 1;
        }

        sprite.movementStep++;

        // Find fraction that we should move by
        float fraction = 1f / 15 * sprite.movementStep;
        // Return squared value to provide simple easing effect on movement
        return (fraction * fraction * fraction);
    }

    public void showItemDetailView(InventoryCard details, SpriteRenderer uiRenderer) {
        // Todo: move text stuff from GameRenderer to here. oioioi
        uiRenderer.addSpriteData(details.sprite.x, details.sprite.y, details.sprite.spriteIndex, 1f);
        uiRenderer.addSpriteData(gridWidth - 3, 2, inventoryOk, 1f);
        uiRenderer.addSpriteData(gridWidth - 2, 2, inventoryCancel, 1f);
    }
}
