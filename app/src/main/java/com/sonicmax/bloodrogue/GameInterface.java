package com.sonicmax.bloodrogue;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
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
 * Class which links together the different parts of the engine and handles user input/other Android events.
 * GameEngine holds the terrain/object data and handles game logic.
 * Each time player takes a turn, updated data is passed to GameRenderer to be drawn to GL surface.
 */

public class GameInterface {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private Context context;
    private GameRenderer gameRenderer;
    private GameEngine gameEngine;
    private NarrationManager narrationManager;

    // User input
    private Vector lastMapTouch;
    private boolean inputLock;
    private float lastTouchX;
    private float lastTouchY;
    private float scaleFactor;
    private boolean pathSelection;

    public GameInterface(Context context) {
        this.context = context;
        this.gameEngine = new GameEngine(this);
        this.gameRenderer = new GameRenderer(context, this);
        this.narrationManager = new NarrationManager();
        this.gameRenderer.setMapSize(gameEngine.getMapSize());

        this.lastTouchX = 0f;
        this.lastTouchY = 0f;
        this.pathSelection = false;
        this.lastMapTouch = null;
        this.scaleFactor = 1f;
    }

    public void init() {
        gameEngine.initState();
        passDataToRenderer();
    }

    public GameRenderer getRenderer() {
        return this.gameRenderer;
    }

    public boolean handleTouchEvent(MotionEvent e) {
        final long PATH_THRESHOLD = 500L;
        float x = e.getX();
        float y = e.getY();

        final Vector mapTouch = gameRenderer.getGridCellForTouchCoords(x, y);

        long eventDuration = e.getEventTime() - e.getDownTime();

        switch(e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;

                if (!inputLock && mapTouch.equals(gameEngine.getPlayer().getVector())) {
                    // Start path selection
                    lastMapTouch = mapTouch;
                    pathSelection = true;
                }

                break;

            case MotionEvent.ACTION_MOVE:
                if (!inputLock && pathSelection) {
                    if (!mapTouch.equals(lastMapTouch)) {
                        gameEngine.setPathDestination(mapTouch);
                        ArrayList<Vector> path = gameEngine.onTouchPathComplete();
                        path.add(mapTouch);
                        gameRenderer.setCurrentPathSelection(path);
                        break;
                    }
                }

                else {
                    float dx = lastTouchX - x;
                    float dy = lastTouchY - y;

                    gameRenderer.setTouchScrollCoords(dx, dy);

                    lastTouchX = x;
                    lastTouchY = y;
                    lastMapTouch = null;
                }

                break;

            case MotionEvent.ACTION_UP:
                if (inputLock) break;

                gameRenderer.setCurrentPathSelection(null);
                pathSelection = false;

                if (eventDuration > PATH_THRESHOLD) {
                    final ArrayList<Vector> path = gameEngine.onTouchPathComplete();
                    // Todo: if square is adjacent then we should just move to it
                    if (path.size() > 0) {
                        path.add(mapTouch);

                        // Execute in background thread to prevent queueAndFollowPath() from blocking touch events
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                gameEngine.queueAndFollowPath(path);
                            }
                        });
                    }
                }

                else {
                    // Check whether player touched a UI element.
                    boolean touchCaptured = gameRenderer.checkUiTouch(x, y);

                    // If touch event wasn't consumed by renderer, pass to engine
                    if (!touchCaptured) {
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                gameEngine.checkUserInput(mapTouch);
                            }
                        });
                    }
                }

                break;
        }

        return true;
    }

    public boolean handleScaleEvent(ScaleGestureDetector detector) {
        scaleFactor *= detector.getScaleFactor();

        // Don't let the object get too small or too large.
        scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 2.0f));
        gameRenderer.setZoom(scaleFactor);
        return true;
    }

    public void passDataToRenderer() {
        gameRenderer.setFrame(gameEngine.getFrame());
        gameRenderer.setHasGameData();
    }

    public void setMoveLock(boolean value) {
        inputLock = value;
    }

    public void addNarration(String narration) {
        narrationManager.addToQueue(narration);
    }

    public void addNarration(String narration, float[] colour) {
        narrationManager.addToQueue(narration, colour);
    }

    public void checkNarrations() {
        narrationManager.checkQueueAndRemove();
        gameRenderer.queueNarrationUpdate(narrationManager.getTextObjects());
    }

    public AssetManager getAssets() {
        return context.getAssets();
    }

    public void displayStatus(GameObject object, String message, float[] color) {
        float[] coords = gameRenderer.getRenderCoordsForObject(object.getVector());
        Status status = new Status(message, coords[0], coords[1], color);
        gameRenderer.queueNewStatus(status);
    }

    public void initFloorChange() {
        gameRenderer.fadeOutAndDisplaySplash();
        narrationManager.clearAll();
    }

    public void startNewFloor() {
        gameRenderer.startNewFloor();
    }

}
