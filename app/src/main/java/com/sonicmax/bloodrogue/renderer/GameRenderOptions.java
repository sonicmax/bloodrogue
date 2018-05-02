package com.sonicmax.bloodrogue.renderer;

/**
 * Class which reads graphics options from config file and stores various other flags that
 * will define how renderer behaves in various situations
 */

public class GameRenderOptions {
    // Values which define various OpenGL features that we may require
    private boolean hasDepthTextureExtension;
    private int maxTextureUnits;

    // Values used to determine renderer features (draw distance, shadows, etc)
    private float shadowVisiblity;
    private float fov;

    private String glExtensions;

    public GameRenderOptions() {
        // Set some default values for use in renderer
        shadowVisiblity = 100f;
        fov = 80f;

        // Todo: save/load config
    }

    public void setHasDepthTex(boolean value) {
        hasDepthTextureExtension = value;
    }

    public void setMaxTextureUnits(int value) {
        maxTextureUnits = value;
    }

    public void setGlExtensions(String extensions) {
        glExtensions = extensions;
    }

    public float getFov() {
        return fov;
    }

    public float getShadowVisiblity() {
        return shadowVisiblity;
    }
}
