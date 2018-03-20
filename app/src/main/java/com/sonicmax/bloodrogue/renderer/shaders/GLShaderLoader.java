package com.sonicmax.bloodrogue.renderer.shaders;

import android.content.Context;
import android.opengl.GLES20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Compiles renderState code and provides reference to program object stored
 */

public class GLShaderLoader {
    private final String COLOUR_VERT_PATH = "shaders/solid_colour.vert";
    private final String BASIC_VERT_PATH = "shaders/basic.vert";
    private final String WAVE_VERT_PATH = "shaders/wave.vert";

    private final String COLOUR_FRAG_PATH = "shaders/solid_colour.frag";
    private final String BASIC_FRAG_PATH = "shaders/basic.frag";
    private final String NOISE_FRAG_PATH = "shaders/perlin_noise.frag";

    private final String WATER_RIPPLE_FRAG = "shaders/water_ripple.frag";
    private final String RIPPLE_FRAG_PATH = "shaders/ripple.frag";

    // Shaders for overlay effects
    private final String RAIN_FRAG_PATH = "shaders/rain.frag";
    private final String FOG_FRAG_PATH = "shaders/fog.frag";
    private final String SNOW_FRAG_PATH = "shaders/snow.frag";

    private final String CUBE_VERT_PATH = "shaders/cube.vert";
    private final String CUBE_FRAG_PATH = "shaders/cube.frag";

    private final String DEPTH_MAP_VERT_PATH = "shaders/depth_map.vert";
    private final String DEPTH_MAP_FRAG_PATH = "shaders/depth_map.frag";

    private final String DEBUG_DEPTH_VERT_PATH = "shaders/debug_depth.vert";
    private final String DEBUG_DEPTH_FRAG_PATH = "shaders/debug_depth.frag";

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

    public int compileWaveShader() {
        return compileShader(BASIC_VERT_PATH, WATER_RIPPLE_FRAG);
    }

    public int compileRainShader() {
        return compileShader(BASIC_VERT_PATH, RAIN_FRAG_PATH);
    }

    public int compileFogShader() {
        return compileShader(BASIC_VERT_PATH, FOG_FRAG_PATH);
    }

    public int compileSnowShader() {
        return compileShader(BASIC_VERT_PATH, SNOW_FRAG_PATH);
    }

    public int compileCubeShader() {
        try {
            InputStream inputStream = context.getAssets().open(CUBE_VERT_PATH);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(CUBE_FRAG_PATH);
            String fragCode = readStringFromStream(inputStream);
            inputStream.close();

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragCode);

            int spriteShaderHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(spriteShaderHandle, vertexShader);
            GLES20.glAttachShader(spriteShaderHandle, fragmentShader);

            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.POSITION, "a_Position");
            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.COLOUR, "a_Color");
            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.TEXCOORD, "a_texCoord");
            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.NORMAL, "a_Normal");

            GLES20.glLinkProgram(spriteShaderHandle);

            return spriteShaderHandle;


        } catch (IOException e) {
            // Todo: static string fallback?
            return 0;
        }
    }

    public int compileDebugDepthMapShader() {
        try {
            InputStream inputStream = context.getAssets().open(DEBUG_DEPTH_VERT_PATH);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(DEBUG_DEPTH_FRAG_PATH);
            String fragCode = readStringFromStream(inputStream);
            inputStream.close();

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragCode);

            int shaderHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(shaderHandle, vertexShader);
            GLES20.glAttachShader(shaderHandle, fragmentShader);

            GLES20.glBindAttribLocation(shaderHandle, Shader.POSITION, "a_Position");
            GLES20.glBindAttribLocation(shaderHandle, Shader.TEXCOORD, "a_texCoord");

            GLES20.glLinkProgram(shaderHandle);

            return shaderHandle;


        } catch (IOException e) {
            // Todo: static string fallback?
            return 0;
        }
    }

    public int compileDepthMapShader() {
        try {
            InputStream inputStream = context.getAssets().open(DEPTH_MAP_VERT_PATH);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(DEPTH_MAP_FRAG_PATH);
            String fragCode = readStringFromStream(inputStream);
            inputStream.close();

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragCode);

            int shaderHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(shaderHandle, vertexShader);
            GLES20.glAttachShader(shaderHandle, fragmentShader);

            GLES20.glBindAttribLocation(shaderHandle, Shader.SHADOW_POSITION, "a_ShadowPosition");
            GLES20.glBindAttribLocation(shaderHandle, Shader.TEXCOORD, "a_texCoord");

            GLES20.glLinkProgram(shaderHandle);

            return shaderHandle;


        } catch (IOException e) {
            // Todo: static string fallback?
            return 0;
        }
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

            int spriteShaderHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(spriteShaderHandle, vertexShader);
            GLES20.glAttachShader(spriteShaderHandle, fragmentShader);

            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.POSITION, "a_Position");
            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.COLOUR, "a_Color");
            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.TEXCOORD, "a_texCoord");

            GLES20.glLinkProgram(spriteShaderHandle);

            return spriteShaderHandle;


        } catch (IOException e) {
            // Todo: static string fallback?
            return 0;
        }
    }

    /**
     * Compiles provided source code for renderState and returns reference int
     *
     * @param type Must be GLES20.GL_VERTEX_SHADER OR GLES20.GL_FRAGMENT_SHADER
     * @param shaderCode Code for renderState
     * @return
     */

    public static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }

    public String readStringFromStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
