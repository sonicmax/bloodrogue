package com.sonicmax.bloodrogue.renderer.effects;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 *  Renders sprites using wave effect (provided by vertex shader)
 */

public class RippleEffectRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    /**
     * Sprite sheet is 512x512, containing 16x16 textures.
     * These will be upscaled to 64x64 when rendering
     */

    private final float SPRITE_BOX_WIDTH = 1f; // 1f / 32 sprites per row 0.03125f
    private final float SPRITE_BOX_HEIGHT = 1f; // 1f / 32 sprites per column 0.03125f
    private final float TARGET_WIDTH = 64f; // Upscaled from 16
    private final int SPRITES_PER_ROW = 1;
    private final float PI2 = 3.1415926535897932384626433832795f * 2.0f;

    private final short[] mIndices = {
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
    private int spriteSheetHandle;
    private int waveShaderHandle;

    private ByteBuffer bb1;
    private ByteBuffer bb2;

    private int matrixLocation;
    private int textureLocation;
    private int waveTimeLocation;
    private int waveDataLocation;

    // Values for wave effect. angleWave and amplitudeWave are set as vec2 in vertex shader.
    private float amplitudeWave = 0.01f;
    private float angleWave = 0.0f;
    private float angleWaveSpeed = 0.025f;

    public RippleEffectRenderer() {
        mUniformScale = 1f;

        // How many bytes we need to skip in VBO to find new entry for same data shader.
        stride = (FLOATS_PER_POSITION + FLOATS_PER_COLOUR + FLOATS_PER_UV) * FLOAT_SIZE;

        int length = 1024;
        int packedSize = (length * POSITION_SIZE) + (length * COLOUR_SIZE) + (length * UV_SIZE);

        packedFloats = new float[packedSize];
        indices = new short[length * INDICES_SIZE];
    }

    public void setUniformScale(float uniformScale) {
        this.mUniformScale = uniformScale;
        this.amplitudeWave = (TARGET_WIDTH * uniformScale) / 20;
    }

    public void setWaveShader(int handle) {
        waveShaderHandle = handle;
    }

    public void setShaderVariableLocations() {
        GLES20.glUseProgram(waveShaderHandle);

        matrixLocation = GLES20.glGetUniformLocation(waveShaderHandle, "u_MVPMatrix");
        textureLocation = GLES20.glGetUniformLocation (waveShaderHandle, "u_Texture");
        waveDataLocation = GLES20.glGetUniformLocation(waveShaderHandle, "u_waveData");
        waveTimeLocation = GLES20.glGetUniformLocation(waveShaderHandle, "u_Time");
    }

    public void setSpriteSheetHandle(int val) {
        spriteSheetHandle = val;
    }

    public void resetInternalCount() {
        vertCount = 0;
        indicesCount = 0;
        packedCount = 0;
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

    public void addSpriteData(int x, int y, int spriteIndex, float[] tint, float lighting) {
        if (spriteIndex == -1) {
            return;
        }

        baseColours[0] = tint[0] * lighting; // r
        baseColours[1] = tint[1] * lighting; // g
        baseColours[2] = tint[2] * lighting; // b
                                             // (skip a - handled in shader)
        baseColours[4] = tint[0] * lighting;
        baseColours[5] = tint[1] * lighting;
        baseColours[6] = tint[2] * lighting;

        baseColours[8] = tint[0] * lighting;
        baseColours[9] = tint[1] * lighting;
        baseColours[10] = tint[2] * lighting;

        baseColours[12] = tint[0] * lighting;
        baseColours[13] = tint[1] * lighting;
        baseColours[14] = tint[2] * lighting;

        addRenderInformation(cachedVecs[x][y], baseColours, cachedUvs[spriteIndex]);
    }

    public void addSpriteData(int x, int y, int spriteIndex, float lighting, float offsetX, float offsetY) {

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

        addRenderInformation(calculateOffset(cachedVecs[x][y], offsetX, offsetY), baseColours, cachedUvs[spriteIndex]);
    }

    /**
     * Vectors, colours and UV coords are stored in a packed array.
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

        for (int j = 0; j < mIndices.length; j++) {
            indices[indicesCount] = (short) (base + mIndices[j]);
            indicesCount++;
        }
    }

    /**
     * Called once we've added all our sprite data and are ready to render frame.
     *
     * @param matrix Model-view-projection matrix to use when rendering
     */

    public void renderWaveEffect(float[] matrix, float dt, float time) {
        GLES20.glUseProgram(waveShaderHandle);
        GLES20.glEnable(GLES20.GL_BLEND);

        if (packedCount == 0) {
            return;
        }

        checkBufferCapacity();

        FloatBuffer floatBuffer = bb1.asFloatBuffer();
        BufferUtils.copy(packedFloats, floatBuffer, packedCount, 0);

        ShortBuffer drawListBuffer = bb2.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indices.length);

        // Add pointers to buffer for each attribute.

        // GLES20.glVertexAttribPointer() doesn't have offset parameter, so we have to
        // add the offset manually using Buffer.position()

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.POSITION,
                FLOATS_PER_POSITION,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.COLOUR);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.COLOUR,
                FLOATS_PER_COLOUR,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer.position(FLOATS_PER_POSITION));

        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.TEXCOORD,
                FLOATS_PER_UV,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer.position(FLOATS_PER_POSITION + FLOATS_PER_COLOUR));

        // Pass MVP matrix to shader
        GLES20.glUniformMatrix4fv(matrixLocation, 1, false, matrix, 0);

        // Pass updated angle & amplitude to shader
        // updateWaveVariables(dt);
        // GLES20.glUniform2f(waveDataLocation, angleWave, amplitudeWave);

        GLES20.glUniform1f(waveTimeLocation, time);
        GLES20.glUniform1i(textureLocation, 2);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
    }

    private void updateWaveVariables(float dt) {
        dt = 1f / dt;

        angleWave += dt * angleWaveSpeed;

        while (angleWave > PI2) {
            angleWave -= PI2;
        }
    }

    /**
     * Makes sure that we have enough capacity in our buffers for our packed floats and shorts.
     * This should strike a good balance between performance (as reallocating buffers every single
     * frame is expensive) and not making things explode.
     */

    private void checkBufferCapacity() {
        int floatBufferSize = packedFloats.length * FLOAT_SIZE;
        int shortBufferSize = indices.length * SHORT_SIZE;

        if (bb1 == null) {
            bb1 = ByteBuffer.allocateDirect(floatBufferSize);
            bb1.order(ByteOrder.nativeOrder());
        }

        else if (packedFloats.length > bb1.capacity()) {
            Log.v(LOG_TAG, "Reallocating floats! old: " + bb1.capacity() + ", new: " + packedFloats.length);
            bb1 = null;
            bb1 = ByteBuffer.allocateDirect(floatBufferSize);
            bb1.order(ByteOrder.nativeOrder());
        }

        if (bb2 == null) {
            bb2 = ByteBuffer.allocateDirect(shortBufferSize);
            bb2.order(ByteOrder.nativeOrder());
        }

        else if (indices.length > bb2.capacity()) {
            Log.v(LOG_TAG, "Reallocating shorts! old: " + bb2.capacity() + ", new: " + indices.length);
            bb2 = null;
            bb2 = ByteBuffer.allocateDirect(shortBufferSize);
            bb2.order(ByteOrder.nativeOrder());
        }
    }
}