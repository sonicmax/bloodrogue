package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class LineDrawer {
    private final int COORDS_PER_VERTEX = 3;
    private float lineCoords[] = {
            0.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f
    };

    private final String vertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
                "gl_Position = uMVPMatrix * vPosition;" +
            "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
                "gl_FragColor = vColor;" +
            "}";

    private FloatBuffer mVertexBuffer;

    // Set color with red, green, blue and alpha (opacity) values
    float color[] = { 0.0f, 0.0f, 0.0f, 1.0f };

    private int mShaderHandle;

    public LineDrawer() {
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                lineCoords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        mVertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        mVertexBuffer.put(lineCoords);
        // set the buffer to read the first coordinate
        mVertexBuffer.position(0);

        int vertexShader = GLShaderLoader.loadShader(GLES20.GL_VERTEX_SHADER,vertexShaderCode);
        int fragmentShader = GLShaderLoader.loadShader(GLES20.GL_FRAGMENT_SHADER,fragmentShaderCode);

        mShaderHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(mShaderHandle, vertexShader);
        GLES20.glAttachShader(mShaderHandle, fragmentShader);
        GLES20.glLinkProgram(mShaderHandle);
    }

    public LineDrawer setVertexes(float v0, float v1, float v2, float v3, float v4, float v5) {
        lineCoords[0] = v0;
        lineCoords[1] = v1;
        lineCoords[2] = v2;
        lineCoords[3] = v3;
        lineCoords[4] = v4;
        lineCoords[5] = v5;

        mVertexBuffer.put(lineCoords);
        // set the buffer to read the first coordinate
        mVertexBuffer.position(0);

        return this;
    }

    public LineDrawer setColor(float red, float green, float blue, float alpha) {
        color[0] = red;
        color[1] = green;
        color[2] = blue;
        color[3] = alpha;

        return this;
    }

    public void draw(float[] mvpMatrix) {
        final int vertexCount = lineCoords.length / COORDS_PER_VERTEX;
        final int vertexStride = COORDS_PER_VERTEX * 4; // bytes per vertex

        GLES20.glUseProgram(mShaderHandle);

        int positionHandle = GLES20.glGetAttribLocation(mShaderHandle, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, mVertexBuffer);

        int colorHandle = GLES20.glGetUniformLocation(mShaderHandle, "vColor");
        GLES20.glUniform4fv(colorHandle, 1, color, 0);

        int uMVPMatrix = GLES20.glGetUniformLocation(mShaderHandle, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(uMVPMatrix, 1, false, mvpMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);
    }
}
