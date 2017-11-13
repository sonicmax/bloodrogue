package com.sonicmax.bloodrogue.renderer;

import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

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

    public void copy(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = bb.asFloatBuffer();
        BufferUtils.copy(data, floatBuffer, data.length, 0);
        GLES20.glBufferData(this.type, floatBuffer.capacity() * FLOAT_SIZE, floatBuffer, GLES20.GL_STATIC_DRAW);

        bb.clear();
        floatBuffer.clear();
    }

    public void copy(short[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * SHORT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer shortBuffer = bb.asShortBuffer();
        BufferUtils.copy(data, 0, shortBuffer, data.length);
        GLES20.glBufferData(this.type, shortBuffer.capacity() * SHORT_SIZE, shortBuffer, GLES20.GL_STATIC_DRAW);

        bb.clear();
        shortBuffer.clear();
    }

    public void delete() {
        GLES20.glDeleteBuffers(1, buffers, 0);
    }
}
