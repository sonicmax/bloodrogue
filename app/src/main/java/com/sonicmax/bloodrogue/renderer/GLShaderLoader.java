package com.sonicmax.bloodrogue.renderer;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Compiles shader code and provides reference to program object stored
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

    private Context context;

    public GLShaderLoader(Context context) {
        this.context = context;
    }

    public int compileSolidColourShader() {
        try {
            InputStream inputStream = context.getAssets().open(COLOUR_VERT_PATH);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(COLOUR_FRAG_PATH);
            String fragCode = readStringFromStream(inputStream);
            inputStream.close();

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragCode);

            int spriteShaderHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(spriteShaderHandle, vertexShader);
            GLES20.glAttachShader(spriteShaderHandle, fragmentShader);

            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.POSITION, "a_Position");
            GLES20.glBindAttribLocation(spriteShaderHandle, Shader.COLOUR, "a_Color");

            GLES20.glLinkProgram(spriteShaderHandle);

            return spriteShaderHandle;


        } catch (IOException e) {
            // Todo: static string fallback?
            return 0;
        }
    }

    public int compileSpriteShader() {
        try {
            InputStream inputStream = context.getAssets().open(BASIC_VERT_PATH);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(BASIC_FRAG_PATH);
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

    public int compileWaveShader() {
        try {
            InputStream inputStream = context.getAssets().open(BASIC_VERT_PATH);
            String vertCode = readStringFromStream(inputStream);
            inputStream = context.getAssets().open(WATER_RIPPLE_FRAG);
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
     * Compiles provided source code for shader and returns reference int
     *
     * @param type Must be GLES20.GL_VERTEX_SHADER OR GLES20.GL_FRAGMENT_SHADER
     * @param shaderCode Code for shader
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
