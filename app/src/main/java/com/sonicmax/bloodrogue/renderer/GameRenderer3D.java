package com.sonicmax.bloodrogue.renderer;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.util.Log;

import com.sonicmax.bloodrogue.GameInterface;
import com.sonicmax.bloodrogue.renderer.cubes.CubeBatch;
import com.sonicmax.bloodrogue.renderer.cubes.ShapeBuilder;
import com.sonicmax.bloodrogue.renderer.shaders.GLShaderLoader;
import com.sonicmax.bloodrogue.renderer.sprites.ImageLoader;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

public class GameRenderer3D implements GLSurfaceView.Renderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    // X, Y, Z
    // The normal is used in light calculations and is a vector which points
    // orthogonal to the plane of the surface. For a cube model, the normals
    // should be orthogonal to the points of each face.
    final float[] cubeNormalData = {
            // Front face
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,

            // Right face
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,

            // Back face
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,

            // Left face
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,

            // Top face
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,

            // Bottom face
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f
    };

    // S, T (or X, Y)

    // Texture coordinate data.
    // Because images have a Y axis pointing downward (values increase as you move down the image) while
    // OpenGL has a Y axis pointing upward, we adjust for that here by flipping the Y axis.
    // What's more is that the texture coordinates are the same for every face.

    final float[] cubeTextureCoordinateData = {
            // Front face
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,

            // Right face
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,

            // Back face
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,

            // Left face
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,

            // Top face
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,

            // Bottom face
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    public static final int Z_DEPTH = 2;

    private GLSurfaceView gameSurfaceView;
    private final Context context;
    private GameInterface mGameInterface;

    private ImageLoader imageLoader;
    private HashMap<String, Integer> textureHandles; // Texture handles for loaded textures
    private HashMap<String, Integer> spriteIndexes; // Position on sprite sheet for particular texture

    private float[] modelMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];
    private final float[] mAccumulatedRotation = new float[16];
    private final float[] currentRotation = new float[16];
    private float[] temporaryMatrix = new float[16];
    private float[] lightModelMatrix = new float[16];

    private float[][] cachedUvs;
    private final float SPRITE_BOX_WIDTH = 0.03125f; // 1f / 32 sprites per row
    private final float SPRITE_BOX_HEIGHT = 0.03125f; // 1f / 32 sprites per column
    private final int SPRITES_PER_ROW = 32;
    private final int CUBE_SIDES = 6;
    private final int CUBE_VERTICES = 12;

    private int chunkStartX;
    private int chunkStartY;
    private int chunkEndX;
    private int chunkEndY;
    private int fullChunkWidth;
    private int fullChunkHeight;
    private int visibleGridWidth;
    private int visibleGridHeight;
    private int mapGridWidth;
    private int mapGridHeight;

    private float scaleFactor;
    private float gridSize;
    private final float SPRITE_SIZE = 64f;
    private float targetWidth = 640f; // This should be multiple of 64

    // Shader handles
    private int programHandle;
    private int mVPMatrixHandle;
    private int mVMatrixHandle;
    private int lightPosHandle;
    private int textureUniformHandle;

    private int mActualCubeFactor;

    private final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
    private final float[] lightPosInWorldSpace = new float[4];
    private final float[] lightPosInEyeSpace = new float[4];

    public volatile float deltaX;
    public volatile float deltaY;

    private final ExecutorService mSingleThreadedExecutor;

    private CubeBatch cubes;

    private int screenWidth;
    private int screenHeight;

    public GameRenderer3D(Context context, GameSurfaceView gameSurfaceView) {
        this.gameSurfaceView = gameSurfaceView;
        this.context = context;
        mSingleThreadedExecutor = Executors.newSingleThreadExecutor();
        mapGridWidth = 64;
        mapGridHeight = 64;
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        Log.v(LOG_TAG, "onSurfaceCreated");
        // Set the background clear color to black.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Use culling to remove back faces.
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Position the eye in front of the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = -0.5f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

        GLShaderLoader loader = new GLShaderLoader(context);
        programHandle = loader.compileCubeShader();

        imageLoader = new ImageLoader();
        imageLoader.loadImagesFromDisk(mGameInterface.getAssets());
        spriteIndexes = imageLoader.getSpriteIndexes();
        textureHandles = imageLoader.getTextureHandles();
        precalculateUvs(spriteIndexes.size());

        int textureHandle = textureHandles.get("sprite_sheets/sheet.png");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);

        // Set program handles for cube drawing.
        mVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix");
        mVMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVMatrix");
        lightPosHandle = GLES20.glGetUniformLocation(programHandle, "u_lightPos");
        textureUniformHandle = GLES20.glGetUniformLocation(programHandle, "u_Texture");

        // Initialize the accumulated rotation matrix
        Matrix.setIdentityM(mAccumulatedRotation, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        Log.v(LOG_TAG, "onSurfaceChanged");

        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        screenWidth = width;
        screenHeight = height;

        scaleContent();
        calculateContentGrid();
        setGridChunkToRender();

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 1000.0f;

        // Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, near, far);
        Matrix.frustumM(projectionMatrix, 0, left / 2, right / 2, bottom / 2, top / 2, near, far);

        generateCubes();
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(programHandle);

        // Calculate position of the light. Push into the distance.
        Matrix.setIdentityM(lightModelMatrix, 0);
        Matrix.translateM(lightModelMatrix, 0, 0.0f, 0.0f, -1.0f);

        Matrix.multiplyMV(lightPosInWorldSpace, 0, lightModelMatrix, 0, mLightPosInModelSpace, 0);
        Matrix.multiplyMV(lightPosInEyeSpace, 0, viewMatrix, 0, lightPosInWorldSpace, 0);

        // Draw a cube.
        // Translate the cube into the screen.
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0.0f, 0.0f, -5.0f);

        // Set a matrix that contains the current rotation.
        Matrix.setIdentityM(currentRotation, 0);
        Matrix.rotateM(currentRotation, 0, deltaX, 0.0f, 1.0f, 0.0f);
        Matrix.rotateM(currentRotation, 0, deltaY, 1.0f, 0.0f, 0.0f);
        deltaX = 0.0f;
        deltaY = 0.0f;

        // Multiply the current rotation by the accumulated rotation, and then set the accumulated rotation to the result.
        Matrix.multiplyMM(temporaryMatrix, 0, currentRotation, 0, mAccumulatedRotation, 0);
        System.arraycopy(temporaryMatrix, 0, mAccumulatedRotation, 0, 16);

        // Rotate the cube taking the overall rotation into account.
        Matrix.multiplyMM(temporaryMatrix, 0, modelMatrix, 0, mAccumulatedRotation, 0);
        System.arraycopy(temporaryMatrix, 0, modelMatrix, 0, 16);

        // This multiplies the view matrix by the model matrix, and stores
        // the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mVMatrixHandle, 1, false, mMVPMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix,
        // and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(temporaryMatrix, 0, projectionMatrix, 0, mMVPMatrix, 0);
        System.arraycopy(temporaryMatrix, 0, mMVPMatrix, 0, 16);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Pass in the light position in eye space.
        GLES20.glUniform3f(lightPosHandle, lightPosInEyeSpace[0], lightPosInEyeSpace[1], lightPosInEyeSpace[2]);

        // Pass in the texture information
        // Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Tell the texture uniform sampler to use this texture in the
        // shader by binding to texture unit 0.
        GLES20.glUniform1i(textureUniformHandle, 0);

        if (cubes != null) {
            cubes.render(visibleGridWidth, visibleGridHeight);
        }
    }

    public void setGameInterface(GameInterface gameInterface) {
        mGameInterface = gameInterface;
    }

    private void generateCubes() {
        mSingleThreadedExecutor.submit(new Runnable() {
            @Override
            public void run() {
                initCubes();
            }
        });
    }

    public void setMapSize(int[] size) {
        mapGridWidth = size[0];
        mapGridHeight = size[1];
    }

    private void initCubes() {
        RandomNumberGenerator rng = new RandomNumberGenerator();

        Log.v(LOG_TAG, "visible: " + visibleGridWidth + ", " + visibleGridHeight);
        try {
            final float[] cubePositionData = new float[108 * visibleGridWidth * visibleGridHeight * Z_DEPTH];
            final float[] cubeUvData = new float[12 * visibleGridWidth * visibleGridHeight * Z_DEPTH];
            int cubePositionDataOffset = 0;
            int cubeUvDataOffset = 0;

            final int segments = (Math.min(visibleGridWidth, visibleGridHeight) * 2) - 1;
            final float minPosition = -1.0f;
            final float maxPosition = 1.0f;
            final float positionRange = (maxPosition - minPosition);
            final float size = 1;

            for (int x = 0; x < visibleGridWidth; x++) {
                for (int y = 0; y < visibleGridHeight; y++) {
                    for (int z = 0; z < Z_DEPTH; z++) {

                        final float x1 = minPosition + ((positionRange / segments) * x);
                        final float x2 = minPosition + ((positionRange / segments) * (x + size));

                        final float y1 = minPosition + ((positionRange / segments) * y);
                        final float y2 = minPosition + ((positionRange / segments) * (y + size));

                        final float z1 = minPosition + ((positionRange / segments) * z);
                        final float z2 = minPosition + ((positionRange / segments) * (z + size));

                        // Define points for a cube.
                        // X, Y, Z
                        final float[] p1p = { x1, y2, z2 };
                        final float[] p2p = { x2, y2, z2 };
                        final float[] p3p = { x1, y1, z2 };
                        final float[] p4p = { x2, y1, z2 };
                        final float[] p5p = { x1, y2, z1 };
                        final float[] p6p = { x2, y2, z1 };
                        final float[] p7p = { x1, y1, z1 };
                        final float[] p8p = { x2, y1, z1 };

                        final float[] thisCubePositionData = ShapeBuilder.generateCubeData(p1p, p2p, p3p, p4p, p5p, p6p, p7p, p8p, p1p.length);

                        System.arraycopy(thisCubePositionData, 0, cubePositionData, cubePositionDataOffset, thisCubePositionData.length);
                        cubePositionDataOffset += thisCubePositionData.length;

                        int random = rng.getRandomInt(0, spriteIndexes.size() - 1);
                        float[] uv = cachedUvs[random];
                        System.arraycopy(uv, 0, cubeUvData, cubeUvDataOffset, uv.length);
                        cubeUvDataOffset += uv.length;
                    }
                }
            }

            createCubeVBO(cubePositionData, cubeNormalData, cubeTextureCoordinateData);

        } catch (OutOfMemoryError e) {
            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d("GameRenderer3D", "Out of memory lol brb");
        }
    }

    private void createCubeVBO(final float[] cubePositionData, final float[] cubeNormalData,
                               final float[] cubeUvData) {

        gameSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (cubes != null) {
                    cubes.release();
                    cubes = null;
                }

                // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
                System.gc();

                try {
                    cubes = new CubeBatch(cubePositionData, cubeNormalData, cubeUvData, visibleGridWidth * visibleGridHeight);

                } catch (OutOfMemoryError err) {
                    if (cubes != null) {
                        cubes.release();
                        cubes = null;
                    }

                    // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
                    System.gc();

                    Log.d("GameRenderer3D", "Out of memory lol brb");
                }
            }
        });
    }

    private void setGridChunkToRender() {
        float[] origin = transformCoordsToWorld(0, 0);

        float spriteSize = SPRITE_SIZE * scaleFactor;

        int originX = (int) (origin[0] / spriteSize);
        int originY = (int) (origin[1] / spriteSize);

        // Use slightly larger chunk of grid than is actually visible, without exceeding bounds of map
        chunkStartX = Math.max(originX - 1, 0);
        chunkStartY = Math.max(originY - 1, 0);
        chunkEndX = Math.min(originX + visibleGridWidth + 2, mapGridWidth);
        chunkEndY = Math.min(originY + visibleGridHeight + 2, mapGridHeight);

        // For some methods we need to retain the total visible chunk
        fullChunkWidth = (originX + visibleGridWidth + 2) - (originX - 1);
        fullChunkHeight = (originY + visibleGridHeight + 2) - (originY - 1);
    }

    private void calculateContentGrid() {
        float width = ScreenSizeGetter.getWidth();
        float height = ScreenSizeGetter.getHeight();

        width *= 1f;
        height *= 1f;

        float spriteSize = SPRITE_SIZE * scaleFactor;

        double xInterval = width / spriteSize;
        double yInterval = height / spriteSize;

        visibleGridWidth = (int) xInterval;
        visibleGridHeight = (int) yInterval;
    }

    private void scaleContent() {
        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = ScreenSizeGetter.getWidth();
            screenHeight = ScreenSizeGetter.getHeight();
        }

        /*if (targetWidth > screenWidth) {
            targetWidth = screenWidth;
        }*/

        float resX = (float) screenWidth / targetWidth;
        float resY = (float) screenHeight / targetWidth;

        if (resX > resY) {
            scaleFactor = resY;
        }

        else {
            scaleFactor = resX;
        }

        gridSize = SPRITE_SIZE * scaleFactor;
    }

    private float[] transformCoordsToWorld(float x, float y) {
        float[] coords = {0.0f, 0.0f, 0.0f, 0.0f};

        int[] viewPort = new int[] {0, 0, screenWidth, screenHeight};

        // We need to reverse any modifications made to the zoom level using gluUnProject
        GLU.gluUnProject(x, y, -1.0f, viewMatrix, 0, projectionMatrix, 0, viewPort, 0, coords, 0);

        // Original origin is centre of screen, so we need to translate back to bottom-left
        coords[0] += (screenWidth / 2);
        coords[1] += (screenHeight / 2);

        // Account for scrolling
        // coords[0] += touchScrollDx;
        // [1] += touchScrollDy;

        return coords;
    }

    public void precalculateUvs(int numberOfIndexes) {
        cachedUvs = new float[numberOfIndexes][CUBE_VERTICES];

        for (int i = 0; i < numberOfIndexes; i++) {
            int row = i / SPRITES_PER_ROW;
            int col = i % SPRITES_PER_ROW;

            float u = col * SPRITE_BOX_WIDTH;
            float u2 = u + SPRITE_BOX_WIDTH;
            float v = row * SPRITE_BOX_HEIGHT;
            float v2 = v + SPRITE_BOX_HEIGHT;

            /*
            0.0f, 0.0f,
                    0.0f, 1.0f,
                    1.0f, 0.0f,
                    0.0f, 1.0f,
                    1.0f, 1.0f,
                    1.0f, 0.0f,     */

            // TODO: yikes
            float[] uv = {
                    u2, v2,
                    u2, v,
                    u, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v2,
                    u2, v,
                    u, v,
                    u, v2
            };

            cachedUvs[i] = uv;
        }
    }
}
