package com.sonicmax.bloodrogue.renderer;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.engine.FloorData;
import com.sonicmax.bloodrogue.engine.objects.Actor;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;
import com.sonicmax.bloodrogue.renderer.sprites.TerrainRenderer;
import com.sonicmax.bloodrogue.renderer.sprites.WaveEffectSpriteRenderer;
import com.sonicmax.bloodrogue.renderer.ui.UserInterfaceBuilder;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Animation;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteLoader;
import com.sonicmax.bloodrogue.renderer.text.Status;
import com.sonicmax.bloodrogue.renderer.text.TextObject;
import com.sonicmax.bloodrogue.renderer.text.TextRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final int SPRITE_SIZE = 64;
    private final int NONE = 0;
    private final int SPLASH = 1;
    private final int GAME = 2;
    private final float DEFAULT_OFFSET_X = 0f;
    private final float DEFAULT_OFFSET_Y = 0f;
    private final long FRAME_TIME = 16L;

    private Context context;
    private GameInterface gameInterface;

    // Game state
    private FloorData currentFloorData;
    private FloorData updatedFloorData;
    private ArrayList<Vector> currentPathSelection;
    private int scrollOffsetX;
    private int scrollOffsetY;
    private double[][] fieldOfVision;
    private ArrayList<GameObject> movingObjects;

    // Renderers
    private TerrainRenderer terrainRenderer;
    private SpriteRenderer spriteRenderer;
    private SpriteRenderer uiRenderer;
    private WaveEffectSpriteRenderer waveRenderer;
    private TextRenderer textRenderer;
    private TextRenderer uiTextRenderer;
    private SolidColourRenderer screenTransitionRenderer;

    // Resources
    private SpriteLoader spriteLoader;
    private HashMap<String, Integer> spriteHandles; // Texture handles for loaded textures
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
    private final float[] viewMatrix;
    private float[] uiMatrix;
    private float[] scrollMatrix;

    // UI
    private UserInterfaceBuilder uiBuilder;
    private boolean inventoryDisplayed = false;
    private String hp;
    private String xp;
    private String fps;
    private String floor;

    /**
     *  Screen size and scaling
     */

    private int screenWidth;
    private int screenHeight;
    private int mapGridWidth;
    private int mapGridHeight;
    private int visibleGridWidth;
    private int visibleGridHeight;
    private float targetWidth = 448f;

    private float zoomLevel;
    private float scaleFactor;

    // Visible grid chunk
    private int chunkOriginX;
    private int chunkOriginY;
    private int chunkWidth;
    private int chunkHeight;

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
        super();
        this.context = context;
        this.gameInterface = gameInterface;

        currentFloorData = null;
        updatedFloorData = null;
        renderState = NONE;
        hasResources = false;
        isRendering = false;

        projMatrix = new float[16];
        viewMatrix = new float[16];
        mvpMatrix = new float[16];
        uiMatrix = new float[16];
        scrollMatrix = new float[16];

        narrations = new ArrayList<>();
        statuses = new ArrayList<>();
        queuedNarrations = new ArrayList<>();
        queuedStatuses = new ArrayList<>();
        movingObjects = new ArrayList<>();

        spriteHandles = new HashMap<>();
        zoomLevel = 1f;

        firstRender = true;
        transitionIn = true;
        transitionOut = false;
        halfSecPassed = false;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLShaderLoader loader = new GLShaderLoader(context);
        solidColourProgram = loader.compileSolidColourShader();
        spriteShaderProgram = loader.compileSpriteShader();
        waveShaderProgram = loader.compileWaveShader();

        // For now, we're using the same shader program for all rendering

        spriteLoader = new SpriteLoader();
        prepareGLSurface();

        renderState = SPLASH;

        loadImages();

        scaleScreen();
        setupMatrixes();
        calculateGridSize();

        setupUiBuilder();
        setupUiMatrixes();

        prepareTextRenderer();
        prepareTerrainRenderer();
        prepareSpriteRenderer();
        prepareWaveRenderer();
        prepareUiRenderer();
        prepareUiTextRenderer();
        prepareScreenTransitionRenderer();

        renderState = GAME;

        // Post new runnable to GLSurfaceView which allows us to load textures/etc in background
        /*mGLSurfaceView.queueEvent(new Runnable() {

            @Override
            public void run() {

            }
        });*/
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (renderState == NONE) {
            renderTransition();
        }

        else if (renderState == SPLASH) {
            renderTransition();
        }

        else if (hasResources && renderState == GAME) {
            endTime = System.currentTimeMillis();

            long dt = endTime - startTime;

            if (dt > 10000) {
                dt = FRAME_TIME;
            }

            /*if (dt < FRAME_TIME) {
                try {
                    Thread.sleep(FRAME_TIME - dt);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Error in onDrawFrame", e);
                }
            }*/

            startTime = endTime;

            checkElapsedTime(dt);
            renderVisibleTiles(dt);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        screenWidth = width;
        screenHeight = height;

        scaleScreen();
        setupMatrixes();
        calculateGridSize();
    }

    /*
    ---------------------------------------------
      Resource loading and preparation
    ---------------------------------------------
    */

    private void prepareGLSurface() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GL10.GL_TEXTURE_2D);

        // Enable alpha blending
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // We can cull the back faces as we wouldn't be able to see them in the first palce
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glCullFace(GL10.GL_BACK);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    }

    private void loadImages() {
        spriteIndexes = new HashMap<>();
        // Iterate over all paths in /assets/img and create SpriteLoader object with handle to loaded texture
        long startTime = System.nanoTime();

        final String IMG_PATH = "sprites/";
        final String SHEET_PATH = "sprite_sheets/";
        final String FONT_PATH = "fonts/";
        AssetManager assetManager = gameInterface.getAssets();

        try {
            String[] images = assetManager.list("sprites");
            int index = 0;

            // As AssetManager.list() returns alphabetically sorted list, and sprite sheet is also
            // ordered alphabetically, we can just increment the index on each filename to get the position
            // on sprite sheet. These will be used later when passing data to SpriteRenderer

            for (String image : images) {
                spriteIndexes.put(IMG_PATH + image, index);
                index++;
            }

            // Now we can load our sprite sheet and fonts

            String[] sheets = assetManager.list("sprite_sheets");

            for (String sheet : sheets) {
                InputStream is = assetManager.open(SHEET_PATH + sheet);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_4444;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = spriteLoader.loadTexture(bitmap);
                spriteHandles.put(SHEET_PATH + sheet, textureHandle);
            }

            String[] fontPaths = assetManager.list("fonts");

            for (String path : fontPaths) {
                InputStream is = assetManager.open(FONT_PATH + path);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_4444;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = spriteLoader.loadTexture(bitmap);
                spriteHandles.put(FONT_PATH + path, textureHandle);
            }

            long stopTime = System.nanoTime();
            Log.v(LOG_TAG, "Loaded images in " + TimeUnit.MILLISECONDS.convert(stopTime - startTime, TimeUnit.NANOSECONDS) + " ms");

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void prepareTerrainRenderer() {
        terrainRenderer = new TerrainRenderer();
        terrainRenderer.setBasicShader(spriteShaderProgram);
        terrainRenderer.setSpriteSheetHandle(spriteHandles.get("sprite_sheets/sheet.png"));
        terrainRenderer.setUniformScale(scaleFactor);
        terrainRenderer.setMapSize(mapGridWidth, mapGridHeight);
        terrainRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareSpriteRenderer() {
        spriteRenderer = new SpriteRenderer();
        spriteRenderer.setBasicShader(spriteShaderProgram);
        spriteRenderer.setSpriteSheetHandle(spriteHandles.get("sprite_sheets/sheet.png"));
        spriteRenderer.setUniformScale(scaleFactor);
        spriteRenderer.precalculatePositions(mapGridWidth, mapGridHeight);
        spriteRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareWaveRenderer() {
        waveRenderer = new WaveEffectSpriteRenderer();
        waveRenderer.setWaveShader(waveShaderProgram);
        waveRenderer.setShaderVariableLocations();
        waveRenderer.setSpriteSheetHandle(spriteHandles.get("sprite_sheets/sheet.png"));
        waveRenderer.setUniformScale(scaleFactor);
        waveRenderer.precalculatePositions(mapGridWidth, mapGridHeight);
        waveRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareTextRenderer() {
        // Create our text manager
        textRenderer = new TextRenderer();
        textRenderer.setShaderProgramHandle(spriteShaderProgram);
        textRenderer.setTextureHandle(spriteHandles.get("fonts/ccra_font.png"));
        textRenderer.setUniformscale(scaleFactor);
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
        uiRenderer.setBasicShader(spriteShaderProgram);
        uiRenderer.setSpriteSheetHandle(spriteHandles.get("sprite_sheets/sheet.png"));
        uiRenderer.setUniformScale(scaleFactor);
        uiRenderer.precalculatePositions(visibleGridWidth, visibleGridHeight);
        uiRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareUiTextRenderer() {
        // Create our text manager
        uiTextRenderer = new TextRenderer();
        uiTextRenderer.setShaderProgramHandle(spriteShaderProgram);
        uiTextRenderer.setTextureHandle(spriteHandles.get("fonts/ccra_font.png"));
        uiTextRenderer.setUniformscale(scaleFactor);
        uiTextRenderer.setTextSize(32f);
        uiTextRenderer.precalculateUv();
        uiTextRenderer.precalculateOffsets();
        uiTextRenderer.precalculateRows(screenHeight);
    }

    private void setupUiBuilder() {
        uiBuilder = new UserInterfaceBuilder(spriteIndexes, visibleGridWidth, visibleGridHeight);
    }

    /*
    ---------------------------------------------
      Scaling and scrolling
    ---------------------------------------------
    */

    private void setupMatrixes() {
        // Setup our screen width and height for normal sprite translation.
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }

        Matrix.orthoM(projMatrix, 0, 0f, screenWidth * zoomLevel, 0.0f, screenHeight * zoomLevel, 0, 50);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0,
                0f, 0f, 1f,
                0f, 0f, 0f,
                0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0);
    }

    private void setupUiMatrixes() {
        uiMatrix = mvpMatrix.clone();

        /*float[] coords = getRenderCoordsForObject(new Vector(visibleGridWidth, 0));
        float differenceX = (float) screenWidth - coords[0];
        differenceX *= 1.45f;

        Log.v(LOG_TAG, "" + coords[0]);
        Log.v(LOG_TAG, "" + differenceX);

        Matrix.translateM(uiMatrix, 0, differenceX, 0f, 0f);*/
    }

    private void centreAtPlayerPos() {
        GameObject player = currentFloorData.getPlayer();

        float gridSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        touchScrollDx = 0f - (gridSize * player.x) + (screenWidth / 2);
        touchScrollDy = 0f - (gridSize * player.y) + (screenHeight / 2);
    }

    private void scaleScreen() {
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }

        float resX = (float) screenWidth / targetWidth;
        float resY = (float) screenHeight / targetWidth;

        if (resX > resY) {
            scaleFactor = resY;
        }

        else {
            scaleFactor = resX;
        }
    }

    private void calculateGridSize() {
        float width = ScreenSizeGetter.getWidth() * zoomLevel;
        float height = ScreenSizeGetter.getHeight() * zoomLevel;

        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        double xInterval = width / spriteSize;
        double yInterval = height / spriteSize;

        visibleGridWidth = (int) xInterval;
        visibleGridHeight = (int) yInterval;

        if (visibleGridWidth > 14) {
            xInterval = width / (spriteSize * 2);
            yInterval = height / (spriteSize * 2);

            visibleGridWidth = (int) xInterval;
            visibleGridHeight = (int) yInterval;
        }
    }

    private void calculateScrollOffset(GameObject player) {
        scrollOffsetX = player.x - (visibleGridWidth / 2);
        scrollOffsetY = player.y - (visibleGridHeight / 2);
    }

    public void setTouchScrollCoords(float dx, float dy) {
        this.touchScrollDx -= dx;
        this.touchScrollDy += dy;
    }

    /*
    ---------------------------------------------
      Rendering
    ---------------------------------------------
    */

    /**
     * Main rendering loop. Iterates over any content we have to display and renders to GL surface.
     *
     * @param dt deltatime
     */

    private void renderVisibleTiles(float dt) {
        if (!isRendering) {

            isRendering = true;

            if (firstRender) {
                centreAtPlayerPos();
                fieldOfVision = currentFloorData.getFov();
                calculateScrollOffset(currentFloorData.getPlayer());
                scrollMatrix = getScrollMatrix();
                cacheTerrainLayer();
                firstRender = false;
            }

            if (updatedFloorData != null) {
                currentFloorData = updatedFloorData;
                fieldOfVision = currentFloorData.getFov();
                calculateScrollOffset(currentFloorData.getPlayer());
            }

            setGridChunkToRender();
            buildUiTextObjects();
            initArrays();

            addSprites();
            addUiLayer();
            addTextLayer();

            // Get scroll matrix inside render loop to make sure each renderer uses the same values.
            // Otherwise you may see drift between layers
            scrollMatrix = getScrollMatrix();

            terrainRenderer.renderSprites(scrollMatrix);
            spriteRenderer.renderSprites(scrollMatrix);
            waveRenderer.renderWaveEffect(scrollMatrix, dt);
            textRenderer.renderText(mvpMatrix);
            uiRenderer.renderSprites(uiMatrix);

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

    /**
     *  Creates VBO containing cached terrain data and holds it in GPU memory.
     *  We need to do this every time terrain layer changes (eg. after changing floor).
     *  Generally doing this on first render will be adequate
     */

    private void cacheTerrainLayer() {
        int size = mapGridWidth * mapGridHeight;
        terrainRenderer.initArrays(size);
        terrainRenderer.prepareIndices(mapGridWidth, mapGridHeight);
        cachedTerrain = cacheTerrainSprites();
        terrainRenderer.createVBO();
    }

    private void initArrays() {
        // Get total sprite count and pass to renderer so we can init arrays used to store rendering data
        terrainRenderer.initLightingArray();
        spriteRenderer.initArrays(countObjectSprites());
        waveRenderer.initArrays(countObjectSprites());
        textRenderer.initArrays(countTextObjects());
        uiRenderer.initArrays(countUiSprites());
        uiTextRenderer.initArrays("Testing".length());
    }

    private float[] getScrollMatrix() {
        float[] renderMatrix = new float[16];

        if (touchScrollDx != 0 || touchScrollDy != 0) {
            Matrix.translateM(renderMatrix, 0, mvpMatrix, 0, touchScrollDx, touchScrollDy, 0f);
        }

        return renderMatrix;
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
        // Todo: this is... not great...
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

        // Switch renderState so renderVisibleTiles() is called next frame
        renderState = GAME;
    }

    /**
     *  Counts total number of object sprites displayed in current chunk & returns total as int.
     *  (this excludes terrain)
     */

    private int countObjectSprites() {
        objectCount = 0;
        int animationCount = 0;
        int uiCount = 0;

        ArrayList<GameObject>[][] objects = currentFloorData.getObjects();
        ArrayList<GameObject>[][] animations = currentFloorData.getAnimations();

        for (int x = chunkOriginX; x < chunkWidth; x++) {
            for (int y = chunkOriginY; y < chunkHeight; y++) {
                if (!inBounds(x, y)) continue;

                // Note: to get true object count, we would have to iterate over objects
                // and separate solid objects from gas/liquid (which use a different renderer).
                // However, we can safely use the total count without breaking anything.

                objectCount += objects[x][y].size();
                animationCount += animations[x][y].size();
            }
        }

        if (currentPathSelection != null) {
            uiCount += currentPathSelection.size();
        }

        return objectCount * 2 + animationCount + uiCount;
    }

    /**
     *  Counts number of spriteHandles used to render UI and returns total as int.
     */

    private int countUiSprites() {
        int windowSize = visibleGridWidth * visibleGridHeight;
        int iconCount = 1;
        return windowSize + iconCount;
    }

    /**
     * Counts number of characters in each TextObject and returns combined total.
     * We count individual latters as they are rendered 1 sprite per letter
     */

    private void buildUiTextObjects() {
        Actor player = (Actor) currentFloorData.getPlayer();
        hp = "HP: " + player.getHpString();
        xp = "XP: " + player.getXpString();
        floor = "Floor " + currentFloorData.getIndex();
        fps = fpsCount + " fps";
    }

    private int countTextObjects() {
        int narrationSize = 0;
        int statusSize = 0;
        int uiText = 0;

        uiText += hp.length() + xp.length() + fps.length();

        for (TextObject object : narrations) {
            narrationSize += object.text.length();
        }

        for (TextObject object : statuses) {
            statusSize += object.text.length();
        }

        return narrationSize + statusSize + uiText;
    }

    private int[][] cacheTerrainSprites() {
        GameObject[][] mapGrid = currentFloorData.getTerrain();

        int[][] cached = new int[mapGridWidth][mapGridHeight];

        for (int y = 0; y < mapGridHeight; y++) {
            for (int x = 0; x < mapGridWidth; x++) {
                terrainRenderer.addSpriteData(
                        x, y,
                        spriteIndexes.get(mapGrid[x][y].sprite));
            }
        }

        return cached;
    }

    /**
     *  Iterates over visible objects in current frame and passes data to SpriteRenderer.
     */

    private void addSprites() {
        ArrayList<GameObject>[][] objectGrid = currentFloorData.getObjects();
        ArrayList<GameObject>[][] animations = currentFloorData.getAnimations();

        for (int y = chunkOriginY; y < chunkHeight; y++) {
            for (int x = chunkOriginX; x < chunkWidth; x++) {
                if (!inBounds(x, y)) continue;

                float lighting = (float) getLightingForGrid(x, y);

                terrainRenderer.addLightingUpdate(x, y, lighting);

                ArrayList<GameObject> objectsInCell = objectGrid[x][y];

                for (int i = 0; i < objectsInCell.size(); i++) {
                    if (objectsInCell.size() == 0) {
                        Log.e(LOG_TAG, "object stack length changed during iteration");
                        break;
                    }

                    GameObject object = objectsInCell.get(i);

                    if (object.spriteIndex == -1) {
                        object.spriteIndex = spriteIndexes.get(object.sprite);
                    }

                    if (object.isProjected) {

                        if (fieldOfVision[object.fovX][object.fovY] > 0.1) {

                            spriteRenderer.addSpriteData(
                                    x, y,
                                    object.spriteIndex,
                                    lighting,
                                    DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                        }
                    }

                    else if (object.isImmutable && object.isStationary) {

                        spriteRenderer.addSpriteData(
                                x, y,
                                object.spriteIndex,
                                lighting,
                                DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                    }

                    else {
                        if (object.lastMove != null) {
                            // Wait until after we've finished iterating before adding to renderer
                            // to make sure they aren't drawn underneath other spriteHandles.

                            // Also note: moving objects will break wave shader, so these objects
                            // should always be rendered using basic shader
                            
                            movingObjects.add(object);
                        }
                        else {
                            if (object.isGasOrLiquid) {
                                waveRenderer.addSpriteData(
                                        x, y,
                                        object.spriteIndex,
                                        lighting,
                                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

                            }
                            else {
                                if (object.hasAnimation()) {
                                    spriteRenderer.addSpriteData(
                                            x, y,
                                            spriteIndexes.get(object.getSprite(1f)),
                                            lighting,
                                            DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                                }
                                else {
                                    // Mutable objects may change sprites, so we need to check each render
                                spriteRenderer.addSpriteData(
                                        x, y,
                                            spriteIndexes.get(object.sprite),
                                        lighting,
                                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                            }
                        }
                    }
                }
                }

                int animationsSize = animations[x][y].size();
                for (int i = 0; i < animationsSize; i++) {
                    Animation animation = (Animation) animations[x][y].get(i);

                    int frameIndex = processAnimation(animation);

                    if (animation.isGasOrLiquid) {
                        waveRenderer.addSpriteData(
                                x, y,
                                frameIndex,
                                lighting,
                                DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                    }

                    else {
                        spriteRenderer.addSpriteData(
                                x, y,
                                frameIndex,
                                lighting,
                                DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                    }
                }

                handleFinishedAnimations(animations[x][y]);
            }
        }

        handleMovingObjects();
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

    private void handleMovingObjects() {
        for (GameObject object : movingObjects) {
            if (object.lastMove == null) continue;
            int x = object.x;
            int y = object.y;
            float fraction = object.advanceMovement();
            float offsetX = (x - object.lastMove.x) * fraction;
            float offsetY = (y - object.lastMove.y) * fraction;
            float lighting = (float) getLightingForGrid(x, y);
            if (fraction == 1) {
                object.setLastMove(null);
                spriteRenderer.addSpriteData(x, y, object.spriteIndex, lighting, 0f, 0f);
            } else {
                spriteRenderer.addSpriteData(object.lastMove.x, object.lastMove.y, object.spriteIndex, lighting, offsetX, offsetY);

                if (object.isPlayerControlled) {
                    /*touchScrollDx -= offsetX * (SPRITE_SIZE / 2);
                    touchScrollDy -= offsetY * (SPRITE_SIZE / 2);*/

                    // No scrolling, but more accurate?
                    centreAtPlayerPos();
                }
            }
        }

        movingObjects.clear();
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
        }

        uiBuilder.buildUi(uiRenderer);
    }

    private int processAnimation(Animation animation) {
        final int TRANSPARENT = 164;

        int x = animation.x();
        int y = animation.y();
        int screenPosX = x - scrollOffsetX;
        int screenPosY = y - scrollOffsetY;

        if (!inBounds(x, y)) return TRANSPARENT;
        if (!inVisibleBounds(screenPosX, screenPosY)) return TRANSPARENT;

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

    private void handleFinishedAnimations(ArrayList<GameObject> animations) {
        Iterator<GameObject> it = animations.iterator();
        while (it.hasNext()) {
            Animation animation = (Animation) it.next();
            if (animation.finished) {
                it.remove();
            }
        }
    }

    private void addTextLayer() {
        int narrationSize = narrations.size();
        for (int i = 0; i < narrationSize; i++) {
            TextObject narration = narrations.get(i);
            textRenderer.addTextData(narration.row, narration.text, narration.color, narration.alphaModifier);
        }

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

        textRenderer.addTextData(textRowHeight - 1, hp, TextColours.RED, 0f);
        textRenderer.addTextData(textRowHeight - 2, xp, TextColours.YELLOW, 0f);

        textRenderer.addTextData(textRowHeight - 1, screenWidth / 1.5f, floor, TextColours.WHITE, 0f);
        textRenderer.addTextData(textRowHeight - 2, screenWidth / 1.5f, fps, TextColours.WHITE, 0f);
    }

    private double getLightingForGrid(int x, int y) {
        double fov;

        if (fieldOfVision[x][y] == 0) {
            fov = 0.1;
        }
        else {
            fov = fieldOfVision[x][y];
        }

        return fov;
    }

    /*
    ---------------------------------------------
      Helper methods
    ---------------------------------------------
    */

    private boolean inVisibleBounds(int x, int y) {
        return (x >= 0 && x <= visibleGridWidth) && (y >= 0 && y <= visibleGridHeight);
    }

    private boolean inBounds(int x, int y) {
        return (x >= 0 && x < mapGridWidth && y >= 0 && y < mapGridHeight);
    }

    public void checkElapsedTime(long dt) {
        currentFrameTime += dt;
        frameCount++;

        // We periodically want to check narrations to see if we need to remove any of them.
        // Narrations are only updated once a second, so it's pointless checking every single frame
        if (currentFrameTime >= 500) {
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
    ---------------------------------------------
      Getters and setters
    ---------------------------------------------
    */

    public Vector getGridCellForTouchCoords(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        x *= zoomLevel;
        y *= zoomLevel;
        float height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        // Account for touch scrolling
        x -= touchScrollDx;
        correctedY -= touchScrollDy;

        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        float gridX = x / spriteSize;
        float gridY = correctedY / spriteSize;

        return new Vector((int) gridX, (int) gridY);
    }

    private void setGridChunkToRender() {
        float x = 0;
        float y = 0;

        // Account for touch scrolling
        x -= touchScrollDx;
        y -= touchScrollDy;

        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        int originX = (int) (x / spriteSize);
        int originY = (int) (y / spriteSize);

        // Render slightly larger chunk of grid than is actually visible, without exceeding bounds of map
        chunkOriginX = Math.max(originX - 1, 0);
        chunkOriginY = Math.max(originY - 1, 0);
        chunkWidth = Math.min(originX + visibleGridWidth + 1, mapGridWidth);
        chunkHeight = Math.min(originY + visibleGridHeight + 1, mapGridHeight);
    }

    public float[] getRenderCoordsForObject(Vector objectPos) {
        float x = objectPos.x();
        float y = objectPos.y();
        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        x *= spriteSize;
        y *= spriteSize;

        x += 30;
        y += spriteSize * 1.1;

        // Subtract scroll offset to find visible surface coords
        x += touchScrollDx;
        y += touchScrollDy;

        return new float[] {x, y};
    }

    public void setFloorData(FloorData floorData) {
        if (currentFloorData == null) {
            currentFloorData = floorData;
        }
        else {
            updatedFloorData = floorData;
        }
    }

    public void setZoom(float mZoom) {
        /*this.zoomLevel = zoomLevel;
        setupMatrixes();*/
    }

    public void setHasGameData() {
        hasResources = true;
    }

    public void setCurrentPathSelection(ArrayList<Vector> path) {
        this.currentPathSelection = path;
    }

    public void setMapSize(int[] size) {
        mapGridWidth = size[0];
        mapGridHeight = size[1];
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
        this.narrations = queuedNarrations; // Narrations need to be replaced
        this.statuses.addAll(queuedStatuses); // Statuses should be added
    }

    public boolean checkUiTouch(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        x *= zoomLevel;
        y *= zoomLevel;
        float height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        int gridX = (int) (x / spriteSize);
        int gridY = (int) (correctedY / spriteSize);

        if (gridX == visibleGridWidth - 1 && gridY == 0) {
            if (!inventoryDisplayed) {
                inventoryDisplayed = true;
                uiBuilder.animateIcon(UserInterfaceBuilder.INVENTORY_OPEN);
            }
            else {
                inventoryDisplayed = false;
                uiBuilder.animateIcon(UserInterfaceBuilder.INVENTORY_CLOSED);
            }
            return true;
        }

        return false;
    }
}
