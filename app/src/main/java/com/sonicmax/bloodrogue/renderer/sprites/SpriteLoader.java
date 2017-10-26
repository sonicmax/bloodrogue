package com.sonicmax.bloodrogue.renderer.sprites;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Provides methods which allow us to load a texture into OpenGL and render it.
 */

public class SpriteLoader {
    private final FloatBuffer mCubeTextureCoordinates;
    private final FloatBuffer vertexBuffer;
    private final ShortBuffer drawListBuffer;
    private final short[] drawOrder = { 0, 1, 2, 0, 2, 3 };

    public SpriteLoader() {
        float[] spriteCoords = {
                -0.5f, 0.5f, // top left
                -0.5f, -0.5f, // bottom left
                0.5f, -0.5f, // bottom right
                0.5f, 0.5f // top right
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(spriteCoords.length * Float.SIZE);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(spriteCoords);
        vertexBuffer.position(0);

        final float[] cubeTextureCoordinateData = {
                0.0f, 0.0f, // top left
                0.0f, 1.0f, // bottom left
                1.0f, 1.0f, // bottom right
                1.0f, 0.0f // top right
        };

        mCubeTextureCoordinates = ByteBuffer.allocateDirect(cubeTextureCoordinateData.length * Float.SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubeTextureCoordinates.put(cubeTextureCoordinateData).position(0);

        //Initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(spriteCoords.length * Float.SIZE);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);
    }

    public int loadTexture(Bitmap bitmap) {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            // Load the bitmap into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // Recycle the bitmap, since its data has been loaded into OpenGL.
            bitmap.recycle();
        }

        else {
            throw new RuntimeException("Error loading texture.");
        }

        return textureHandle[0];
    }
}
