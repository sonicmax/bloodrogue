package com.sonicmax.bloodrogue.renderer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class GameSurfaceView extends GLSurfaceView {
    private GameRenderer3D renderer;

    public GameSurfaceView(Context context) {
        super(context);
    }

    public void setRenderer(GameRenderer3D renderer) {
        this.renderer = renderer;
        super.setRenderer(renderer);
    }
}
