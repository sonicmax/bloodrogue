package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

/**
 * Compiles shader code and provides reference to program object stored
 */

public class GLShaderLoader {
    private final String vertexShaderCode =
            "uniform mat4 u_MVPMatrix;" +

            "attribute vec4 a_Position;" +
            "attribute vec4 a_Color;" +
            "attribute vec2 a_texCoord;" +

            "varying vec2 v_texCoord;" +
            "varying vec4 v_Color;" +

            "void main() {" +
                "gl_Position = u_MVPMatrix * a_Position;" +
                "v_texCoord = a_texCoord;" +
                "v_Color = a_Color;" +
            "}";

    /**
     *  Transforms vertexes using sin/cos functions to produce a wave effect
     */

    private final String vertexWaveShaderCode =
            "uniform mat4 u_MVPMatrix;" +
            "uniform vec2 u_waveData;" +
            "attribute vec4 a_Position;" +
            "attribute vec4 a_Color;" +
            "attribute vec2 a_texCoord;" +
            "varying vec4 v_Color;" +
            "varying vec2 v_texCoord;" +

            "void main() {" +
                "v_texCoord = a_texCoord;" +
                "v_Color = a_Color;" +
                "vec4 newPos = vec4(" +
                    "a_Position.x + u_waveData.y * sin(u_waveData.x + a_Position.x + a_Position.y), " +
                    "a_Position.y + u_waveData.y * sin(u_waveData.x + a_Position.x + a_Position.y), " +
                    "a_Position.z, " +
                    "a_Position.w);" +
                "gl_Position = u_MVPMatrix * newPos;" +
            "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +

            "varying vec4 v_Color;" +
            "varying vec2 v_texCoord;" +

            "uniform sampler2D s_texture;" +

            "void main() {" +
                "vec4 diffuse = texture2D(s_texture, v_texCoord);" +
                "gl_FragColor = diffuse * v_Color;" +
            "}";

    public GLShaderLoader() {}

    public int compileSpriteShader() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int spriteShaderHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(spriteShaderHandle, vertexShader);
        GLES20.glAttachShader(spriteShaderHandle, fragmentShader);

        GLES20.glLinkProgram(spriteShaderHandle);

        return spriteShaderHandle;
    }

    public int compileWaveShader() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexWaveShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int textShader = GLES20.glCreateProgram();
        GLES20.glAttachShader(textShader, vertexShader);
        GLES20.glAttachShader(textShader, fragmentShader);

        GLES20.glLinkProgram(textShader);

        return textShader;
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
}
