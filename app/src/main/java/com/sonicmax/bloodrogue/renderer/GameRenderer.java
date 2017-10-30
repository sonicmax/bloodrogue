package com.sonicmax.bloodrogue.renderer;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.engine.Frame;
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

    private final long FRAME_TIME = 17L;

    private GameInterface mGameInterface;

    // Game/renderer state
    private Frame mFrame;
    private Frame mNewFrame;
    private ArrayList<Vector> mCurrentPath;
    private Vector mScrollOffset;
    private double[][] mLightMap = null;
    private double[][] mFov = null;

    // Resources
    private SpriteLoader mSpriteManager;
    private SpriteSheetRenderer mSpriteRenderer;
    private SpriteSheetRenderer mUiRenderer;
    private HashMap<String, Integer> mSprites; // Texture handles for loaded textures
    private HashMap<String, Integer> mSpriteIndexes; // Position on sprite sheet for particular texture
    private TextRenderer mTextRenderer;

    // Text
    private ArrayList<TextObject> mNarrations;
    private ArrayList<TextObject> mStatuses;
    private ArrayList<TextObject> mQueuedStatuses;
    private ArrayList<TextObject> mQueuedNarrations;
    private int mTextRowHeight;

    // Matrixes for GL surface
    private final float[] mMVPMatrix;
    private final float[] mProjMatrix;
    private final float[] mVMatrix;
    private float[] mUiMatrix;

    // UI
    private UserInterfaceBuilder mUiBuilder;
    private boolean mInventoryShowing = false;

    /**
     *  Screen size and scaling
     */

    private int mWidth; // Screen size
    private int mHeight;
    private int mMapWidth; // Grid cells in map
    private int mMapHeight;
    private int mVisibleGridWidth; // Screen size in grid cells
    private int mVisibleGridHeight;
    private float mResTargetWidth = 320f;
    private float mResTargetHeight = 480f;

    private float mZoom;
    private float resX;
    private float resY;
    private float ssu;

    // Visible grid chunk
    private int mChunkOriginX;
    private int mChunkOriginY;
    private int mChunkWidth;
    private int mChunkHeight;

    // Scrolling
    private float scrollDx = 0f;
    private float scrollDy = 0f;

    // Timing
    private long mStartTime;
    private long mEndTime;
    private int frameCount;
    private long currentFrameTime;
    private int mFps;

    // GL handles
    private int mSpriteShaderProgram;
    private int mWaveShaderProgram;
    private int mRenderState;

    // State handlers
    private boolean hasResources;
    private boolean isRendering;
    private boolean mFirstRender;

    public GameRenderer(GameInterface gameInterface) {
        super();
        mGameInterface = gameInterface;

        mFrame = null;
        mNewFrame = null;
        mRenderState = NONE;
        hasResources = false;
        isRendering = false;

        mProjMatrix = new float[16];
        mVMatrix = new float[16];
        mMVPMatrix = new float[16];
        mUiMatrix = new float[16];

        mNarrations = new ArrayList<>();
        mStatuses = new ArrayList<>();
        mQueuedNarrations = new ArrayList<>();
        mQueuedStatuses = new ArrayList<>();

        mSprites = new HashMap<>();
        mZoom = 1f;

        mFirstRender = true;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLShaderLoader loader = new GLShaderLoader();
        mSpriteShaderProgram = loader.compileSpriteShader();
        mWaveShaderProgram = loader.compileWaveShader();

        // For now, we're using the same shader program for all rendering

        mSpriteManager = new SpriteLoader();
        prepareGLSurface();

        mRenderState = SPLASH;
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
        mRenderState = GAME;

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

        if (mRenderState == NONE) {
            Log.v(LOG_TAG, "Render state = none");
        }

        else if (mRenderState == SPLASH) {
            // Show loading screen
        }

        else if (hasResources && mRenderState == GAME) {

            mEndTime = System.currentTimeMillis();

            long dt = mEndTime - mStartTime;

            if (dt > 10000) {
                dt = FRAME_TIME;
            }

            if (dt < FRAME_TIME) {
                try {
                    Thread.sleep(FRAME_TIME - dt);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Error in onDrawFrame", e);
                }
            }

            mStartTime = mEndTime;

            checkElapsedTime(dt);
            renderScreen(dt);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        mWidth = width;
        mHeight = height;

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
        mSpriteIndexes = new HashMap<>();
        // Iterate over all paths in /assets/img and create SpriteLoader object with handle to loaded texture
        long startTime = System.nanoTime();

        final String IMG_PATH = "sprites/";
        final String SHEET_PATH = "sprite_sheets/";
        final String FONT_PATH = "fonts/";
        AssetManager assetManager = mGameInterface.getAssets();

        try {
            String[] images = assetManager.list("sprites");
            int index = 0;

            // As AssetManager.list() returns alphabetically sorted list, and sprite sheet is also
            // ordered alphabetically, we can just increment the index on each filename to get the position
            // on sprite sheet. These will be used later when passing data to SpriteSheetRenderer

            for (String image : images) {
                mSpriteIndexes.put(IMG_PATH + image, index);
                index++;
            }

            // Now we can load our sprite sheet and fonts

            String[] sheets = assetManager.list("sprite_sheets");

            for (String sheet : sheets) {
                InputStream is = assetManager.open(SHEET_PATH + sheet);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_4444;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = mSpriteManager.loadTexture(bitmap);
                mSprites.put(SHEET_PATH + sheet, textureHandle);
            }

            String[] fontPaths = assetManager.list("fonts");

            for (String path : fontPaths) {
                InputStream is = assetManager.open(FONT_PATH + path);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_4444;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = mSpriteManager.loadTexture(bitmap);
                mSprites.put(FONT_PATH + path, textureHandle);
            }

            long stopTime = System.nanoTime();
            Log.v(LOG_TAG, "Loaded images in " + TimeUnit.MILLISECONDS.convert(stopTime - startTime, TimeUnit.NANOSECONDS) + " ms");

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void prepareSpriteRenderer() {
        mSpriteRenderer = new SpriteSheetRenderer();
        mSpriteRenderer.setBasicShader(mSpriteShaderProgram);
        mSpriteRenderer.setSpriteSheetHandle(mSprites.get("sprite_sheets/sheet.png"));
        mSpriteRenderer.setUniformScale(ssu);
        mSpriteRenderer.precalculatePositions(mMapWidth, mMapHeight);
        mSpriteRenderer.precalculateUv(mSpriteIndexes.size());
    }

    private SpriteSheetRenderer mWaveRenderer;

    private void prepareWaveRenderer() {
        mWaveRenderer = new SpriteSheetRenderer();
        mWaveRenderer.setBasicShader(mSpriteShaderProgram);
        mWaveRenderer.setWaveShader(mWaveShaderProgram);
        mWaveRenderer.setSpriteSheetHandle(mSprites.get("sprite_sheets/sheet.png"));
        mWaveRenderer.setUniformScale(ssu);
        mWaveRenderer.precalculatePositions(mMapWidth, mMapHeight);
        mWaveRenderer.precalculateUv(mSpriteIndexes.size());
    }

    private void prepareTextRenderer() {
        // Create our text manager
        mTextRenderer = new TextRenderer();
        mTextRenderer.setShaderProgramHandle(mSpriteShaderProgram);
        mTextRenderer.setTextureHandle(mSprites.get("fonts/ccra_font.png"));
        mTextRenderer.setUniformscale(ssu);
        mTextRenderer.precalculateUv();
        mTextRenderer.precalculateOffsets();
        mTextRowHeight = mTextRenderer.precalculateRows(mHeight);
    }

    private void prepareUiRenderer() {
        mUiRenderer = new SpriteSheetRenderer();
        mUiRenderer.setBasicShader(mSpriteShaderProgram);
        mUiRenderer.setSpriteSheetHandle(mSprites.get("sprite_sheets/sheet.png"));
        mUiRenderer.setUniformScale(ssu);
        mUiRenderer.precalculatePositions(mVisibleGridWidth, mVisibleGridHeight);
        mUiRenderer.precalculateUv(mSpriteIndexes.size());
    }

    private TextRenderer mUiTextRenderer;

    private void prepareUiTextRenderer() {
        // Create our text manager
        mUiTextRenderer = new TextRenderer();
        mUiTextRenderer.setShaderProgramHandle(mSpriteShaderProgram);
        mUiTextRenderer.setTextureHandle(mSprites.get("fonts/ccra_font.png"));
        mUiTextRenderer.setUniformscale(ssu);
        mUiTextRenderer.setTextSize(32f);
        mUiTextRenderer.precalculateUv();
        mUiTextRenderer.precalculateOffsets();
        mUiTextRenderer.precalculateRows(mHeight);
    }

    private void setupUiBuilder() {
        mUiBuilder = new UserInterfaceBuilder(mSpriteIndexes, mVisibleGridWidth, mVisibleGridHeight);
    }

    /*
    ---------------------------------------------
      Scaling and scrolling
    ---------------------------------------------
    */

    private void setupMatrixes() {
        // Setup our screen width and height for normal sprite translation.
        if (mWidth == 0 || mHeight == 0) {
            mWidth = ScreenSizeGetter.getWidth();
            mHeight = ScreenSizeGetter.getHeight();
        }

        Matrix.orthoM(mProjMatrix, 0, 0f, mWidth * mZoom, 0.0f, mHeight * mZoom, 0, 50);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mVMatrix, 0,
                0f, 0f, 1f,
                0f, 0f, 0f,
                0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);
    }

    private void setupUiMatrixes() {
        mUiMatrix = mMVPMatrix.clone();

        /*float[] coords = getRenderCoordsForObject(new Vector(mVisibleGridWidth, 0));
        float differenceX = (float) mWidth - coords[0];
        differenceX *= 1.45f;

        Log.v(LOG_TAG, "" + coords[0]);
        Log.v(LOG_TAG, "" + differenceX);

        Matrix.translateM(mUiMatrix, 0, differenceX, 0f, 0f);*/
    }

    private void centreAtPlayerPos() {
        Vector pos = mFrame.getPlayer().getVector();
        float gridSize = SPRITE_SIZE * mZoom * ssu;

        // TODO: align better to centre by checking if odd/even number of grid squares in visible width

        scrollDx = 0f - (gridSize * pos.x()) + (mWidth / 2);
        scrollDy = 0f - (gridSize * pos.y()) + (mHeight / 2);

        // We have to make sure MainActivity has same coords as renderer
        // mGameInterface.updateScrollPos(scrollDx, scrollDy);
    }

    private void scaleScreen() {
        // Desired resolution is 320x480 (or 480x320 in landscape)
        // Setup our screen width and height for normal sprite translation.
        if (mWidth == 0 || mHeight == 0) {
            mWidth = ScreenSizeGetter.getWidth();
            mHeight = ScreenSizeGetter.getHeight();
        }

        resX = (float) mWidth / mResTargetWidth;
        resY = (float) mHeight / mResTargetHeight;

        /*if (mWidth > mHeight) {
            resX = (float) mWidth / mResTargetHeight;
            resY = (float) mHeight / mResTargetWidth;
        }
        else {
            resX = (float) mWidth / mResTargetHeight;
            resY = (float) mHeight / mResTargetWidth;
        }*/

        Log.i(LOG_TAG, "" + resX + ", " + resY);

        if (resX > resY) {
            ssu = resY;
        }

        else {
            ssu = resX;
        }
    }

    private void calculateGridSize() {
        float width = ScreenSizeGetter.getWidth() * mZoom;
        float height = ScreenSizeGetter.getHeight() * mZoom;

        float spriteSize = SPRITE_SIZE * mZoom * ssu;

        double xInterval = width / spriteSize;
        double yInterval = height / spriteSize;

        mVisibleGridWidth = (int) xInterval;
        mVisibleGridHeight = (int) yInterval;

        if (mVisibleGridWidth > 14) {
            xInterval = width / (spriteSize * 2);
            yInterval = height / (spriteSize * 2);

            mVisibleGridWidth = (int) xInterval;
            mVisibleGridHeight = (int) yInterval;
        }
    }

    private Vector calculateScrollOffset() {
        GameObject player = mFrame.getPlayer();
        return new Vector(player.x() - (mVisibleGridWidth / 2), player.y() - (mVisibleGridHeight / 2));
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

    private void renderScreen(float dt) {
        if (!isRendering) {

            isRendering = true;

            if (mFirstRender) {
                centreAtPlayerPos();
                mLightMap = mFrame.getLightMap();
                mFov = mFrame.getFov();
                mFirstRender = false;
            }

            if (mNewFrame != null) {
                // Replace existing frame with new frame
                mFrame = mNewFrame;
                mLightMap = mFrame.getLightMap();
                mFov = mFrame.getFov();
            }

            mScrollOffset = calculateScrollOffset();
            setGridChunkToRender();

            String fps = mFps + " fps";
            // Get total sprite count and pass to renderer so we can init arrays used to store rendering data
            int spriteCount = countSprites();

            mSpriteRenderer.initArrays(spriteCount);
            mWaveRenderer.initArrays(spriteCount);
            mTextRenderer.initArrays(countTextObjects() + fps.length());
            mUiRenderer.initArrays(countUiSprites());
            mUiTextRenderer.initArrays("Testing".length());

            // Iterate over game data and send to renderer
            addSprites();
            addUiLayer();
            addTextLayer();
            mTextRenderer.addTextData(mTextRowHeight - 1, fps, TextColours.WHITE, 0f);

            // Check whether we need to translate matrix to account for touch scrolling
            float[] renderMatrix = new float[16];

            if (scrollDx != 0 || scrollDy != 0) {
                Matrix.translateM(renderMatrix, 0, mMVPMatrix, 0, scrollDx, scrollDy, 0f);
            }

            mSpriteRenderer.renderSprites(renderMatrix);
            mWaveRenderer.renderWaveEffect(renderMatrix, dt);
            mTextRenderer.renderText(mMVPMatrix);
            mUiRenderer.renderSprites(mUiMatrix);

            isRendering = false;

            addQueuedTextUpdates();
        }
    }

    /**
     *  Counts total number of sprites displayed in current chunk & returns total as int.
     *  This includes terrain, objects, animations, and any other effects
     */

    private int countSprites() {
        int terrainCount = mChunkWidth * mChunkHeight;
        int objectCount = 0;
        int animationCount = 0;
        int uiCount = 0;

        ArrayList<GameObject>[][] objects = mFrame.getObjects();
        ArrayList<GameObject>[][] animations = mFrame.getAnimations();

        for (int x = mChunkOriginX; x < mChunkWidth; x++) {
            for (int y = mChunkOriginY; y < mChunkHeight; y++) {
                objectCount += objects[x][y].size();
                animationCount += animations[x][y].size();
            }
        }

        if (mCurrentPath != null) {
            uiCount += mCurrentPath.size();
        }

        return terrainCount + objectCount + animationCount + uiCount;
    }

    /**
     *  Counts number of sprites used to render UI and returns total as int.
     */

    private int countUiSprites() {
        int windowSize = mVisibleGridWidth * mVisibleGridHeight;
        int iconCount = 1;
        return windowSize + iconCount;
    }

    /**
     * Counts number of characters in each TextObject and returns combined total.
     * We count individual latters as they are rendered 1 sprite per letter
     */

    private int countTextObjects() {
        int narrationSize = 0;
        int statusSize = 0;

        for (TextObject object : mNarrations) {
            narrationSize += object.text.length();
        }

        for (TextObject object : mStatuses) {
            statusSize += object.text.length();
        }

        return narrationSize + statusSize;
    }

    private void addSprites() {
        GameObject[][] mapGrid = mFrame.getTerrain();
        ArrayList<GameObject>[][] objectGrid = mFrame.getObjects();
        ArrayList<GameObject>[][] animations = mFrame.getAnimations();
        ArrayList<GameObject> movingObjects = new ArrayList<>();

        for (int y = mChunkOriginY; y < mChunkHeight; y++) {
            for (int x = mChunkOriginX; x < mChunkWidth; x++) {
                if (!inBounds(x, y)) continue;

                float lighting = (float) getLightingForGrid(x, y);

                GameObject terrain = mapGrid[x][y];
                int index = mSpriteIndexes.get(terrain.tile());

                mSpriteRenderer.addSpriteData(
                        x, y,
                        index,
                        lighting,
                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

                ArrayList<GameObject> objectsInCell = objectGrid[x][y];

                int objectsSize = objectsInCell.size();

                for (int i = 0; i < objectsSize; i++) {
                    GameObject object = objectsInCell.get(i);

                    if (object.isProjected()) {
                        int fovX = object.getFovX();
                        int fovY = object.getFovY();

                        if (mFov[fovX][fovY] > 0.1) {
                            mSpriteRenderer.addSpriteData(
                                    x, y,
                                    mSpriteIndexes.get(object.tile()),
                                    lighting,
                                    DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                        }

                    }

                    else if (object.isImmutable() && (object.isStationary())) {
                        mSpriteRenderer.addSpriteData(
                                x, y,
                                mSpriteIndexes.get(object.tile()),
                                lighting,
                                DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                    }

                    else {
                        if (object.getlastMove() != null) {
                            // Wait until after we've finished iterating before adding to renderer
                            // to make sure they aren't drawn underneath other sprites.

                            // Also note: moving objects will break wave shader, so these objects
                            // should always be rendered using basic shader
                            
                            movingObjects.add(object);
                        }
                        else {
                            if (object.isGasOrLiquid()) {
                                mWaveRenderer.addSpriteData(
                                        x, y,
                                        mSpriteIndexes.get(object.tile()),
                                        lighting,
                                        DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);

                            }
                            else {
                                mSpriteRenderer.addSpriteData(
                                        x, y,
                                        mSpriteIndexes.get(object.tile()),
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
                    mSpriteRenderer.addSpriteData(
                            x, y,
                            frameIndex,
                            lighting,
                            DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
                }

                handleFinishedAnimations(animations[x][y]);
            }
        }

        handleMovingObjects(movingObjects);
    }

    private void handleMovingObjects(ArrayList<GameObject> objects) {
        for (GameObject object : objects) {
            int x = object.x();
            int y = object.y();
            float fraction = object.advanceMovement();
            float offsetX = (x - object.getlastMove().x()) * fraction;
            float offsetY = (y - object.getlastMove().y()) * fraction;
            float lighting = (float) getLightingForGrid(x, y);
            if (fraction == 1) {
                object.setLastMove(null);
                mSpriteRenderer.addSpriteData(x, y, mSpriteIndexes.get(object.tile()), lighting, 0f, 0f);
            } else {
                mSpriteRenderer.addSpriteData(object.getlastMove().x(), object.getlastMove().y(), mSpriteIndexes.get(object.tile()), lighting, offsetX, offsetY);

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
        if (mCurrentPath != null) {
            int index = mSpriteIndexes.get("sprites/cursor_default.png");
            int pathSize = mCurrentPath.size();
            for (int i = 0; i < pathSize; i++) {
                Vector segment = mCurrentPath.get(i);
                int x = segment.x();
                int y = segment.y();
                mSpriteRenderer.addSpriteData(x, y, index, 1f, 0f, 0f);
            }
        }

        if (mInventoryShowing) {
            mUiBuilder.addWindow(mUiRenderer);
        }

        mUiBuilder.buildUi(mUiRenderer);
    }

    private int processAnimation(Animation animation) {
        final int TRANSPARENT = 164;

        int x = animation.x();
        int y = animation.y();
        int screenPosX = x - mScrollOffset.x();
        int screenPosY = y - mScrollOffset.y();

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

        return mSpriteIndexes.get(animation.getNextFrame());
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
        for (TextObject object : mNarrations) {
            mTextRenderer.addTextData(object.row, object.text, object.color, object.alphaModifier);
        }

        Iterator<TextObject> it = mStatuses.iterator();

        while (it.hasNext()) {
            Status status = (Status) it.next();
            float fraction = status.advanceScroll();
            if (fraction == 1) {
                it.remove();
            } else {
                status.offsetY = fraction;
                status.alphaModifier = fraction;
                mTextRenderer.addTextData(status.x, status.y, status.offsetY, status.scale, status.text, status.color, status.alphaModifier);
            }
        }
    }

    private double getLightingForGrid(int x, int y) {
        double lightSource, fov;

        if (mLightMap == null || mLightMap[x][y] == 0) {
            lightSource = 0.1;
        }
        else {
            lightSource = mLightMap[x][y];
        }

        if (mFov[x][y] == 0) {
            fov = 0.1;
        }
        else {
            fov = mFov[x][y];
        }

        return (lightSource + fov) / 2;
    }

    /*
    ---------------------------------------------
      Helper methods
    ---------------------------------------------
    */

    private boolean inVisibleBounds(int x, int y) {
        return (x >= 0 && x <= mVisibleGridWidth) && (y >= 0 && y <= mVisibleGridHeight);
    }

    private boolean inBounds(int x, int y) {
        return (x >= 0 && x < 32 && y >= 0 && y < 32);
    }

    private boolean mHalfSec = false;

    public void checkElapsedTime(long dt) {
        currentFrameTime += dt;
        frameCount++;

        // We periodically want to check narrations to see if we need to remove any of them.
        // Narrations are only updated once a second, so it's pointless checking every single frame
        if (currentFrameTime >= 500) {
            mGameInterface.checkNarrations();
            mHalfSec = true;
        }

        // Update FPS count
        if (currentFrameTime >= 1000) {
            mFps = frameCount;
            currentFrameTime = 0;
            frameCount = 0;
            if (!mHalfSec) {
                mGameInterface.checkNarrations();
            }

            mHalfSec = false;
        }
    }

    /*
    ---------------------------------------------
      Getters and setters
    ---------------------------------------------
    */

    public Vector getGridCellForTouchCoords(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        x *= mZoom;
        y *= mZoom;
        float height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        // Account for touch scrolling
        x -= scrollDx;
        correctedY -= scrollDy;

        float spriteSize = SPRITE_SIZE * mZoom * ssu;

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

        float spriteSize = SPRITE_SIZE * mZoom * ssu;

        int originX = (int) (x / spriteSize);
        int originY = (int) (y / spriteSize);

        // Render slightly larger chunk of grid than is actually visible
        mChunkOriginX = originX - 1;
        mChunkOriginY = originY - 1;
        mChunkWidth = originX + mVisibleGridWidth + 2;
        mChunkHeight = originY + mVisibleGridHeight + 2;
    }

    public float[] getRenderCoordsForObject(Vector objectPos) {
        float x = objectPos.x();
        float y = objectPos.y();
        float spriteSize = SPRITE_SIZE * mZoom * ssu;

        x *= spriteSize;
        y *= spriteSize;

        x += 30;
        y += spriteSize * 1.1;

        // Subtract scroll offset to find visible surface coords
        x += scrollDx;
        y += scrollDy;

        return new float[] {x, y};
    }

    public void setFrame(Frame frame) {
        if (mFrame == null) {
            mFrame = frame;
        }
        else {
            mNewFrame = frame;
        }
    }

    public void setZoom(float mZoom) {
        /*this.mZoom = mZoom;
        setupMatrixes();*/
    }

    public void setHasGameData() {
        hasResources = true;
    }

    public void setCurrentPathSelection(ArrayList<Vector> path) {
        this.mCurrentPath = path;
    }

    public void setMapSize(int[] size) {
        mMapWidth = size[0];
        mMapHeight = size[1];
    }

    /**
     *  Adding new text objects while renderScreen() is executing runs the risk of causing ArrayOutOfBoundsException
     *  because float arrays in renderer won't be big enough to accomodate new objects. So we queue them and add
     *  once the rendering has finished.
     */

    public void queueNarrationUpdate(ArrayList<TextObject> narrations) {
        this.mQueuedNarrations = narrations;
    }

    public void queueNewStatus(TextObject object) {
        this.mQueuedStatuses.add(object);
    }

    private void addQueuedTextUpdates() {
        this.mNarrations = mQueuedNarrations; // Narrations need to be replaced
        this.mStatuses.addAll(mQueuedStatuses); // Statuses should be added
    }

    public boolean checkUiTouch(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        x *= mZoom;
        y *= mZoom;
        float height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        float spriteSize = SPRITE_SIZE * mZoom * ssu;

        int gridX = (int) (x / spriteSize);
        int gridY = (int) (correctedY / spriteSize);

        if (gridX == mVisibleGridWidth - 1 && gridY == 0) {
            if (!mInventoryShowing) {
                mInventoryShowing = true;
                mUiBuilder.animateIcon(UserInterfaceBuilder.INVENTORY_OPEN);
            }
            else {
                mInventoryShowing = false;
                mUiBuilder.animateIcon(UserInterfaceBuilder.INVENTORY_CLOSED);
            }
            return true;
        }

        return false;
    }

}
