package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class SolidColourRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final float TARGET_WIDTH = 64f;

    private final int POSITIONS_SIZE = 12;
    private final int COLOURS_SIZE = 16;
    private final int FLOATS_PER_POSITION = 3;
    private final int FLOATS_PER_COLOUR = 4;

    private final int FLOAT_SIZE = 4;

    private final short[] mIndices = {
            0, 1, 2, // top-left, bottom-left, bottom right
            0, 2, 3  // top-left, bottom-right, top-right
    };

    private float[] vertices;
    private float[] colours;
    private short[] indices;

    private int colourCount;
    private int indicesCount;
    private float uniformScale;
    private int shaderHandle;

    private VertexBufferObject positionBuffer;
    private VertexBufferObject indicesBuffer;

    public SolidColourRenderer() {}

    public void setUniformScale(float uniformScale) {
        this.uniformScale = uniformScale;
    }

    public void setBasicShader(int handle) {
        shaderHandle = handle;
    }

    public void initColourArray(int count) {
        this.colourCount = 0;
        this.colours = new float[count * COLOURS_SIZE];
    }

    /**
     * Indices won't change for each render, so we can safely precalculate these
     * and set up a ShortBuffer to pass them into glDrawElements()
     *
     * @param width
     * @param height
     */

    public void addIndices(int width, int height) {
        indices = new short[(width * height) * mIndices.length];
        int vertCount = 0;

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
    }

    public void addPositions(int width, int height) {
        vertices = new float[(width * height) * POSITIONS_SIZE];
        int vertCount = 0;

        float x;
        float y;
        float yUnit = TARGET_WIDTH * uniformScale;

        // Iterate over row of terrainIndexes and setup vertices/etc for each sprite
        for (int tileY = 0; tileY < height; tileY++) {
            x = 0f;
            y = 0f + (tileY * yUnit);

            for (int tileX = 0; tileX < width; tileX++) {

                float[] vecs = new float[12];

                vecs[0] = x;
                vecs[1] = y + (TARGET_WIDTH * uniformScale);
                vecs[2] = 1f;

                vecs[3] = x;
                vecs[4] = y;
                vecs[5] = 1f;

                vecs[6] = x + (TARGET_WIDTH * uniformScale);
                vecs[7] = y;
                vecs[8] = 1f;

                vecs[9] = x + (TARGET_WIDTH * uniformScale);
                vecs[10] = y + (TARGET_WIDTH * uniformScale);
                vecs[11] = 1f;

                for (int i = 0; i < vecs.length; i++) {
                    vertices[vertCount] = vecs[i];
                    vertCount++;
                }

                x += TARGET_WIDTH * uniformScale;
            }
        }
    }

    public void addSolidTile(float[] colours) {
        for (int i = 0; i < colours.length; i++) {
            this.colours[colourCount] = colours[i];
            colourCount++;
        }
    }

    public void createVBO() {
        // We need two VBOs - one for floats, one for shorts.
        // Get object name for later use

        positionBuffer = new VertexBufferObject();
        indicesBuffer = new VertexBufferObject();

        positionBuffer.bind(GLES20.GL_ARRAY_BUFFER);
        positionBuffer.copy(vertices);
        positionBuffer.unbind();

        indicesBuffer.bind(GLES20.GL_ELEMENT_ARRAY_BUFFER);
        indicesBuffer.copy(indices);
        indicesBuffer.unbind();

        // Once data has been copied to GPU, we can remove local references to data
        vertices = null;
        indices = null;
    }

    public void renderSolidColours(float[] matrix) {
        GLES20.glUseProgram(shaderHandle);

        positionBuffer.bind();

        GLES20.glEnableVertexAttribArray(Shader.POSITION);
        GLES20.glVertexAttribPointer(
                Shader.POSITION,
                FLOATS_PER_POSITION,
                GLES20.GL_FLOAT,
                false,
                0,
                0);

        positionBuffer.unbind();

        GLES20.glEnableVertexAttribArray(Shader.COLOUR);

        ByteBuffer bb = ByteBuffer.allocateDirect(colours.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer colourBuffer = bb.asFloatBuffer();
        BufferUtils.copy(colours, colourBuffer, colours.length, 0);

        GLES20.glVertexAttribPointer(
                Shader.COLOUR,
                FLOATS_PER_COLOUR,
                GLES20.GL_FLOAT,
                false,
                0,
                colourBuffer);

        // Pass MVP matrix to shader
        int uniformMatrix = GLES20.glGetUniformLocation(shaderHandle, "u_MVPMatrix");
        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);

        indicesBuffer.bind();
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, 0);
        indicesBuffer.unbind();
    }
}

