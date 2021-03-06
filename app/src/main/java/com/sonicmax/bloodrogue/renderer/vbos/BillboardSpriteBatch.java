package com.sonicmax.bloodrogue.renderer.vbos;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Creates a batch of 2d sprites in 3d space to be rendered. Similar to SpriteBatch, but passes the
 * centre point of each quad with other attributes so we can calculate billboard rotation in vertex shader.
 */

public class BillboardSpriteBatch {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final int VERTICES_PER_SPRITE = 6;

    private final int BILLBOARD_DATA_SIZE = 4; // vec4(x, y, z, cornerIndex)
    private final int NORMAL_DATA_SIZE = 3; // vec3(x, y, z)
    private final int UV_DATA_SIZE = 2; // vec2(x, y)
    private final int BYTES_PER_FLOAT = 4;

    private final int spriteBufferId;

    private final int numberOfSprites;

    // private final FloatBuffer billboardBuffer;
    // private final FloatBuffer uvBuffer;

    /**
     * Prepares batch of sprites for billboard shader. The main difference between this class and
     * SpriteBatch is that here we just pass in the centre point and corner index for each vertex
     * packed into a vec4.
     *
     * @param billboardData
     * @param normals
     * @param uvCoords
     * @param numberOfSprites
     * @param drawMode
     */

    public BillboardSpriteBatch(float[] billboardData, float[] normals, float[] uvCoords, int numberOfSprites, int drawMode) {
        this.numberOfSprites = numberOfSprites;

        // Create interleaved buffer for VBO, and separate position/UV buffers for depth map rendering.
        FloatBuffer spriteBuffer = createInterleavedBuffer(billboardData, normals, uvCoords, numberOfSprites);

        // Second, copy these buffers into OpenGL's memory.
        spriteBufferId = createVBO(spriteBuffer, drawMode);
        spriteBuffer.limit(0);
        spriteBuffer = null;

        // Todo: Figure out how we can use VBO for all methods. Duplicating the data is silly

        // Desperate attempt to free up more memory:
        System.gc();

        // Now create buffers for depth map rendering
        // billboardBuffer = createFloatBuffer(billboardData);
        // uvBuffer = createFloatBuffer(uvCoords);

        Log.v(LOG_TAG, "Sprite VBO id: " + spriteBufferId);
    }

