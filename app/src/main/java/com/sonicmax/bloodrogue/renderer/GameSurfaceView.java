package com.sonicmax.bloodrogue.renderer;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class GameSurfaceView extends GLSurfaceView {
    public GameSurfaceView(Context context, GameRenderer gameRenderer) {
        super(context);
        // Create an OpenGL ES 2.0 context and set renderer
        setEGLContextClientVersion(2);
        setRenderer(gameRenderer);
        setRenderMode(GameSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
}
