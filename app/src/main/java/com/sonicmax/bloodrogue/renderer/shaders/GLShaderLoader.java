package com.sonicmax.bloodrogue.renderer.shaders;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Compiles renderState code and provides reference to program object stored
 */

public class GLShaderLoader {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final String COLOUR_VERT_PATH = "shaders/solid_colour.vert";
    private final String BASIC_VERT_PATH = "shaders/basic.vert";
    private final String WAVE_VERT_PATH = "shaders/wave.vert";

    private final String COLOUR_FRAG_PATH = "shaders/solid_colour.frag";
    private final String BASIC_FRAG_PATH = "shaders/basic.frag";
    private final String NOISE_FRAG_PATH = "shaders/perlin_noise.frag";

    private final String WATER_RIPPLE_FRAG = "shaders/water_ripple.frag";
    private final String RIPPLE_FRAG_PATH = "shaders/ripple.frag";

    private final String RAIN_FRAG_PATH = "shaders/rain.frag";
    private final String FOG_FRAG_PATH = "shaders/fog.frag";
    private final String SNOW_FRAG_PATH = "shaders/snow.frag";

    private final String CUBE_VERT_PATH = "shaders/cube.vert";
    private final String CUBE_FRAG_PATH = "shaders/cube.frag";
    private final String MOON_FRAG_PATH = "shaders/moon.frag";
    private final String BILLBOARD_SPRITE_VERT_PATH = "shaders/billboard_sprite.vert";

    private final String DEPTH_MAP_VERT_PATH = "shaders/depth_map.vert";
    private final String DEPTH_MAP_FRAG_PATH = "shaders/depth_map.frag";

    private final String SKY_BOX_VERT_PATH = "shaders/sky_box.vert";
    private final String SKY_BOX_FRAG_PATH = "shaders/sky_box.frag";

    private final String WATER_VERT_PATH = "shaders/water.vert";
    private final String WATER_FRAG_PATH = "shaders/water.frag";

    // For debugging:

    // Renders lines to screen. Used for debugging surface normals
    private final String DEBUG_LINE_VERT_PATH = "shaders/debug_line.vert";
    private final String DEBUG_LINE_FRAG_PATH = "shaders/debug_line.frag";

    // Renders depth map as texture to quad. Used to debug our depth map
    private final String DEBUG_DEPTH_VERT_PATH = "shaders/debug_depth.vert";
    private final String DEBUG_DEPTH_FRAG_PATH = "shaders/debug_depth.frag";

    // Renders texture bound to texture unit to quad. Used to debug framebuffers
    private final String DEBUG_TEX_VERT_PATH = "shaders/debug_tex.vert";
    private final String DEBUG_TEX_FRAG_PATH = "shaders/debug_tex.frag";

    private Context context;

    public GLShaderLoader(Context context) {
        this.context = context;
    }

    public int compileSolidColourShader() {
        return compileShader(COLOUR_VERT_PATH, COLOUR_FRAG_PATH);
    }

    public int compileSpriteShader() {
        return compileShader(BASIC_VERT_PATH, BASIC_FRAG_PATH);
    }

    public int compileRainShader() {
        return compileShader(BASIC_VERT_PATH, RAIN_FRAG_PATH);
    }

    public int compileSnowShader() {
        return compileShader(BASIC_VERT_PATH, SNOW_FRAG_PATH);
    }

    public int compileCubeShader() {
        return compileShader(CUBE_VERT_PATH, CUBE_FRAG_PATH);
    }

    public int compileMoonShader() {
        return compileShader(BILLBOARD_SPRITE_VERT_PATH, MOON_FRAG_PATH);
    }

    public int compileBillboardShader() {
        return compileShader(BILLBOARD_SPRITE_VERT_PATH, CUBE_FRAG_PATH);
    }

    public int compileDebugTexShader() {
        return compileShader(DEBUG_TEX_VERT_PATH, DEBUG_TEX_FRAG_PATH);
    }

    public int compileDebugDepthMapShader() {
        return compileShader(DEBUG_DEPTH_VERT_PATH, DEBUG_DEPTH_FRAG_PATH);
    }

    public int compileDepthMapShader() {
        return compileShader(DEPTH_MAP_VERT_PATH, DEPTH_MAP_FRAG_PATH);
    }

    public int compileSkyBoxShader() {
        return compileShader(SKY_BOX_VERT_PATH, SKY_BOX_FRAG_PATH);
    }

    public int compileWaterShader() {
        return compileShader(WATER_VERT_PATH, WATER_FRAG_PATH);
    }

    public int compileDebugLineShader() {
        return compileShader(DEBUG_LINE_VERT_PATH, DEBUG_LINE_FRAG_PATH);
    }

    private int compileShader(String vertPath, String fragPath) {
        try {
            InputStream inputStream = context.getAssets().open(vertPath);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(fragPath);
            String fragCode = readStringFromStream(inputStream);
            inputStream.close();

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragCode);

            int shaderHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(shaderHandle, vertexShader);
            GLES20.glAttachShader(shaderHandle, fragmentShader);

            // These attributes are not used in all shaders - be careful when binding vertex attribute arrays
            GLES20.glBindAttribLocation(shaderHandle, ShaderAttributes.POSITION, "a_Position");
            GLES20.glBindAttribLocation(shaderHandle, ShaderAttributes.SHADOW_POSITION, "a_ShadowPosition");
            GLES20.glBindAttribLocation(shaderHandle, ShaderAttributes.COLOUR, "a_Color");
            GLES20.glBindAttribLocation(shaderHandle, ShaderAttributes.TEXCOORD, "a_texCoord");
            GLES20.glBindAttribLocation(shaderHandle, ShaderAttributes.NORMAL, "a_Normal");
            GLES20.glBindAttribLocation(shaderHandle, ShaderAttributes.BILLBOARD_DATA, "a_BillboardData");

            GLES20.glLinkProgram(shaderHandle);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(shaderHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(shaderHandle);
                throw new RuntimeException("Could not link program: " + GLES20.glGetProgramInfoLog(shaderHandle));
            }

            return shaderHandle;


        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while reading shader source from disk:", e);
            throw new RuntimeException("Could not link program: " + vertPath + ", " + fragPath);
        }
    }

    /**
     * Compiles provided source code for renderState and returns reference int
     *
     * @param type Must be GLES20.GL_VERTEX_SHADER OR GLES20.GL_FRAGMENT_SHADER
     * @param source Code for shader
     * @return
     */

    public static int loadShader(int type, String source) throws RuntimeException {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Could not compile program: "
                    + GLES20.glGetShaderInfoLog(shader) + " | " + source);
        }

        return shader;
    }

    private String readStringFromStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder stringBuilder = new StringBuilder();

        String line;
        String newline = "\n"; // Todo: use System.getProperty("line.separator") instead?

        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);

            // Note: if we don't add newlines, then shaders with double slash style comments will break
            // (as the specification requires a newline after the double slash).
            stringBuilder.append(newline);
        }

        reader.close();

        return stringBuilder.toString();
    }
}
