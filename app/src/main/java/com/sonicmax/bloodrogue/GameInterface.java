package com.sonicmax.bloodrogue;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;

import com.sonicmax.bloodrogue.maths.Vector;
import com.sonicmax.bloodrogue.renderer.GameRenderer;
import com.sonicmax.bloodrogue.renderer.GameSurfaceView;
import com.sonicmax.bloodrogue.text.NarrationManager;

import java.util.ArrayList;

/**
 * Links together the different components of the engine
 */

public class GameInterface {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private Context mContext;
    private GameRenderer mRenderer;
    private GameEngine mGameEngine;
    private NarrationManager mNarrationManager;

    // User input
    private Vector mLastMapTouch;
    private boolean mInputLock;
    private float mLastTouchX = 0f;
    private float mLastTouchY = 0f;
    private float mScaleFactor = 1f;
    private boolean mPathSelection = false;

    // Todo: MainActivity should be used strictly to handle Android stuff and the game logic should be moved to a new class

    public GameInterface(Context context) {
        mContext = context;
        mGameEngine = new GameEngine(this);
        mRenderer = new GameRenderer(this);
        mNarrationManager = new NarrationManager();
        mRenderer.setMapSize(mGameEngine.getMapSize());
        mLastMapTouch = null;
    }

    public void init() {
        mGameEngine.initState();
        passDataToRenderer();
    }

    public GameRenderer getRenderer() {
        return this.mRenderer;
    }

    public boolean handleTouchEvent(MotionEvent e) {
        final long PATH_THRESHOLD = 500L;
        float x = e.getX();
        float y = e.getY();

        Vector mapTouch = mRenderer.getGridCellForTouchCoords(x, y);

        long eventDuration = e.getEventTime() - e.getDownTime();

        switch(e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX = x;
                mLastTouchY = y;

                if (!mInputLock && mapTouch.equals(mGameEngine.getPlayer().getVector())) {
                    // Start path selection
                    mLastMapTouch = mapTouch;
                    mPathSelection = true;
                }

                break;

            case MotionEvent.ACTION_MOVE:
                if (!mInputLock && mPathSelection && !mapTouch.equals(mLastMapTouch)) {
                    mGameEngine.setPathDestination(mapTouch);
                    ArrayList<Vector> path = mGameEngine.onTouchPathComplete();
                    path.add(mapTouch);
                    mRenderer.setCurrentPathSelection(path);
                    break;
                }

                else if (!mPathSelection) {
                    Log.v(LOG_TAG, "scrolling");
                    float dx = mLastTouchX - x;
                    float dy = mLastTouchY - y;

                    mRenderer.setTouchScrollCoords(dx, dy);

                    mLastTouchX = x;
                    mLastTouchY = y;
                }

                break;

            case MotionEvent.ACTION_UP:
                if (mInputLock) break;

                mRenderer.setCurrentPathSelection(null);
                mPathSelection = false;

                if (eventDuration > PATH_THRESHOLD) {
                    ArrayList<Vector> path = mGameEngine.onTouchPathComplete();
                    // Todo: if square is adjacent then we should just move to it
                    if (path.size() > 0) {
                        path.add(mapTouch);
                        mGameEngine.queueAndFollowPath(path);
                    }
                }

                else {
                    mGameEngine.checkUserInput(mapTouch);
                    passDataToRenderer();
                }

                break;
        }

        return true;
    }

    public boolean handleScaleEvent(ScaleGestureDetector detector) {
        mScaleFactor *= detector.getScaleFactor();

        // Don't let the object get too small or too large.
        mScaleFactor = Math.max(0.5f, Math.min(mScaleFactor, 2.0f));
        mRenderer.setZoom(mScaleFactor);
        return true;
    }

    public void passDataToRenderer() {
        mRenderer.setFrame(mGameEngine.getFrame());
        mRenderer.setHasGameData();
    }

    public void setMoveLock(boolean value) {
        mInputLock = value;
    }

    public void addNarration(String narration) {
        mNarrationManager.addToQueue(narration);
    }

    public void checkNarrations() {
        mNarrationManager.checkQueueAndRemove();
        mRenderer.setNarrations(mNarrationManager.getTextObjects());
    }

    public AssetManager getAssets() {
        return mContext.getAssets();
    }
}
