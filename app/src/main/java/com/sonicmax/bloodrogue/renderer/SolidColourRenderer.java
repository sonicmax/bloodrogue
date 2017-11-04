package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class SolidColourRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final String BUFFER_UTILS = "buffer-utils";

    /**
     * Sprite sheet is 512x512, containing 16x16 textures.
     * These will be upscaled to 64x64 when rendering
     */

    private final float TARGET_WIDTH = 64f;

    private final short[] mIndices = {
            0, 1, 2, // top-left, bottom-left, bottom right
            0, 2, 3  // top-left, bottom-right, top-right
    };

    private float[][][] cachedVecs;

    private float[] vecs;
    private short[] indices;
    private float[] colors;

    private int index_vecs;
    private int index_indices;
    private int index_colors;

    private float mUniformScale;

    // Handles for OpenGL
    private int mBasicShaderHandle;

    public SolidColourRenderer() {
        mUniformScale = 1f;
        System.loadLibrary(BUFFER_UTILS);
    }

    public void setUniformScale(float uniformScale) {
        this.mUniformScale = uniformScale;
    }

    public void setBasicShader(int handle) {
        mBasicShaderHandle = handle;
    }

    public void initArrays(int size) {
        index_vecs = 0;
        index_indices = 0;
        index_colors = 0;

        vecs = new float[size * 12];
        colors = new float[size * 16];
        indices = new short[size * 6];
    }

    public void precalculatePositions(int width, int height) {
        cachedVecs = new float[width][height][12];

        float x;
        float y;
        float yUnit = TARGET_WIDTH * mUniformScale;

        // Iterate over row of terrainIndexes and setup vertices/etc for each sprite
        for (int tileY = 0; tileY < height; tileY++) {
            x = 0f;
            y = 0f + (tileY * yUnit);

            for (int tileX = 0; tileX < width; tileX++) {

                cachedVecs[tileX][tileY][0] = x;
                cachedVecs[tileX][tileY][1] = y + (TARGET_WIDTH * mUniformScale);
                cachedVecs[tileX][tileY][2] = 1f;
                cachedVecs[tileX][tileY][3] = x;
                cachedVecs[tileX][tileY][4] = y;
                cachedVecs[tileX][tileY][5] = 1f;
                cachedVecs[tileX][tileY][6] = x + (TARGET_WIDTH * mUniformScale);
                cachedVecs[tileX][tileY][7] = y;
                cachedVecs[tileX][tileY][8] = 1f;
                cachedVecs[tileX][tileY][9] = x + (TARGET_WIDTH * mUniformScale);
                cachedVecs[tileX][tileY][10] = y + (TARGET_WIDTH * mUniformScale);
                cachedVecs[tileX][tileY][11] = 1f;

                x += TARGET_WIDTH * mUniformScale;
            }
        }
    }

    private float[] calculateOffset(float[] vec, float offsetX, float offsetY) {
        float[] offsetVec = new float[vec.length];
        System.arraycopy(vec, 0, offsetVec, 0, vec.length);
        offsetX *= TARGET_WIDTH;
        offsetY *= TARGET_WIDTH;

        // Add offset to vectors, ignoring z axis
        offsetVec[0] += offsetX;
        offsetVec[1] += offsetY;
        // vec[2] = 1f;
        offsetVec[3] += offsetX;
        offsetVec[4] += offsetY;
        // vec[5] = 1f;
        offsetVec[6] += offsetX;
        offsetVec[7] += offsetY;
        // vec[8] = 1f;
        offsetVec[9] += offsetX;
        offsetVec[10] += offsetY;
        // vec[11] = 1f;

        return offsetVec;
    }

    public void addSolidTile(int x, int y, float[] colour, float offsetX, float offsetY) {
        if (offsetX == 0f && offsetY == 0f) {
            addColourRenderInformation(cachedVecs[x][y], colour);
        }
        else {
            addColourRenderInformation(calculateOffset(cachedVecs[x][y], offsetX, offsetY), colour);
        }
    }

    private void addColourRenderInformation(float[] vec, float[] cs) {
        // Translate the indices to align with the location in our array of vectors
        short base = (short) (index_vecs / 3);

        // Add data to be passed into GL buffers
        for (int i = 0; i < vec.length; i++) {
            vecs[index_vecs] = vec[i];
            index_vecs++;
        }

        for(int i = 0; i < cs.length; i++) {
            colors[index_colors] = cs[i];
            index_colors++;
        }

        for (int j = 0; j < mIndices.length; j++) {
            indices[index_indices] = (short) (base + mIndices[j]);
            index_indices++;
        }
    }

    public void renderSolidColours(float[] matrix) {
        GLES20.glUseProgram(mBasicShaderHandle);

        FloatBuffer vertexBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer drawListBuffer;

        ByteBuffer bb = ByteBuffer.allocateDirect(vecs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        BufferUtils.copy(vecs, vertexBuffer, vecs.length, 0);

        ByteBuffer bb3 = ByteBuffer.allocateDirect(colors.length * 4);
        bb3.order(ByteOrder.nativeOrder());
        colorBuffer = bb3.asFloatBuffer();
        BufferUtils.copy(colors, colorBuffer, colors.length, 0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indices.length);

        // Prepare screen coordinate data
        int mPositionHandle = GLES20.glGetAttribLocation(mBasicShaderHandle, "a_Position");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // Prepare colours
        int mColorHandle = GLES20.glGetAttribLocation(mBasicShaderHandle, "a_Color");
        GLES20.glEnableVertexAttribArray(mColorHandle);
        GLES20.glVertexAttribPointer(mColorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        // Apply projection and view transformation
        int mtrxhandle = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_MVPMatrix");
        GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, matrix, 0);

        // Render the triangles
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mColorHandle);
    }
}

