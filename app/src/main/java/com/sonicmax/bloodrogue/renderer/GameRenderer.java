package com.sonicmax.bloodrogue.renderer;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.engine.Component;
import com.sonicmax.bloodrogue.engine.Frame;
import com.sonicmax.bloodrogue.engine.components.Container;
import com.sonicmax.bloodrogue.engine.components.Dexterity;
import com.sonicmax.bloodrogue.engine.components.Experience;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.components.Vitality;
import com.sonicmax.bloodrogue.renderer.sprites.ImageLoader;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;
import com.sonicmax.bloodrogue.renderer.sprites.WaveEffectSpriteRenderer;
import com.sonicmax.bloodrogue.renderer.ui.Animation;
import com.sonicmax.bloodrogue.renderer.ui.InventoryCard;
import com.sonicmax.bloodrogue.renderer.ui.UserInterfaceBuilder;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.utils.Array2DHelper;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.renderer.text.Status;
import com.sonicmax.bloodrogue.renderer.text.TextObject;
import com.sonicmax.bloodrogue.renderer.text.TextRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {
    public static GameRenderer INSTANCE;

    private final String LOG_TAG = this.getClass().getSimpleName();
    private final int SPRITE_SIZE = 64;

    public static final int NONE = 0; // Rendering nothing
    public static final int TITLE = 1; // Rendering title screen
    public static final int MENU = 2; // Rendering main menu
    public static final int SPLASH = 3; // Rendering splash screen, waiting for operation to finish
    public static final int GAME = 4; // Rendering game content

    private final long FRAME_TIME = 16L;

    private Context context;
    private GameInterface gameInterface;
    private UserInterfaceBuilder uiBuilder;

    // Game state
    private Frame currentFloorData;
    private Frame updatedFloorData;
    private ArrayList<Vector> currentPathSelection;
    private double[][] fieldOfVision;
    private boolean[][] visitedTiles;
    private ArrayList<Sprite> movingSprites;

    // Renderers
    private SpriteRenderer spriteRenderer;
    private SpriteRenderer uiRenderer;
    private WaveEffectSpriteRenderer waveRenderer;
    private TextRenderer textRenderer;
    private TextRenderer uiTextRenderer;
    private SolidColourRenderer screenTransitionRenderer;

    // Resources
    private ImageLoader imageLoader;
    private HashMap<String, Integer> textureHandles; // Texture handles for loaded textures
    private HashMap<String, Integer> spriteIndexes; // Position on sprite sheet for particular texture
    private int[][] cachedTerrain;
    private int objectCount;

    // Text
    private ArrayList<TextObject> narrations;
    private ArrayList<TextObject> statuses;
    private ArrayList<TextObject> queuedStatuses;
    private ArrayList<TextObject> queuedNarrations;
    private int textRowHeight;

    // Matrixes for GL surface
    private final float[] mvpMatrix;
    private final float[] projMatrix;
    private final float[] modelViewMatrix;
    private float[] uiMatrix;
    private float[] scrollMatrix;

    // UI
    private boolean inventoryDisplayed = false;
    private String hp;
    private String xp;
    private String fps;
    private String floor;

    // Screen size and scaling
    private int screenWidth;
    private int screenHeight;
    private int mapGridWidth;
    private int mapGridHeight;
    private int visibleGridWidth;
    private int visibleGridHeight;
    private float refreshRate;
    private float targetUiWidth = 448f;
    private float targetWidth = 640f; // This should be multiple of 64

    private float zoomLevel;
    private float scaleFactor;
    private float uiScaleFactor;

    // Visible grid chunk
    private int chunkStartX;
    private int chunkStartY;
    private int chunkEndX;
    private int chunkEndY;
    private float fullChunkWidth;
    private float fullChunkHeight;

    private float gridSize;
    private float uiGridSize;
    private int uiGridWidth;
    private int uiGridHeight;

    private float[] upperWorldBounds;

    // Scrolling
    private float touchScrollDx = 0f;
    private float touchScrollDy = 0f;

    // Timing
    private long startTime;
    private long endTime;
    private int frameCount;
    private long currentFrameTime;
    private int fpsCount;

    // GL handles
    private int solidColourProgram;
    private int spriteShaderProgram;
    private int waveShaderProgram;

    // Player components that we need for UI
    private Container inventory;
    private Dexterity equipment;
    private Position position;
    private Vitality vitality;
    private Experience experience;

    // Inventory data
    private boolean inventorySelection;
    private InventoryCard inventoryCard;

    // State handlers
    private boolean hasResources;
    private boolean isRendering;
    private boolean firstRender;
    private boolean transitionIn;
    private boolean transitionOut;
    private float currentTransitionAlpha = 1f;
    private int renderState;
    private boolean halfSecPassed;

    public GameRenderer(Context context, GameInterface gameInterface) {
        this.context = context;
        this.gameInterface = gameInterface;

        currentFloorData = null;
        updatedFloorData = null;
        renderState = NONE;
        hasResources = false;
        isRendering = false;

        projMatrix = new float[16];
        modelViewMatrix = new float[16];
        mvpMatrix = new float[16];
        uiMatrix = new float[16];
        scrollMatrix = new float[16];

        narrations = new ArrayList<>();
        statuses = new ArrayList<>();
        queuedNarrations = new ArrayList<>();
        queuedStatuses = new ArrayList<>();
        movingSprites = new ArrayList<>();

        textureHandles = new HashMap<>();
        zoomLevel = 1f;

        firstRender = true;
        transitionIn = true;
        transitionOut = false;
        halfSecPassed = false;

        INSTANCE = this;

        refreshRate = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate();
        Log.v(LOG_TAG, "refresh rate = " + refreshRate);
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLShaderLoader loader = new GLShaderLoader(context);
        solidColourProgram = loader.compileSolidColourShader();
        spriteShaderProgram = loader.compileSpriteShader();
        waveShaderProgram = loader.compileWaveShader();

        imageLoader = new ImageLoader();
        prepareGLSurface();

        renderState = TITLE;
        prepareResources();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        switch (renderState) {
            case NONE:
                addSplashText("Render state = NONE");
                break;

            case TITLE:
                addSplashText("Loading textures...");
                break;

            case MENU:
                addSplashText("Touch to start");
                break;

            case SPLASH:
                renderTransition();
                addSplashText("Generating terrain...");
                break;

            case GAME:
                if (hasResources) {
                    endTime = System.currentTimeMillis();

                    long dt = endTime - startTime;

                    // Lazy fix for first call of onDrawFrame (where startTime == 0)
                    if (dt > 100000) {
                        dt = FRAME_TIME;
                    }

                    startTime = endTime;

                    checkElapsedTime(dt);
                    renderVisibleTiles(dt);
                }

                break;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        screenWidth = width;
        screenHeight = height;

        scaleUi();
        scaleContent();
        setupMatrixes();
        calculateUiGrid();
        calculateContentGrid();
        setUpperWorldBounds();
        setGridChunkToRender();
    }

    /*
    ------------------------------------------------------------------------------------------
    Getters and setters
    ------------------------------------------------------------------------------------------
    */

    public static GameRenderer getInstance() {
        if (INSTANCE == null) {
            Log.w(GameRenderer.class.getSimpleName(), "No instance of GameRenderer to return");
        }

        return INSTANCE;
    }

    public void setFrame(Frame floorData) {
        if (currentFloorData == null) {
            currentFloorData = floorData;
        }
        else {
            updatedFloorData = floorData;
        }
    }

    public void setHasGameData() {
        hasResources = true;
        renderState = GAME;
    }

    public void setCurrentPathSelection(ArrayList<Vector> path) {
        this.currentPathSelection = path;
    }

    public void setMapSize(int[] size) {
        mapGridWidth = size[0];
        mapGridHeight = size[1];
    }

    public void setRenderState(int state) {
        renderState = state;
    }

    public int getRenderState() {
        return renderState;
    }

    /*
    ------------------------------------------------------------------------------------------
    Resource loading and renderer preparation
    ------------------------------------------------------------------------------------------
    */

    private void prepareResources() {
        imageLoader.loadImagesFromDisk(gameInterface.getAssets());
        spriteIndexes = imageLoader.getSpriteIndexes();
        textureHandles = imageLoader.getTextureHandles();

        scaleUi();
        scaleContent();
        setupMatrixes();
        calculateUiGrid();
        calculateContentGrid();
        setUpperWorldBounds();
        setGridChunkToRender();

        uiBuilder = new UserInterfaceBuilder(spriteIndexes, uiGridWidth, uiGridHeight);
        uiMatrix = mvpMatrix.clone();

        prepareTextRenderer();
        prepareSpriteRenderer();
        prepareWaveRenderers();
        prepareUiRenderer();
        prepareUiTextRenderer();
        prepareScreenTransitionRenderer();
    }

    private void prepareGLSurface() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GL10.GL_TEXTURE_2D);

        // Enable alpha blending
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Todo: I guess we don't need depth testing but this doesn't seem to make any difference?
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // We can cull the back faces
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glCullFace(GL10.GL_BACK);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    }

    private SpriteRenderer backgroundLayer;

    private void prepareSpriteRenderer() {
        backgroundLayer = new SpriteRenderer();
        backgroundLayer.initShader(spriteShaderProgram);
        backgroundLayer.setSpriteSheet(textureHandles.get("sprite_sheets/sheet.png"));
        backgroundLayer.setUniformScale(scaleFactor);
        backgroundLayer.precalculatePositions(mapGridWidth, mapGridHeight);
        backgroundLayer.precalculateUv(spriteIndexes.size());

        spriteRenderer = new SpriteRenderer();
        spriteRenderer.initShader(spriteShaderProgram);
        spriteRenderer.setSpriteSheet(textureHandles.get("sprite_sheets/sheet.png"));
        spriteRenderer.setUniformScale(scaleFactor);
        spriteRenderer.precalculatePositions(mapGridWidth, mapGridHeight);
        spriteRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareWaveRenderers() {
        waveRenderer = new WaveEffectSpriteRenderer();
        waveRenderer.setWaveShader(waveShaderProgram);
        waveRenderer.setShaderVariableLocations();
        waveRenderer.setSpriteSheetHandle(textureHandles.get("sprite_sheets/sheet.png"));
        waveRenderer.setUniformScale(scaleFactor);
        waveRenderer.precalculatePositions(mapGridWidth, mapGridHeight);
        waveRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareTextRenderer() {
        // Create our text manager
        textRenderer = new TextRenderer();
        textRenderer.initShader(spriteShaderProgram);
        textRenderer.setTextureHandle(textureHandles.get("fonts/ccra_font.png"));
        textRenderer.setUniformscale(uiScaleFactor);
        textRenderer.precalculateUv();
        textRenderer.precalculateOffsets();
        textRowHeight = textRenderer.precalculateRows(screenHeight);
    }

    private void prepareScreenTransitionRenderer() {
        screenTransitionRenderer = new SolidColourRenderer();
        screenTransitionRenderer.setBasicShader(solidColourProgram);
        screenTransitionRenderer.setUniformScale(scaleFactor);
        int width = visibleGridWidth + 1;
        int height = visibleGridHeight + 1;
        screenTransitionRenderer.addPositions(width, height);
        screenTransitionRenderer.addIndices(width, height);
        screenTransitionRenderer.createVBO();
    }

    private void prepareUiRenderer() {
        uiRenderer = new SpriteRenderer();
        uiRenderer.initShader(spriteShaderProgram);
        uiRenderer.setSpriteSheet(textureHandles.get("sprite_sheets/sheet.png"));
        uiRenderer.setUniformScale(uiScaleFactor);
        uiRenderer.precalculatePositions(uiGridWidth, uiGridHeight);
        uiRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareUiTextRenderer() {
        // Create our text manager
        uiTextRenderer = new TextRenderer();
        uiTextRenderer.initShader(spriteShaderProgram);
        uiTextRenderer.setTextureHandle(textureHandles.get("fonts/ccra_font.png"));
        uiTextRenderer.setUniformscale(uiScaleFactor);
        uiTextRenderer.setTextSize(32f);
        uiTextRenderer.precalculateUv();
        uiTextRenderer.precalculateOffsets();
        uiTextRenderer.precalculateRows(screenHeight);
    }

    /*
    ------------------------------------------------------------------------------------------
    Scaling and scrolling
    ------------------------------------------------------------------------------------------
    */

    private void setupMatrixes() {
        // Setup our screen width and height for normal sprite translation.
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }


        Matrix.orthoM(projMatrix, 0,
                (-screenWidth / 2) * zoomLevel, (screenWidth / 2) * zoomLevel,
                (-screenHeight / 2) * zoomLevel, (screenHeight / 2) * zoomLevel, -1, 1);

        // Set the camera position
        Matrix.setLookAtM(modelViewMatrix, 0,
                1f, 0f, 1f,
                1f, 0f, 0f,
                0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelViewMatrix, 0);

        // Finally, translate so that origin is in bottom-left corner
        Matrix.translateM(mvpMatrix, 0,
                -screenWidth / 2, -screenHeight / 2, 0f);
    }

    private void centreAtPlayerPos() {
        touchScrollDx = (gridSize * position.x) - (screenWidth / 2);
        touchScrollDy = (gridSize * position.y) - (screenHeight / 2);
    }

    private void scaleUi() {
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }

        /*if (targetWidth > screenWidth) {
            targetWidth = screenWidth;
        }*/

        float resX = (float) screenWidth / targetUiWidth;
        float resY = (float) screenHeight / targetUiWidth;

        if (resX > resY) {
            uiScaleFactor = resY;
        }

        else {
            uiScaleFactor = resX;
        }

        uiGridSize = SPRITE_SIZE * uiScaleFactor;
    }

    private void scaleContent() {
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }

        /*if (targetWidth > screenWidth) {
            targetWidth = screenWidth;
        }*/

        float resX = (float) screenWidth / targetWidth;
        float resY = (float) screenHeight / targetWidth;

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

        float spriteSize = SPRITE_SIZE * uiScaleFactor;

        double xInterval = width / spriteSize;
        double yInterval = height / spriteSize;

        uiGridWidth = (int) xInterval;
        uiGridHeight = (int) yInterval;
    }

    private void calculateContentGrid() {
        float width = ScreenSizeGetter.getWidth();
        float height = ScreenSizeGetter.getHeight();

        width *= zoomLevel;
        height *= zoomLevel;

        float spriteSize = SPRITE_SIZE * scaleFactor;

        double xInterval = width / spriteSize;
        double yInterval = height / spriteSize;

        visibleGridWidth = (int) xInterval;
        visibleGridHeight = (int) yInterval;
    }

    /*
    ------------------------------------------------------------------------------------------
      Game rendering
    ------------------------------------------------------------------------------------------
    */

    /**
     * The main rendering method for game content. Decides which portion of the grid needs to be rendered
     * and passes data to the various renderer instances.
     *
     * @param dt Time since last frame in ms
     */

    private void renderVisibleTiles(float dt) {
        if (!isRendering) {

            isRendering = true;

            if (firstRender) {
                setPlayerComponents();
                centreAtPlayerPos();
                fieldOfVision = currentFloorData.getFov();
                visitedTiles = currentFloorData.getVisitedTiles();
                translateScrollMatrix();
                cacheTerrainSprites();

                float left[] = getRenderCoordsForObject(new Vector(1, 0), false);
                float right[] = getRenderCoordsForObject(new Vector(visibleGridWidth - 1, 0), false);

                uiBuilder.setBoundsAndScale(left[0], right[0], scaleFactor);

                firstRender = false;
            }

            if (updatedFloorData != null) {
                currentFloorData = updatedFloorData;
                fieldOfVision = currentFloorData.getFov();
                visitedTiles = currentFloorData.getVisitedTiles();
            }

            setGridChunkToRender();
            buildUiTextObjects();
            initArrays();

            addSpriteDataToRenderer();
            addUiLayer();
            addUiTextLayer();

            // Get scroll matrix inside render loop to make sure each renderer uses the same values.
            // Otherwise you may see drift between layers
            translateScrollMatrix();

            backgroundLayer.renderSprites(scrollMatrix);
            waveRenderer.renderWaveEffect(scrollMatrix, dt, elapsedTimeSeconds);
            spriteRenderer.renderSprites(scrollMatrix);
            textRenderer.renderText(uiMatrix);
            uiRenderer.renderSprites(uiMatrix);
            uiTextRenderer.renderText(uiMatrix);

            if (transitionIn && !transitionOut) {
                fadeTransitionIn(dt);
            }
            else if (transitionOut) {
                fadeTransitionOut(dt);
            }

            addQueuedTextUpdates();

            isRendering = false;
        }
    }

    private void setGridChunkToRender() {
        float[] origin = transformCoordsToWorld(0, 0);

        float spriteSize = SPRITE_SIZE * scaleFactor;

        int originX = (int) (origin[0] / spriteSize);
        int originY = (int) (origin[1] / spriteSize);

        // Use slightly larger chunk of grid than is actually visible, without exceeding bounds of map
        chunkStartX = Math.max(originX - 1, 0);
        chunkStartY = Math.max(originY - 1, 0);
        chunkEndX = Math.min(originX + visibleGridWidth + 2, mapGridWidth);
        chunkEndY = Math.min(originY + visibleGridHeight + 2, mapGridHeight);

        // For some methods we need to retain the total visible chunk
        fullChunkWidth = (originX + visibleGridWidth + 2) - (originX - 1);
        fullChunkHeight = (originY + visibleGridHeight + 2) - (originY - 1);
    }

    /**
     * Renderers use primitive float arrays to store data, so we need to reinitialise these on each
     * frame to make sure we don't run out of room
     */

    private void initArrays() {
        backgroundLayer.resetInternalCount();
        spriteRenderer.resetInternalCount();
        waveRenderer.resetInternalCount();
        textRenderer.initArrays(countTextObjects() * 2);
        uiRenderer.resetInternalCount();
        uiTextRenderer.initArrays(countTextObjects());
    }

    public void fadeOutAndDisplaySplash() {
        this.transitionOut = true;
    }

    /**
     * Creates fade-in effect for content rendered from game engine.
     * Called when we have new game data to display (eg. when entering a new floor)
     *
     * @param dt deltatime
     */

    private void fadeTransitionIn(float dt) {
        if (currentTransitionAlpha >= 0f) {
            float fraction = 1 / dt;
            currentTransitionAlpha -= fraction;
        }
        else {
            transitionIn = false;
            currentTransitionAlpha = 0f;
            // Game data will already be present and rendered, so we don't need to do anything here
        }

        renderTransition();
    }

    /**
     * Fades out current screen contents and sets game state to SPLASH.
     * Called when we want to switch to a new floor.
     *
     * @param dt deltatime
     */

    private void fadeTransitionOut(float dt) {
        if (currentTransitionAlpha < 1f) {
            float fraction = 1 / dt;
            currentTransitionAlpha += fraction;
        }
        else {
            transitionOut = false;
            currentTransitionAlpha = 1f;

            // Display splash screen and wait for signal to render new data.
            // We can also use this opportunity to make sure everything is in its default state
            renderState = SPLASH;
        }

        renderTransition();
    }

    /**
     *  Called when renderer is in SPLASH state and we have new game content to display.
     */

    public void startNewFloor() {
        while (currentTransitionAlpha < 1f) {
            // Wait for transition to finish.
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Thread interrupted while waiting for screen transition to end", e);
            }
        }

        // Switch transition flags so renderer will transition into new content
        transitionIn = true;
        transitionOut = false;

        // Treat this as the first render of a new data set
        firstRender = true;

        // We need to make sure that we update the floor data before renderVisibleTiles() is called
        // otherwise the terrain caching will still be using the data from the old floor.
        if (updatedFloorData != null) {
            currentFloorData = updatedFloorData;
            fieldOfVision = currentFloorData.getFov();
        }

        // Switch renderState so renderVisibleTiles() is called next frame
        renderState = GAME;
    }

    /**
     * As terrain tiles are mostly static, it would save time if we cached the sprite indexes
     * before rendering and reused them each frame.
     *
     * @return 2d array containing terrain sprite index for each cell
     */

    private int[][] cachedBackground;
    private int[][] cachedTerrainLiquids;

    private void cacheTerrainSprites() {
        Sprite[][] mapGrid = currentFloorData.getTerrain();

        cachedTerrain = Array2DHelper.fillIntArray(mapGridWidth, mapGridHeight, -1);
        cachedBackground = Array2DHelper.fillIntArray(mapGridWidth, mapGridHeight, -1);
        cachedTerrainLiquids = Array2DHelper.fillIntArray(mapGridWidth, mapGridHeight, -1);

        for (int y = 0; y < mapGridHeight; y++) {
            for (int x = 0; x < mapGridWidth; x++) {
                Sprite sprite = mapGrid[x][y];

                if (sprite.shader == Sprite.WAVE) {
                    // Water, lava, etc
                    cachedTerrainLiquids[x][y] = spriteIndexes.get(sprite.path);
                }

                else {
                    if (sprite.layer == Sprite.BACKGROUND) {
                        cachedBackground[x][y] = spriteIndexes.get(sprite.path);
                    }

                    else {
                        cachedTerrain[x][y] = spriteIndexes.get(sprite.path);
                    }
                }
            }
        }
    }

    /**
     * Iterates over the various different collections of sprite components and adds to appropriate
     * renderer. Sprites added first will be rendered underneath others occupying the same grid square.
     */

    private void addSpriteDataToRenderer() {
        ArrayList<Sprite> sprites = currentFloorData.getObjects();
        ArrayList<Animation> animations = currentFloorData.getAnimations();

        for (int y = chunkStartY; y < chunkEndY; y++) {
            for (int x = chunkStartX; x < chunkEndX; x++) {
                float lighting = (float) getLightingForGrid(x, y);

                backgroundLayer.addSpriteData(x, y, cachedBackground[x][y], lighting);
                spriteRenderer.addSpriteData(x, y, cachedTerrain[x][y], lighting);
                waveRenderer.addSpriteData(x, y, cachedTerrainLiquids[x][y], lighting);
            }
        }

        int spriteSize = sprites.size();
        for (int i = 0; i < spriteSize; i++) {
            Sprite sprite = sprites.get(i);

            if (sprite.shader == Sprite.NONE) continue;

            int x = sprite.x;
            int y = sprite.y;

            if (notInVisibleBounds(x, y)) continue;

            float lighting = (float) getLightingForGrid(x, y);

            if (sprite.spriteIndex == -1) {
                sprite.spriteIndex = spriteIndexes.get(sprite.path);
            }

            if (sprite.overlayShader != Sprite.NONE && sprite.overlayIndex == -1) {
                sprite.overlayIndex = spriteIndexes.get(sprite.overlayPath);
            }

            if (sprite.effectShader != Sprite.NONE && sprite.effectIndex == -1) {
                sprite.effectIndex = spriteIndexes.get(sprite.effectPath);
            }

            if (sprite.shader == Sprite.STATIC) {

                spriteRenderer.addSpriteData(
                        x, y,
                        sprite.spriteIndex,
                        lighting);

                if (sprite.overlayShader != Sprite.NONE) {

                    spriteRenderer.addSpriteData(
                            x, y,
                            sprite.overlayIndex,
                            lighting);
                }

                if (sprite.effectShader != Sprite.NONE) {

                    waveRenderer.addSpriteData(
                            x, y,
                            sprite.overlayIndex,
                            lighting);
                }
            }

            else {

                if (sprite.lastX != -1 && sprite.lastY != -1) {
                    // Wait until after we've finished iterating before adding to renderer
                    // to make sure they aren't drawn underneath other sprites.

                    // Also note: moving objects will break wave shader, so these objects
                    // should always be rendered using basic shader
                    movingSprites.add(sprite);
                }

                else {
                    if (sprite.shader == Sprite.WAVE) {
                        waveRenderer.addSpriteData(
                                x, y,
                                sprite.spriteIndex,
                                lighting);

                        if (sprite.overlayShader != Sprite.NONE) {

                            waveRenderer.addSpriteData(
                                    x, y,
                                    sprite.overlayIndex,
                                    lighting);
                        }
                        if (sprite.effectShader != Sprite.NONE) {

                            waveRenderer.addSpriteData(
                                    x, y,
                                    sprite.overlayIndex,
                                    lighting);
                        }


                    }
                    else {
                        spriteRenderer.addSpriteData(
                                x, y,
                                spriteIndexes.get(sprite.path),
                                lighting);

                        if (sprite.overlayShader != Sprite.NONE) {

                            spriteRenderer.addSpriteData(
                                    x, y,
                                    spriteIndexes.get(sprite.overlayPath),
                                    lighting);
                        }

                        if (sprite.effectShader != Sprite.NONE) {

                            waveRenderer.addSpriteData(
                                    x, y,
                                    sprite.effectIndex,
                                    lighting);
                        }
                    }
                }
            }
        }

        // Render moving objects after terrain/static objects, but before animation layer
        handleMovingObjects();

        int animationSize = animations.size();
        for (int i = 0; i < animationSize; i++) {
            Animation animation = animations.get(i);
            int x = animation.x;
            int y = animation.y;

            float lighting = (float) getLightingForGrid(x, y);

            int frameIndex = processAnimation(animation);

            if (animation.type == Animation.WAVE) {
                waveRenderer.addSpriteData(
                        x, y,
                        frameIndex,
                        lighting);
            }

            else {
                spriteRenderer.addSpriteData(
                        x, y,
                        frameIndex,
                        lighting);
            }
        }

        handleFinishedAnimations(animations);
    }

    /**
     *  Covers screen with transparent black squares which slowly fade into view.
     *  Used to handle screen transitions/etc
     */

    private void renderTransition() {
        int size = (visibleGridWidth + 1) * (visibleGridHeight + 1);

        screenTransitionRenderer.initColourArray(size);

        float[] colour = new float[] {
                0f, 0f, 0f, currentTransitionAlpha,
                0f, 0f, 0f, currentTransitionAlpha,
                0f, 0f, 0f, currentTransitionAlpha,
                0f, 0f, 0f, currentTransitionAlpha
        };

        for (int i = 0; i < size; i++) {
            screenTransitionRenderer.addSolidTile(colour);
        }

        screenTransitionRenderer.renderSolidColours(mvpMatrix);
    }

    private void addSplashText(String text) {
        if (!isRendering) {
            isRendering = true;

            textRenderer.initArrays(text.length() * 10);

            float x = (float) screenWidth / 2;
            float y = (float) screenHeight / 2;

            x -= textRenderer.getExpectedTextWidth(text) / 2;

            textRenderer.addTextData(x, y, 0f, 1f, text, TextColours.WHITE, 0f);

            textRenderer.renderText(uiMatrix);

            isRendering = false;
        }
    }

    /**
     * Translates position of moving objects from last position to target position.
     */

    private void handleMovingObjects() {
        int size = movingSprites.size();
        for (int i = 0; i < size; i++) {
            Sprite sprite = movingSprites.get(i);

            if (sprite.spriteIndex == -1) continue;

            if (sprite.lastX < 0 || sprite.lastY < 0) continue;
            float fraction = advanceMovement(sprite);
            float offsetX = (sprite.x - sprite.lastX) * fraction * scaleFactor;
            float offsetY = (sprite.y - sprite.lastY) * fraction * scaleFactor;
            float lighting = (float) getLightingForGrid(sprite.x, sprite.y);
            if (fraction == 1) {
                sprite.lastX = -1;
                sprite.lastY = -1;
                spriteRenderer.addSpriteData(sprite.x, sprite.y, sprite.spriteIndex, lighting);
                if (sprite.overlayPath != null)
                    spriteRenderer.addSpriteData(sprite.x, sprite.y, sprite.overlayIndex, lighting);

                if (sprite.effectPath != null)
                    spriteRenderer.addSpriteData(sprite.x, sprite.y, sprite.effectIndex, lighting);

            } else {
                spriteRenderer.addSpriteData(sprite.lastX, sprite.lastY, sprite.spriteIndex, lighting, offsetX, offsetY);

                if (sprite.overlayPath != null)
                    spriteRenderer.addSpriteData(sprite.lastX, sprite.lastY, sprite.overlayIndex, lighting, offsetX, offsetY);

                if (sprite.effectPath != null)
                    spriteRenderer.addSpriteData(sprite.lastX, sprite.lastY, sprite.effectIndex, lighting, offsetX, offsetY);

                if (sprite.id == currentFloorData.getPlayer()[0].id) {
                    // touchScrollDx -= offsetX * (SPRITE_SIZE / 2);
                    // touchScrollDy -= offsetY * (SPRITE_SIZE / 2);

                    // No scrolling, but more accurate?
                    centreAtPlayerPos();
                }
            }
        }

        movingSprites.clear();
    }

    private float advanceMovement(Sprite sprite) {
        if (sprite.movementStep >= 10) {
            sprite.movementStep = 0;
            return 1;
        }

        sprite.movementStep++;

        // Find fraction that we should move by
        float fraction = 1f / 11 * sprite.movementStep;
        // Return squared value to provide simple easing effect on movement
        return (fraction * fraction);
    }

    private int processAnimation(Animation animation) {
        final int TRANSPARENT = 164;

        int x = animation.x;
        int y = animation.y;

        if (!inBounds(x, y)) return TRANSPARENT;

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

    private void handleFinishedAnimations(ArrayList<Animation> animations) {
        Iterator<Animation> it = animations.iterator();
        while (it.hasNext()) {
            Animation animation = it.next();
            if (animation.finished) {
                it.remove();
            }
        }
    }

    public void freeBuffers() {
        spriteRenderer.freeBuffers();
    }

    private void setPlayerComponents() {
        Component[] player = currentFloorData.getPlayer();
        // These array positions should be static and can be double-checked in PlayerFactory class
        position = (Position) player[0];
        vitality = (Vitality) player[7];
        experience = (Experience) player[5];
        inventory = (Container) player[12];
        equipment = (Dexterity) player[13];

        uiBuilder.setPlayerComponents(inventory, equipment);
    }

    private double getLightingForGrid(int x, int y) {
        double fov;

        // If tile is outside of FOV, only highlight if we have visited before.
        // Otherwise tile is blind

        if (fieldOfVision[x][y] == 0) {

            if (visitedTiles[x][y]) {
                fov = 0.1;
            }
            else {
                fov = 0;
            }
        }

        else {
            fov = fieldOfVision[x][y];
        }

        return 1;
    }

    /*
    ------------------------------------------------------------------------------------------
    UI rendering
    ------------------------------------------------------------------------------------------
    */

    private int countUiSprites() {
        int windowSize = visibleGridWidth * visibleGridHeight;
        int inventoryCount = ((Container) currentFloorData.getPlayer()[12]).contents.size();
        int iconCount = 1;
        return windowSize + inventoryCount + iconCount;
    }

    private void buildUiTextObjects() {
        hp = "HP: " + vitality.hp;
        xp = "XP: " + experience.xp;
        floor = "(" + ((int) touchScrollDx) + ", " + ((int) touchScrollDy) + ")";
        fps = fpsCount + " fps";

        if (inventorySelection && inventoryCard != null) {
            uiBuilder.processInventoryCard(inventoryCard, textRenderer);
        }

        else {
            uiBuilder.clearInventoryCard();

        }
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

        return narrationSize + statusSize + uiText + uiBuilder.getDetailTextSize() * 2;
    }

    private void addUiLayer() {
        if (currentPathSelection != null) {
            int index = spriteIndexes.get("sprites/cursor_default.png");
            int pathSize = currentPathSelection.size();
            for (int i = 0; i < pathSize; i++) {
                Vector segment = currentPathSelection.get(i);
                int x = segment.x();
                int y = segment.y();
                spriteRenderer.addSpriteData(x, y, index, 1f, 0f, 0f);
            }
        }

        if (inventoryDisplayed) {
            uiBuilder.addWindow(uiRenderer);

            if (inventorySelection && inventoryCard != null) {
                if (!uiBuilder.itemDetailTransitionComplete) {
                    uiBuilder.animateItemDetailTransition(this, uiRenderer, uiTextRenderer, inventoryCard.sprite);
                }
                else {
                    uiBuilder.showItemDetailView(uiRenderer, inventoryCard);
                    uiBuilder.renderDetailText(this, uiTextRenderer);
                }

            }
            else {
                if (!uiBuilder.inventoryTransitionComplete && inventoryCard != null) {
                    uiBuilder.animateInventoryTransition(this, uiRenderer, uiTextRenderer, inventoryCard.sprite);
                }
                else {
                    uiBuilder.populateInventory(uiRenderer);
                }
            }
        }

        uiBuilder.addUiIcons(uiRenderer);
    }

    private void addUiTextLayer() {
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
                textRenderer.addTextData(status.x, status.y, status.offsetY, status.scale, status.text, status.color, status.alphaModifier);
            }
        }

        textRenderer.addTextRowData(textRowHeight - 1, hp, TextColours.RED, 0f);
        textRenderer.addTextRowData(textRowHeight - 2, xp, TextColours.YELLOW, 0f);

        textRenderer.addTextRowData(textRowHeight - 1, screenWidth / 1.5f, floor, TextColours.WHITE, 0f);
        textRenderer.addTextRowData(textRowHeight - 2, screenWidth / 1.5f, fps, TextColours.WHITE, 0f);
    }

    /**
     *  Adding new text objects while renderVisibleTiles() is executing runs the risk of causing ArrayOutOfBoundsException
     *  because float arrays in renderer won't be big enough to accomodate new objects. So we queue them and add
     *  once the rendering has finished.
     */

    public void queueNarrationUpdate(ArrayList<TextObject> narrations) {
        this.queuedNarrations = narrations;
    }

    public void queueNewStatus(TextObject object) {
        this.queuedStatuses.add(object);
    }

    private void addQueuedTextUpdates() {
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

                if (textRenderer.getExpectedTextWidth(stringBuilder.toString() + word) < screenWidth) {
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
                textRenderer.addTextRowData(row, currentNarration.get(k), narration.color, narration.alphaModifier);
                row++;
            }

            currentNarration.clear();
            stringBuilder.setLength(0);
        }
    }

    /*
    ------------------------------------------------------------------------------------------
    Helper methods
    ------------------------------------------------------------------------------------------
    */

    private boolean notInVisibleBounds(int x, int y) {
        return (x < chunkStartX || x >= chunkEndX || y < chunkStartY && y >= chunkEndY);
    }

    private boolean inBounds(int x, int y) {
        return (x >= 0 && x < mapGridWidth && y >= 0 && y < mapGridHeight);
    }

    private float elapsedTimeSeconds = 0f;
    private float elapsedTimeMs = 0f;

    public void checkElapsedTime(long dt) {
        currentFrameTime += dt;

        elapsedTimeSeconds += dt / 1000f;
        elapsedTimeMs += dt;

        frameCount++;

        // We periodically want to check narrations to see if we need to remove any of them.
        // Narrations are only updated once a second, so it's pointless checking every single frame
        if (currentFrameTime >= 500 && !halfSecPassed) {
            gameInterface.checkNarrations();
            halfSecPassed = true;
        }

        // Update FPS count
        if (currentFrameTime >= 1000) {
            fpsCount = frameCount;
            currentFrameTime = 0;
            frameCount = 0;
            if (!halfSecPassed) {
                gameInterface.checkNarrations();
            }

            halfSecPassed = false;
        }
    }

    /*
    ------------------------------------------------------------------------------------------
    World/screen/touch coordinate transformations
    ------------------------------------------------------------------------------------------
    */

    private void translateScrollMatrix() {
        Matrix.translateM(
                scrollMatrix, 0,
                mvpMatrix, 0,
                -touchScrollDx, -touchScrollDy, 0f);
    }

    public Vector getGridCellForTouchCoords(float x, float y) {
        // NOTE: Origin for touch events is top-left, so y needs to be inverted
        float correctedY = screenHeight - y;

        float[] worldCoords = transformCoordsToWorld(x, correctedY);

        float spriteSize = SPRITE_SIZE * scaleFactor;

        float gridX = worldCoords[0] / spriteSize;
        float gridY = worldCoords[1] / spriteSize;

        return new Vector((int) gridX, (int) gridY);
    }

    private void setUpperWorldBounds() {
        float spriteSize = SPRITE_SIZE * scaleFactor;

        // Get coords of uppermost corner
        upperWorldBounds = transformWorldToCoords(mapGridWidth * spriteSize, mapGridHeight * spriteSize);

        upperWorldBounds[0] -= (screenWidth + (screenWidth / 2));
        upperWorldBounds[1] -= (screenHeight + (screenHeight / 2));
    }

    private float[] transformCoordsToWorld(float x, float y) {
        float[] coords = {0.0f, 0.0f, 0.0f, 0.0f};

        int[] viewPort = new int[] {0, 0, screenWidth, screenHeight};

        // We need to reverse any modifications made to the zoom level using gluUnProject
        GLU.gluUnProject(x, y, -1.0f, modelViewMatrix, 0, projMatrix, 0, viewPort, 0, coords, 0);

        // Original origin is centre of screen, so we need to translate back to bottom-left
        coords[0] += (screenWidth / 2);
        coords[1] += (screenHeight / 2);

        // Account for scrolling
        coords[0] += touchScrollDx;
        coords[1] += touchScrollDy;

        return coords;
    }

    private float[] transformWorldToCoords(float x, float y) {
        float[] coords = {0.0f, 0.0f, 0.0f, 0.0f};

        int[] viewPort = new int[] {0, 0, screenWidth, screenHeight};

        GLU.gluProject(x, y, -1.0f, modelViewMatrix, 0, projMatrix, 0, viewPort, 0, coords, 0);

        return coords;
    }

    public float[] getRenderCoordsForObject(Vector objectPos, boolean withScroll) {
        float x = objectPos.x();
        float y = objectPos.y();
        float spriteSize = SPRITE_SIZE * scaleFactor;

        x *= spriteSize;
        y *= spriteSize;

        x += 30;
        y += spriteSize * 1.1;

        // Subtract scroll offset to find visible surface coords
        if (withScroll) {
            x -= touchScrollDx;
            y -= touchScrollDy;
        }

        return new float[] {x, y};
    }

    /*
    ------------------------------------------------------------------------------------------
    User input

        Occasionally we need to pass user input to the renderer to either directly modify state
        (eg. after scroll or scale gesture) or to get grid position of touch event.
    ------------------------------------------------------------------------------------------
    */

    public void setTouchScrollCoords(float dx, float dy) {
        // Note: Y coord of touch event has opposite origin to grid
        this.touchScrollDx += dx;
        this.touchScrollDy -= dy;

        if (this.touchScrollDx < 0) {
            this.touchScrollDx = 0;
        }
        if (this.touchScrollDy < 0) {
            this.touchScrollDy = 0;
        }

        if (this.touchScrollDx > upperWorldBounds[0]) {
            // this.touchScrollDx = upperWorldBounds[0];
        }

        if (this.touchScrollDy > upperWorldBounds[1]) {
            // this.touchScrollDy = upperWorldBounds[1];
        }
    }

    public boolean checkUiTouch(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        float height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        float spriteSize = SPRITE_SIZE * uiScaleFactor;

        int gridX = (int) (x / spriteSize);
        int gridY = (int) (correctedY / spriteSize);


        if (gridX == uiGridWidth - 1 && gridY == 0) {
            if (!inventoryDisplayed) {
                inventoryDisplayed = true;
                uiBuilder.animateIcon(UserInterfaceBuilder.INVENTORY_OPEN);
            }
            else {
                inventoryDisplayed = false;
                inventorySelection = false;
                uiBuilder.animateIcon(UserInterfaceBuilder.INVENTORY_CLOSED);
            }

            return true;
        }

        if (inventoryDisplayed) {

            if (inventorySelection) {
                // Check whether user clicked UI button & handle event. Otherwise close detail view
                if (gridY == 2) {

                    if (gridX == uiGridWidth - 3) { // OK
                        gameInterface.handleInventorySelection(inventoryCard.sprite.id, true);
                    }
                    else if (gridX == uiGridWidth - 2) { // Cancel
                        gameInterface.handleInventorySelection(inventoryCard.sprite.id, false);
                    }
                }

                // Todo: maybe close inventory screen completely?
                inventorySelection = false;
                uiBuilder.inventoryTransitionComplete = false;
                return true;
            }

            // Check if user touched inventory item.
            else if (gridX > 0 && gridX < uiGridWidth - 1 && gridY > 1 && gridY < uiGridHeight - 1) {
                int inventoryWidth = uiGridWidth - 2;
                int inventoryHeight = uiGridHeight - 2;

                // This messy code gives us a coord with 0,0 origin at top-left (matching order
                // that items are displayed) so we can figure out which item was selected
                gridX -= 1;
                gridY -= inventoryHeight;
                gridY = -gridY;

                int index = (gridY * inventoryWidth) + gridX;
                long entity = gameInterface.processInventoryClick(index);
                if (entity > -1) {
                    uiBuilder.itemDetailTransitionComplete = false;
                    inventorySelection = true;
                    inventoryCard = gameInterface.getEntityDetails(entity);
                }
            }

            // Always return true while inventory is displayed - game content is not in focus.
            return true;
        }

        return false;
    }

    public void startZoom(ScaleGestureDetector detector) {}

    public void setZoom(float zoomLevel) {
        this.zoomLevel = zoomLevel;

        setupMatrixes();
        calculateContentGrid();
        setUpperWorldBounds();
        setGridChunkToRender();
    }

    public void endZoom() {}
}
