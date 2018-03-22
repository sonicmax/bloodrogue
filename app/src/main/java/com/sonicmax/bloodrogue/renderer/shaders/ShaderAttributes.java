package com.sonicmax.bloodrogue.renderer.shaders;

/**
 * Container for shader attribute indexes.
 * Use glBindAttribLocation() to bind to these constants after compiling shader (but before linking)
 */

public class ShaderAttributes {

    // Vert
    public final static int POSITION = 1; // attribute vec4 a_Position;
    public final static int COLOUR = 2; // attribute vec4 a_Color;
    public final static int TEXCOORD = 3; // attribute vec2 a_texCoord;
    public final static int NORMAL = 4; // attribute vec3 a_Normal;
    public final static int SHADOW_POSITION = 5; // attribute vec4 a_ShadowPosition;

    // Frag
}
