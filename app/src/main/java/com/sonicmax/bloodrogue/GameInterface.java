package com.sonicmax.bloodrogue;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.sonicmax.bloodrogue.audio.FxFilePaths;
import com.sonicmax.bloodrogue.audio.MusicFilePaths;
import com.sonicmax.bloodrogue.audio.AudioPlayer;
import com.sonicmax.bloodrogue.engine.GameEngine;
import com.sonicmax.bloodrogue.engine.GameState;
import com.sonicmax.bloodrogue.engine.components.Position;
import com.sonicmax.bloodrogue.renderer.GameRenderer;
import com.sonicmax.bloodrogue.renderer.text.NarrationManager;
import com.sonicmax.bloodrogue.renderer.text.Status;
import com.sonicmax.bloodrogue.renderer.ui.InventoryCard;
import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

/**
 * Class which links together the different parts of the engine and handles user input/other Android events.
 */

public class GameInterface {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private Context context;
    private AudioPlayer audioPlayer;
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

    // Game state
    private boolean waitingForMenuInput;
    private boolean inGame;

    private Handler handler;

    private boolean startFresh = true; // This is just for debugging lol

    public GameInterface(Context context) {
        this.context = context;

        // Initialise game components.
        this.gameEngine = new GameEngine(this);
        this.audioPlayer = new AudioPlayer(context);
        this.gameRenderer = new GameRenderer(context, this);
        this.narrationManager = new NarrationManager();
        this.gameRenderer.setMapSize(gameEngine.getMapSize());

        // Set some variables required for UI interactions
        this.lastTouchX = 0f;
        this.lastTouchY = 0f;
        this.pathSelection = false;
        this.lastMapTouch = null;
        this.scaleFactor = 1f;

        this.waitingForMenuInput = false;
        this.inGame = false;

        this.handler = new Handler();
    }

