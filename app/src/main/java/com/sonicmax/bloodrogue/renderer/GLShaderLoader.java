package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Compiles shader code and provides reference to program object stored
 */

public class GLShaderLoader {
    private final String vertexShaderCode =
            "attribute vec2 a_TexCoordinate;" +
            "varying vec2 v_TexCoordinate;" +
            "uniform mat4 u_MVPMatrix;" +
            "attribute vec4 v_Position;" +

            "void main() { " +
                "gl_Position = u_MVPMatrix * v_Position;" +
                "v_TexCoordinate = a_TexCoordinate;" +
            "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                "uniform vec4 v_Color;" +
                "uniform sampler2D u_Texture;" +
                "varying vec2 v_TexCoordinate;" +

                "void main() {" +
                    "gl_FragColor = texture2D(u_Texture, v_TexCoordinate) * v_Color;" +
                "}";

    private final String textVertexCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 a_Color;" +
                    "attribute vec2 a_texCoord;" +
                    "varying vec4 v_Color;" +
                    "varying vec2 v_texCoord;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "  v_texCoord = a_texCoord;" +
                    "  v_Color = a_Color;" +
                    "}";

    private final String textFragmentCode =
            "precision mediump float;" +
                    "varying vec4 v_Color;" +
                    "varying vec2 v_texCoord;" +
                    "uniform sampler2D s_texture;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D( s_texture, v_texCoord ) * v_Color;" +
                    "  gl_FragColor.rgb *= v_Color.a;" +
                    "}";

    private int spriteShaderHandle;


    public GLShaderLoader() {}

    public GLShaderLoader compileSpriteShader() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        spriteShaderHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(spriteShaderHandle, vertexShader);
        GLES20.glAttachShader(spriteShaderHandle, fragmentShader);

        GLES20.glBindAttribLocation(spriteShaderHandle, 0, "a_TexCoordinate");
        GLES20.glLinkProgram(spriteShaderHandle);

        return this;
    }

    private int textShaderHandle;

    public GLShaderLoader compileTextShader() {
        Log.v("GLShaderLoad", "compiling text shader");
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, textVertexCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, textFragmentCode);

        textShaderHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(textShaderHandle, vertexShader);
        GLES20.glAttachShader(textShaderHandle, fragmentShader);

        GLES20.glLinkProgram(textShaderHandle);
        Log.v("GLShaderLoad", "compiled text shader to " + textShaderHandle);
        return this;
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

    /**
     * Returns program object reference returned by glCreateProgram()
     */

    public int getSpriteShader() {
        return spriteShaderHandle;
    }

    public int getTextShader() {
        return textShaderHandle;
    }
}
