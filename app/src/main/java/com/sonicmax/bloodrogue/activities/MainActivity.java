package com.sonicmax.bloodrogue.activities;

import android.media.AudioManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManager;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.renderer.GameSurfaceView;

/**
 * The launch activity for the game. Doesn't do much - just sets up our views and input surfaces
 */

public class MainActivity extends AppCompatActivity {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private Thread.UncaughtExceptionHandler defaultUEH;
    private GameSurfaceView gameSurfaceView;
    private GameInterface gameInterface;
    private ScaleGestureDetector scaleDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Listen for phone state changes
        /*TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        if (mgr != null) {
            mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }*/

        // GameInterface initialises all the required components. Once we've set the content view
        // we can generate game data (or load from disk) and start accepting player input.

        gameInterface = new GameInterface(this);
        gameSurfaceView = new GameSurfaceView(this);

        // Second, set custom UncaughtExceptionHandler
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(mCaughtExceptionHandler);

        setContentView(gameSurfaceView);
        initInputSurfaces();
        gameInterface.showTitle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameSurfaceView.onPause();
        gameInterface.saveState();
        gameInterface.haltAudio();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gameInterface.haltAudio();
        gameInterface.freeResources();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() > 1) {
            return scaleDetector.onTouchEvent(e);
        }

        else {
            return gameInterface.handleTouchEvent(e);
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
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return gameInterface.handleScaleBegin(detector);
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return gameInterface.handleScaleChange(detector);
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            gameInterface.handleScaleEnd(detector);
        }
    }


    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    break;

                case TelephonyManager.CALL_STATE_OFFHOOK:
                    break;

                case TelephonyManager.CALL_STATE_IDLE:
                    break;
            }

            super.onCallStateChanged(state, incomingNumber);
        }
    };

    private Thread.UncaughtExceptionHandler mCaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            Log.v(LOG_TAG, "Uncaught exception");
            gameInterface.freeResources();
            defaultUEH.uncaughtException(thread, ex);
        }
    };
}
