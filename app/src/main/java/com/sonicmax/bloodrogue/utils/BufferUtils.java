package com.sonicmax.bloodrogue.utils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 *  Some stuff I borrowed from libGDX
 */

public class BufferUtils {
    private static final String BUFFER_UTILS = "buffer-utils";
    private static final int BYTES_PER_FLOAT = 4;

    static {
        System.loadLibrary(BUFFER_UTILS);
    }

    public static float[] concat(float[] first, float[] second) {
        float[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static FloatBuffer createFloatBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static int bytesToElements (Buffer dst, int bytes) {
        if (dst instanceof ByteBuffer)
            return bytes;
        else if (dst instanceof ShortBuffer)
            return bytes >>> 1;
        else if (dst instanceof CharBuffer)
            return bytes >>> 1;
        else if (dst instanceof IntBuffer)
            return bytes >>> 2;
        else if (dst instanceof LongBuffer)
            return bytes >>> 3;
        else if (dst instanceof FloatBuffer)
            return bytes >>> 2;
        else if (dst instanceof DoubleBuffer)
            return bytes >>> 3;
        else
            throw new Error("Can't copy to a " + dst.getClass().getSimpleName() + " instance");
    }

    private static int positionInBytes (Buffer dst) {
        if (dst instanceof ByteBuffer)
            return dst.position();
        else if (dst instanceof ShortBuffer)
            return dst.position() << 1;
        else if (dst instanceof CharBuffer)
            return dst.position() << 1;
        else if (dst instanceof IntBuffer)
            return dst.position() << 2;
        else if (dst instanceof LongBuffer)
            return dst.position() << 3;
        else if (dst instanceof FloatBuffer)
            return dst.position() << 2;
        else if (dst instanceof DoubleBuffer)
            return dst.position() << 3;
        else
            throw new Error("Can't copy to a " + dst.getClass().getSimpleName() + " instance");
    }

    public static void copy(float[] src, Buffer dst, int numFloats, int offset) {
        copyJni(src, dst, numFloats, offset);
        dst.position(0);

        if (dst instanceof ByteBuffer)
            dst.limit(numFloats << 2);
        else
        if (dst instanceof FloatBuffer)
            dst.limit(numFloats);
    }

    public static void copy (short[] src, int srcOffset, Buffer dst, int numElements) {
        dst.limit(dst.position() + bytesToElements(dst, numElements << 1));
        copyJni(src, srcOffset, dst, positionInBytes(dst), numElements << 1);
    }

    /** Writes the specified number of zeros to the buffer. This is generally faster than reallocating a new buffer. */
    public static native void clear (ByteBuffer buffer, int numBytes); /*
		memset(buffer, 0, numBytes);
	*/

    /**
     *  Note: not sure why Android Studio is unable to resolve these JNI functions, but it still works
     */

    private native static void copyJni(float[] src, Buffer dst, int numFloats, int offset); /*
		memcpy(dst, src + offset, numFloats << 2 );
	*/

    private native static void copyJni(short[] src, int srcOffset, Buffer dst, int dstOffset, int numBytes); /*
		memcpy(dst + dstOffset, src + srcOffset, numBytes);
	 */
}
