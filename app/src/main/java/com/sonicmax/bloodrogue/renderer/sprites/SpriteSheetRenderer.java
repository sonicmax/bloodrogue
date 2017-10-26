package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

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

    private final short[] mIndices = {0, 1, 2, 0, 2, 3};

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
    private int mShaderHandle;

    private ArrayList<Sprite> mSprites;
    private ArrayList<Sprite> mCachedSprites;
    private float[][] mCachedLighting;

    public SpriteSheetRenderer() {
        mSprites = new ArrayList<>();
        mCachedSprites = new ArrayList<>();
        mCachedLighting = new float[32][32];

        mUniformScale = 1f;
        offsetX = 0;
        offsetY = 0;
    }

    public void setUniformScale(float mUniformScale) {
        this.mUniformScale = mUniformScale;
    }

    public void setShaderProgramHandle(int handle) {
        mShaderHandle = handle;
    }

    public void setSpriteSheetHandle(int val) {
        mSpriteSheetHandle = val;
    }

    public void setScrollOffset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public void setLighting(int x, int y, double lighting) {
        mCachedLighting[x][y] = (float) (1f * lighting);
    }

    /**
     *  Precalculates the position for each visible tile on screen.
     */

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
        offsetVec[10] += offsetY;;
        // vec[11] = 1f;

        return offsetVec;
    }

    /**
     *  Precalculates UV coords for each texture in sprite sheet.
     *  Textures are ordered alphabetically.
     */

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

    /**
     *  Sprites added using this method will be stored indefinitely.
     *  Use for immutable objects like terrain, furniture, etc
     */

    public void cacheSprite(Sprite sprite) {
        mCachedSprites.add(sprite);
    }

    /**
     * Sprites added using this method will be cleared after each frame.
     * Use for mutable objects like actors, animations or chests
     */

    public void addSprite(Sprite sprite) {
        mSprites.add(sprite);
    }

    public void clear() {
        mSprites.clear();
    }

    public void prepareDrawInfo() {
        // Reset the indices.
        index_vecs = 0;
        index_indices = 0;
        index_uvs = 0;
        index_colors = 0;

        // Get the total amount of sprites to be rendered
        int spriteCount = mSprites.size() + mCachedSprites.size();

        // Create the arrays we need with the correct size.
        vecs = new float[spriteCount * 12];
        colors = new float[spriteCount * 16];
        uvs = new float[spriteCount * 8];
        indices = new short[spriteCount * 6];

        int cachedSpritesSize = mCachedSprites.size();

        for (int i = 0; i < cachedSpritesSize; i++) {
            convertSpriteToTriangleInfo(mCachedSprites.get(i));
        }

        int spritesSize = mSprites.size();

        for (int i = 0; i < spritesSize; i++) {
            convertSpriteToTriangleInfo(mSprites.get(i));
        }
    }

    private void addRenderInformation(float[] vec, float[] cs, float[] uv, short[] indi) {
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

        // Translate the indices to align with the location in our vecs array of vectors
        short base = (short) (index_vecs / 3);

        for (int j = 0; j < indi.length; j++) {
            indices[index_indices] = (short) (base + indi[j]);
            index_indices++;
        }
    }

    private void convertSpriteToTriangleInfo(Sprite sprite) {
        int x = sprite.x;
        int y = sprite.y;
        float lighting;
        if (sprite.lighting > -1f) {
            lighting = sprite.lighting;
        }
        else {
            lighting = mCachedLighting[x][y];
        }

        float[] colors = new float[] {
                lighting, lighting, lighting, 1f,
                lighting, lighting, lighting, 1f,
                lighting, lighting, lighting, 1f,
                lighting, lighting, lighting, 1f
        };

        if (sprite.index == -1) {
            Log.e(LOG_TAG, "Invalid sprite at " + x + ", " + y);
            return;
        }

        if (sprite.offsetX == 0 && sprite.offsetY == 0) {
            addRenderInformation(cachedVecs[sprite.x][sprite.y], colors, cachedUvs[sprite.index], mIndices);
        }
        else {
            addRenderInformation(calculateOffset(cachedVecs[sprite.x][sprite.y], sprite.offsetX, sprite.offsetY), colors, cachedUvs[sprite.index], mIndices);
        }
    }

    public void renderSprites(float[] matrix) {
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

        ByteBuffer bb2 = ByteBuffer.allocateDirect(vecs.length * 4);
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
        int mPositionHandle = GLES20.glGetAttribLocation(mShaderHandle, "v_Position");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        int mTexCoordLoc = GLES20.glGetAttribLocation(mShaderHandle, "a_texCoord");

        // Prepare the texturecoordinates
        GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTexCoordLoc);

        int mColorHandle = GLES20.glGetAttribLocation(mShaderHandle, "a_Color");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mColorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        // get handle to shape's transformation matrix
        int mtrxhandle = GLES20.glGetUniformLocation(mShaderHandle, "u_MVPMatrix");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, matrix, 0);

        int textureLocation = GLES20.glGetUniformLocation (mShaderHandle, "s_texture");

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
