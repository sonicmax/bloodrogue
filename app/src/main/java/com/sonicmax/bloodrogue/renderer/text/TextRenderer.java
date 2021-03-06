package com.sonicmax.bloodrogue.renderer.text;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class TextRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();
    private final float UV_BOX_WIDTH = 0.0625f;
    private final float ORIGINAL_WIDTH = 32f;
    private float TEXT_WIDTH = 24f;
    private final float TEXT_SPACESIZE = 13.3f;
    private final int SPRITES_PER_ROW = 16;
    private final int NUMBER_OF_CHARS = 94;

    private final short[] INDICES = {0, 1, 2, 0, 2, 3};

    private float[] baseColours = new float[] {
            1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f
    };

    // Sizes of each letter. These were scraped from the output provided by Codehead's Bitmap Font Generator
    // (chars 33 to 126)
    private final int[] CHAR_WIDTHS = {5, 10, 21, 15, 21, 13, 8, 12, 12, 19, 15, 8, 10, 8, 18, 17,
            10, 15, 15, 15, 17, 17, 17, 17, 17, 8, 8, 13, 13, 13, 13, 21, 15, 16, 17, 16, 13, 13,
            16, 16, 5, 14, 15, 13, 21, 16, 17, 16, 15, 16, 17, 15, 16, 15, 21, 15, 16, 15, 10, 18,
            11, 16, 21, 8, 17, 16, 17, 16, 17, 13, 16, 16, 5, 10, 13, 5, 21, 16, 17, 16, 16, 16, 17,
            10, 16, 15, 21, 16, 15, 17, 12, 5, 12, 21};

    private final int POSITION_SIZE = 12;
    private final int COLOUR_SIZE = 16;
    private final int UV_SIZE = 8;
    private final int INDICES_SIZE = 6;

    private final int FLOATS_PER_POSITION = 3;
    private final int FLOATS_PER_COLOUR = 4;
    private final int FLOATS_PER_UV = 2;

    private final int FLOAT_SIZE = 4;
    private final int SHORT_SIZE = 2;

    private float[][] cachedRows;
    private float[][] cachedUvs;
    private float[] cachedOffsets;

    private float[] packedFloats;
    private short[] indices;

    private ByteBuffer bb1;
    private ByteBuffer bb2;

    private int packedCount;
    private int lastPackedCount;
    private int vertCount;
    private int indicesCount;

    private int stride;

    private int textureHandle;
    private int shaderHandle;
    private int uniformMatrix;
    private int uniformTexture;

    private float uniformScale;

    public TextRenderer() {
        stride = (FLOATS_PER_POSITION + FLOATS_PER_COLOUR + FLOATS_PER_UV) * FLOAT_SIZE;
        bb1 = null;
        bb2 = null;
    }

    public void setTextureHandle(int handle) {
        // GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        // GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle);
        textureHandle = handle;
    }

    public void initShader(int handle) {
        shaderHandle = handle;
        uniformMatrix = GLES20.glGetUniformLocation(shaderHandle, "u_MVPMatrix");
        uniformTexture = GLES20.glGetUniformLocation(shaderHandle, "u_Texture");
    }

    public void setUniformscale(float uniformscale) {
        this.uniformScale = uniformscale;
    }

    public void setTextSize(float size) {
        this.TEXT_WIDTH = size;
    }

    public void initArrays(int size) {
        packedCount = 0;
        vertCount = 0;
        indicesCount = 0;

        int packedSize = (size * POSITION_SIZE) + (size * COLOUR_SIZE) + (size * UV_SIZE);
        packedFloats = new float[packedSize];
        indices = new short[size * INDICES_SIZE];
    }

    /**
     * Works out how many text rows we can fit on screen and precalculates position vectors.
     * Returns number of rows generated
     *
     * @param height Screen height
     */

    public int precalculateRows(int height) {
        int rows = (int) (height / (TEXT_WIDTH * uniformScale));

        cachedRows = new float[rows][12];

        float x;
        float y;
        float yUnit = TEXT_WIDTH * uniformScale;

        // Iterate over row of terrainIndexes and setup vertices/etc for each sprite
        for (int row = 0; row < rows; row++) {
            x = 0f;
            y = 0f + (row * yUnit);

            cachedRows[row][0] = x;
            cachedRows[row][1] = y + (TEXT_WIDTH * uniformScale);
            cachedRows[row][2] = 1f;
            cachedRows[row][3] = x;
            cachedRows[row][4] = y;
            cachedRows[row][5] = 1f;
            cachedRows[row][6] = x + (TEXT_WIDTH * uniformScale);
            cachedRows[row][7] = y;
            cachedRows[row][8] = 1f;
            cachedRows[row][9] = x + (TEXT_WIDTH * uniformScale);
            cachedRows[row][10] = y + (TEXT_WIDTH * uniformScale);
            cachedRows[row][11] = 1f;
        }

        return rows;
    }

    public void precalculateOffsets() {
        cachedOffsets = new float[NUMBER_OF_CHARS];

        // Offset array only applies to original sprite width, so we have to scale it
        float scaleFactor = uniformScale * (TEXT_WIDTH / ORIGINAL_WIDTH);

        for (int i = 0; i < NUMBER_OF_CHARS; i++) {
            cachedOffsets[i] = (CHAR_WIDTHS[i]) * scaleFactor;
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
                offset += ((TEXT_SPACESIZE) * uniformScale);
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

    public void addTextRowData(int row, String text, float[] colour, float alphaModifier) {
        addTextRowData(row, 0f, text, colour, alphaModifier);
    }

    /**
     *  Renders text string at provided row, starting at offset provided.
     *  First row (0) is at bottom of screen
     *
     * @param row
     * @param offsetX
     * @param text
     * @param colour
     * @param alphaModifier
     */

    public void addTextRowData(int row, float offsetX, String text, float[] colour, float alphaModifier) {
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                offsetX += ((TEXT_SPACESIZE) * uniformScale);
                continue;
            }

            // Creating the triangle information
            float[] vec = getRowVector(row, offsetX);

            baseColours[0] = colour[0];
            baseColours[1] = colour[1];
            baseColours[2] = colour[2];
            baseColours[3] = colour[3] - alphaModifier;
            baseColours[4] = colour[0];
            baseColours[5] = colour[1];
            baseColours[6] = colour[2];
            baseColours[7] = colour[3] - alphaModifier;
            baseColours[8] = colour[0];
            baseColours[9] = colour[1];
            baseColours[10] = colour[2];
            baseColours[11] = colour[3] - alphaModifier;
            baseColours[12] = colour[0];
            baseColours[13] = colour[1];
            baseColours[14] = colour[2];
            baseColours[15] = colour[3] - alphaModifier;

            // Add our triangle information to our collection for 1 render call.
            addCharRenderInformation(vec, baseColours, cachedUvs[charIndex]);

            // Calculate the new position
            offsetX += cachedOffsets[charIndex];
        }
    }

    /**
     * Translates row using offsetX/offsetY before rendering.
     *
     * @param row
     * @param offsetX
     * @param offsetY
     * @param text
     * @param colour
     * @param alphaModifier
     */

    public void addTextRowData(int row, float offsetX, float offsetY, String text, float[] colour, float alphaModifier) {
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                offsetX += ((TEXT_SPACESIZE) * uniformScale);
                continue;
            }

            // Creating the triangle information
            float[] vec = getRowVector(row, offsetX, offsetY);

            baseColours[0] = colour[0];
            baseColours[1] = colour[1];
            baseColours[2] = colour[2];
            baseColours[3] = colour[3] - alphaModifier;
            baseColours[4] = colour[0];
            baseColours[5] = colour[1];
            baseColours[6] = colour[2];
            baseColours[7] = colour[3] - alphaModifier;
            baseColours[8] = colour[0];
            baseColours[9] = colour[1];
            baseColours[10] = colour[2];
            baseColours[11] = colour[3] - alphaModifier;
            baseColours[12] = colour[0];
            baseColours[13] = colour[1];
            baseColours[14] = colour[2];
            baseColours[15] = colour[3] - alphaModifier;

            // Add our triangle information to our collection for 1 render call.
            addCharRenderInformation(vec, baseColours, cachedUvs[charIndex]);

            // Calculate the new position
            offsetX += cachedOffsets[charIndex];
        }
    }

    /**
     * Renders text string to screen at given x and y position.
     *
     * @param x
     * @param y
     * @param offsetY
     * @param scale
     * @param text
     * @param colour
     * @param alphaModifier
     */

    public void addTextData(float x, float y, float offsetY, float scale, String text, float[] colour, float alphaModifier) {
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                x += ((TEXT_SPACESIZE) * uniformScale);
                continue;
            }

            baseColours[0] = colour[0];
            baseColours[1] = colour[1];
            baseColours[2] = colour[2];
            baseColours[3] = colour[3] - alphaModifier;
            baseColours[4] = colour[0];
            baseColours[5] = colour[1];
            baseColours[6] = colour[2];
            baseColours[7] = colour[3] - alphaModifier;
            baseColours[8] = colour[0];
            baseColours[9] = colour[1];
            baseColours[10] = colour[2];
            baseColours[11] = colour[3] - alphaModifier;
            baseColours[12] = colour[0];
            baseColours[13] = colour[1];
            baseColours[14] = colour[2];
            baseColours[15] = colour[3] - alphaModifier;

            // Add our triangle information to our collection for 1 render call.
            addCharRenderInformation(getFreeVector(x, y, offsetY, scale), baseColours, cachedUvs[charIndex]);

            // Calculate the new position
            x += cachedOffsets[charIndex];
        }
    }

    private float[] getRowVector(int row, float x, float y) {
        float[] vec = cachedRows[row].clone();

        // Update x values in cached row with letter positions
        vec[0] = x;
        vec[1] += y;

        vec[3] = x;
        vec[4] += y;

        vec[6] = x + (TEXT_WIDTH * uniformScale);
        vec[7] += y;

        vec[9] = x + (TEXT_WIDTH * uniformScale);
        vec[10] += y;

        return vec;
    }

    private float[] getRowVector(int row, float x) {
        float[] vec = cachedRows[row].clone();

        // Update x values in cached row with letter positions
        vec[0] = x;
        vec[3] = x;
        vec[6] = x + (TEXT_WIDTH * uniformScale);
        vec[9] = x + (TEXT_WIDTH * uniformScale);

        return vec;
    }

    private float[] getFreeVector(float x, float y, float offsetY, float textScale) {
        float[] vec = new float[12];

        float size = (TEXT_WIDTH * uniformScale * textScale);

        y += (offsetY * size);

        vec[0] = x;
        vec[1] = y + size;
        vec[2] = 1f;
        vec[3] = x;
        vec[4] = y;
        vec[5] = 1f;
        vec[6] = x + size;
        vec[7] = y;
        vec[8] = 1f;
        vec[9] = x + size;
        vec[10] = y + size;
        vec[11] = 1f;

        return vec;
    }

    public void addCharRenderInformation(float[] vec, float[] colours, float[] uv) {
        // Translate the indices to align with the location in our array of vertices
        short base = (short) (vertCount / 3);

        // Add floats for each vertex into packed array.
        // Position vertices, colour floats and UV coords are packed in this format: x, y, z, r, g, b, a, x, y

        for (int i = 0; i < 3; i++) {
            packedFloats[packedCount] = vec[i];
            packedCount++;
            vertCount++;
        }

        for (int i = 0; i < 4; i++) {
            packedFloats[packedCount] = colours[i];
            packedCount++;
        }

        for (int i = 0; i < 2; i++) {
            packedFloats[packedCount] = uv[i];
            packedCount++;
        }

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

        // Add indices

        for (int j = 0; j < INDICES.length; j++) {
            indices[indicesCount] = (short) (base + INDICES[j]);
            indicesCount++;
        }
    }

    public void renderText(float[] matrix) {
        GLES20.glUseProgram(shaderHandle);

        if (packedCount == 0) {
            return;
        }

        checkBufferCapacity();

        FloatBuffer floatBuffer = bb1.asFloatBuffer();
        BufferUtils.copy(packedFloats, floatBuffer, packedCount, 0);

        ShortBuffer drawListBuffer = bb2.asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indicesCount);

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

        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);

        GLES20.glUniform1i(uniformTexture, 1);

        // render the triangle
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.COLOUR);
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
}