    public void showTitle() {
        gameRenderer.setRenderState(GameRenderer.TITLE);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                openMainMenu();
            }
        }, 2000);
    }

    public void openMainMenu() {
        gameRenderer.setRenderState(GameRenderer.MENU);

        waitingForMenuInput = true;
    }

    public void handleMenuInput() {
        waitingForMenuInput = false;
        gameRenderer.setRenderState(GameRenderer.SPLASH);

        // Note: startGame() is an expensive method call and should be executed in background thread

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                startGame();
                audioPlayer.startNewMusicLoop(MusicFilePaths.TRACK_01);
                passDataToRenderer();
                transitionToNewContent();
                inGame = true;
            }
        });
    }

    public AssetManager getAssets() {
        return context.getAssets();
    }

    public void startGame() {
        GameState state = loadState();

        if (state == null || startFresh) {
            Log.v(LOG_TAG, "Load failed or no save state exists");
            gameEngine.startNewGame();
        }
        else {
            Log.v(LOG_TAG, "Load successful");
            gameEngine.restoreGameState(state);
        }
    }

    /**
     * Saves game state to disk. Currently the component classes are serializable, so we
     * can just use an ObjectOutputStream to write the GameState to disk. Ideally we would be using
     * something like protobuf instead.
     */

    public void saveState(GameState state) {
        String FILENAME = "save_data.sav";

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;

        try {
            File file = new File(context.getFilesDir(), FILENAME);
            file.createNewFile();
            fos = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(state);

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing to disk", e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (oos != null) {
                    oos.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error closing output streams", e);
            }
        }
    }

    public void saveState() {
        saveState(gameEngine.getGameState());
    }

    /**
     * Attempts to load saved state from disk and returns GameState object (if no exceptions were thrown)
     *
     * @return GameState that was saved to disk
     */

    private GameState loadState() {
        GameState state = null;
        String FILENAME = "save_data.sav";

        FileInputStream fis = null;
        ObjectInputStream ois = null;

        try {
            fis = context.openFileInput(FILENAME);
            ois = new ObjectInputStream(fis);
            state = (GameState) ois.readObject();

        } catch (FileNotFoundException notFound) {
            // We don't really have to do anything here apart from notify the user?
            Log.e(LOG_TAG, "Save file not found", notFound);

        } catch (ClassNotFoundException e1) {
            Log.e(LOG_TAG, "Error while loading state", e1);
            File file = new File(context.getFilesDir(), FILENAME);
            file.delete();

        } catch (IOException e2) {
            Log.e(LOG_TAG, "Error while loading from disk", e2);

        } catch (IllegalArgumentException e3) {
            // Probably made a change to the GameState class without incrementing serialVersionUID.
            Log.e(LOG_TAG, "Error while loading state", e3);
            File file = new File(context.getFilesDir(), FILENAME);
            file.delete();

        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error closing output streams", e);
            }
        }

        return state;
    }

    /**
     * Receives touch events from MainActivity and passes coordinates to engine/renderer
     *
     * @param e The event passed to onTouchEvent() of MainActivity
     * @return true if event is consumed
     */

    public boolean handleTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        long eventDuration = e.getEventTime() - e.getDownTime();

        switch(e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleTouchDown(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                handleTouchMove(x, y, eventDuration);
                break;

            case MotionEvent.ACTION_UP:
                if (inputLock) break;
                handleTouchUp(x, y, eventDuration);
                break;
        }

        return true;
    }

    /**
     * ACTION_DOWN events indicate that user has started to interact with the game. We keep track
     * of the starting coordinates and set some flags depending on user intent.
     *
     * @param x
     * @param y
     */

    private void handleTouchDown(float x, float y) {
        if (inGame) {
            Vector mapTouch = gameRenderer.getGridCellForTouchCoords(x, y);

            lastTouchX = x;
            lastTouchY = y;

            if (!inputLock && mapTouch.equals(gameEngine.getPlayerVector())) {
                // Start path selection
                lastMapTouch = mapTouch;
                pathSelection = true;
            }
        }
        else {

        }
    }

    /**
     * ACTION_MOVE events are used to scroll the game window and update current grid selection.
     *
     * @param x
     * @param y
     * @param duration Duration of touch event
     */

    private void handleTouchMove(float x, float y, long duration) {
        if (inGame) {
            final long SCROLL_THRESHOLD = 100L;  // Number of milliseconds to wait before scrolling

            // If player is currently selecting a path, we should update the path destination with current position
            if (!inputLock && pathSelection) {
                Vector mapTouch = gameRenderer.getGridCellForTouchCoords(x, y);

                if (!mapTouch.equals(lastMapTouch)) {
                    gameEngine.setPathDestination(mapTouch);
                    ArrayList<Vector> path = gameEngine.onTouchPathComplete();
                    path.add(mapTouch);
                    gameRenderer.setCurrentPathSelection(path);
                }
            }

            // If player isn't selecting a path, we should wait for at least 50ms before scrolling (otherwise
            // you get jerky screen movement when tapping squares to move player/attack enemies/etc)
            else if (duration > SCROLL_THRESHOLD) {
                float dx = lastTouchX - x;
                float dy = lastTouchY - y;

                gameRenderer.setTouchScrollCoords(dx, dy);

                lastTouchX = x;
                lastTouchY = y;
                lastMapTouch = null;
            }
        }

        else {

        }
    }

    /**
     * ACTION_UP events indicate that current action (press/drag/etc) is finished.
     * As well as coordinates, this method requires the event duration so we can do some basic
     * filtering (eg. for intentional press vs accidental taps).
     *
     * @param x
     * @param y
     * @param eventDuration Duration of event in milliseconds
     */

    private void handleTouchUp(float x, float y, long eventDuration) {
        if (inGame) {
            final long PATH_THRESHOLD = 500L; // Amount of time before we start displaying path selection nodes
            final Vector mapTouch = gameRenderer.getGridCellForTouchCoords(x, y);

            // Reset path selection and scrolling.
            gameRenderer.setCurrentPathSelection(null);
            pathSelection = false;

            if (eventDuration < PATH_THRESHOLD) {
                // First, check whether player touched a UI element.
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
            } else {
                final ArrayList<Vector> path = gameEngine.onTouchPathComplete();
                // Todo: if square is adjacent then we should just move to it
                    /*if (path.size() > 0) {
                        path.add(mapTouch);

                        // Execute in background thread to prevent queueAndFollowPath() from blocking touch events
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                gameEngine.queueAndFollowPath(path);
                            }
                        });
                    }*/
            }
        }

        else {
            if (waitingForMenuInput) {
                handleMenuInput();
            }
        }
    }

    public boolean handleScaleBegin(ScaleGestureDetector detector) {
        gameRenderer.startZoom(detector);
        return true;
    }

    /**
     * Passes scale gestures to renderer to we can set zoom level.
     *
     * @param detector Event from onScale() in MainActivity
     * @return True if event was consumed
     */

    public boolean handleScaleChange(ScaleGestureDetector detector) {
        scaleFactor *= detector.getScaleFactor();

        // Don't let the object get too small or too large.
        scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 2.0f));
        gameRenderer.setZoom(scaleFactor);
        return true;
    }

    public void handleScaleEnd(ScaleGestureDetector detector) {
        gameRenderer.endZoom();
    }

    public long processInventoryClick(int index) {
        return gameEngine.getInventoryEntity(index);
    }

    public void handleInventorySelection(long entity, boolean ok) {
        if (ok) {
            gameEngine.useEntity(entity);
        }
        else {
            gameEngine.unequipEntity(entity);
        }
    }

    public InventoryCard getEntityDetails(long entity) {
        return gameEngine.getEntityDetails(entity);
    }

    public void passDataToRenderer() {
        gameRenderer.setFrame(gameEngine.getCurrentFrameData());
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

    public void displayStatus(Position position, String message, float[] color) {
        Vector vector = new Vector(position.x, position.y);
        float[] coords = gameRenderer.getRenderCoordsForObject(vector, true);
        Status status = new Status(message, coords[0], coords[1], color);
        gameRenderer.queueNewStatus(status);
    }

    public void startFloorChange() {
        gameRenderer.fadeOutAndDisplaySplash();
        narrationManager.clearAll();
    }

    public void transitionToNewContent() {
        gameRenderer.startNewFloor();
    }

    public void freeResources() {
        gameRenderer.freeBuffers();
    }

    public void haltAudio() {
        audioPlayer.stopAndReleaseResources();
    }

    public void triggerSoundEffect(String fx) {
        audioPlayer.playSound(fx);
    }
}
