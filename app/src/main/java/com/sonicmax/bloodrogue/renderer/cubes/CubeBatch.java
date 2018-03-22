package com.sonicmax.bloodrogue.renderer.cubes;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.Shader;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CubeBatch {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final int POSITION_DATA_SIZE = 3;
    private final int NORMAL_DATA_SIZE = 3;
    private final int UV_DATA_SIZE = 2;
    private final int BYTES_PER_FLOAT = 4;

    private int cubeBufferId;

    private final int numberOfCubes;

    private FloatBuffer cubeBuffer;
    private FloatBuffer positionBuffer;
    private FloatBuffer uvBuffer;

    public CubeBatch(float[] cubePositions, float[] cubeNormals, float[] cubeTextureCoordinates, int numberOfCubes) {
        this.numberOfCubes = numberOfCubes;

        // Create interleaved buffer for VBO, and separate position/UV buffers for depth map rendering.
        cubeBuffer = getInterleavedBuffer(cubePositions, cubeNormals, cubeTextureCoordinates, numberOfCubes);
        positionBuffer = getFloatBuffer(cubePositions);
        uvBuffer = getFloatBuffer(cubeTextureCoordinates);

        // Second, copy these buffers into OpenGL's memory.
        // cubeBufferId = createVBO(cubeBuffer);
        // cubeBuffer.limit(0);
        // cubeBuffer = null;
    }

    private int createVBO(FloatBuffer buffer) {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * BYTES_PER_FLOAT, buffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return buffers[0];
    }

    private FloatBuffer getInterleavedBuffer(float[] cubePositions, float[] cubeNormals, float[] cubeTextureCoordinates, int numberOfCubes) {
        Log.v(LOG_TAG, "Allocating buffer for " + numberOfCubes + " cubes");
        final int cubeDataLength = cubePositions.length + cubeNormals.length + cubeTextureCoordinates.length;

        int cubePositionOffset = 0;
        int cubeNormalOffset = 0;
        int cubeTextureOffset = 0;

        final FloatBuffer cubeBuffer = ByteBuffer.allocateDirect(cubeDataLength * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        for (int i = 0; i < numberOfCubes; i++) {
            for (int v = 0; v < 36; v++) {
                cubeBuffer.put(cubePositions, cubePositionOffset, POSITION_DATA_SIZE);
                cubePositionOffset += POSITION_DATA_SIZE;
                cubeBuffer.put(cubeNormals, cubeNormalOffset, NORMAL_DATA_SIZE);
                cubeNormalOffset += NORMAL_DATA_SIZE;
                cubeBuffer.put(cubeTextureCoordinates, cubeTextureOffset, UV_DATA_SIZE);
                cubeTextureOffset += UV_DATA_SIZE;
            }
        }

        cubeBuffer.position(0);

        return cubeBuffer;
    }

    private FloatBuffer getFloatBuffer(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        BufferUtils.copy(data, buffer, data.length, 0);

        return buffer;
    }

    public void render() {
        final int count = numberOfCubes * 36;
        final int stride = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;

        // Pass in the position information
        // GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cubeBufferId);

        GLES20.glEnableVertexAttribArray(Shader.POSITION);
        GLES20.glVertexAttribPointer(Shader.POSITION,
                POSITION_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                cubeBuffer.position(0));

        GLES20.glEnableVertexAttribArray(Shader.NORMAL);
        GLES20.glVertexAttribPointer(Shader.NORMAL,
                NORMAL_DATA_SIZE, GLES20.GL_FLOAT,
                false,
                stride,
                cubeBuffer.position(POSITION_DATA_SIZE));

        GLES20.glEnableVertexAttribArray(Shader.TEXCOORD);
        GLES20.glVertexAttribPointer(Shader.TEXCOORD,
                UV_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                cubeBuffer.position(POSITION_DATA_SIZE + NORMAL_DATA_SIZE));

        // Draw the cubes.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

        // Clear the currently bound buffer (so future OpenGL calls do not use this buffer).
        // GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDisableVertexAttribArray(Shader.POSITION);
        GLES20.glDisableVertexAttribArray(Shader.NORMAL);
        GLES20.glDisableVertexAttribArray(Shader.TEXCOORD);
    }

    public void renderDepthMap() {
        final int count = numberOfCubes * 36;

        // Make sure these are disabled - not used in depth map shader
        GLES20.glDisableVertexAttribArray(Shader.NORMAL);

        // Pass in the position information so we can calculate depth
        GLES20.glEnableVertexAttribArray(Shader.SHADOW_POSITION);
        GLES20.glVertexAttribPointer(
                Shader.SHADOW_POSITION,
                POSITION_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                0,
                positionBuffer);

        // Pass in UV coords - this is so we can discard fragments based on the texel alpha values
        GLES20.glEnableVertexAttribArray(Shader.TEXCOORD);
        GLES20.glVertexAttribPointer(
                Shader.TEXCOORD,
                UV_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                0,
                uvBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

        GLES20.glDisableVertexAttribArray(Shader.SHADOW_POSITION);
        GLES20.glDisableVertexAttribArray(Shader.TEXCOORD);
    }

    public void release() {
        // Delete buffers from OpenGL's memory
        final int[] buffersToDelete = new int[] {cubeBufferId};
        GLES20.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
    }
}
