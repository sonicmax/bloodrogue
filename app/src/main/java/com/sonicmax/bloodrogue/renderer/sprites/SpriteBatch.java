package com.sonicmax.bloodrogue.renderer.sprites;

import android.opengl.GLES20;
import android.util.Log;

import com.sonicmax.bloodrogue.renderer.shaders.Shader;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Creates a batch of 2d sprites in 3d space to be rendered
 */

public class SpriteBatch {
    private final String LOG_TAG = this.getClass().getSimpleName();

    public static final float[] SPRITE_FRONT_NORMAL_DATA = {
            // Front face
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f
    };

    private final int POSITION_DATA_SIZE = 3;
    private final int NORMAL_DATA_SIZE = 3;
    private final int UV_DATA_SIZE = 2;
    private final int BYTES_PER_FLOAT = 4;
    private final int BYTES_PER_SHORT = 2;

    private final int spriteBufferId;
    private int programHandle;
    private int textureUniformHandle;

    private final int numberOfSprites;

    private FloatBuffer spriteBuffer;
    private FloatBuffer positionBuffer;
    private FloatBuffer uvBuffer;

    private final short[] INDICES = {
            0, 2, 1, // top-left, bottom-left, bottom right
            3, 2, 1  // top-left, bottom-right, top-right
    };

    private int vertCount;
    private int indicesCount;

    private short[] indices;

    private ShortBuffer drawListBuffer;

    public SpriteBatch(float[] spritePositions, float[] spriteNormals, float[] spriteUvs, int numberOfSprites) {
        this.numberOfSprites = numberOfSprites;

        vertCount = 0;
        indicesCount = 0;
        indices = new short[numberOfSprites * INDICES.length];

        // Create interleaved buffer for VBO, and separate position/UV buffers for depth map rendering.
        spriteBuffer = getInterleavedBuffer(spritePositions, spriteNormals, spriteUvs, numberOfSprites);
        positionBuffer = getFloatBuffer(spritePositions);
        uvBuffer = getFloatBuffer(spriteUvs);

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

    public void setShaderHandles(int programHandle, int textureUniformHandle) {
        this.programHandle = programHandle;
        this.textureUniformHandle = textureUniformHandle;
    }

    private FloatBuffer getInterleavedBuffer(float[] spritePositions, float[] spriteNormals, float[] spriteUvs, int numberOfSprites) {
        Log.v(LOG_TAG, "Allocating buffer for " + numberOfSprites + " cubes");
        final int spriteDataLength = spritePositions.length + spriteNormals.length + spriteUvs.length;

        int positionOffset = 0;
        int normalOffset = 0;
        int textureOffset = 0;

        final FloatBuffer spriteBuffer = ByteBuffer.allocateDirect(spriteDataLength * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        for (int i = 0; i < numberOfSprites; i++) {
            short base = (short) (vertCount / 6);

            for (int v = 0; v < 6; v++) {
                spriteBuffer.put(spritePositions, positionOffset, POSITION_DATA_SIZE);
                positionOffset += POSITION_DATA_SIZE;
                spriteBuffer.put(spriteNormals, normalOffset, NORMAL_DATA_SIZE);
                normalOffset += NORMAL_DATA_SIZE;
                spriteBuffer.put(spriteUvs, textureOffset, UV_DATA_SIZE);
                textureOffset += UV_DATA_SIZE;

                indices[indicesCount] = (short) (base + INDICES[v]);
                indicesCount++;
            }

            vertCount += 6;
        }

        drawListBuffer = ByteBuffer.allocateDirect(indices.length * BYTES_PER_SHORT).order(ByteOrder.nativeOrder()).asShortBuffer();
        BufferUtils.copy(indices, 0, drawListBuffer, indicesCount);

        spriteBuffer.position(0);

        return spriteBuffer;
    }

    public void render() {
        final int count = numberOfSprites * 6;
        final int stride = (POSITION_DATA_SIZE + NORMAL_DATA_SIZE + UV_DATA_SIZE) * BYTES_PER_FLOAT;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spriteBufferId);

        GLES20.glEnableVertexAttribArray(Shader.POSITION);
        GLES20.glVertexAttribPointer(Shader.POSITION, POSITION_DATA_SIZE, GLES20.GL_FLOAT, false, stride, 0);

        GLES20.glEnableVertexAttribArray(Shader.NORMAL);
        GLES20.glVertexAttribPointer(Shader.NORMAL, NORMAL_DATA_SIZE, GLES20.GL_FLOAT, false, stride, POSITION_DATA_SIZE * BYTES_PER_FLOAT);

        GLES20.glEnableVertexAttribArray(Shader.TEXCOORD);
        GLES20.glVertexAttribPointer(Shader.TEXCOORD, UV_DATA_SIZE, GLES20.GL_FLOAT, false,
                stride, (POSITION_DATA_SIZE + NORMAL_DATA_SIZE) * BYTES_PER_FLOAT);

        // Draw the sprites.
        // GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesCount, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDisableVertexAttribArray(Shader.POSITION);
        GLES20.glDisableVertexAttribArray(Shader.NORMAL);
        GLES20.glDisableVertexAttribArray(Shader.TEXCOORD);
    }

    public void renderDepthMap() {
        final int count = numberOfSprites * 6;

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

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisableVertexAttribArray(Shader.SHADOW_POSITION);
        GLES20.glDisableVertexAttribArray(Shader.TEXCOORD);
    }

    public void release() {
        // Delete buffers from OpenGL's memory
        final int[] buffersToDelete = new int[] {spriteBufferId};
        GLES20.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
    }

    private FloatBuffer getFloatBuffer(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        BufferUtils.copy(data, buffer, data.length, 0);

        return buffer;
    }
}
