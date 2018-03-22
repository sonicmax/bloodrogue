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
 * Renders various different types of weather effects
 */

public class WeatherRenderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    /**
     * Sprite sheet is 512x512, containing 16x16 textures.
     * These will be upscaled to 64x64 when rendering
     */

    private final float TARGET_WIDTH = 64f; // Upscaled from 16

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

    private int packedCount;
    private int stride;

    private final int POSITION_SIZE = 12;
    private final int COLOUR_SIZE = 16;
    private final int INDICES_SIZE = 6;

    private final int FLOATS_PER_POSITION = 3;
    private final int FLOATS_PER_COLOUR = 4;

    private final int FLOAT_SIZE = 4;
    private final int SHORT_SIZE = 2;

    private float[] packedFloats;
    private short[] indices;

    private int vertCount;
    private int indicesCount;

    private float[] fullscreenVec;

    // Handles for OpenGL
    private int mBasicShaderHandle;
    private int uniformMatrix;
    private int uniformLighting;
    private int uniformTime;
    private int uniformResolution;
    private int uniformIntensity;

    private float intensity;
    private float[] resolution;
    private float ambientLighting;

    private ByteBuffer bb1;
    private ByteBuffer bb2;

    public WeatherRenderer() {
        intensity = 1.0f;
        ambientLighting = 1.0f;
        resolution = new float[] {1.0f, 1.0f};

        stride = (FLOATS_PER_POSITION + FLOATS_PER_COLOUR) * FLOAT_SIZE;
        bb1 = null;
        bb2 = null;
    }

    public void initShader(int handle) {
        mBasicShaderHandle = handle;
        uniformMatrix = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_MVPMatrix");
        uniformTime = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_Time");
        uniformLighting = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_Lighting");
        uniformResolution = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_Resolution");
        uniformIntensity = GLES20.glGetUniformLocation(mBasicShaderHandle, "u_Intensity");
    }

    public void initArrays(int length) {
        int packedSize = (length * POSITION_SIZE) + (length * COLOUR_SIZE);

        packedFloats = new float[packedSize];
        indices = new short[length * INDICES_SIZE];
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public void resetInternalCount() {
        vertCount = 0;
        indicesCount = 0;
        packedCount = 0;
    }

    public void setFullscreenVertices(float screenWidth, float screenHeight) {
        resolution = new float[2];
        resolution[0] = screenWidth;
        resolution[1] = screenHeight;

        fullscreenVec = new float[12];

        fullscreenVec[0] = 0f;
        fullscreenVec[1] = 0f + screenHeight;
        fullscreenVec[2] = 1f;

        fullscreenVec[3] = 0f;
        fullscreenVec[4] = 0f;
        fullscreenVec[5] = 1f;

        fullscreenVec[6] = 0f + screenWidth;
        fullscreenVec[7] = 0f;
        fullscreenVec[8] = 1f;

        fullscreenVec[9] = 0f + screenWidth;
        fullscreenVec[10] = 0f + screenHeight;
        fullscreenVec[11] = 1f;
    }

    public void addEffect(float[] tint, float lighting, float alpha) {
        ambientLighting = lighting;

        baseColours[0] = tint[0] * lighting; // r
        baseColours[1] = tint[1] * lighting; // g
        baseColours[2] = tint[2] * lighting; // b
        baseColours[3] = alpha;
        baseColours[4] = tint[0] * lighting;
        baseColours[5] = tint[1] * lighting;
        baseColours[6] = tint[2] * lighting;
        baseColours[7] = alpha;
        baseColours[8] = tint[0] * lighting;
        baseColours[9] = tint[1] * lighting;
        baseColours[10] = tint[2] * lighting;
        baseColours[11] = alpha;
        baseColours[12] = tint[0] * lighting;
        baseColours[13] = tint[1] * lighting;
        baseColours[14] = tint[2] * lighting;
        baseColours[15] = alpha;

        addRenderInformation(fullscreenVec, baseColours);
    }

    /**
     * Vectors, baseColours and UV coords are stored in a packed array.
     * Indices are stored in an array of shorts.
     *
     * @param vec
     * @param colours
     */

    private void addRenderInformation(float[] vec, float[] colours) {
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

        // Indices

        for (int j = 0; j < INDICES.length; j++) {
            indices[indicesCount] = (short) (base + INDICES[j]);
            indicesCount++;
        }
    }

    public void renderWeather(float[] matrix, float time) {
        GLES20.glUseProgram(mBasicShaderHandle);

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

        // Pass MVP matrix to shader
        GLES20.glUniformMatrix4fv(uniformMatrix, 1, false, matrix, 0);
        GLES20.glUniform1f(uniformTime, time);
        GLES20.glUniform1f(uniformIntensity, intensity);
        GLES20.glUniform1f(uniformLighting, ambientLighting);
        GLES20.glUniform2f(uniformResolution, resolution[0], resolution[1]);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
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
