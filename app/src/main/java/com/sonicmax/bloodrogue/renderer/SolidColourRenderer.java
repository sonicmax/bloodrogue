package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class SolidColourRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final float TARGET_WIDTH = 64f;

    private final int POSITIONS_SIZE = 12;
    private final int COLOURS_SIZE = 16;
    private final int FLOATS_PER_POSITION = 3;
    private final int FLOATS_PER_COLOUR = 4;
    private final int stride;

    private final int FLOAT_SIZE = 4;
    private final int SHORT_SIZE = 2;

    private final short[] mIndices = {
            0, 1, 2, // top-left, bottom-left, bottom right
            0, 2, 3  // top-left, bottom-right, top-right
    };


    private float[][][] cachedVecs;

    private float[] packedArray;
    private short[] indices;

    private int packedCount;
    private int vertCount;
    private int indicesCount;

    private FloatBuffer floatBuffer;
    private ShortBuffer drawListBuffer;

    private float mUniformScale;

    // Handles for OpenGL
    private int mBasicShaderHandle;

    public SolidColourRenderer() {
        stride = (FLOATS_PER_POSITION + FLOATS_PER_COLOUR) * FLOAT_SIZE;

        mUniformScale = 1f;
        vertCount = 0;
    }

    public void setUniformScale(float uniformScale) {
        this.mUniformScale = uniformScale;
    }

    public void setBasicShader(int handle) {
        mBasicShaderHandle = handle;
    }

    public void initArrays(int count) {
        packedCount = 0;

        int packedSize = (count * POSITIONS_SIZE) + (count * COLOURS_SIZE);
        packedArray = new float[packedSize];
    }

    /**
     * Indices won't change for each render, so we can safely precalculate these
     * and set up a ShortBuffer to pass them into glDrawElements()
     *
     * @param width
     * @param height
     */

    public void prepareIndicesBuffer(int width, int height) {
        indices = new short[(width * height) * mIndices.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Translate the indices to align with the location in our array of vertices
                short base = (short) (vertCount / 3);

                for (int j = 0; j < mIndices.length; j++) {
                    indices[indicesCount] = (short) (base + mIndices[j]);
                    indicesCount++;
                }

                vertCount += POSITIONS_SIZE;
            }
        }

        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * SHORT_SIZE);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indices.length);
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

    public void addSolidTile(int x, int y, float[] colour) {
        addColourRenderInformation(cachedVecs[x][y], colour);
    }

    private void addColourRenderInformation(float[] vec, float[] colours) {
        // Add floats for each vertex into packed array.
        // Position vertices, colour floats and UV coords are packed in this format: x, y, z, r, g, b, a, x, y

        /*
            First vertex
         */

        for (int i = 0; i < 3; i++) {
            packedArray[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 0; i < 4; i++) {
            packedArray[packedCount] = colours[i];
            packedCount++;
        }


        /*
            Second vertex
         */

        for (int i = 3; i < 6; i++) {
            packedArray[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 4; i < 8; i++) {
            packedArray[packedCount] = colours[i];
            packedCount++;
        }


        /*
            Third vertex
         */

        for (int i = 6; i < 9; i++) {
            packedArray[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 8; i < 12; i++) {
            packedArray[packedCount] = colours[i];
            packedCount++;
        }


        /*
            Fourth vertex
         */

        for (int i = 9; i < 12; i++) {
            packedArray[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 12; i < 16; i++) {
            packedArray[packedCount] = colours[i];
            packedCount++;
        }
    }

    public void renderSolidColours(float[] matrix) {
        GLES20.glUseProgram(mBasicShaderHandle);

        ByteBuffer bb = ByteBuffer.allocateDirect(packedArray.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        floatBuffer = bb.asFloatBuffer();
        BufferUtils.copy(packedArray, floatBuffer, packedArray.length, 0);

        GLES20.glEnableVertexAttribArray(Shader.POSITION);
        GLES20.glEnableVertexAttribArray(Shader.COLOUR);

        // Add pointers to buffer for each attribute.

        // GLES20.glVertexAttribPointer() doesn't have offset parameter, so we have to
        // add the offset manually using Buffer.duplicate().position()

        GLES20.glVertexAttribPointer(
                Shader.POSITION,
                FLOATS_PER_POSITION,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer);

        GLES20.glVertexAttribPointer(
                Shader.COLOUR,
                FLOATS_PER_COLOUR,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer.duplicate().position(FLOATS_PER_POSITION));

        int uniformMatrix = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_MVPMatrix");

        // Pass MVP matrix to shader
        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
    }
}

