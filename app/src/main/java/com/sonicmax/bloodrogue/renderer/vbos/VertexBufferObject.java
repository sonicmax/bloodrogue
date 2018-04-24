package com.sonicmax.bloodrogue.renderer.vbos;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 *  Class which creates a VBO and provides various convenience methods for copying data, binding/unbinding, etc
 *  Currently only supports GL_STATIC_DRAW usage pattern
 */

public class VertexBufferObject {
    private final int FLOAT_SIZE = 4;
    private final int SHORT_SIZE = 2;

    private final int name;
    private final int[] buffers;
    private final int type;

    private int size;

    public VertexBufferObject(int type) {
        this.buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        this.name = buffers[0];

        if (typeIsAllowed(type)) {
            this.type = type;
        }
        else {
            throw new IllegalArgumentException("Type is not allowed. Use GL_ARRAY_BUFFER or GL_ELEMENT_ARRAY_BUFFER");
        }
    }

    private boolean typeIsAllowed(int type) {
        switch (type) {
            case GLES20.GL_ARRAY_BUFFER:
            case GLES20.GL_ELEMENT_ARRAY_BUFFER:
                return true;

            default:
                return false;
        }
    }

    public int getName() {
        return this.name;
    }

    public void bind() {
        GLES20.glBindBuffer(this.type, this.name);
    }

    public void unbind() {
        GLES20.glBindBuffer(this.type, 0);
    }

    public void bindAndCopy(float[] data) {
        this.bind();
        this.copy(data);
        this.unbind();
    }

    public void bindAndCopy(short[] data) {
        this.bind();
        this.copy(data);
        this.unbind();
    }

    private Buffer buffer;

    public void copy(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
        BufferUtils.copy(data, buffer, data.length, 0);
        size = buffer.capacity() * FLOAT_SIZE;
        GLES20.glBufferData(this.type, size, buffer, GLES20.GL_STATIC_DRAW);

        bb.clear();
        buffer.clear();
    }

    public void copy(short[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * SHORT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asShortBuffer();
        BufferUtils.copy(data, 0, buffer, data.length);
        size = buffer.capacity() * SHORT_SIZE;
        GLES20.glBufferData(this.type, size, buffer, GLES20.GL_STATIC_DRAW);

        bb.clear();
        buffer.clear();
    }

    public void invalidate() {
        GLES20.glBufferData(this.type, size, buffer, GLES20.GL_STATIC_DRAW);
        GLES20.glDeleteBuffers(1, buffers, 0);
    }
}
