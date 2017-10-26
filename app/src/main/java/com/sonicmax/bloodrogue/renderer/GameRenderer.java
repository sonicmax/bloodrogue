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
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.Animation;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.renderer.sprites.Sprite;
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
    private SpriteSheetRenderer mSpriteSheetRenderer;
    private HashMap<String, Integer> mSpriteIndexes;
    private HashMap<String, Integer> mSprites;
    private TextRenderer mTextManager;
    private boolean mNeedsCache;

    // Matrixes for GL surface
    private final float[] mMVPMatrix;
    private final float[] mProjMatrix;
    private final float[] mVMatrix;
    private float[] mTMatrix;

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

    // Scrolling
    private float scrollDx = 0f;
    private float scrollDy = 0f;

    // Timing
    private long mStartTime;
    private long mEndTime;
    private int frameCount;
    private long currentFrameTime;
    private int mFps;

    // Text
    private ArrayList<TextObject> mNarrations;
    private int mTextRowHeight;

    // GL handles
    private int mSpriteShaderProgram;
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
        mTMatrix = new float[16];

        mNarrations = new ArrayList<>();

        mSprites = new HashMap<>();
        mZoom = 1f;

        mNeedsCache = true; // Always cache on first run
        mFirstRender = true;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLShaderLoader loader = new GLShaderLoader();
        mSpriteShaderProgram = loader.compileSpriteShader();
        // For now, we're using the same shader program for all rendering
        GLES20.glUseProgram(mSpriteShaderProgram);

        mSpriteManager = new SpriteLoader();
        prepareGLSurface();
        calculateGridSize();

                mRenderState = SPLASH;
                loadImages();
                setupMatrixes();
                scaleScreen();
                prepareTextRenderer();
                prepareSpriteRenderer();
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

            if (dt < FRAME_TIME) {
                try {
                    Thread.sleep(FRAME_TIME - dt);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Error in onDrawFrame", e);
                }
            }

            mStartTime = mEndTime;

            calculateFps(dt);
            renderScreen();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        mWidth = width;
        mHeight = height;

        setupMatrixes();
        scaleScreen();
        calculateGridSize();
    }

    private void prepareGLSurface() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GL10.GL_TEXTURE_2D);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glCullFace(GL10.GL_BACK);
    }

    /*
    ---------------------------------------------
      Resource loading and preparation
    ---------------------------------------------
    */

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
        mSpriteSheetRenderer = new SpriteSheetRenderer();
        mSpriteSheetRenderer.setShaderProgramHandle(mSpriteShaderProgram);
        mSpriteSheetRenderer.setSpriteSheetHandle(mSprites.get("sprite_sheets/sheet.png"));
        mSpriteSheetRenderer.setUniformScale(ssu);
        mSpriteSheetRenderer.precalculatePositions(mMapWidth, mMapHeight);
        mSpriteSheetRenderer.precalculateUv(mSpriteIndexes.size());
    }

    private void prepareTextRenderer() {
        // Create our text manager
        mTextManager = new TextRenderer();
        mTextManager.setShaderProgramHandle(mSpriteShaderProgram);
        mTextManager.setTextureHandle(mSprites.get("fonts/ccra_font.png"));
        mTextManager.setUniformscale(ssu);
        mTextManager.precalculateUv();
        mTextManager.precalculateOffsets();
        mTextRowHeight = mTextManager.precalculateRows(mHeight);
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

        if (mWidth > mHeight) {
            resX = mWidth / mResTargetHeight;
            resY = mHeight / mResTargetWidth;
        }
        else {
            resX = mWidth / mResTargetHeight;
            resY = mHeight / mResTargetWidth;
        }

        if (resX > resY) {
            ssu = resY;
        }

        else {
            ssu = resX;
        }
    }

    private void calculateScroll() {
        mScrollOffset = calculateScrollOffset();
    }

    private void calculateGridSize() {
        int width = ScreenSizeGetter.getWidth();
        int height = ScreenSizeGetter.getHeight();

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

        mVisibleGridWidth++;
        mVisibleGridHeight++;
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

    private void renderScreen() {
        if (!isRendering) {

            isRendering = true;

            if (mFirstRender) {
                centreAtPlayerPos();
            }

            if (mNewFrame == null) {
                mScrollOffset = calculateScrollOffset();
                mLightMap = mFrame.getLightMap();
                mFov = mFrame.getFov();

                mFirstRender = false;
            }

            else {
                // Replace existing frame with new frame
                mFrame = mNewFrame;
                mLightMap = mFrame.getLightMap();
                mFov = mFrame.getFov();
                calculateScroll();
            }

            if (mNeedsCache) {
                cacheImmutableSprites();
                mNeedsCache = false;
            }

            // Iterate over the data in Frame object and send to renderer
            addSpritesToRenderer();
            addUiLayer();

            // Check whether we need to translate matrix to account for touch scrolling
            float[] renderMatrix = mTMatrix.clone();

            if (scrollDx != 0 || scrollDy != 0) {
                Matrix.translateM(renderMatrix, 0, mMVPMatrix, 0, scrollDx, scrollDy, 0f);
            }

            mSpriteSheetRenderer.prepareDrawInfo();
            mSpriteSheetRenderer.renderSprites(renderMatrix);

            // Add our text overlay
            mTextManager.clear();
            mTextManager.addText(new TextObject(mFps + " fps", mTextRowHeight - 1));
            for (TextObject object : mNarrations) {
                mTextManager.addText(object);
            }
            mTextManager.prepareText();
            mTextManager.renderText(mMVPMatrix);

            isRendering = false;
        }
    }

    /**
     *  Caches terrain and stationary, non-interactive objects
     */

    private void cacheImmutableSprites() {
        GameObject[][] mapGrid = mFrame.getTerrain();
        ArrayList<GameObject>[][] objectGrid = mFrame.getObjects();

        for (int y = 0; y < mMapHeight; y++) {
            for (int x = 0; x < mMapWidth; x++) {
                if (!inBounds(x, y)) continue;

                GameObject terrain = mapGrid[x][y];
                int index = mSpriteIndexes.get(terrain.tile());

                mSpriteSheetRenderer.cacheSprite(new Sprite(x, y, index));

                ArrayList<GameObject> objectsInCell = objectGrid[x][y];

                int objectsSize = objectsInCell.size();

                for (int i = 0; i < objectsSize; i++) {
                    GameObject object = objectsInCell.get(i);
                    if (object.isProjected()) {
                        if (mFov[x][y] > 0) {
                            int destX = object.getDestX();
                            int destY = object.getDestY();
                            mSpriteSheetRenderer.cacheSprite(new Sprite(destX, destY, mSpriteIndexes.get(object.tile())));
                        }

                    }

                    else if (object.isImmutable() && (object.isStationary())) {
                        mSpriteSheetRenderer.cacheSprite(new Sprite(x, y, mSpriteIndexes.get(object.tile())));
                    }
                }
            }
        }
    }

    private void addSpritesToRenderer() {
        ArrayList<GameObject>[][] objectGrid = mFrame.getObjects();
        ArrayList<GameObject>[][] animations = mFrame.getAnimations();

        mSpriteSheetRenderer.clear();

        for (int y = 0; y < mMapHeight; y++) {
            for (int x = 0; x < mMapWidth; x++) {
                mSpriteSheetRenderer.setLighting(x, y, getLightingForGrid(x, y));

                // Todo: maybe we could draw objects outside FOV using low lighting + alpha

                if (mFov[x][y] > 0.1) {
                    int objectsSize = objectGrid[x][y].size();
                    // It's possible that object stack will change during rendering. Todo: this is bad
                    for (int i = 0; i < objectsSize; i++) {
                        GameObject object = objectGrid[x][y].get(i);
                        if (!object.isProjected() && (!object.isStationary() || !object.isImmutable())) {
                            if (object.getlastMove() != null) {
                                handleMovingObject(x, y, object);
                            }
                            else {
                            mSpriteSheetRenderer.addSprite(new Sprite(x, y, mSpriteIndexes.get(object.tile())));
                        }
                    }
                    }
                    int animationsSize = animations[x][y].size();
                    for (int i = 0; i < animationsSize; i++) {
                        Animation animation = (Animation) animations[x][y].get(i);
                        int frameIndex = processAnimation(animation);
                        mSpriteSheetRenderer.addSprite(new Sprite(x, y, frameIndex));
                    }
                }

                handleFinishedAnimations(animations[x][y]);
            }
        }
    }

    private void handleMovingObject(int x, int y, GameObject object) {
        float fraction = object.advanceMovement();
        float offsetX = (object.x() - object.getlastMove().x()) * fraction;
        float offsetY = (object.y() - object.getlastMove().y()) * fraction;
        if (fraction == 1) {
            object.setLastMove(null);
            mSpriteSheetRenderer.addSprite(new Sprite(x, y, mSpriteIndexes.get(object.tile())));
        }
        else {
            mSpriteSheetRenderer.addSprite(new Sprite(object.getlastMove().x(), object.getlastMove().y(), mSpriteIndexes.get(object.tile()), offsetX, offsetY));
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

                Sprite sprite = new Sprite(x, y, index);
                sprite.lighting = 1f;

                mSpriteSheetRenderer.addSprite(sprite);
            }
        }
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

    public void calculateFps(long dt) {
        currentFrameTime += dt;
        frameCount++;

        if (currentFrameTime >= 1000) {
            mFps = frameCount;
            currentFrameTime = 0;
            frameCount = 0;
            mGameInterface.checkNarrations();
        }
    }

    /*
    ---------------------------------------------
      Getters and setters
    ---------------------------------------------
    */

    public Vector getGridCellForTouchCoords(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        int height = ScreenSizeGetter.getHeight();
        float correctedY = height - y;

        // Account for touch scrolling
        x -= scrollDx;
        correctedY -= scrollDy;

        float spriteSize = SPRITE_SIZE * mZoom * ssu;
        float gridX = x / spriteSize;
        float gridY = correctedY / spriteSize;

        return new Vector((int) gridX, (int) gridY);
    }

    public void setFrame(Frame frame) {
        if (mFirstRender) {
            mFrame = frame;
            mLightMap = mFrame.getLightMap();
            mFov = mFrame.getFov();
        }
        else {
            mNewFrame = frame;
        }
    }

    public void setZoom(float mZoom) {
        this.mZoom = mZoom;
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

    public void setNarrations(ArrayList<TextObject> narrations) {
        this.mNarrations = narrations;
    }
}
