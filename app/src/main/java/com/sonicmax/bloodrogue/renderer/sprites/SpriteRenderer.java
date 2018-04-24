package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
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

    private float scaleFactor;

    // Handles for OpenGL
    private int spriteSheetHandle;
    private int shaderHandle;
    private int uniformMatrix;
    private int uniformTexture;

    private ByteBuffer bb1;
    private ByteBuffer bb2;

    public SpriteRenderer() {
        scaleFactor = 1f;

        // How many bytes we need to skip in VBO to find new entry for same data renderState.
        stride = (FLOATS_PER_POSITION + FLOATS_PER_COLOUR + FLOATS_PER_UV) * FLOAT_SIZE;

        // Todo: this is stupid
        initArrays(6000);

        bb1 = null;
        bb2 = null;
    }

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public void initShader(int handle) {
        shaderHandle = handle;
        uniformMatrix = GLES20.glGetUniformLocation(shaderHandle, "u_MVPMatrix");
        uniformTexture = GLES20.glGetUniformLocation(shaderHandle, "u_Texture");
    }

    public void setSpriteSheet(int handle) {
        spriteSheetHandle = handle;
    }

    public void initArrays(int length) {
        int packedSize = (length * POSITION_SIZE) + (length * COLOUR_SIZE) + (length * UV_SIZE);

        packedFloats = new float[packedSize];
        indices = new short[length * INDICES_SIZE];
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
        float yUnit = TARGET_WIDTH * scaleFactor;

        for (int tileY = 0; tileY < height; tileY++) {
            x = 0f;
            y = 0f + (tileY * yUnit);

            for (int tileX = 0; tileX < width; tileX++) {

                cachedVecs[tileX][tileY][0] = x;
                cachedVecs[tileX][tileY][1] = y + (TARGET_WIDTH * scaleFactor);
                cachedVecs[tileX][tileY][2] = 1f;
                cachedVecs[tileX][tileY][3] = x;
                cachedVecs[tileX][tileY][4] = y;
                cachedVecs[tileX][tileY][5] = 1f;
                cachedVecs[tileX][tileY][6] = x + (TARGET_WIDTH * scaleFactor);
                cachedVecs[tileX][tileY][7] = y;
                cachedVecs[tileX][tileY][8] = 1f;
                cachedVecs[tileX][tileY][9] = x + (TARGET_WIDTH * scaleFactor);
                cachedVecs[tileX][tileY][10] = y + (TARGET_WIDTH * scaleFactor);
                cachedVecs[tileX][tileY][11] = 1f;

                x += TARGET_WIDTH * scaleFactor;
            }
        }
    }

    private float[] fullScreenVec = new float[12];
    private float[] fullScreenUv = new float[8];
    private float[] fullScreenMvpMatrix = new float[16];

    public void prepareFullScreenRender(float screenWidth, float screenHeight) {
        float x = 0f;
        float y = 0f;

        fullScreenVec[0] = x;
        fullScreenVec[1] = y + (screenHeight);
        fullScreenVec[2] = 1f;

        fullScreenVec[3] = x;
        fullScreenVec[4] = y;
        fullScreenVec[5] = 1f;

        fullScreenVec[6] = x + (screenWidth);
        fullScreenVec[7] = y;
        fullScreenVec[8] = 1f;

        fullScreenVec[9] = x + (screenWidth);
        fullScreenVec[10] = y + (screenHeight);
        fullScreenVec[11] = 1f;

        fullScreenUv[0] = 0f;
        fullScreenUv[1] = 1f;

        fullScreenUv[2] = 0f;
        fullScreenUv[3] = 0f;

        fullScreenUv[4] = 1f;
        fullScreenUv[5] = 0f;

        fullScreenUv[6] = 1f;
        fullScreenUv[7] = 1f;

        addDepthMapRenderData(fullScreenVec, fullScreenUv);

        float[] projMatrix = new float[16];
        float[] modelViewMatrix = new float[16];

        Matrix.orthoM(projMatrix, 0,
                (-screenWidth / 2), (screenWidth / 2),
                (-screenHeight / 2), (screenHeight / 2),
                -1f, 1f);

        // Set the camera position
        Matrix.setLookAtM(modelViewMatrix, 0,
                1f, 0f, 1f,
                1f, 0f, 0f,
                0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(fullScreenMvpMatrix, 0, projMatrix, 0, modelViewMatrix, 0);

        // Finally, translate so that origin is in bottom-left corner
        Matrix.translateM(fullScreenMvpMatrix, 0,
                -screenWidth / 2, -screenHeight / 2, 0f);
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

    private float[] white = {1f, 1f, 1f};

    private float[] currentTint = white;

    public void setColourTint(float[] tint) {
        currentTint = tint;
    }

    public void addSpriteData(int x, int y, int spriteIndex, float[] tint, float lighting) {
        if (spriteIndex == -1) {
            return;
        }

        baseColours[0] = tint[0] * lighting; // r
        baseColours[1] = tint[1] * lighting; // g
        baseColours[2] = tint[2] * lighting; // b
        baseColours[3] = 1f;
        baseColours[4] = tint[0] * lighting;
        baseColours[5] = tint[1] * lighting;
        baseColours[6] = tint[2] * lighting;
        baseColours[7] = 1f;
        baseColours[8] = tint[0] * lighting;
        baseColours[9] = tint[1] * lighting;
        baseColours[10] = tint[2] * lighting;
        baseColours[11] = 1f;
        baseColours[12] = tint[0] * lighting;
        baseColours[13] = tint[1] * lighting;
        baseColours[14] = tint[2] * lighting;
        baseColours[15] = 1f;

        addRenderInformation(cachedVecs[x][y], baseColours, cachedUvs[spriteIndex]);
    }

    public void addSpriteData(int x, int y, int spriteIndex, float lighting) {
        if (spriteIndex == -1) {
            return;
        }

        baseColours[0] = currentTint[0]; // r
        baseColours[1] = currentTint[1]; // g
        baseColours[2] = currentTint[2]; // b
        baseColours[3] = 1f;
        baseColours[4] = currentTint[0];
        baseColours[5] = currentTint[1];
        baseColours[6] = currentTint[2];
        baseColours[7] = 1f;
        baseColours[8] = currentTint[0];
        baseColours[9] = currentTint[1];
        baseColours[10] = currentTint[2];
        baseColours[11] = 1f;
        baseColours[12] = currentTint[0];
        baseColours[13] = currentTint[1];
        baseColours[14] = currentTint[2];
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

    private final float[] DEFAULT_COLOUR = new float[] {1f, 1f, 1f};

    public void addSpriteData(int x, int y, int spriteIndex, float lighting, float offsetX, float offsetY) {
        addSpriteData(x, y, spriteIndex, DEFAULT_COLOUR, lighting, offsetX, offsetY);
    }

    public void addSpriteData(int x, int y, int spriteIndex, float[] tint, float lighting, float offsetX, float offsetY) {
        if (spriteIndex == -1 || x < 0 || y < 0) {
            return;
        }

        baseColours[0] = tint[0] * lighting; // r
        baseColours[1] = tint[1] * lighting; // g
        baseColours[2] = tint[2] * lighting; // b
        baseColours[3] = 1f;
        baseColours[4] = tint[0] * lighting;
        baseColours[5] = tint[1] * lighting;
        baseColours[6] = tint[2] * lighting;
        baseColours[7] = 1f;
        baseColours[8] = tint[0] * lighting;
        baseColours[9] = tint[1] * lighting;
        baseColours[10] = tint[2] * lighting;
        baseColours[11] = 1f;
        baseColours[12] = tint[0] * lighting;
        baseColours[13] = tint[1] * lighting;
        baseColours[14] = tint[2] * lighting;
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
        // You can use drawArrays() instead of drawElements() but this may have negative performance implications

        for (int j = 0; j < INDICES.length; j++) {
            indices[indicesCount] = (short) (base + INDICES[j]);
            indicesCount++;
        }
    }

    private void addDepthMapRenderData(float[] vec, float[] uv) {
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
        GLES20.glUseProgram(shaderHandle);

        if (packedCount == 0) {
            return;
        }

        checkBufferCapacity();

        // Copy modified portion of packed float array to buffer.
        FloatBuffer floatBuffer = bb1.asFloatBuffer();
        BufferUtils.copy(packedFloats, floatBuffer, packedCount, 0);

        ShortBuffer drawListBuffer = bb2.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indicesCount);

        // Add pointers to buffer for each attribute.

        // GLES20.glVertexAttribPointer() doesn't have offset parameter for buffers, so we have to
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

        // Pass MVP matrix to renderState
        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);
        GLES20.glUniform1i(uniformTexture, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.COLOUR);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    /**
     * For debugging - renders whatever texture was bound to the texture unit passed into method.
     * Will probably explode if wrong shader was passed to initShader()
     *
     * @param textureUnit Texture unit to render to screen
     */

    public void renderTexture(int textureUnit) {
        GLES20.glUseProgram(shaderHandle);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        if (packedCount == 0) {
            return;
        }

        checkBufferCapacity();

        int stride = (FLOATS_PER_POSITION + FLOATS_PER_UV) * FLOAT_SIZE;

        FloatBuffer floatBuffer = bb1.asFloatBuffer();
        BufferUtils.copy(packedFloats, floatBuffer, packedCount, 0);

        ShortBuffer drawListBuffer = bb2.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indicesCount);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.POSITION,
                FLOATS_PER_POSITION,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.TEXCOORD,
                FLOATS_PER_UV,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer.position(FLOATS_PER_POSITION));

        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, fullScreenMvpMatrix, 0);
        GLES20.glUniform1i(uniformTexture, textureUnit);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    /**
     * For debugging - renders depth map using debug_depth shader.
     * Will probably explode if wrong shader was passed to initShader()
     * Uses near/far values to linearise depth in order to improve visibility of output
     *
     * @param near Near frustrum
     * @param far Far frustrum
     */

    public void renderDepthMap(float near, float far) {
        GLES20.glUseProgram(shaderHandle);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        int depthMapNearHandle = GLES20.glGetUniformLocation(shaderHandle, "u_Near");
        int depthMapFarHandle = GLES20.glGetUniformLocation(shaderHandle, "u_Far");

        GLES20.glUniform1f(depthMapNearHandle, near);
        GLES20.glUniform1f(depthMapFarHandle, far);

        if (packedCount == 0) {
            return;
        }

        checkBufferCapacity();

        int stride = (FLOATS_PER_POSITION + FLOATS_PER_UV) * FLOAT_SIZE;

        FloatBuffer floatBuffer = bb1.asFloatBuffer();
        BufferUtils.copy(packedFloats, floatBuffer, packedCount, 0);

        ShortBuffer drawListBuffer = bb2.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indicesCount);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.POSITION,
                FLOATS_PER_POSITION,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.TEXCOORD,
                FLOATS_PER_UV,
                GLES20.GL_FLOAT,
                false,
                stride,
                floatBuffer.position(FLOATS_PER_POSITION));

        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, fullScreenMvpMatrix, 0);
        GLES20.glUniform1i(uniformTexture, 3);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
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

    public void freeBuffers() {
        bb1 = null;
        bb2 = null;
    }
}
