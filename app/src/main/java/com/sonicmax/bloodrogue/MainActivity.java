package com.sonicmax.bloodrogue;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;

import com.sonicmax.bloodrogue.maths.Vector;
import com.sonicmax.bloodrogue.renderer.GameRenderer;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private GameRenderer mRenderer;
    private GLSurfaceView mGLView;
    private GameEngine mGameEngine;
    private ScaleGestureDetector mScaleDetector;
    private Vector lastMapTouch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mGameEngine = new GameEngine(this);
        mGLView = new GameSurfaceView(this);
        mRenderer.setMapSize(mGameEngine.getMapSize());
        lastMapTouch = null;

        setContentView(mGLView);
        mGameEngine.initState();
        passDataToRenderer();
        initInputSurfaces();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLView.onPause();
    }

    private float mLastTouchX = 0f;
    private float mLastTouchY = 0f;
    private float mScrollPosX = 0f;
    private float mScrollPosY = 0f;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final long PATH_THRESHOLD = 500L;
        float x = e.getX();
        float y = e.getY();

        Vector mapTouch = mRenderer.getGridCellForTouchCoords(x, y);

        if (mLock) return true;

        if (mScaleDetector != null) {
            if (e.getPointerCount() > 1) {
                return mScaleDetector.onTouchEvent(e);
            }
        }

        long eventDuration = e.getEventTime() - e.getDownTime();

        switch(e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start path selection
                lastMapTouch = mapTouch;
                mLastTouchX = x;
                mLastTouchY = y;
                break;

            case MotionEvent.ACTION_MOVE:
                /*if (!mapTouch.equals(lastMapTouch)) {
                    mGameEngine.setPathDestination(mapTouch);
                    ArrayList<Vector> path = mGameEngine.onTouchPathComplete();
                    path.add(mapTouch);
                    mRenderer.setCurrentPathSelection(path);
                }*/

                float dx = mLastTouchX - x;
                float dy = mLastTouchY - y;

                mRenderer.setTouchScrollCoords(dx, dy);

                mLastTouchX = x;
                mLastTouchY = y;
                break;

            case MotionEvent.ACTION_UP:
                mRenderer.setCurrentPathSelection(null);

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

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        private float mScaleFactor = 1f;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.5f, Math.min(mScaleFactor, 2.0f));
            mRenderer.setZoom(mScaleFactor);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            // mRenderer.calculateGridSize();
        }
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch(keycode) {
            case KeyEvent.KEYCODE_MENU:
                return true;
        }

        return super.onKeyDown(keycode, e);
    }


    /**
     *  Define surface view here so we can keep a reference to GameRenderer in MainActivity
     */

    class GameSurfaceView extends GLSurfaceView {

        public GameSurfaceView(Context context) {
            super(context);

            // Create an OpenGL ES 2.0 context and set renderer
            setEGLContextClientVersion(2);
            mRenderer = new GameRenderer(context, this);
            setRenderer(mRenderer);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
    }

    private void initInputSurfaces() {
        mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    /*
    ---------------------------------------------
     Game loop
    ---------------------------------------------
    */

    public void passDataToRenderer() {
        mRenderer.setFrame(mGameEngine.getFrame());
        mRenderer.setHasGameData();
    }

    private boolean mLock;

    public void setMoveLock(boolean value) {
        mLock = value;
    }

    public void updateScrollPos(float x, float y) {
        mScrollPosX = x;
        mScrollPosY = y;
    }
}
