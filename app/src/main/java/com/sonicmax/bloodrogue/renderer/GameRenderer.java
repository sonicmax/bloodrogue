package com.sonicmax.bloodrogue.renderer;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.sonicmax.bloodrogue.MainActivity;
import com.sonicmax.bloodrogue.engine.Frame;
import com.sonicmax.bloodrogue.maths.Vector;
import com.sonicmax.bloodrogue.objects.Animation;
import com.sonicmax.bloodrogue.objects.GameObject;

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
    private final int SPRITE_SIZE = 64; // width/height in pixels
    private final int NONE = 0;
    private final int SPLASH = 1;
    private final int GAME = 2;

    private final long FRAME_TIME = 17L;

    private Context mContext;
    private GLSurfaceView mGLSurfaceView;

    // Game/renderer state
    private Frame mFrame;
    private Frame mNewFrame;
    private boolean mHasNewFrame = false;
    private Vector mCurrentSelection;
    private ArrayList<Vector> mCurrentPath;
    private Vector mScrollOffset;
    private double[][] mLightMap = null;
    private double[][] mFov = null;

    // Resources
    private SpriteLoader mSpriteManager;
    private SpriteSheetRenderer mSpriteSheetRenderer;
    private HashMap<String, Integer> mSpriteIndexes;
    private HashMap<String, Integer> mSprites;
    private TextManager mTextManager;

    // Matrixes for GL surface
    private final float[] mMVPMatrix;
    private final float[] mProjMatrix;
    private final float[] mVMatrix;
    private float[] mTMatrix;

    // Misc vars
    private int mSpriteShaderProgram;
    private int mRenderState;
    private boolean hasResources;
    private boolean isRendering;
    private int mFps;

    // Screen size and scaling
    private int mWidth;
    private int mHeight;
    private int mVisibleGridWidth;
    private int mVisibleGridHeight;
    private float mTranslationX;
    private float mTranslationY;
    private float mZoom;
    private float mRatio;
    private float resX;
    private float resY;
    private float ssu;

    // Scrolling

    private int oldOffsetX = 0;
    private int oldOffsetY = 0;
    private double mScrollFrameCount;
    private int mScrollProgress = 0;
    private double mScrollIntervalX = 0;
    private double mScrollIntervalY = 0;
    private double mCurrentScrollX = 0;
    private double mCurrentScrollY = 0;
    private boolean mNeedsScroll;

    // Timing
    private long mStartTime;
    private long mEndTime;
    private int frameCount;
    private long currentFrameTime;

    public GameRenderer(Context context, GLSurfaceView surfaceView) {
        super();
        mContext = context;
        mGLSurfaceView = surfaceView;

        mFrame = null;
        mNewFrame = null;
        mRenderState = NONE;
        hasResources = false;
        mCurrentSelection = null;
        isRendering = false;

        mProjMatrix = new float[16];
        mVMatrix = new float[16];
        mMVPMatrix = new float[16];
        mTMatrix = new float[16];

        mSprites = new HashMap<>();
        mZoom = 1f;
        mTranslationX = 0f;
        mTranslationY = 0f;

        mScrollProgress = 0;
        mScrollIntervalX = 0;
        mScrollIntervalY = 0;
        mCurrentScrollX = 0;
        mCurrentScrollY = 0;
        mNeedsScroll = false;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLShaderLoader loader = new GLShaderLoader();
        mSpriteShaderProgram = loader.compileSpriteShader().getSpriteShader();
        mSpriteShaderProgram = loader.compileTextShader().getTextShader();

        mSpriteManager = new SpriteLoader();
        prepareGLSurface();
        calculateGridSize();

        // Post new runnable to GLSurfaceView which allows us to load textures/etc in background
        mGLSurfaceView.queueEvent(new Runnable() {

            @Override
            public void run() {
                // (Inside GL thread)
                mRenderState = SPLASH;
                loadImages();
                setupMatrixes();
                scaleScreen();
                prepareText();
                prepareSpriteSheets();
                mRenderState = GAME;
            }
        });
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
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnable(GL10.GL_TEXTURE_2D);
        GLES20.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);

        // Don't think we need depth testing?

        /*GLES20.glClearDepthf(1.0f);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GL10.GL_LEQUAL);*/


        // We could cull the front face (but doesn't seem to improve performance)
        // GLES20.glEnable(GL10.GL_CULL_FACE);
        // GLES20.glCullFace(GL10.GL_FRONT);
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
        AssetManager assetManager = mContext.getAssets();

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
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = mSpriteManager.loadTexture(bitmap);
                mSprites.put(SHEET_PATH + sheet, textureHandle);
            }

            String[] fontPaths = assetManager.list("fonts");

            for (String path : fontPaths) {
                InputStream is = assetManager.open(FONT_PATH + path);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = mSpriteManager.loadSpriteSheet(bitmap);
                mSprites.put(FONT_PATH + path, textureHandle);
            }

            long stopTime = System.nanoTime();
            Log.v(LOG_TAG, "Loaded images in " + TimeUnit.MILLISECONDS.convert(stopTime - startTime, TimeUnit.NANOSECONDS) + " ms");

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void prepareSpriteSheets() {
        mSpriteSheetRenderer = new SpriteSheetRenderer();
        mSpriteSheetRenderer.setShaderProgramHandle(mSpriteShaderProgram);
        mSpriteSheetRenderer.setSpriteSheetHandle(mSprites.get("sprite_sheets/sheet.png"));
        mSpriteSheetRenderer.setUniformscale(ssu);
        mSpriteSheetRenderer.precalculatePositions(mVisibleGridWidth, mVisibleGridHeight);
        mSpriteSheetRenderer.precalculateUv(mSpriteIndexes.size());
    }

    private void prepareText() {
        // Create our text manager
        mTextManager = new TextManager();
        mTextManager.setShaderProgramHandle(mSpriteShaderProgram);
        mTextManager.setTextureID(mSprites.get("fonts/font.png"));
        // mTextManager.setTextureID(mSprites.get("fonts/font.png"));

        // Pass the uniform scale
        mTextManager.setUniformscale(ssu);
    }

    /*
    ---------------------------------------------
      Scaling and scrolling
    ---------------------------------------------
    */

    private void setupMatrixes() {
        // Setup our screen width and height for normal sprite translation.
        Matrix.orthoM(mProjMatrix, 0, 0f, mWidth, 0.0f, mHeight, 0, 50);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mVMatrix, 0,
                0f, 0f, 1f,
                0f, 0f, 0f,
                0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);
    }

    private float mResTargetWidth = 320f;
    private float mResTargetHeight = 480f;

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
        oldOffsetX = mScrollOffset.x();
        oldOffsetY = mScrollOffset.y();
        mScrollOffset = calculateScrollOffset();

        mScrollFrameCount = frameCount / 3;

        // Divide difference by sprite size to find amount that should be scrolled each frame
        mScrollIntervalX = (oldOffsetX - mScrollOffset.x()) / mScrollFrameCount;
        mScrollIntervalY = (oldOffsetY - mScrollOffset.y()) / mScrollFrameCount;

        // Log.v(LOG_TAG, "difference: " + (oldOffsetX - mScrollOffset.x()) + ", " + (oldOffsetY - mScrollOffset.y()));
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

        // For odd grid sizes, player position will already be centred.
        // Otherwise, we have to manually translate the matrix

        if (mVisibleGridWidth % 2 == 0) {
            mTranslationX = -spriteSize / 2;
        }
        if (mVisibleGridHeight % 2 == 0) {
            mTranslationY = -spriteSize / 2;
        }

        Matrix.translateM(mTMatrix, 0, mMVPMatrix, 0, mTranslationX, mTranslationY, 0f);
    }

    private void checkScrollStatus() {
        if ((mScrollIntervalX != 0 || mScrollIntervalY != 0) && mScrollProgress < mScrollFrameCount) {
            mNeedsScroll = true;
            ((MainActivity) mContext).setMoveLock(true);
            mCurrentScrollX += mScrollIntervalX;
            mCurrentScrollY += mScrollIntervalY;
            mScrollProgress++;
        }
        else {
            mNeedsScroll = false;
            ((MainActivity) mContext).setMoveLock(false);
            mCurrentScrollX = 0;
            mCurrentScrollY = 0;
            mScrollIntervalX = 0;
            mScrollIntervalY = 0;
            mScrollProgress = 0;
        }
    }

    private Vector calculateScrollOffset() {
        GameObject player = mFrame.getPlayer();
        int width = Math.min(mVisibleGridWidth, 32);
        int height = Math.min(mVisibleGridHeight, 32);

        return new Vector(player.x() - (width / 2), player.y() - (height / 2));
    }

    /*
    ---------------------------------------------
      Rendering
    ---------------------------------------------
    */

    private void renderScreen() {
        if (!isRendering) {

            isRendering = true;

            if (mNewFrame == null) {
                // First render
                mScrollOffset = calculateScrollOffset();
                oldOffsetX = mScrollOffset.x();
                oldOffsetY = mScrollOffset.y();
                mLightMap = mFrame.getLightMap();
                mFov = mFrame.getFov();

            } else if (mHasNewFrame) {
                // Replace existing frame with new frame
                mFrame = mNewFrame;
                mLightMap = mFrame.getLightMap();
                mFov = mFrame.getFov();
                calculateScroll();

                mHasNewFrame = false;
            }

            checkScrollStatus();

            // Iterate over the data in Frame object and convert to sprite rows for renderer
            getGameObjectSpriteRows();
            getUiSpriteRows();

            // Render everything in single call
            mSpriteSheetRenderer.prepareSprites();
            mSpriteSheetRenderer.renderSprites(mTMatrix);

            // Add our text overlay
            mTextManager.clear();
            mTextManager.addText(new TextObject(mFps + " fps", 10f, 10f));
            mTextManager.prepareText();
            mTextManager.renderText(mMVPMatrix);

            isRendering = false;
        }
    }

    private void getGameObjectSpriteRows() {
        GameObject[][] mapGrid = mFrame.getTerrain();
        ArrayList<GameObject>[][] objectGrid = mFrame.getObjects();
        ArrayList<GameObject>[][] animations = mFrame.getAnimations();

        ArrayList<GameObject> projectedObjects = new ArrayList<>();

        mSpriteSheetRenderer.clear();

        float yUnit = SPRITE_SIZE * ssu;

        for (int y = 0; y < mVisibleGridHeight; y++) {
            SpriteRow spriteRow = new SpriteRow();

            spriteRow.y += (y * yUnit);
            spriteRow.tileY = y;

            ArrayList<Integer> indexes = new ArrayList<>();
            ArrayList<Integer>[] objectIndexArrays = new ArrayList[mVisibleGridWidth];

            for (int x = 0; x < mVisibleGridWidth; x++) {
                // Use scroll offset to find corresponding grid tile for visible area
                int offsetX = x + mScrollOffset.x();
                int offsetY = y + mScrollOffset.y();

                if (!inBounds(offsetX, offsetY)) continue;

                GameObject terrain = mapGrid[offsetX][offsetY];
                spriteRow.lighting.add(getLightingForGrid(offsetX, offsetY));
                indexes.add(mSpriteIndexes.get(terrain.tile()));

                objectIndexArrays[x] = new ArrayList<>();
                ArrayList<GameObject> objectsInCell = objectGrid[offsetX][offsetY];

                for (GameObject object : objectsInCell) {
                    if (object.isProjected()) {
                        // Iterate over these separately. Add a transparent tile for this row otherwise
                        // index count in SpriteSheetRenderer will fail
                        objectIndexArrays[x].add(mSpriteIndexes.get("sprites/transparent.png"));

                        if (mFov[x][y] > 0) {
                            projectedObjects.add(object);
                        }

                    } else {
                        objectIndexArrays[x].add(mSpriteIndexes.get(object.tile()));
                    }
                }

                ArrayList<GameObject> animationsInCell = animations[offsetX][offsetY];

                for (int i = 0; i < animationsInCell.size(); i++) {
                    Animation animation = (Animation) animationsInCell.get(i);
                    int index = processAnimation(animation);
                    objectIndexArrays[x].add(index);
                }

                handleFinishedAnimations(animationsInCell);
            }

            spriteRow.indexes = indexes;
            spriteRow.setObjectArrays(objectIndexArrays);

            mSpriteSheetRenderer.addRow(spriteRow);
        }

        getProjectedObjectRows(projectedObjects);
    }

    private void getProjectedObjectRows(ArrayList<GameObject> projectedObjects) {
        float yUnit = SPRITE_SIZE * ssu;

        for (GameObject object : projectedObjects) {
            int destX = object.getDestX();
            int destY = object.getDestY();
            int x = destX - mScrollOffset.x();
            int y = destY - mScrollOffset.y();

            if (!inVisibleBounds(x, y)) continue;

            // Todo: we could probably find which projected share same row & make this more efficient
            SpriteRow spriteRow = new SpriteRow();

            spriteRow.y += (y * yUnit);
            spriteRow.tileY = y;

            ArrayList<Integer> indexes = spriteRow.getEmptySpriteRow(mVisibleGridWidth);

            indexes.set(x, mSpriteIndexes.get(object.tile()));

            spriteRow.indexes = indexes;
            spriteRow.lighting = spriteRow.getDefaultLighting(mVisibleGridWidth);
            spriteRow.lighting.set(x, getLightingForGrid(destX, destY));

            mSpriteSheetRenderer.addRow(spriteRow);
        }
    }

    private void getUiSpriteRows() {
        float yUnit = SPRITE_SIZE * ssu;

        if (mCurrentPath != null) {
            for (Vector segment : mCurrentPath) {
                Vector adjustedSegment = segment.subtract(mScrollOffset);

                int x = adjustedSegment.x();
                int y = adjustedSegment.y();

                // Todo: we could probably find which path segments share same row & make this more efficient
                SpriteRow spriteRow = new SpriteRow();

                spriteRow.y += (y * yUnit);
                spriteRow.tileY = y;

                ArrayList<Integer> indexes = spriteRow.getEmptySpriteRow(mVisibleGridWidth);

                indexes.set(x, mSpriteIndexes.get("sprites/cursor_default.png"));

                spriteRow.indexes = indexes;
                spriteRow.lighting = spriteRow.getDefaultLighting(mVisibleGridWidth);

                mSpriteSheetRenderer.addRow(spriteRow);
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
        }
    }

    /*
    ---------------------------------------------
      Getters and setters
    ---------------------------------------------
    */

    public Vector getMapGridTouchCoords(float x, float y) {
        return getOnScreenTouchCoords(x, y).add(calculateScrollOffset());
    }

    public Vector getOnScreenTouchCoords(float x, float y) {
        // NOTE: Origin for touch events is top-left, origin for game area is bottom-left.
        int height = ScreenSizeGetter.getHeight();

        float spriteSize = SPRITE_SIZE * mZoom * ssu;

        float correctedY = height - y;

        float gridX = x / spriteSize;
        float gridY = correctedY / spriteSize;

        return new Vector((int) gridX, (int) gridY);
    }

    public void setFrame(Frame frame) {
        if (mFrame == null) {
            mFrame = frame;
            mLightMap = mFrame.getLightMap();
            mFov = mFrame.getFov();
        }
        else {
            mNewFrame = frame;
            mHasNewFrame = true;
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
}
