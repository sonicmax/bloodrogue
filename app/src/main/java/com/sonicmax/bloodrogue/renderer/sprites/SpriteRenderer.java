package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.Shader;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 *  Class which handles the majority of rendering for our game. Caches UV coordinates for our sprite
 *  sheet and and caches vectors for each grid square. Then takes an array of SpriteRow
 *  objects and renders everything in one call.
 */

public class SpriteRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    /**
     * Sprite sheet is 512x512, containing 16x16 textures.
     * These will be upscaled to 64x64 when rendering
     */

    private final float SPRITE_BOX_WIDTH = 0.03125f; // 1f / 32 sprites per row
    private final float SPRITE_BOX_HEIGHT = 0.03125f; // 1f / 32 sprites per column
    private final float TARGET_WIDTH = 64f; // Upscaled from 16
    private final int SPRITES_PER_ROW = 32;

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

    private float[][][] cachedVecs;
    private float[][] cachedUvs;

    private int packedCount;
    private int stride;

    private final int POSITION_SIZE = 12;
    private final int COLOUR_SIZE = 16;
    private final int UV_SIZE = 8;
    private final int INDICES_SIZE = 6;

    private final int FLOATS_PER_POSITION = 3;
    private final int FLOATS_PER_COLOUR = 4;
    private final int FLOATS_PER_UV = 2;

    private final int FLOAT_SIZE = 4;
    private final int SHORT_SIZE = 2;

    private float[] packedFloats;
    private short[] indices;

    private int vertCount;
    private int indicesCount;

    private float mUniformScale;

    // Handles for OpenGL
    private int mSpriteSheetHandle;
    private int mBasicShaderHandle;
    private int uniformMatrix;
    private int uniformTexture;

    private FloatBuffer floatBuffer;
    private ShortBuffer drawListBuffer;

    public SpriteRenderer() {
        mUniformScale = 1f;

        // How many bytes we need to skip in VBO to find new entry for same data shader.
        stride = (FLOATS_PER_POSITION + FLOATS_PER_COLOUR + FLOATS_PER_UV) * FLOAT_SIZE;

        int length = 128;
        int packedSize = (length * POSITION_SIZE) + (length * COLOUR_SIZE) + (length * UV_SIZE);

        packedFloats = new float[packedSize];
        indices = new short[length * INDICES_SIZE];
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

    public void resetInternalCount() {
        vertCount = 0;
        indicesCount = 0;
        packedCount = 0;

        /*int packedSize = (length * POSITION_SIZE) + (length * COLOUR_SIZE) + (length * UV_SIZE);

        packedFloats = new float[packedSize];
        indices = new short[length * INDICES_SIZE];*/
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

            // 0.001f = texture bleeding hack/fix
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

    public void addSpriteData(int x, int y, int spriteIndex, float lighting) {
        baseColours[0] = lighting; // r
        baseColours[1] = lighting; // g
        baseColours[2] = lighting; // b
        baseColours[3] = 1f;
        baseColours[4] = lighting;
        baseColours[5] = lighting;
        baseColours[6] = lighting;
        baseColours[7] = 1f;
        baseColours[8] = lighting;
        baseColours[9] = lighting;
        baseColours[10] = lighting;
        baseColours[11] = 1f;
        baseColours[12] = lighting;
        baseColours[13] = lighting;
        baseColours[14] = lighting;
        baseColours[15] = 1f;

        addRenderInformation(cachedVecs[x][y], baseColours, cachedUvs[spriteIndex]);
    }

    public void addSpriteData(int x, int y, int spriteIndex, float lighting, float alpha) {
        baseColours[0] = lighting;
        baseColours[1] = lighting;
        baseColours[2] = lighting;
        baseColours[3] = alpha;
        baseColours[4] = lighting;
        baseColours[5] = lighting;
        baseColours[6] = lighting;
        baseColours[7] = alpha;
        baseColours[8] = lighting;
        baseColours[9] = lighting;
        baseColours[10] = lighting;
        baseColours[11] = alpha;
        baseColours[12] = lighting;
        baseColours[13] = lighting;
        baseColours[14] = lighting;
        baseColours[15] = alpha;

        addRenderInformation(cachedVecs[x][y], baseColours, cachedUvs[spriteIndex]);
    }

    public void addSpriteData(int x, int y, int spriteIndex, float lighting, float offsetX, float offsetY) {
        baseColours[0] = lighting; // r
        baseColours[1] = lighting; // g
        baseColours[2] = lighting; // b
        baseColours[3] = 1f;
        baseColours[4] = lighting;
        baseColours[5] = lighting;
        baseColours[6] = lighting;
        baseColours[7] = 1f;
        baseColours[8] = lighting;
        baseColours[9] = lighting;
        baseColours[10] = lighting;
        baseColours[11] = 1f;
        baseColours[12] = lighting;
        baseColours[13] = lighting;
        baseColours[14] = lighting;
        baseColours[15] = 1f;

        addRenderInformation(calculateOffset(cachedVecs[x][y], offsetX, offsetY), baseColours, cachedUvs[spriteIndex]);
    }

    /**
     * Vectors, baseColours and UV coords are stored in a packed array.
     * Indices are stored in an array of shorts.
     *
     * @param vec
     * @param colours
     * @param uv
     */

    private void addRenderInformation(float[] vec, float[] colours, float[] uv) {
        // Translate the indices to align with the location in our array of vertices
        short base = (short) (vertCount / 3);

        // Add floats for each vertex into packed array.
        // Position vertices, colour floats and UV coords are packed in this format: x, y, z, r, g, b, a, x, y

        /*
            First vertex
         */

        for (int i = 0; i < 3; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
            vertCount++; // Note: we keep a separate count for vertices so we can translate indices
        }

        for (int i = 0; i < 4; i++) {
            packedFloats[packedCount] = colours[i];
            packedCount++;
        }

        for (int i = 0; i < 2; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }


        /*
            Second vertex
         */

        for (int i = 3; i < 6; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
            vertCount++;
        }

        for (int i = 4; i < 8; i++) {
            packedFloats[packedCount] = colours[i];
            packedCount++;
        }

        for (int i = 2; i < 4; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }


        /*
            Third vertex
         */

        for (int i = 6; i < 9; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
            vertCount++;
        }

        for (int i = 8; i < 12; i++) {
            packedFloats[packedCount] = colours[i];
            packedCount++;
        }

        for (int i = 4; i < 6; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }


        /*
            Fourth vertex
         */

        for (int i = 9; i < 12; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
            vertCount++;
        }

        for (int i = 12; i < 16; i++) {
            packedFloats[packedCount] = colours[i];
            packedCount++;
        }

        for (int i = 6; i < 8; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }

        // Todo: is there anyway to include indices in packed array?

        for (int j = 0; j < INDICES.length; j++) {
            indices[indicesCount] = (short) (base + INDICES[j]);
            indicesCount++;
        }
    }

    /**
     * Called once we've added all our sprite data and are ready to render frame.
     *
     * @param matrix Model-view-projection matrix to use when rendering
     */

    public void renderSprites(float[] matrix) {
        GLES20.glUseProgram(mBasicShaderHandle);

        if (packedCount == 0) {
            return;
        }

        // Copy modified portion of packed float array to buffer.
        ByteBuffer bb = ByteBuffer.allocateDirect(packedFloats.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        floatBuffer = bb.asFloatBuffer();
        BufferUtils.copy(packedFloats, floatBuffer, packedCount, 0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * SHORT_SIZE);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indices.length);

        GLES20.glEnableVertexAttribArray(Shader.POSITION);
        GLES20.glEnableVertexAttribArray(Shader.COLOUR);
        GLES20.glEnableVertexAttribArray(Shader.TEXCOORD);

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

        GLES20.glVertexAttribPointer(
                Shader.TEXCOORD,
                FLOATS_PER_UV,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer.duplicate().position(FLOATS_PER_POSITION + FLOATS_PER_COLOUR));

        // Pass MVP matrix to shader
        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);

        // Bind texture to unit 0 and render triangles
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSpriteSheetHandle);
        GLES20.glUniform1i(uniformTexture, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
    }
}
