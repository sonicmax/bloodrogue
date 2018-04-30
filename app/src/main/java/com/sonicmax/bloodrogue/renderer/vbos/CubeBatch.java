package com.sonicmax.bloodrogue.renderer.vbos;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CubeBatch {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final int VERTICES_PER_CUBE = 36;
    private final int VERTEX_DATA_SIZE = 3;
    private final int NORMAL_DATA_SIZE = 3;
    private final int UV_DATA_SIZE = 2;
    private final int BYTES_PER_FLOAT = 4;

    private int cubeBufferId;

    private final int numberOfCubes;

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer uvBuffer;

    public CubeBatch(float[] vertices, float[] normals, float[] uvCoords, int numberOfCubes) {
        this.numberOfCubes = numberOfCubes;

        // Create interleaved buffer for VBO, and separate position/UV buffers for depth map rendering.
        FloatBuffer cubeBuffer = createInterleavedBuffer(vertices, normals, uvCoords, numberOfCubes);

        // Second, copy these buffers into OpenGL's memory.
        cubeBufferId = createVBO(cubeBuffer);
        cubeBuffer.limit(0);
        cubeBuffer = null;

        // Todo: Figure out how we can use VBO for all methods. Duplicating the data is silly

        // Desperate attempt to free up more memory:
        System.gc();

        // Now create buffers for depth map rendering
        vertexBuffer = createFloatBuffer(vertices);
        uvBuffer = createFloatBuffer(uvCoords);

        Log.v(LOG_TAG, "VBO id: " + cubeBufferId);
    }

    private int createVBO(FloatBuffer buffer) {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * BYTES_PER_FLOAT, buffer, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return buffers[0];
    }

    private FloatBuffer createInterleavedBuffer(float[] vertices, float[] normals, float[] uvCoords, int numberOfCubes) {
        Log.v(LOG_TAG, "Allocating buffer for " + numberOfCubes + " cubes");
        final int cubeDataLength = vertices.length + normals.length + uvCoords.length;

        int vertexOffset = 0;
        int normalOffset = 0;
        int textureOffset = 0;

        final FloatBuffer cubeBuffer = ByteBuffer.allocateDirect(cubeDataLength * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        for (int i = 0; i < numberOfCubes; i++) {
            for (int v = 0; v < VERTICES_PER_CUBE; v++) {
                cubeBuffer.put(vertices, vertexOffset, VERTEX_DATA_SIZE);
                vertexOffset += VERTEX_DATA_SIZE;
                cubeBuffer.put(normals, normalOffset, NORMAL_DATA_SIZE);
                normalOffset += NORMAL_DATA_SIZE;
                cubeBuffer.put(uvCoords, textureOffset, UV_DATA_SIZE);
                textureOffset += UV_DATA_SIZE;
            }
        }

        cubeBuffer.position(0);

        return cubeBuffer;
    }

    private FloatBuffer createFloatBuffer(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        BufferUtils.copy(data, buffer, data.length, 0);

        return buffer;
    }

    /**
     * Updates vertices for cube in buffer.
     *
     * @param index Cube to modify (0-indexed)
     * @param data Updated data
     */

    public void updateVertices(int index, float[] data) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cubeBufferId);

        int[] params = new int[1];
        GLES20.glGetBufferParameteriv(GLES20.GL_ARRAY_BUFFER, GLES20.GL_BUFFER_SIZE, params, 0);
        int bufferSize = params[0];

        final int stride = (VERTEX_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;
        final int size = VERTEX_DATA_SIZE * BYTES_PER_FLOAT;

        // Find start point to modify
        int offset = (index * VERTICES_PER_CUBE) * stride;

        // Make sure that we are updating an index that exists in buffer, and make sure that our updated
        // data does not exceed buffer capacity.

        if (offset > bufferSize) {
            Log.e(LOG_TAG, "Error: index for updateVertices() is out of range. \n\tBuffer size: " + bufferSize + "\n\tStart point: " + offset);
            return;
        }

        int endRange = offset + (VERTICES_PER_CUBE * VERTEX_DATA_SIZE);

        if (endRange > bufferSize) {
            Log.e(LOG_TAG, "Error: data for updateVertices() would exceed buffer capacity. \n\tBuffer size: " + bufferSize + "\n\tEnd point: " + endRange);
            return;
        }

        FloatBuffer floatBuffer = createFloatBuffer(data);

        // Vertices are stored as first piece of data in interleaved buffer
        for (int i = 0; i < VERTICES_PER_CUBE; i++) {
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                    offset,
                    size,
                    floatBuffer.position(i * VERTEX_DATA_SIZE));

            offset += stride;
        }

        floatBuffer.limit(0);
        floatBuffer = null;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void render() {
        final int count = numberOfCubes * VERTICES_PER_CUBE;
        final int stride = (VERTEX_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;

        // Pass in the position information
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cubeBufferId);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(ShaderAttributes.POSITION,
                VERTEX_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                0);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glVertexAttribPointer(ShaderAttributes.NORMAL,
                NORMAL_DATA_SIZE, GLES20.GL_FLOAT,
                false,
                stride,
                VERTEX_DATA_SIZE * BYTES_PER_FLOAT);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(ShaderAttributes.TEXCOORD,
                UV_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                (VERTEX_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT);

        // Draw the cubes.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

        // Clear the currently bound buffer (so future OpenGL calls do not use this buffer).
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    public void renderSkyBox() {
        final int count = numberOfCubes * VERTICES_PER_CUBE;
        final int stride = 0;

        // Pass in the position information so we can calculate depth
        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.POSITION,
                VERTEX_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                vertexBuffer);

        // Draw the cubes.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.NORMAL);
    }

    public void renderDepthMap() {
        final int count = numberOfCubes * VERTICES_PER_CUBE;
        final int stride = 0;

        // Pass in the position information so we can calculate depth
        GLES20.glEnableVertexAttribArray(ShaderAttributes.SHADOW_POSITION);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.SHADOW_POSITION,
                VERTEX_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                vertexBuffer);

        // Pass in UV coords - this is so we can discard fragments based on the texel alpha values
        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.TEXCOORD,
                UV_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                uvBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.SHADOW_POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    public void release() {
        // Delete buffers from OpenGL's memory
        final int[] buffersToDelete = new int[] {cubeBufferId};
        GLES20.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
    }
}
