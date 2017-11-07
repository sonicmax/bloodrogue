package com.sonicmax.bloodrogue.renderer.text;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class TextRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final String BUFFER_UTILS = "buffer-utils";
    private final float UV_BOX_WIDTH = 0.0625f;
    private final float ORIGINAL_WIDTH = 32f;
    private float TEXT_WIDTH = 24f;
    private final float TEXT_SPACESIZE = 13.3f;
    private final int SPRITES_PER_ROW = 16;
    private final int NUMBER_OF_CHARS = 94;

    private final short[] mIndices = {0, 1, 2, 0, 2, 3};

    // Sizes of each letter. These were scraped from the output provided by Codehead's Bitmap Font Generator
    // (chars 33 to 126)
    private final int[] charWidths = {5, 10, 21, 15, 21, 13, 8, 12, 12, 19, 15, 8, 10, 8, 18, 17,
            10, 15, 15, 15, 17, 17, 17, 17, 17, 8, 8, 13, 13, 13, 13, 21, 15, 16, 17, 16, 13, 13,
            16, 16, 5, 14, 15, 13, 21, 16, 17, 16, 15, 16, 17, 15, 16, 15, 21, 15, 16, 15, 10, 18,
            11, 16, 21, 8, 17, 16, 17, 16, 17, 13, 16, 16, 5, 10, 13, 5, 21, 16, 17, 16, 16, 16, 17,
            10, 16, 15, 21, 16, 15, 17, 12, 5, 12, 21};

    private float[][] cachedRows;
    private float[][] cachedUvs;
    private float[] cachedOffsets;

    private float[] vecs;
    private float[] uvs;
    private short[] indices;
    private float[] colors;

    private int index_vecs;
    private int index_indices;
    private int index_uvs;
    private int index_colors;

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private FloatBuffer colorBuffer;
    private ShortBuffer drawListBuffer;

    private int positionLocation;
    private int texCoordLocation;
    private int colorLocation;
    private int textureLocation;
    private int matrixLocation;

    private int mTextureHandle;
    private int mShaderHandle;

    private float mUniformScale;

    public TextRenderer() {
        System.loadLibrary(BUFFER_UTILS);
    }

    public void setTextureHandle(int val) {
        mTextureHandle = val;
    }

    public void setShaderProgramHandle(int handle) {
        mShaderHandle = handle;
    }

    public void getShaderVariableLocations() {
        GLES20.glUseProgram(mShaderHandle);

        positionLocation = GLES20.glGetAttribLocation(mShaderHandle, "a_Position");
        texCoordLocation = GLES20.glGetAttribLocation(mShaderHandle, "a_texCoord" );
        colorLocation = GLES20.glGetAttribLocation(mShaderHandle, "a_Color");
        textureLocation = GLES20.glGetUniformLocation (mShaderHandle, "u_Texture");
        matrixLocation = GLES20.glGetUniformLocation(mShaderHandle, "u_MVPMatrix");
    }

    public void setUniformscale(float uniformscale) {
        this.mUniformScale = uniformscale;
    }

    public void setTextSize(float size) {
        this.TEXT_WIDTH = size;
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

    /**
     * Works out how many text rows we can fit on screen and precalculates position vectors.
     * Returns number of rows generated
     *
     * @param height Screen height
     */

    public int precalculateRows(int height) {
        int rows = (int) (height / (TEXT_WIDTH * mUniformScale));

        cachedRows = new float[rows][12];

        float x;
        float y;
        float yUnit = TEXT_WIDTH * mUniformScale;

        // Iterate over row of terrainIndexes and setup vertices/etc for each sprite
        for (int row = 0; row < rows; row++) {
            x = 0f;
            y = 0f + (row * yUnit);

            cachedRows[row][0] = x;
            cachedRows[row][1] = y + (TEXT_WIDTH * mUniformScale);
            cachedRows[row][2] = 1f;
            cachedRows[row][3] = x;
            cachedRows[row][4] = y;
            cachedRows[row][5] = 1f;
            cachedRows[row][6] = x + (TEXT_WIDTH * mUniformScale);
            cachedRows[row][7] = y;
            cachedRows[row][8] = 1f;
            cachedRows[row][9] = x + (TEXT_WIDTH * mUniformScale);
            cachedRows[row][10] = y + (TEXT_WIDTH * mUniformScale);
            cachedRows[row][11] = 1f;
        }

        return rows;
    }

    public void precalculateOffsets() {
        cachedOffsets = new float[NUMBER_OF_CHARS];

        // Offset array only applies to original sprite width, so we have to scale it
        float scaleFactor = mUniformScale * (TEXT_WIDTH / ORIGINAL_WIDTH);

        for (int i = 0; i < NUMBER_OF_CHARS; i++) {
            cachedOffsets[i] = (charWidths[i]) * scaleFactor;
        }
    }

    public void precalculateUv() {
        cachedUvs = new float[NUMBER_OF_CHARS][8];

        for (int i = 0; i < NUMBER_OF_CHARS; i++) {
            int row = i / SPRITES_PER_ROW;
            int col = i % SPRITES_PER_ROW;

            float v = row * UV_BOX_WIDTH;
            float v2 = v + UV_BOX_WIDTH;
            float u = col * UV_BOX_WIDTH;
            float u2 = u + UV_BOX_WIDTH;

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

    public void addCharRenderInformation(float[] vec, float[] cs, float[] uv, short[] indi) {
        // Translate the indices to align with the location in our vecs array of vectors
        short base = (short) (index_vecs / 3);

        // We should add the vec, translating the indices to our saved vector
        for (int i = 0; i < vec.length; i++) {
            vecs[index_vecs] = vec[i];
            index_vecs++;
        }

        // We should add the colors.
        for (int i = 0; i < cs.length; i++) {
            colors[index_colors] = cs[i];
            index_colors++;
        }

        // We should add the uvs
        for (int i = 0; i < uv.length; i++) {
            uvs[index_uvs] = uv[i];
            index_uvs++;
        }

        // We handle the indices
        for (int j = 0; j < indi.length; j++) {
            indices[index_indices] = (short) (base + indi[j]);
            index_indices++;
        }
    }

    private int convertCharValueToUvIndex(int value) {
        // ccra_font starts at char 33, so we can just subtract 33 to get the respective uv texture index.
        return value - 33;
    }

    /**
     * Iterates over each character in given string and returns total width as float.
     *
     * @param text String to check
     * @return Total width in pixels
     */

    public float getExpectedTextWidth(String text) {
        float offset = 0f;

        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                offset += ((TEXT_SPACESIZE) * mUniformScale);
                continue;
            }

            // Calculate the new position
            offset += cachedOffsets[charIndex];
        }

        return offset;
    }

    /**
     * Tells renderer to display a given string at a particular row on screen.
     */

    public void addTextData(int row, String text, float[] color, float alphaModifier) {
        addTextData(row, 0f, text, color, alphaModifier);
    }

    /**
     *  Tells renderer to display a given string at coordinates provided where (0, 0) = bottom left
     *  Also takes a Y offset to support scrolling effect
     */

    public void addTextData(int row, float offset, String text, float[] color, float alphaModifier) {
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                offset += ((TEXT_SPACESIZE) * mUniformScale);
                continue;
            }

            // Creating the triangle information
            float[] vec = getRowVector(row, offset);

            float[] colors = new float[] {
                    color[0], color[1], color[2], color[3] - alphaModifier,
                    color[0], color[1], color[2], color[3] - alphaModifier,
                    color[0], color[1], color[2], color[3] - alphaModifier,
                    color[0], color[1], color[2], color[3] - alphaModifier
            };

            // Add our triangle information to our collection for 1 render call.
            addCharRenderInformation(vec, colors, cachedUvs[charIndex], mIndices);

            // Calculate the new position
            offset += cachedOffsets[charIndex];
        }
    }

    public void addTextData(float x, float y, float offsetY, float scale, String text, float[] color, float alphaModifier) {
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                x += ((TEXT_SPACESIZE) * mUniformScale);
                continue;
            }

            // Creating the triangle information
            float[] vec = getFreeVector(x, y, offsetY, scale);

            float[] colors = new float[] {
                    color[0], color[1], color[2], color[3] - alphaModifier,
                    color[0], color[1], color[2], color[3] - alphaModifier,
                    color[0], color[1], color[2], color[3] - alphaModifier,
                    color[0], color[1], color[2], color[3] - alphaModifier
            };

            // Add our triangle information to our collection for 1 render call.
            addCharRenderInformation(vec, colors, cachedUvs[charIndex], mIndices);

            // Calculate the new position
            x += cachedOffsets[charIndex];
        }
    }

    private float[] getRowVector(int row, float x) {
        float[] vec = cachedRows[row].clone();

        // Update x values in cached row with letter positions
        vec[0] = x;
        vec[3] = x;
        vec[6] = x + (TEXT_WIDTH * mUniformScale);
        vec[9] = x + (TEXT_WIDTH * mUniformScale);

        return vec;
    }

    private float[] getFreeVector(float x, float y, float offsetY, float textScale) {
        float[] vec = new float[12];

        float size = (TEXT_WIDTH * mUniformScale * textScale);

        y += (offsetY * size);

        vec[0] = x;
        vec[1] = y + size;
        vec[2] = 0.99f;
        vec[3] = x;
        vec[4] = y;
        vec[5] = 0.99f;
        vec[6] = x + size;
        vec[7] = y;
        vec[8] = 0.99f;
        vec[9] = x + size;
        vec[10] = y + size;
        vec[11] = 0.99f;

        return vec;
    }

    public void renderText(float[] matrix) {
        GLES20.glUseProgram(mShaderHandle);

        ByteBuffer bb = ByteBuffer.allocateDirect(vecs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        BufferUtils.copy(vecs, vertexBuffer, vecs.length, 0);

        ByteBuffer bb3 = ByteBuffer.allocateDirect(colors.length * 4);
        bb3.order(ByteOrder.nativeOrder());
        colorBuffer = bb3.asFloatBuffer();
        BufferUtils.copy(colors, colorBuffer, colors.length, 0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(uvs.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureBuffer = bb2.asFloatBuffer();
        BufferUtils.copy(uvs, textureBuffer, uvs.length, 0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indices.length);

        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glEnableVertexAttribArray(texCoordLocation);
        GLES20.glEnableVertexAttribArray(colorLocation);

        GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glVertexAttribPointer(colorLocation, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        GLES20.glUniformMatrix4fv(matrixLocation, 1, false, matrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle);
        GLES20.glUniform1i(textureLocation, 0);

        // render the triangle
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
    }
}
