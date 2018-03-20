package com.sonicmax.bloodrogue.ui;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.renderer.ScreenSizeGetter;
import com.sonicmax.bloodrogue.renderer.text.Status;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.renderer.text.TextObject;
import com.sonicmax.bloodrogue.renderer.ui.InventoryCard;
import com.sonicmax.bloodrogue.renderer.ui.UserInterfaceRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class UserInterfaceController {
    private final float SPRITE_SIZE = 64f;
    private final float TARGET_UI_WIDTH = 448f;

    private GameInterface gameInterface;
    private UserInterfaceRenderer uiRenderer;

    // UI state
    private boolean inventoryDisplayed;
    private boolean inventorySelection;
    private InventoryCard inventoryCard;
    private String hp;
    private String xp;
    private String fps;
    private String floor;

    // UI scaling
    private float screenWidth;
    private float screenHeight;
    private int gridWidth;
    private int gridHeight;
    private float gridSize;
    private float scaleFactor;

    // Text
    private ArrayList<TextObject> narrations;
    private ArrayList<TextObject> statuses;
    private ArrayList<TextObject> queuedStatuses;
    private ArrayList<TextObject> queuedNarrations;
    private int textRowHeight;

    // Sprite sheet data
    private HashMap<String, Integer> spriteIndexes;

    public UserInterfaceController(GameInterface gameInterface, HashMap<String, Integer> spriteIndexes) {
        this.gameInterface = gameInterface;
        uiRenderer = new UserInterfaceRenderer(spriteIndexes, gridWidth, gridHeight);

        narrations = new ArrayList<>();
        statuses = new ArrayList<>();
        queuedNarrations = new ArrayList<>();
        queuedStatuses = new ArrayList<>();

        scaleUi();
        calculateUiGrid();

        hp = "";
        xp = "";
        floor = "";
        fps = "";
    }

    /*
    ------------------------------------------------------------------------------------------
    UI scaling
    ------------------------------------------------------------------------------------------
    */

    private void scaleUi() {
        screenWidth = ScreenSizeGetter.getWidth();
        screenHeight = ScreenSizeGetter.getHeight();

        /*if (targetWidth > screenWidth) {
            targetWidth = screenWidth;
        }*/

        float resX = (float) screenWidth / TARGET_UI_WIDTH;
        float resY = (float) screenHeight / TARGET_UI_WIDTH;

        if (resX > resY) {
            scaleFactor = resY;
        }

        else {
            scaleFactor = resX;
        }

        gridSize = SPRITE_SIZE * scaleFactor;
    }

    private void calculateUiGrid() {
        float width = ScreenSizeGetter.getWidth();
        float height = ScreenSizeGetter.getHeight();

        float spriteSize = SPRITE_SIZE * scaleFactor;

        double xInterval = width / spriteSize;
        double yInterval = height / spriteSize;

        gridWidth = (int) xInterval;
        gridHeight = (int) yInterval;
    }

    public void calculateTextRowHeight() {
        textRowHeight = uiRenderer.getTextRowHeight(ScreenSizeGetter.getHeight());
    }

    /*
    ------------------------------------------------------------------------------------------
    UI rendering
    ------------------------------------------------------------------------------------------
    */

    public void setUiText(String hp, String xp, String floor, String fps) {
        this.hp = hp;
        this.xp = xp;
        this.floor = floor;
        this.fps = fps;
    }

    public void buildUiTextObjects(int vitality, String time, String position, int fpsCount) {
        hp = "HP: " + vitality;

        xp = "Time: " + time; // timeManager.getTimeString() + " (" + weatherManager.getWeatherString()

        floor = "(" + position + ")";
        fps = fpsCount + " fps";

        if (inventorySelection && inventoryCard != null) {
            uiRenderer.processInventoryCard(inventoryCard);
        }

        else {
            uiRenderer.clearInventoryCard();

        }
    }

    public void addSplashText(String text) {
        uiRenderer.prepareGlSurface();
        uiRenderer.renderSplashText(text);
    }

    private int countTextObjects() {
        int narrationSize = 0;
        int statusSize = 0;
        int uiText = 0;

        uiText += hp.length() + xp.length() + + floor.length() + fps.length();

        int size = narrations.size();
        for (int i = 0; i < size; i++) {
            narrationSize += narrations.get(i).text.length();
        }

        size = statuses.size();
        for (int i = 0; i < size; i++) {
            statusSize += statuses.get(i).text.length();
        }

        return narrationSize + statusSize + uiText + uiRenderer.getDetailTextSize() * 2;
    }

    public boolean checkUiTouch(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        float height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        float spriteSize = SPRITE_SIZE * scaleFactor;

        int gridX = (int) (x / spriteSize);
        int gridY = (int) (correctedY / spriteSize);


        if (gridX == gridWidth - 1 && gridY == 0) {
            if (!inventoryDisplayed) {
                inventoryDisplayed = true;
                uiRenderer.animateIcon(UserInterfaceRenderer.INVENTORY_OPEN);
            }
            else {
                inventoryDisplayed = false;
                inventorySelection = false;
                uiRenderer.animateIcon(UserInterfaceRenderer.INVENTORY_CLOSED);
            }

            return true;
        }

        if (inventoryDisplayed) {

            if (inventorySelection) {
                // Check whether user clicked UI button & handle event. Otherwise close detail view
                if (gridY == 2) {

                    if (gridX == gridWidth - 3) { // OK
                        gameInterface.handleInventorySelection(inventoryCard.sprite.id, true);
                    }
                    else if (gridX == gridWidth - 2) { // Cancel
                        gameInterface.handleInventorySelection(inventoryCard.sprite.id, false);
                    }
                }

                // Todo: maybe close inventory screen completely?
                inventorySelection = false;
                uiRenderer.inventoryTransitionComplete = false;
                return true;
            }

            // Check if user touched inventory item.
            else if (gridX > 0 && gridX < gridWidth - 1 && gridY > 1 && gridY < gridHeight - 1) {
                int inventoryWidth = gridWidth - 2;
                int inventoryHeight = gridHeight - 2;

                // This messy code gives us a coord with 0,0 origin at top-left (matching order
                // that items are displayed) so we can figure out which item was selected
                gridX -= 1;
                gridY -= inventoryHeight;
                gridY = -gridY;

                int index = (gridY * inventoryWidth) + gridX;
                long entity = gameInterface.processInventoryClick(index);
                if (entity > -1) {
                    uiRenderer.itemDetailTransitionComplete = false;
                    inventorySelection = true;
                    inventoryCard = gameInterface.getEntityDetails(entity);
                }
            }

            // Always return true while inventory is displayed - game content is not in focus.
            return true;
        }

        return false;
    }

    private int countUiSprites() {
        int windowSize = gridWidth * gridHeight;
        int inventoryCount = windowSize;
        int iconCount = 1;
        return windowSize + inventoryCount + iconCount;
    }

    public void render(float dt) {
        uiRenderer.prepareGlSurface();
        uiRenderer.initArrays(countTextObjects());
        addUiLayer(dt);
        addUiTextLayer();
        uiRenderer.render();
    }

    public void addUiLayer(float dt) {
        /*if (currentPathSelection != null) {
            int index = spriteIndexes.get("sprites/cursor_default.png");
            for (int i = 0; i < currentPathSelection.size(); i++) {
                Vector segment = currentPathSelection.get(i);
                int x = segment.x();
                int y = segment.y();
                exteriorRenderer.addSpriteData(x, y, index, 1f, 0f, 0f);
            }
        }*/

        if (inventoryDisplayed) {
            uiRenderer.addWindow();

            if (inventorySelection && inventoryCard != null) {
                if (!uiRenderer.itemDetailTransitionComplete) {
                    uiRenderer.animateItemDetailTransition(inventoryCard.sprite);
                }
                else {
                    uiRenderer.showItemDetailView(inventoryCard);
                    uiRenderer.renderDetailText();
                }

            }
            else {
                if (!uiRenderer.inventoryTransitionComplete && inventoryCard != null) {
                    uiRenderer.animateInventoryTransition(inventoryCard.sprite);
                }
                else {
                    uiRenderer.populateInventory();
                }
            }
        }

        uiRenderer.addIcons(dt);
    }

    public void addUiTextLayer() {
        addNarrationsToRenderer();

        Iterator<TextObject> it = statuses.iterator();

        while (it.hasNext()) {
            Status status = (Status) it.next();
            float fraction = status.advanceScroll();
            if (fraction == 1) {
                it.remove();
            } else {
                status.offsetY = fraction;
                status.alphaModifier = fraction;
                uiRenderer.addTextData(status.x, status.y, status.offsetY, status.scale, status.text, status.color, status.alphaModifier);
            }
        }

        uiRenderer.addTextRowData(textRowHeight - 1, hp, TextColours.RED, 0f);
        uiRenderer.addTextRowData(textRowHeight - 2, xp, TextColours.YELLOW, 0f);

        uiRenderer.addTextRowData(textRowHeight - 1, screenWidth / 1.5f, floor, TextColours.WHITE, 0f);
        uiRenderer.addTextRowData(textRowHeight - 2, screenWidth / 1.5f, fps, TextColours.WHITE, 0f);
    }

    /**
     *  Adding new text objects while renderVisibleTiles() is executing runs the risk of causing ArrayOutOfBoundsException
     *  because float arrays in renderer won't be big enough to accomodate new objects. So we queue them and add
     *  once the rendering has finished.
     */

    public void queueNarrationUpdate(ArrayList<TextObject> narrations) {
        this.queuedNarrations = narrations;
    }

    public void prepareUiRenderer(int programHandle, int spriteSheetHandle, int fontHandle) {
        uiRenderer.prepareUiRenderer(programHandle, spriteSheetHandle);
        uiRenderer.prepareUiTextRenderer(programHandle, fontHandle);
    }

    public void queueNewStatus(TextObject object) {
        this.queuedStatuses.add(object);
    }

    public void addQueuedTextUpdates() {
        this.narrations = queuedNarrations;
        this.statuses.addAll(queuedStatuses);
        queuedStatuses.clear();
    }

    private void addNarrationsToRenderer() {
        ArrayList<String> currentNarration = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();

        int narrationSize = narrations.size();

        int row = 3;

        for (int i = 0; i < narrationSize; i++) {
            TextObject narration = narrations.get(i);
            String[] split = narration.text.split(" ");

            for (int j = 0; j < split.length; j++) {
                String word = split[j] + " ";

                if (uiRenderer.getExpectedTextWidth(stringBuilder.toString() + word) < screenWidth) {
                    stringBuilder.append(word);
                } else {
                    // Finish this line and add to renderer. Always add to 0th index
                    currentNarration.add(0, stringBuilder.toString());
                    stringBuilder.setLength(0);
                    stringBuilder.append(word);
                }
            }

            currentNarration.add(0, stringBuilder.toString());

            for (int k = 0; k < currentNarration.size(); k++) {
                uiRenderer.addTextRowData(row, currentNarration.get(k), narration.color, narration.alphaModifier);
                row++;
            }

            currentNarration.clear();
            stringBuilder.setLength(0);
        }
    }
}
