package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Creates a batch of 2d sprites in 3d space to be rendered
 */

public class SpriteBatch {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final int VERTICES_PER_SPRITE = 6;
    private final int POSITION_DATA_SIZE = 3;
    private final int NORMAL_DATA_SIZE = 3;
    private final int UV_DATA_SIZE = 2;
    private final int BYTES_PER_FLOAT = 4;
    private final int BYTES_PER_SHORT = 2;

    private final int spriteBufferId;

    private final int numberOfSprites;

    private final FloatBuffer positionBuffer;
    private final FloatBuffer uvBuffer;

    public SpriteBatch(float[] spritePositions, float[] spriteNormals, float[] spriteUvs, int numberOfSprites) {
        this.numberOfSprites = numberOfSprites;

        // Create interleaved buffer for VBO, and separate position/UV buffers for depth map rendering.
        FloatBuffer spriteBuffer = createInterleavedBuffer(spritePositions, spriteNormals, spriteUvs, numberOfSprites);
        positionBuffer = createFloatBuffer(spritePositions);
        uvBuffer = createFloatBuffer(spriteUvs);

        // Second, copy these buffers into OpenGL's memory.
        spriteBufferId = createVBO(spriteBuffer);
        spriteBuffer.limit(0);
        spriteBuffer = null;
    }

    private int createVBO(FloatBuffer buffer) {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * BYTES_PER_FLOAT, buffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return buffers[0];
    }

    private FloatBuffer createInterleavedBuffer(float[] spritePositions, float[] spriteNormals, float[] spriteUvs, int numberOfSprites) {
        Log.v(LOG_TAG, "Allocating buffer for " + numberOfSprites + " cubes");
        final int spriteDataLength = spritePositions.length + spriteNormals.length + spriteUvs.length;

        int positionOffset = 0;
        int normalOffset = 0;
        int textureOffset = 0;

        final FloatBuffer spriteBuffer = ByteBuffer.allocateDirect(spriteDataLength * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        for (int i = 0; i < numberOfSprites; i++) {
            for (int v = 0; v < 6; v++) {
                spriteBuffer.put(spritePositions, positionOffset, POSITION_DATA_SIZE);
                positionOffset += POSITION_DATA_SIZE;
                spriteBuffer.put(spriteNormals, normalOffset, NORMAL_DATA_SIZE);
                normalOffset += NORMAL_DATA_SIZE;
                spriteBuffer.put(spriteUvs, textureOffset, UV_DATA_SIZE);
                textureOffset += UV_DATA_SIZE;
            }
        }

        spriteBuffer.position(0);

        return spriteBuffer;
    }

    public void render() {
        final int count = numberOfSprites * VERTICES_PER_SPRITE;
        final int stride = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;
        final int normalOffset = POSITION_DATA_SIZE * BYTES_PER_FLOAT;
        final int uvOffset = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteBufferId);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(ShaderAttributes.POSITION,
                POSITION_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                0);

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

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.NORMAL);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
    }

    public void renderDepthMap() {
        final int count = numberOfSprites * VERTICES_PER_SPRITE;
        final int stride = 0;

        // Pass in the position information so we can calculate depth
        GLES20.glEnableVertexAttribArray(ShaderAttributes.SHADOW_POSITION);
        GLES20.glVertexAttribPointer(
                ShaderAttributes.SHADOW_POSITION,
                POSITION_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                positionBuffer);

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

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.SHADOW_POSITION);
        GLES20.glDisableVertexAttribArray(ShaderAttributes.TEXCOORD);
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
