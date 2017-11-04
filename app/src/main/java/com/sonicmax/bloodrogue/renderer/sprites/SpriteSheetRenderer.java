package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 *  Class which handles the majority of rendering for our game. Caches UV coordinates for our sprite
 *  sheet and and caches vectors for each grid square. Then takes an array of SpriteRow
 *  objects and renders everything in one call.
 */

public class SpriteSheetRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    /**
     * Sprite sheet is 512x512, containing 16x16 textures.
     * These will be upscaled to 64x64 when rendering
     */

    private final float SPRITE_BOX_WIDTH = 0.03125f; // 1f / 32 sprites per row
    private final float SPRITE_BOX_HEIGHT = 0.03125f; // 1f / 32 sprites per column
    private final float TARGET_WIDTH = 64f; // Upscaled from 16
    private final int SPRITES_PER_ROW = 32;
    private final float PI2 = 3.1415926535897932384626433832795f * 2.0f;

    private final short[] mIndices = {
            0, 1, 2, // top-left, bottom-left, bottom right
            0, 2, 3  // top-left, bottom-right, top-right
    };

    private float[][][] cachedVecs;
    private float[][] cachedUvs;

    private float[] vecs;
    private float[] uvs;
    private short[] indices;
    private float[] colors;

    private int index_vecs;
    private int index_indices;
    private int index_uvs;
    private int index_colors;

    private int offsetX;
    private int offsetY;
    private float mUniformScale;

    // Handles for OpenGL
    private int mSpriteSheetHandle;
    private int mBasicShaderHandle;
    private int mWaveShaderHandle;

    // Values for wave effect. angleWave and amplitudeWave are set as vec2 in vertex shader.
    private float amplitudeWave = 6f;
    private float angleWave = 0.0f;
    private float angleWaveSpeed = 1.0f;

    public SpriteSheetRenderer() {
        mUniformScale = 1f;
        offsetX = 0;
        offsetY = 0;
    }

    public void setUniformScale(float uniformScale) {
        this.mUniformScale = uniformScale;
        this.amplitudeWave = (TARGET_WIDTH * uniformScale) / 20;
    }

    public void setBasicShader(int handle) {
        mBasicShaderHandle = handle;
    }

    public void setWaveShader(int handle) {
        mWaveShaderHandle = handle;
    }

    public void setSpriteSheetHandle(int val) {
        mSpriteSheetHandle = val;
    }

    public void initArrays(int size) {
        index_vecs = 0;
        index_indices = 0;
        index_uvs = 0;
        index_colors = 0;

        vecs = new float[size * 12];
        colors = new float[size * 16];
        uvs = new float[size * 8];
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

    public void addSpriteData(int x, int y, int spriteIndex, float lighting, float offsetX, float offsetY) {

        float[] colors = new float[] {
                lighting, lighting, lighting, 1f,
                lighting, lighting, lighting, 1f,
                lighting, lighting, lighting, 1f,
                lighting, lighting, lighting, 1f
        };

        if (spriteIndex == -1) {
            Log.e(LOG_TAG, "Invalid sprite at " + x + ", " + y);
            return;
        }

        if (offsetX == 0f && offsetY == 0f) {
            addRenderInformation(cachedVecs[x][y], colors, cachedUvs[spriteIndex]);
        }
        else {
            addRenderInformation(calculateOffset(cachedVecs[x][y], offsetX, offsetY), colors, cachedUvs[spriteIndex]);
        }
    }

    private void addRenderInformation(float[] vec, float[] cs, float[] uv) {
        // Translate the indices to align with the location in our array of vectors
        short base = (short) (index_vecs / 3);

        // Add data to be passed into GL buffers
        for (int i = 0; i < vec.length; i++) {
            vecs[index_vecs] = vec[i];
            index_vecs++;
        }

        for(int i=0; i < cs.length; i++) {
            colors[index_colors] = cs[i];
            index_colors++;
        }

        for (int i=0; i < uv.length; i++) {
            uvs[index_uvs] = uv[i];
            index_uvs++;
        }

        for (int j = 0; j < mIndices.length; j++) {
            indices[index_indices] = (short) (base + mIndices[j]);
            index_indices++;
        }
    }

    public void renderSprites(float[] matrix) {
        GLES20.glUseProgram(mBasicShaderHandle);

        FloatBuffer vertexBuffer;
        FloatBuffer textureBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer drawListBuffer;

        ByteBuffer bb = ByteBuffer.allocateDirect(vecs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vecs);
        vertexBuffer.position(0);

        ByteBuffer bb3 = ByteBuffer.allocateDirect(colors.length * 4);
        bb3.order(ByteOrder.nativeOrder());
        colorBuffer = bb3.asFloatBuffer();
        colorBuffer.put(colors);
        colorBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(uvs.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureBuffer = bb2.asFloatBuffer();
        textureBuffer.put(uvs);
        textureBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(indices);
        drawListBuffer.position(0);

        // get handle to vertex shader's vPosition member
        int mPositionHandle = GLES20.glGetAttribLocation(mBasicShaderHandle, "a_Position");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        int mTexCoordLoc = GLES20.glGetAttribLocation(mBasicShaderHandle, "a_texCoord");

        // Prepare the texturecoordinates
        GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTexCoordLoc);

        int mColorHandle = GLES20.glGetAttribLocation(mBasicShaderHandle, "a_Color");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mColorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        // get handle to shape's transformation matrix
        int mtrxhandle = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_MVPMatrix");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, matrix, 0);

        int textureLocation = GLES20.glGetUniformLocation (mBasicShaderHandle, "u_Texture");

        //Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        //Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSpriteSheetHandle);

        //Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(textureLocation, 0);

        // render the triangle
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTexCoordLoc);
        GLES20.glDisableVertexAttribArray(mColorHandle);
    }

    public void renderWaveEffect(float[] matrix, float dt) {
        GLES20.glUseProgram(mWaveShaderHandle);

        FloatBuffer vertexBuffer;
        FloatBuffer textureBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer drawListBuffer;

        ByteBuffer bb = ByteBuffer.allocateDirect(vecs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vecs);
        vertexBuffer.position(0);

        ByteBuffer bb3 = ByteBuffer.allocateDirect(colors.length * 4);
        bb3.order(ByteOrder.nativeOrder());
        colorBuffer = bb3.asFloatBuffer();
        colorBuffer.put(colors);
        colorBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(uvs.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureBuffer = bb2.asFloatBuffer();
        textureBuffer.put(uvs);
        textureBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(indices);
        drawListBuffer.position(0);

        dt = 1f / dt;

        angleWave += dt * angleWaveSpeed;

        while (angleWave > PI2) {
            angleWave -= PI2;
        }

        // get handle to vertex shader's vPosition member
        int mPositionHandle = GLES20.glGetAttribLocation(mWaveShaderHandle, "a_Position");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        int mTexCoordLoc = GLES20.glGetAttribLocation(mWaveShaderHandle, "a_texCoord");

        // Prepare the texturecoordinates
        GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTexCoordLoc);

        int mColorHandle = GLES20.glGetAttribLocation(mWaveShaderHandle, "a_Color");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mColorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        // get handle to shape's transformation matrix
        int mtrxhandle = GLES20.glGetUniformLocation(mWaveShaderHandle, "u_MVPMatrix");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, matrix, 0);

        int textureLocation = GLES20.glGetUniformLocation (mWaveShaderHandle, "u_Texture");

        int mWaveDataHandle = GLES20.glGetUniformLocation(mWaveShaderHandle, "u_waveData");
        GLES20.glUniform2f(mWaveDataHandle, angleWave, amplitudeWave);

        //Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        //Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSpriteSheetHandle);

        //Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(textureLocation, 0);

        // render the triangle
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTexCoordLoc);
        GLES20.glDisableVertexAttribArray(mColorHandle);
    }
}