    private int createVBO(FloatBuffer buffer, int drawMode) {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * BYTES_PER_FLOAT, buffer, drawMode);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return buffers[0];
    }

    private FloatBuffer createInterleavedBuffer(float[] billboardData, float[] normals, float[] uvCoords, int numberOfSprites) {
        Log.v(LOG_TAG, "Allocating buffer for " + numberOfSprites + " sprites");
        final int spriteDataLength = billboardData.length + normals.length + uvCoords.length;

        int bbDataOffset = 0;
        int normalOffset = 0;
        int textureOffset = 0;

        final FloatBuffer spriteBuffer = ByteBuffer.allocateDirect(spriteDataLength * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        // Interleave floats for each vertex in this order: {v0, v1, v2, n0, n1, n2, u, v}
        for (int i = 0; i < numberOfSprites; i++) {
            for (int v = 0; v < 6; v++) {
                spriteBuffer.put(billboardData, bbDataOffset, BILLBOARD_DATA_SIZE);
                bbDataOffset += BILLBOARD_DATA_SIZE;
                spriteBuffer.put(normals, normalOffset, NORMAL_DATA_SIZE);
                normalOffset += NORMAL_DATA_SIZE;
                spriteBuffer.put(uvCoords, textureOffset, UV_DATA_SIZE);
                textureOffset += UV_DATA_SIZE;
            }
        }

        spriteBuffer.position(0);

        return spriteBuffer;
    }

    public void updateBillboardData(int index, float[] data) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteBufferId);

        int[] params = new int[1];
        GLES20.glGetBufferParameteriv(GLES20.GL_ARRAY_BUFFER, GLES20.GL_BUFFER_SIZE, params, 0);
        int bufferSize = params[0];

        final int stride = (BILLBOARD_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;
        final int size = BILLBOARD_DATA_SIZE * BYTES_PER_FLOAT;

        // Find start point to modify
        int offset = (index * VERTICES_PER_SPRITE) * stride;

        // Make sure that we are updating an index that exists in buffer, and make sure that our updated
        // data does not exceed buffer capacity.

        if (offset > bufferSize) {
            Log.e(LOG_TAG, "Error: index for updateBillboardData() is out of range. \n\tBuffer size: " + bufferSize + "\n\tStart point: " + offset);
            return;
        }

        int endRange = offset + (VERTICES_PER_SPRITE * BILLBOARD_DATA_SIZE);

        if (endRange > bufferSize) {
            Log.e(LOG_TAG, "Error: data for updateBillboardData() would exceed buffer capacity. \n\tBuffer size: " + bufferSize + "\n\tEnd point: " + endRange);
            return;
        }

        FloatBuffer floatBuffer = createFloatBuffer(data);

        // Vertices are stored as first piece of data in interleaved buffer.
        // For each vertice in sprite, we copy
        for (int i = 0; i < VERTICES_PER_SPRITE; i++) {
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                    offset,
                    size,
                    floatBuffer.position(i * BILLBOARD_DATA_SIZE));

            offset += stride;
        }

        floatBuffer.limit(0);
        floatBuffer = null;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void updateUvCoords(int index, float[] newUvCoords) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteBufferId);

        // Find start point to modify
        int stride = (BILLBOARD_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;
        int offset = index * stride;

        // UV coords are stored after the position and normal data in interleaved buffer.
        // We need to add this value to offset when using glBufferSubData
        int uvPosition = (BILLBOARD_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT;

        FloatBuffer floatBuffer = createFloatBuffer(newUvCoords);

        for (int i = 0; i < VERTICES_PER_SPRITE; i++) {
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                    offset + uvPosition,
                    UV_DATA_SIZE * BYTES_PER_FLOAT,
                    floatBuffer.position(i * UV_DATA_SIZE));

            offset += stride;
        }

        floatBuffer.limit(0);
        floatBuffer = null;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void render() {
        final int count = numberOfSprites * VERTICES_PER_SPRITE;
        final int stride = (BILLBOARD_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;

        final int billboardOffset = 0;
        final int normalOffset = BILLBOARD_DATA_SIZE * BYTES_PER_FLOAT;
        final int uvOffset = (BILLBOARD_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteBufferId);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.BILLBOARD_DATA);
        GLES20.glVertexAttribPointer(ShaderAttributes.BILLBOARD_DATA,
                BILLBOARD_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                billboardOffset);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glVertexAttribPointer(ShaderAttributes.NORMAL,
                NORMAL_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                normalOffset);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.TEXCOORD);
        GLES20.glVertexAttribPointer(ShaderAttributes.TEXCOORD,
                UV_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                uvOffset);

        // Draw the sprites.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.BILLBOARD_DATA);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    public void renderDepthMap() {
        /*final int count = numberOfSprites * VERTICES_PER_SPRITE;
        final int stride = 0;

        // When rendering depth map we can ignore normals as we don't require lighting, shadows, etc

        // Pass in the position data so we can calculate depth
        GLES20.glEnableVertexAttribArray(ShaderAttributes.BILLBOARD_DATA);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.BILLBOARD_DATA,
                BILLBOARD_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                billboardBuffer);

        // Pass in texture coord data so we can check alpha values
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
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);*/
    }

    public void release() {
        // Delete buffers from OpenGL's memory
        final int[] buffersToDelete = new int[] {spriteBufferId};
        GLES20.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
    }

    private FloatBuffer createFloatBuffer(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        BufferUtils.copy(data, buffer, data.length, 0);

        return buffer;
    }
}
