package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.Shader;
import com.sonicmax.bloodrogue.renderer.VertexBufferObject;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 *  Renderer class with some optimisations to handle fully populated grids of terrain sprites.
 *  Uses same VBO for vertices/indices each frame and just swaps around the UV coordinates/colours.
 */

public class TerrainRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    /**
     * Sprite sheet is 512x512, containing 16x16 textures.
     * These will be upscaled to 64x64 when rendering
     */

    private final float SPRITE_BOX_WIDTH = 0.03125f; // 1f / 32 sprites per row
    private final float SPRITE_BOX_HEIGHT = 0.03125f; // 1f / 32 sprites per column
    private final float TARGET_WIDTH = 64f; // Upscaled from 16
    private final int SPRITES_PER_ROW = 32;

    private final int POSITIONS_SIZE = 12;
    private final int COLOURS_SIZE = 16;
    private final int UVS_SIZE = 8;
    private final int INDICES_SIZE = 6;

    private final int FLOATS_PER_POSITION = 3;
    private final int FLOATS_PER_COLOUR = 4;
    private final int FLOATS_PER_UV = 2;

    private final int FLOAT_SIZE = 4;
    private final int stride;

    private final short[] INDICES = {
            0, 1, 2, // top-left, bottom-left, bottom right
            0, 2, 3  // top-left, bottom-right, top-right
    };

    private float[] baseColours = new float[] {
            1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f
    };

    private float[][] cachedUvs;

    private short[] indices;
    private float[] emptyColourData;
    private float[] lightingUpdate;
    private int lightIndex;

    private float[] packedFloats;
    private int packedCount;

    private int vecCount;
    private int indexCount;

    private int gridWidth;
    private int gridHeight;
    private float mUniformScale;

    // Handles for OpenGL
    private int mSpriteSheetHandle;
    private int mBasicShaderHandle;
    private int uniformMatrix;
    private int uniformTexture;

    private VertexBufferObject packedBuffer;
    private VertexBufferObject indicesBuffer;

    public TerrainRenderer() {
        // How many bytes we need to skip in VBO to find new entry for same data shader.
        stride = (FLOATS_PER_POSITION + FLOATS_PER_UV) * FLOAT_SIZE;
        mUniformScale = 1f;
    }

    public void setUniformScale(float uniformScale) {
        this.mUniformScale = uniformScale;
    }

    public void initShader(int handle) {
        mBasicShaderHandle = handle;
        uniformMatrix = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_MVPMatrix");
        uniformTexture = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_Texture");
    }

    public void setSpriteSheetHandle(int val) {
        mSpriteSheetHandle = val;
    }

    public void initArrays(int length) {
        vecCount = 0;
        indexCount = 0;
        packedCount = 0;

        int packedSize = (length * POSITIONS_SIZE) + (length * COLOURS_SIZE) + (length * UVS_SIZE);

        packedFloats = new float[packedSize];
        indices = new short[length * INDICES_SIZE];

        emptyColourData = new float[length * COLOURS_SIZE];

        for (int i = 0; i < emptyColourData.length; i++) {
            emptyColourData[i] = 0f;
        }
    }

    public void setMapSize(int width, int height) {
        this.gridWidth = width;
        this.gridHeight = height;
    }

    public void precalculateUv(int numberOfIndexes) {
        cachedUvs = new float[numberOfIndexes][8];

        for (int i = 0; i < numberOfIndexes; i++) {
            int row = i / SPRITES_PER_ROW;
            int col = i % SPRITES_PER_ROW;

            float v = row * SPRITE_BOX_HEIGHT;
            float v2 = v + SPRITE_BOX_HEIGHT;
            float u = col * SPRITE_BOX_WIDTH;
            float u2 = u + SPRITE_BOX_WIDTH;

            // Creating the triangle information
            float[] uv = new float[8];

            // float textureBleedingFix = 0.001f;

            uv[0] = u;
            uv[1] = v;
            uv[2] = u;
            uv[3] = v2;
            uv[4] = u2;
            uv[5] = v2;
            uv[6] = u2;
            uv[7] = v;

            cachedUvs[i] = uv;
        }
    }

    /**
     * This method expects that you pass the sprite data in the same order that vertices and indices
     * are prepared - this will be row by row starting from the origin
     * (eg. 1st sprite = [0, 0], 2nd sprite = [1, 0], etc)
     */

    public void addSpriteData(int x, int y, int spriteIndex) {
        if (spriteIndex == -1) {
            Log.e(LOG_TAG, "Invalid sprite at " + x + ", " + y);
            return;
        }

        float yUnit = TARGET_WIDTH * mUniformScale;

        float vecX = x * yUnit;
        float vecY = y * yUnit;

        float[] vertices = new float[12];

        vertices[0] = vecX;
        vertices[1] = vecY + (TARGET_WIDTH * mUniformScale);
        vertices[2] = 1f;

        vertices[3] = vecX;
        vertices[4] = vecY;
        vertices[5] = 1f;

        vertices[6] = vecX + (TARGET_WIDTH * mUniformScale);
        vertices[7] = vecY;
        vertices[8] = 1f;

        vertices[9] = vecX + (TARGET_WIDTH * mUniformScale);
        vertices[10] = vecY + (TARGET_WIDTH * mUniformScale);
        vertices[11] = 1f;

        addRenderInformation(vertices, cachedUvs[spriteIndex]);
    }

    private void addRenderInformation(float[] vec, float[] uv) {
        // Add floats for each vertex into packed array.
        // Vertices, colour floats and UV coords are packed in this format: x, y, z, r, g, b, a, x, y

        // First vector

        for (int i = 0; i < 3; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 0; i < 2; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }

        // Second vector

        for (int i = 3; i < 6; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 2; i < 4; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }


        // Third vector

        for (int i = 6; i < 9; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 4; i < 6; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }

        // Fourth vector

        for (int i = 9; i < 12; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
        }

        for (int i = 6; i < 8; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }
    }

    public void initLightingArray() {
        lightIndex = 0;
        lightingUpdate = emptyColourData.clone();
    }

    public void addLightingUpdate(int x, int y, float lighting) {

        lightIndex = (x + (y * gridHeight)) * COLOURS_SIZE;

        baseColours[0] = lighting; // r
        baseColours[1] = lighting; // g
        baseColours[2] = lighting; // b
                                   // (skip a)
        baseColours[4] = lighting;
        baseColours[5] = lighting;
        baseColours[6] = lighting;

        baseColours[8] = lighting;
        baseColours[9] = lighting;
        baseColours[10] = lighting;

        baseColours[12] = lighting;
        baseColours[13] = lighting;
        baseColours[14] = lighting;

        for (int i = 0; i < baseColours.length; i++) {
            lightingUpdate[lightIndex] = baseColours[i];
            lightIndex++;
        }
    }

    public void prepareIndices(int width, int height) {
        indexCount = 0;
        indices = new short[(width * height) * INDICES.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Translate the indices to align with the location in our array of vertices
                short base = (short) (vecCount / 3);

                for (int j = 0; j < INDICES.length; j++) {
                    indices[indexCount] = (short) (base + INDICES[j]);
                    indexCount++;
                }

                vecCount += POSITIONS_SIZE;
            }
        }
    }

    private int offset;

    public int calculateOffset(int x, int y) {
        offset = (y * gridWidth) + x;
        return offset;
    }

    public void prepareIndices(int startX, int startY, int endX, int endY) {
        int width = endX - startX;
        int height = endY - startY;

        indices = new short[(width * height) * INDICES.length];

        indexCount = 0;
        vecCount = calculateOffset(startX, startY) * POSITIONS_SIZE;
        int carriage = (gridWidth - width);

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                // Translate the indices to align with the location in our array of vertices
                short base = (short) (vecCount / 3);

                for (int j = 0; j < INDICES.length; j++) {
                    indices[indexCount] = (short) (base + INDICES[j]);
                    indexCount++;
                }

                vecCount += POSITIONS_SIZE;
            }

            // Make sure next row of indices are in correct position
            vecCount += carriage * POSITIONS_SIZE;
        }

        indicesBuffer.bindAndCopy(indices);
    }

    public void setupVBOs() {
        // We need two VBOs - one for floats, one for shorts.
        // Get object name for later use
        packedBuffer = new VertexBufferObject(GLES20.GL_ARRAY_BUFFER);
        indicesBuffer = new VertexBufferObject(GLES20.GL_ELEMENT_ARRAY_BUFFER);

        packedBuffer.bindAndCopy(packedFloats);
        indicesBuffer.bindAndCopy(indices);

        // Once data has been copied to GPU, we can remove local references to data
        packedFloats = null;
        indices = null;
    }

    public void flushBuffers() {
        if (packedBuffer != null) {
            packedBuffer.invalidate();
        }

        if (indicesBuffer != null) {
            indicesBuffer.invalidate();
        }
    }

    public void renderSprites(float[] matrix) {
        GLES20.glUseProgram(mBasicShaderHandle);

        packedBuffer.bind();
        GLES20.glEnableVertexAttribArray(Shader.POSITION);
        GLES20.glVertexAttribPointer(
                Shader.POSITION,
                FLOATS_PER_POSITION,
                GLES20.GL_FLOAT,
                false,
                stride,
                0);
        packedBuffer.unbind();


        // Add updated colour data to buffer and pass to shader
        ByteBuffer bb = ByteBuffer.allocateDirect(lightingUpdate.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer colourBuffer = bb.asFloatBuffer();
        BufferUtils.copy(lightingUpdate, colourBuffer, lightingUpdate.length, 0);

        GLES20.glEnableVertexAttribArray(Shader.COLOUR);
        GLES20.glVertexAttribPointer(
                Shader.COLOUR,
                FLOATS_PER_COLOUR,
                GLES20.GL_FLOAT,
                false,
                0,
                colourBuffer);


        // Rebind VBO and add UV coordinate data
        packedBuffer.bind();
        GLES20.glEnableVertexAttribArray(Shader.TEXCOORD);
        GLES20.glVertexAttribPointer(
                Shader.TEXCOORD,
                FLOATS_PER_UV,
                GLES20.GL_FLOAT,
                false,
                stride,
                FLOATS_PER_POSITION * FLOAT_SIZE);

        // Pass MVP matrix to shader
        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);

        // Bind texture to unit 0 and render triangles
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSpriteSheetHandle);
        GLES20.glUniform1i(uniformTexture, 0);

        indicesBuffer.bind();
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Unbind VBOs once we've finished using them
        packedBuffer.unbind();
        indicesBuffer.unbind();
    }
}
