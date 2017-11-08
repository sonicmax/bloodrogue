package com.sonicmax.bloodrogue.renderer;


import android.opengl.GLES20;

import com.sonicmax.bloodrogue.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.security.InvalidParameterException;

public class VertexBufferObject {
    private final int FLOAT_SIZE = 4;
    private final int SHORT_SIZE = 2;

    private final int name;
    private int type;

    public VertexBufferObject() {
        final int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        name = buffers[0];
        type = -1;
    }

    public int getName() {
        return this.name;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void bind(int type) {
        GLES20.glBindBuffer(type, this.name);
        this.type = type;
    }

    public void bind() {
        if (this.type == -1) {
            throw new InvalidParameterException("Must use setType(int type) or bind(int type) before calling bind() with no arguments.");
        }
        else {
            GLES20.glBindBuffer(this.type, this.name);
        }
    }

    public void unbind() {
        GLES20.glBindBuffer(this.type, 0);
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
}
