package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public class TextRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final float UV_BOX_WIDTH = 0.0625f;
    private final float TEXT_WIDTH = 32f;
    private final float TEXT_SPACESIZE = 20f;
    private final int SPRITES_PER_ROW = 16;
    private final int NUMBER_OF_CHARS = 94;

    private final short[] mIndices = {0, 1, 2, 0, 2, 3};

    // Sizes of each letter. These were scraped from the output provided by Codehead's Bitmap Font Generator
    // (chars 33 to 126)
    private final int[] charWidths = {17, 10, 5, 10, 21, 15, 21,
            13, 8, 12, 12, 19, 15, 8, 10, 8, 18, 17, 10, 15, 15, 15, 17, 17, 17, 17, 17, 8, 8, 13,
            13, 13, 13, 21, 15, 16, 17, 16, 13, 13, 16, 16, 5, 14, 15, 13, 21, 16, 17, 16, 15, 16,
            17, 15, 16, 15, 21, 15, 16, 15, 10, 18, 11, 16, 21, 8, 17, 16, 17, 16, 17, 13, 16, 16,
            5, 10, 13, 5, 21, 16, 17, 16, 16, 16, 17, 10, 16, 15, 21, 16, 15, 17, 12, 5, 12, 21};

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

    private int mTextureHandle;
    private int mShaderHandle;

    private float mUniformScale;

    public ArrayList<TextObject> mText;

    public TextRenderer() {
        // Create our container
        mText = new ArrayList<>();

        // Create the arrays
        vecs = new float[3 * 10];
        colors = new float[4 * 10];
        uvs = new float[2 * 10];
        indices = new short[10];
    }

    public void setTextureHandle(int val) {
        mTextureHandle = val;
    }

    public void setShaderProgramHandle(int handle) {
        mShaderHandle = handle;
    }

    public void setUniformscale(float uniformscale) {
        this.mUniformScale = uniformscale;
    }

    public void addText(TextObject obj) {
        // Add text object to our collection
        mText.add(obj);
    }

    public void clear() {
        mText = new ArrayList<>();
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

        for (int i = 0; i < NUMBER_OF_CHARS; i++) {
            cachedOffsets[i] = (charWidths[i]) * mUniformScale;
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
        for(int i=0;i<vec.length;i++)
        {
            vecs[index_vecs] = vec[i];
            index_vecs++;
        }

        // We should add the colors.
        for(int i=0;i<cs.length;i++)
        {
            colors[index_colors] = cs[i];
            index_colors++;
        }

        // We should add the uvs
        for(int i=0;i<uv.length;i++)
        {
            uvs[index_uvs] = uv[i];
            index_uvs++;
        }

        // We handle the indices
        for(int j=0;j<indi.length;j++)
        {
            indices[index_indices] = (short) (base + indi[j]);
            index_indices++;
        }
    }

    public void prepareDrawInfo() {
        // Reset the indices.
        index_vecs = 0;
        index_indices = 0;
        index_uvs = 0;
        index_colors = 0;

        // Get the total amount of characters
        int charCount = 0;
        int size = mText.size();

        for (int i = 0; i < size; i++) {
            charCount += mText.get(i).text.length();
        }

        // Create the arrays we need with the correct size.
        vecs = null;
        colors = null;
        uvs = null;
        indices = null;

        vecs = new float[charCount * 12];
        colors = new float[charCount * 16];
        uvs = new float[charCount * 8];
        indices = new short[charCount * 6];
    }

    public void prepareText() {
        prepareDrawInfo();

        int size = mText.size();

        for (int i = 0; i < size; i++) {
            convertTextToTriangleInfo(mText.get(i));
        }
    }

    private int convertCharValueToUvIndex(int value) {
        // ccra_font starts at char 33, so we can just subtract 33 to get the respective uv texture index.
        return value - 33;
    }

    private void convertTextToTriangleInfo(TextObject val) {
        float x = val.x;
        float y = val.y;
        String text = val.text;

        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            int charIndex = convertCharValueToUvIndex((int) c);

            if (charIndex == -1) {
                // Space or unknown character
                x += ((TEXT_SPACESIZE) * mUniformScale);
                continue;
            }

            // Creating the triangle information
            float[] vec;

            if (val.row == -1) {
                vec = getFreeVector(x, y);
            } else {
                vec = getRowVector(val.row, x);
            }

            float[] colors = new float[] {
                    val.color[0], val.color[1], val.color[2], val.color[3],
                    val.color[0], val.color[1], val.color[2], val.color[3],
                    val.color[0], val.color[1], val.color[2], val.color[3],
                    val.color[0], val.color[1], val.color[2], val.color[3]
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

    private float[] getFreeVector(float x, float y) {
        float[] vec = new float[12];

        vec[0] = x;
        vec[1] = y + (TEXT_WIDTH * mUniformScale);
        vec[2] = 0.99f;
        vec[3] = x;
        vec[4] = y;
        vec[5] = 0.99f;
        vec[6] = x + (TEXT_WIDTH * mUniformScale);
        vec[7] = y;
        vec[8] = 0.99f;
        vec[9] = x + (TEXT_WIDTH * mUniformScale);
        vec[10] = y + (TEXT_WIDTH * mUniformScale);
        vec[11] = 0.99f;

        return vec;
    }

    public void renderText(float[] matrix) {
        FloatBuffer vertexBuffer;
        FloatBuffer textureBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer drawListBuffer;

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
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle);

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
