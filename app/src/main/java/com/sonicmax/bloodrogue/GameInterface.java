package com.sonicmax.bloodrogue;

import android.content.Context;
import android.content.res.AssetManager;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.sonicmax.bloodrogue.engine.GameEngine;
import com.sonicmax.bloodrogue.utils.maths.Vector;
import com.sonicmax.bloodrogue.engine.objects.GameObject;
import com.sonicmax.bloodrogue.renderer.GameRenderer;
import com.sonicmax.bloodrogue.renderer.text.NarrationManager;
import com.sonicmax.bloodrogue.renderer.text.Status;

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
                    final ArrayList<Vector> path = mGameEngine.onTouchPathComplete();
                    // Todo: if square is adjacent then we should just move to it
                    if (path.size() > 0) {
                        path.add(mapTouch);

                        // Execute in background thread to prevent queueAndFollowPath() from blocking touch events
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                        mGameEngine.queueAndFollowPath(path);
                    }
                        });
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

    public void addNarration(String narration, float[] colour) {
        mNarrationManager.addToQueue(narration, colour);
    }

    public void checkNarrations() {
        mNarrationManager.checkQueueAndRemove();
        mRenderer.queueNarrationUpdate(mNarrationManager.getTextObjects());
    }

    public AssetManager getAssets() {
        return mContext.getAssets();
    }

    public void displayStatus(GameObject object, String message, float[] color) {
        float[] coords = mRenderer.getRenderCoordsForObject(object.getVector());
        Log.v(LOG_TAG, "coords: " + coords[0] + ", " + coords[1]);
        Status status = new Status(message, coords[0], coords[1], color);
        mRenderer.queueNewStatus(status);
    }
}
