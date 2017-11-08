package com.sonicmax.bloodrogue.renderer;

/**
 * Container for shader attribute indexes.
 * Use glBindAttribLocation() to bind to these constants after compiling shader (but before linking)
 */

public class Shader {

    // Vert
    public final static int POSITION = 1; // attribute vec4 a_Position;
    public final static int COLOUR = 2; // attribute vec4 a_Color;
    public final static int TEXCOORD = 3; // attribute vec2 a_texCoord;

    // Frag
}
