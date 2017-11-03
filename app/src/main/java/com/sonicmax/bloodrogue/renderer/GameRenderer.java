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
import com.sonicmax.bloodrogue.renderer.ui.UserInterfaceBuilder;
import com.sonicmax.bloodrogue.renderer.text.TextColours;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Animation;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteLoader;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteSheetRenderer;
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
    private Vector scrollOffset;
    private double[][] lightMap = null;
    private double[][] fieldOfVision = null;

    // Renderers
    private SpriteSheetRenderer spriteRenderer;
    private SpriteSheetRenderer uiRenderer;
    private SpriteSheetRenderer waveRenderer;
    private TextRenderer textRenderer;
    private SpriteSheetRenderer screenTransitionRenderer;

    // Resources
    private SpriteLoader spriteLoader;
    private HashMap<String, Integer> spriteHandles; // Texture handles for loaded textures
    private HashMap<String, Integer> spriteIndexes; // Position on sprite sheet for particular texture
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

    // UI
    private UserInterfaceBuilder uiBuilder;
    private boolean inventoryDisplayed = false;

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
    private float scrollDx = 0f;
    private float scrollDy = 0f;

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

        narrations = new ArrayList<>();
        statuses = new ArrayList<>();
        queuedNarrations = new ArrayList<>();
        queuedStatuses = new ArrayList<>();

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
            screenTransitionRenderer.initArrays(visibleGridWidth * visibleGridHeight);
            fillScreen();
            screenTransitionRenderer.renderSolidColours(mvpMatrix);
        }

        else if (renderState == SPLASH) {
            // Show loading screen
            screenTransitionRenderer.initArrays(visibleGridWidth * visibleGridHeight);
            fillScreen();
            screenTransitionRenderer.renderSolidColours(mvpMatrix);
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
            renderScreen(dt);
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

        // GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        // GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        // GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
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
            // on sprite sheet. These will be used later when passing data to SpriteSheetRenderer

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

    private void prepareSpriteRenderer() {
        spriteRenderer = new SpriteSheetRenderer();
        spriteRenderer.setBasicShader(spriteShaderProgram);
        spriteRenderer.setSpriteSheetHandle(spriteHandles.get("sprite_sheets/sheet.png"));
        spriteRenderer.setUniformScale(scaleFactor);
        spriteRenderer.precalculatePositions(mapGridWidth, mapGridHeight);
        spriteRenderer.precalculateUv(spriteIndexes.size());
    }

    private void prepareWaveRenderer() {
        waveRenderer = new SpriteSheetRenderer();
        waveRenderer.setBasicShader(spriteShaderProgram);
        waveRenderer.setWaveShader(waveShaderProgram);
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
        screenTransitionRenderer = new SpriteSheetRenderer();
        screenTransitionRenderer.setBasicShader(solidColourProgram);
        screenTransitionRenderer.setUniformScale(scaleFactor);
        screenTransitionRenderer.precalculatePositions(visibleGridWidth, visibleGridHeight);
    }

    private void prepareUiRenderer() {
        uiRenderer = new SpriteSheetRenderer();
        uiRenderer.setBasicShader(spriteShaderProgram);
        uiRenderer.setSpriteSheetHandle(spriteHandles.get("sprite_sheets/sheet.png"));
        uiRenderer.setUniformScale(scaleFactor);
        uiRenderer.precalculatePositions(visibleGridWidth, visibleGridHeight);
        uiRenderer.precalculateUv(spriteIndexes.size());
    }

    private TextRenderer uiTextRenderer;

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
        Vector pos;
        if (updatedFloorData != null) {
            pos = updatedFloorData.getPlayer().getVector();
        }
        else {
            pos = currentFloorData.getPlayer().getVector();
        }
        float gridSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        // TODO: align better to centre by checking if odd/even number of grid squares in visible width

        scrollDx = 0f - (gridSize * pos.x()) + (screenWidth / 2);
        scrollDy = 0f - (gridSize * pos.y()) + (screenHeight / 2);

        // We have to make sure MainActivity has same coords as renderer
        // gameInterface.updateScrollPos(scrollDx, scrollDy);
    }

    private void scaleScreen() {
        // Desired resolution is 320x480 (or 480x320 in landscape)
        // Setup our screen width and height for normal sprite translation.
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }

        float resX = (float) screenWidth / targetWidth;
        float resY = (float) screenHeight / targetWidth;

        /*if (screenWidth > screenHeight) {
            resX = (float) screenWidth / mResTargetHeight;
            resY = (float) screenHeight / mResTargetWidth;
        }
        else {
            resX = (float) screenWidth / mResTargetHeight;
            resY = (float) screenHeight / mResTargetWidth;
        }*/

        Log.i(LOG_TAG, "" + resX + ", " + resY);

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

    private Vector calculateScrollOffset() {
        GameObject player = currentFloorData.getPlayer();
        return new Vector(player.x() - (visibleGridWidth / 2), player.y() - (visibleGridHeight / 2));
    }

    public void setTouchScrollCoords(float dx, float dy) {
        this.scrollDx -= dx;
        this.scrollDy += dy;
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

    private void renderScreen(float dt) {
        if (!isRendering) {

            isRendering = true;

            if (firstRender) {
                centreAtPlayerPos();
                lightMap = currentFloorData.getLightMap();
                fieldOfVision = currentFloorData.getFov();
                firstRender = false;
            }

            if (updatedFloorData != null) {
                // Replace existing frame with new frame
                currentFloorData = updatedFloorData;
                lightMap = currentFloorData.getLightMap();
                fieldOfVision = currentFloorData.getFov();
            }

            scrollOffset = calculateScrollOffset();
            setGridChunkToRender();

            buildUiTextObjects();

            fps = fpsCount + " fps";

            // Get total sprite count and pass to renderer so we can init arrays used to store rendering data
            int spriteCount = countSprites();

            spriteRenderer.initArrays(spriteCount);
            waveRenderer.initArrays(objectCount); // Todo: we should explicitly count objects that need this renderer
            textRenderer.initArrays(countTextObjects());
            uiRenderer.initArrays(countUiSprites());
            uiTextRenderer.initArrays("Testing".length());

            // Iterate over game data and send to renderer
            addSprites();
            addUiLayer();
            addTextLayer();

            // Check whether we need to translate matrix to account for touch scrolling
            float[] renderMatrix = new float[16];

            if (scrollDx != 0 || scrollDy != 0) {
                Matrix.translateM(renderMatrix, 0, mvpMatrix, 0, scrollDx, scrollDy, 0f);
            }

            spriteRenderer.renderSprites(renderMatrix);
            waveRenderer.renderWaveEffect(renderMatrix, dt);
            textRenderer.renderText(mvpMatrix);
            uiRenderer.renderSprites(uiMatrix);

            addQueuedTextUpdates();

            if (transitionIn && !transitionOut) {
                fadeTransitionIn(dt);
            }
            else if (transitionOut) {
                fadeTransitionOut(dt);
            }

            isRendering = false;
        }
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

        screenTransitionRenderer.initArrays(visibleGridWidth * visibleGridHeight);
        fillScreen();
        screenTransitionRenderer.renderSolidColours(mvpMatrix);
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

        screenTransitionRenderer.initArrays(visibleGridWidth * visibleGridHeight);
        fillScreen();
        screenTransitionRenderer.renderSolidColours(mvpMatrix);
    }

    /**
     *  Called when renderer is in SPLASH state and we have new game content to display.
     */

    public void startNewFloor() {
        transitionIn = true;
        transitionOut = false;
        firstRender = true;
        renderState = GAME;
        centreAtPlayerPos();
    }

    /**
     *  Counts total number of spriteHandles displayed in current chunk & returns total as int.
     *  This includes terrain, objects, animations, and any other effects
     */

    private int countSprites() {
        int terrainCount = chunkWidth * chunkHeight;
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

        return terrainCount + objectCount + animationCount + uiCount;
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

    private String hp;
    private String xp;
    private String fps;

    private void buildUiTextObjects() {
        Actor player = (Actor) currentFloorData.getPlayer();
        hp = "HP: " + player.getHpString();
        xp = "XP: " + player.getXpString();
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

    /**
     *  Iterates over visible objects in current frame and passes data to SpriteSheetRenderer.
     */

    private void addSprites() {
        GameObject[][] mapGrid = currentFloorData.getTerrain();
        ArrayList<GameObject>[][] objectGrid = currentFloorData.getObjects();
        ArrayList<GameObject>[][] animations = currentFloorData.getAnimations();
        ArrayList<GameObject> movingObjects = new ArrayList<>();

        for (int y = chunkOriginY; y < chunkHeight; y++) {
            for (int x = chunkOriginX; x < chunkWidth; x++) {
                if (!inBounds(x, y)) continue;

                float lighting = (float) getLightingForGrid(x, y);

                GameObject terrain = mapGrid[x][y];
                int index = spriteIndexes.get(terrain.getSprite());

                spriteRenderer.addSpriteData(
                        x, y,
                        index,
                        lighting,
                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

                ArrayList<GameObject> objectsInCell = objectGrid[x][y];

                for (int i = 0; i < objectsInCell.size(); i++) {
                    GameObject object = objectsInCell.get(i);

                    if (object.isProjected()) {
                        int fovX = object.getFovX();
                        int fovY = object.getFovY();

                        if (fieldOfVision[fovX][fovY] > 0.1) {
                            spriteRenderer.addSpriteData(
                                    x, y,
                                    spriteIndexes.get(object.getSprite()),
                                    lighting,
                                    DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                        }

                    }

                    else if (object.isImmutable() && object.isStationary()) {
                        spriteRenderer.addSpriteData(
                                x, y,
                                spriteIndexes.get(object.getSprite()),
                                lighting,
                                DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                    }

                    else {
                        if (object.getlastMove() != null) {
                            // Wait until after we've finished iterating before adding to renderer
                            // to make sure they aren't drawn underneath other spriteHandles.

                            // Also note: moving objects will break wave shader, so these objects
                            // should always be rendered using basic shader
                            
                            movingObjects.add(object);
                        }
                        else {
                            if (object.isGasOrLiquid()) {
                                waveRenderer.addSpriteData(
                                        x, y,
                                        spriteIndexes.get(object.getSprite()),
                                        lighting,
                                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

                            }
                            else {
                                int sprite;
                                if (object.hasAnimation()) {
                                    sprite = spriteIndexes.get(object.getSprite(1f));
                                }
                                else {
                                    sprite = spriteIndexes.get(object.getSprite());
                                }

                                spriteRenderer.addSpriteData(
                                        x, y,
                                        sprite,
                                        lighting,
                                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                            }
                        }
                    }
                }

                int animationsSize = animations[x][y].size();
                for (int i = 0; i < animationsSize; i++) {
                    Animation animation = (Animation) animations[x][y].get(i);
                    int frameIndex = processAnimation(animation);

                    if (animation.isGasOrLiquid()) {
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

        handleMovingObjects(movingObjects);
    }

    /**
     *  Covers screen with transparent black squares which slowly fade into view.
     *  Used to handle screen transitions/etc
     */

    private void fillScreen() {
        float[] colour = new float[] {
                0f, 0f, 0f, currentTransitionAlpha,
                0f, 0f, 0f, currentTransitionAlpha,
                0f, 0f, 0f, currentTransitionAlpha,
                0f, 0f, 0f, currentTransitionAlpha
        };

        for (int y = 0; y < visibleGridHeight; y++) {
            for (int x = 0; x < visibleGridWidth; x++) {
                screenTransitionRenderer.addSolidTile(
                        x, y,
                        colour,
                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
            }
        }
    }

    private void handleMovingObjects(ArrayList<GameObject> objects) {
        for (GameObject object : objects) {
            if (object.getlastMove() == null) continue;
            int x = object.x();
            int y = object.y();
            float fraction = object.advanceMovement();
            float offsetX = (x - object.getlastMove().x()) * fraction;
            float offsetY = (y - object.getlastMove().y()) * fraction;
            float lighting = (float) getLightingForGrid(x, y);
            if (fraction == 1) {
                object.setLastMove(null);
                spriteRenderer.addSpriteData(x, y, spriteIndexes.get(object.getSprite()), lighting, 0f, 0f);
            } else {
                spriteRenderer.addSpriteData(object.getlastMove().x(), object.getlastMove().y(), spriteIndexes.get(object.getSprite()), lighting, offsetX, offsetY);

                if (object.isPlayerControlled()) {
                    /*scrollDx -= offsetX * (SPRITE_SIZE / 2);
                    scrollDy -= offsetY * (SPRITE_SIZE / 2);*/

                    // No scrolling, but more accurate?
                    centreAtPlayerPos();
                }
            }
        }
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
        int screenPosX = x - scrollOffset.x();
        int screenPosY = y - scrollOffset.y();

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
            if (animation.isFinished()) {
                it.remove();
            }
        }
    }

    private void addTextLayer() {
        for (TextObject object : narrations) {
            textRenderer.addTextData(object.row, object.text, object.color, object.alphaModifier);
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

        textRenderer.addTextData(textRowHeight - 1, targetWidth, fps, TextColours.WHITE, 0f);
        textRenderer.addTextData(textRowHeight - 1, hp, TextColours.RED, 0f);
        textRenderer.addTextData(textRowHeight - 2, xp, TextColours.YELLOW, 0f);
    }

    private double getLightingForGrid(int x, int y) {
        double lightSource, fov;

        if (lightMap == null || lightMap[x][y] == 0) {
            lightSource = 0.1;
        }
        else {
            lightSource = lightMap[x][y];
        }

        if (fieldOfVision[x][y] == 0) {
            fov = 0.1;
        }
        else {
            fov = fieldOfVision[x][y];
        }

        return (lightSource + fov) / 2;
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
        x -= scrollDx;
        correctedY -= scrollDy;

        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        float gridX = x / spriteSize;
        float gridY = correctedY / spriteSize;

        return new Vector((int) gridX, (int) gridY);
    }

    private void setGridChunkToRender() {
        float x = 0;
        float y = 0;

        // Account for touch scrolling
        x -= scrollDx;
        y -= scrollDy;

        float spriteSize = SPRITE_SIZE * zoomLevel * scaleFactor;

        int originX = (int) (x / spriteSize);
        int originY = (int) (y / spriteSize);

        // Render slightly larger chunk of grid than is actually visible.
        // Don't exceed bounds of map
        chunkOriginX = Math.max(originX - 1, 0);
        chunkOriginY = Math.max(originY - 1, 0);
        chunkWidth = Math.max(originX + visibleGridWidth + 2, mapGridWidth);
        chunkHeight = Math.max(originY + visibleGridHeight + 2, mapGridHeight);
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
        x += scrollDx;
        y += scrollDy;

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
     *  Adding new text objects while renderScreen() is executing runs the risk of causing ArrayOutOfBoundsException
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

    public void fadeOutAndDisplaySplash() {
        this.transitionOut = true;
    }

}
