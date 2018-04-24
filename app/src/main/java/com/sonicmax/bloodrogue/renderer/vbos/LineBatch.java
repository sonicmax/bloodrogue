package com.sonicmax.bloodrogue.renderer.vbos;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.renderer.shaders.ShaderAttributes;
import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Mildly useful debugging class for rendering red lines to screen (eg. to visualise surface normals,
 * checking FOV code, etc).
 */

public class LineBatch {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final int BYTES_PER_FLOAT = 4;
    private final int POSITION_DATA_SIZE = 3;

    private final int lineBufferId;
    private final int numberOfLines;

    public LineBatch(float[] linePositions, int numberOfLines) {
        this.numberOfLines = numberOfLines;

        FloatBuffer positionBuffer = createFloatBuffer(linePositions);
        lineBufferId = createVBO(positionBuffer);
        positionBuffer.limit(0);
        positionBuffer = null;
    }

    private FloatBuffer createFloatBuffer(float[] data) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        BufferUtils.copy(data, buffer, data.length, 0);

        return buffer;
    }

    private int createVBO(FloatBuffer buffer) {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * BYTES_PER_FLOAT, buffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return buffers[0];
    }

    public void draw() {
        final int count = numberOfLines * 2;
        final int stride = POSITION_DATA_SIZE * BYTES_PER_FLOAT;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lineBufferId);

        GLES20.glEnableVertexAttribArray(ShaderAttributes.POSITION);
        GLES20.glVertexAttribPointer(ShaderAttributes.POSITION,
                POSITION_DATA_SIZE,
                GLES20.GL_FLOAT,
                false,
                stride,
                0);

        // Draw the sprites.
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, count);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDisableVertexAttribArray(ShaderAttributes.POSITION);
    }

    public void release() {
        // Delete buffers from OpenGL's memory
        final int[] buffersToDelete = new int[] {lineBufferId};
        GLES20.glDeleteBuffers(buffersToDelete.length, buffersToDelete, 0);
    }
}
