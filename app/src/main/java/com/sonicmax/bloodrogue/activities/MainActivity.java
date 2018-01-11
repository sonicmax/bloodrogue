package com.sonicmax.bloodrogue.activities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.renderer.GameSurfaceView;

public class MainActivity extends AppCompatActivity {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private GameSurfaceView mGLView;
    private GameInterface mGameInterface;
    private ScaleGestureDetector mScaleDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mGameInterface = new GameInterface(this);
        mGLView = new GameSurfaceView(this);
        setContentView(mGLView);
        mGameInterface.initState();
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
        mGameInterface.saveState();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mScaleDetector != null) {
            if (e.getPointerCount() > 1) {
                return mScaleDetector.onTouchEvent(e);
            }
        }

        return mGameInterface.handleTouchEvent(e);
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        private float mScaleFactor = 1f;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return mGameInterface.handleScaleEvent(detector);
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

    private void initInputSurfaces() {
        mScaleDetector = null;
        // mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());
    }
}
