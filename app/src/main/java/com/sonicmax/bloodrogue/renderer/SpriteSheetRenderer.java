package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *  Class which handles the majority of rendering for our game. Caches UV coordinates for our sprite
 *  sheet and and caches vectors for each grid square. Then takes an array of SpriteRow
 *  objects and renders everything in one call.
 */

public class SpriteSheetRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final float SPRITE_BOX_WIDTH = 0.125f; // 8 sprites per row = 0.125 each
    private final float SPRITE_BOX_HEIGHT = 0.03125f; // 32 sprites per column - 0.03125 each
    private final float SPRITE_WIDTH = 64f; // 64 pixels each

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

    // Handles for OpenGL
    private int mSpriteSheetHandle;
    private int mShaderHandle;

    private float uniformscale;

    private ArrayList<SpriteRow> spriteRows;

    public SpriteSheetRenderer() {
        // Create our container
        spriteRows = new ArrayList<>();

        // Create the arrays
        vecs = new float[3 * 10];
        colors = new float[4 * 10];
        uvs = new float[2 * 10];
        indices = new short[10];

        // init as 0 as default
        mSpriteSheetHandle = 0;
    }

    public void setUniformscale(float uniformscale) {
        this.uniformscale = uniformscale;
    }

    public void setShaderProgramHandle(int handle) {
        mShaderHandle = handle;
    }

    public void setSpriteSheetHandle(int val) {
        mSpriteSheetHandle = val;
    }

    /**
     *  Precalculates the position for each visible tile on screen.
     */

    public void precalculatePositions(int width, int height) {
        cachedVecs = new float[width + 2][height + 2][12];

        float x;
        float y;
        float yUnit = 64 * uniformscale;

        // Iterate over row of indexes and setup vertices/etc for each sprite
        for (int tileY = 0; tileY <= height; tileY++) {
            x = 10f;
            y = 10f + (tileY * yUnit);

            for (int tileX = 0; tileX <= width; tileX++) {

                cachedVecs[tileX][tileY][0] = x;
                cachedVecs[tileX][tileY][1] = y + (SPRITE_WIDTH * uniformscale);
                cachedVecs[tileX][tileY][2] = 0.99f;
                cachedVecs[tileX][tileY][3] = x;
                cachedVecs[tileX][tileY][4] = y;
                cachedVecs[tileX][tileY][5] = 0.99f;
                cachedVecs[tileX][tileY][6] = x + (SPRITE_WIDTH * uniformscale);
                cachedVecs[tileX][tileY][7] = y;
                cachedVecs[tileX][tileY][8] = 0.99f;
                cachedVecs[tileX][tileY][9] = x + (SPRITE_WIDTH * uniformscale);
                cachedVecs[tileX][tileY][10] = y + (SPRITE_WIDTH * uniformscale);
                cachedVecs[tileX][tileY][11] = 0.99f;

                x += SPRITE_WIDTH * uniformscale;
            }
        }
    }

    /**
     *  Precalculates UV coords for each texture in sprite sheet.
     *  Textures are ordered alphabetically.
     */

    public void precalculateUv(int numberOfIndexes) {
        cachedUvs = new float[numberOfIndexes][8];

        for (int i = 0; i < numberOfIndexes; i++) {
            int row = i / 8;
            int col = i % 8;

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

    public void addRow(SpriteRow obj) {
        // Add text object to our collection
        spriteRows.add(obj);
    }

    private void addRenderInformation(float[] vec, float[] cs, float[] uv, short[] indi) {
        // Translate the indices to align with the location in our vecs array of vectors
        short base = (short) (index_vecs / 3);

        // We should add the vec, translating the indices to our saved vector
        for (int i = 0; i < vec.length; i++) {
            vecs[index_vecs] = vec[i];
            index_vecs++;
        }

        // We should add the colors.
        for(int i=0; i < cs.length; i++) {
            colors[index_colors] = cs[i];
            index_colors++;
        }

        // We should add the uvs
        for (int i=0; i < uv.length; i++) {
            uvs[index_uvs] = uv[i];
            index_uvs++;
        }

        // We handle the indices
        for (int j = 0; j < indi.length; j++) {
            indices[index_indices] = (short) (base + indi[j]);
            index_indices++;
        }
    }

    private void prepareDrawInfo() {
        // Reset the indices.
        index_vecs = 0;
        index_indices = 0;
        index_uvs = 0;
        index_colors = 0;

        // Get the total amount of sprites to be rendered
        int spriteCount = 0;

        for (SpriteRow spriteRow : spriteRows) {
            spriteCount += spriteRow.indexes.size();

            if (spriteRow.hasArray()) {
                for (int i = 0; i < spriteRow.getIndexArray().length; i++) {
                    if (spriteRow.getIndexArray()[i] != null) {
                        spriteCount += spriteRow.getIndexArray()[i].size();
                    }
                }
            }
        }

        // Create the arrays we need with the correct size.
        vecs = null;
        colors = null;
        uvs = null;
        indices = null;

        vecs = new float[spriteCount * 12];
        colors = new float[spriteCount * 16];
        uvs = new float[spriteCount * 8];
        indices = new short[spriteCount * 6];
    }

    public void prepareSprites() {
        prepareDrawInfo();

        for (SpriteRow spriteRow : spriteRows) {
            convertSpriteRowToTriangleInfo(spriteRow);
        }
    }

    public void clear() {
        spriteRows = new ArrayList<>();
    }

    public void renderSprites(float[] matrix) {
        FloatBuffer vertexBuffer;
        FloatBuffer textureBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer drawListBuffer;

        GLES20.glUseProgram(mShaderHandle);

        // Enable alpha blending
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);

        // The vertex buffer.
        ByteBuffer bb = ByteBuffer.allocateDirect(vecs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vecs);
        vertexBuffer.position(0);

        // The color buffer.
        ByteBuffer bb3 = ByteBuffer.allocateDirect(colors.length * 4);
        bb3.order(ByteOrder.nativeOrder());
        colorBuffer = bb3.asFloatBuffer();
        colorBuffer.put(colors);
        colorBuffer.position(0);

        // The texture buffer
        ByteBuffer bb2 = ByteBuffer.allocateDirect(uvs.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureBuffer = bb2.asFloatBuffer();
        textureBuffer.put(uvs);
        textureBuffer.position(0);

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(indices);
        drawListBuffer.position(0);

        // get handle to vertex shader's vPosition member
        int mPositionHandle = GLES20.glGetAttribLocation(mShaderHandle, "vPosition");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mPositionHandle, 3,
                GLES20.GL_FLOAT, false,
                0, vertexBuffer);

        int mTexCoordLoc = GLES20.glGetAttribLocation(mShaderHandle, "a_texCoord" );

        // Prepare the texturecoordinates
        GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT,
                false,
                0, textureBuffer);

        GLES20.glEnableVertexAttribArray ( mPositionHandle );
        GLES20.glEnableVertexAttribArray ( mTexCoordLoc );

        int mColorHandle = GLES20.glGetAttribLocation(mShaderHandle, "a_Color");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Prepare the background coordinate data
        GLES20.glVertexAttribPointer(mColorHandle, 4,
                GLES20.GL_FLOAT, false,
                0, colorBuffer);

        // get handle to shape's transformation matrix
        int mtrxhandle = GLES20.glGetUniformLocation(mShaderHandle, "uMVPMatrix");

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

    private void convertSpriteRowToTriangleInfo(SpriteRow spriteRow) {
        int tileX = 0;
        int tileY = spriteRow.tileY;

        ArrayList<Integer>[] objectIndexes = null;

        if (spriteRow.hasArray()) {
            objectIndexes = spriteRow.getIndexArray();
        }

        // Indices are the same for each iteration
        final short[] inds = {0, 1, 2, 0, 2, 3};

        // Iterate over row of indexes and setup vertices/etc for each sprite
        for (Integer index : spriteRow.indexes) {

            // Todo: would be better to have early exit here & figure out different way to handle lighting

            // Get lighting for this tile and apply transformation to colour matrix
            double lighting = spriteRow.lighting.remove(0);

            if (index == -1) continue;

            float[] colors = new float[]{
                    1f, 1f, 1f, 1f,
                    1f, 1f, 1f, 1f,
                    1f, 1f, 1f, 1f,
                    1f, 1f, 1f, 1f
            };

            applyLighting(lighting, colors);

            // Add our triangle information to our collection for 1 render call.
            addRenderInformation(cachedVecs[tileX][tileY], colors, cachedUvs[index], inds);

            if (objectIndexes != null && objectIndexes[tileX] != null) {
                ArrayList<Integer> indexes = objectIndexes[tileX];

                for (Integer objectIndex : indexes) {
                    addRenderInformation(cachedVecs[tileX][tileY], colors, cachedUvs[objectIndex], inds);
                }
            }

            tileX++;
        }
    }

    /**
     *  Applies lighting to RGB values, ignoring alpha values
     */

    private void applyLighting(double lighting, float[] colors) {
        colors[0] *= lighting;
        colors[1] *= lighting;
        colors[2] *= lighting;
        colors[4] *= lighting;
        colors[5] *= lighting;
        colors[6] *= lighting;
        colors[8] *= lighting;
        colors[9] *= lighting;
        colors[10] *= lighting;
        colors[12] *= lighting;
        colors[13] *= lighting;
        colors[14] *= lighting;
    }

    private void setAlpha(float alpha, float[] colors) {
        colors[3] = alpha;
        colors[7] = alpha;
        colors[11] = alpha;
        colors[15] = alpha;
    }
}
