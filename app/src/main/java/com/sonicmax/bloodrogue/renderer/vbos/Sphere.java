package com.sonicmax.bloodrogue.renderer.vbos;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.geometry.SphereData;
import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Sphere {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final int POSITION_DATA_SIZE = 3;
    private final int NORMAL_DATA_SIZE = 3;
    private final int UV_DATA_SIZE = 2;
    private final int BYTES_PER_FLOAT = 4;

    private int sphereBufferId;

    private final int verticesPerSphere;

    public Sphere(SphereData sphereData, int drawMode) {
        FloatBuffer sphereBuffer = createInterleavedBuffer(sphereData.vertices, sphereData.normals, sphereData.texCoords);

        this.verticesPerSphere = sphereData.vertices.length / POSITION_DATA_SIZE;

        Log.v(LOG_TAG, "Sun vertices: " + verticesPerSphere);

        // Second, copy these buffers into OpenGL's memory.
        sphereBufferId = createVBO(sphereBuffer, drawMode);
        sphereBuffer.limit(0);
        sphereBuffer = null;

        Log.v(LOG_TAG, "Sphere VBO id: " + sphereBufferId);
    }

    private int createVBO(FloatBuffer buffer, int drawMode) {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * BYTES_PER_FLOAT, buffer, drawMode);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return buffers[0];
    }

    public void updatePosition(float[] newPosition) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereBufferId);

        // Find start point to modify
        int stride = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;
        int offset = 0;

        FloatBuffer floatBuffer = createFloatBuffer(newPosition);

        for (int i = 0; i < verticesPerSphere; i++) {
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                    offset,
                    POSITION_DATA_SIZE * BYTES_PER_FLOAT,
                    floatBuffer.position(i * POSITION_DATA_SIZE));

            offset += stride;
        }

        floatBuffer.limit(0);
        floatBuffer = null;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private FloatBuffer createInterleavedBuffer(float[] positions, float[] normals, float[] texCoords) {
        final int sphereDataLength = positions.length + normals.length + texCoords.length;

        int spherePositionOffset = 0;
        int sphereNormalOffset = 0;
        int sphereTextureOffset = 0;

        final FloatBuffer sphereBuffer = ByteBuffer.allocateDirect(sphereDataLength * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        for (int v = 0; v < verticesPerSphere; v++) {
            sphereBuffer.put(positions, spherePositionOffset, POSITION_DATA_SIZE);
            spherePositionOffset += POSITION_DATA_SIZE;
            sphereBuffer.put(normals, sphereNormalOffset, NORMAL_DATA_SIZE);
            sphereNormalOffset += NORMAL_DATA_SIZE;
            sphereBuffer.put(texCoords, sphereTextureOffset, UV_DATA_SIZE);
            sphereTextureOffset += UV_DATA_SIZE;
        }

        sphereBuffer.position(0);

        return sphereBuffer;
    }

    private FloatBuffer createFloatBuffer(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        BufferUtils.copy(data, buffer, data.length, 0);

        return buffer;
    }

    public void render() {
        final int stride = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;

        // Pass in the position information
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereBufferId);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(ShaderAttributes.POSITION,
                POSITION_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                0);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glVertexAttribPointer(ShaderAttributes.NORMAL,
                NORMAL_DATA_SIZE, GLES20.GL_FLOAT,
                false,
                stride,
                POSITION_DATA_SIZE * BYTES_PER_FLOAT);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(ShaderAttributes.TEXCOORD,
                UV_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                (POSITION_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT);

        // Draw the sphere.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, verticesPerSphere);

        // Clear the currently bound buffer (so future OpenGL calls do not use this buffer).
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    public void release() {
        // Delete buffers from OpenGL's memory
        final int[] buffersToDelete = new int[] {sphereBufferId};
        GLES20.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
    }
}
