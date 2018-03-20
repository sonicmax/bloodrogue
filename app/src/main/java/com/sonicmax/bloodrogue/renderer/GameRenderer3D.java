package com.sonicmax.bloodrogue.renderer;

import java.util.ArrayList;
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
import com.sonicmax.bloodrogue.engine.Frame;
import com.sonicmax.bloodrogue.engine.components.Sprite;
import com.sonicmax.bloodrogue.engine.environment.TimeManager;
import com.sonicmax.bloodrogue.engine.environment.WeatherManager;
import com.sonicmax.bloodrogue.renderer.cubes.CubeBatch;
import com.sonicmax.bloodrogue.renderer.cubes.ShapeBuilder;
import com.sonicmax.bloodrogue.renderer.shaders.GLShaderLoader;
import com.sonicmax.bloodrogue.renderer.sprites.ImageLoader;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteBatch;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;
import com.sonicmax.bloodrogue.renderer.text.Status;
import com.sonicmax.bloodrogue.renderer.text.TextObject;
import com.sonicmax.bloodrogue.ui.UserInterfaceController;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

public class GameRenderer3D implements GLSurfaceView.Renderer {
    private final String LOG_TAG = this.getClass().getSimpleName();
    public static final int Z_DEPTH = 1;

    private final int CUBE_POSITION_SIZE = 108;
    private final int CUBE_NORMAL_SIZE = 108;
    private final int CUBE_UV_SIZE = 72;

    private final int SPRITE_POSITION_SIZE = 18;
    private final int SPRITE_NORMAL_SIZE = 18;
    private final int SPRITE_UV_SIZE = 12;

    private final long FRAME_TIME = 16L;

    public static final int NONE = 0; // Rendering nothing
    public static final int TITLE = 1; // Rendering title screen
    public static final int MENU = 2; // Rendering main menu
    public static final int SPLASH = 3; // Rendering splash screen, waiting for operation to finish
    public static final int GAME = 4; // Rendering game content

    private HashMap<String, Integer> textureHandles; // Texture handles for loaded textures
    private HashMap<String, Integer> spriteIndexes; // Position on sprite sheet for particular texture

    private final float[] lightPosInModelSpace = new float [] {64f, 10f, 64f, 1f};
    private final float[] lightPosInEyeSpace = new float[4];

    /** Used to hold the current position of the light in world space (after transformation via model matrix). */
    private final float[] lightPosInWorldSpace = new float[4];

    private float[] modelMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] mvMatrix = new float[16];
    private float[] mvpMatrix = new float[16];
    private float[] normalMatrix = new float[16];

    private float[] lightModelMatrix = new float[16];
    private float[] lightViewMatrix = new float[16];
    private float[] lightProjectionMatrix = new float[16];
    private float[] lightMvpMatrix = new float[16];

    private float[][] cachedCubeUvs;
    private final float SPRITE_BOX_WIDTH = 0.03125f; // 1f / 32 sprites per row
    private final float SPRITE_BOX_HEIGHT = 0.03125f; // 1f / 32 sprites per column
    private final int SPRITES_PER_ROW = 32;

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

    // GL handles
    private int cubeProgramHandle;
    private int spriteProgramHandle;
    private int depthMapProgramHandle;
    private int mvpMatrixHandle;
    private int mvMatrixHandle;
    private int lightPosHandle;
    private int lightColourHandle;
    private int textureUniformHandle;
    private int spriteSheetHandle;
    private int fontHandle;
    private int normalMatrixHandle;

    public int renderState;

    public volatile float deltaX;
    public volatile float deltaY;

    private ExecutorService singleThreadedExecutor;
    private CubeBatch cubes;
    private SpriteBatch sprites;
    private UserInterfaceController uiController;
    private GLSurfaceView gameSurfaceView;
    private Context context;
    private GameInterface gameInterface;
    private ImageLoader imageLoader;

    private int screenWidth;
    private int screenHeight;

    private long currentFrameTime;
    private long startTime;
    private long endTime;
    private int frameCount;
    private boolean halfSecPassed;
    private int fpsCount;

    private float zoomLevel = 1f;

    private Frame currentFloorData;
    private boolean hasGameData = false;

    // Eye position
    private float x = 64f;
    private float y = 10f;
    private float z = 64f;

    // Position to look at
    private float lx = 64f;
    private float ly = -10f;
    private float lz = 64f;

    // Camera rotation
    private float xAngle = 0.0f;
    private float yAngle = 0f;
    private float fraction = 0.05f;

    private TimeManager timeManager;
    private WeatherManager weatherManager;

    private float elapsedTimeSeconds = 0f;
    private float elapsedTimeMs = 0f;

    private boolean hasDepthTextureExtension;

    private SpriteRenderer depthMapDebugger;

    public GameRenderer3D(GameSurfaceView gameSurfaceView, Context context) {
        this.gameSurfaceView = gameSurfaceView;
        this.context = context;
        singleThreadedExecutor = Executors.newSingleThreadExecutor();
        hasDepthTextureExtension = false;
        renderState = NONE;
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        // Test OES_depth_texture extension
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);

        if (extensions.contains("OES_depth_texture")) {
            hasDepthTextureExtension = true;
            // Todo: handle cases where OES_depth_texture extension doesn't exist
            Log.v(LOG_TAG, "Has OES_depth_texture extension");
        }

        loadShaders();
        loadResources();
        setCameraPosition();

        precalculateSpriteUvs(spriteIndexes.size());
        precalculateCubeUvs(spriteIndexes.size());

        uiController = new UserInterfaceController(gameInterface, spriteIndexes);
        uiController.prepareUiRenderer(spriteProgramHandle, spriteSheetHandle, fontHandle);
        uiController.calculateTextRowHeight();

        // Create a renderer to output depth map texture to screen
        depthMapDebugger = new SpriteRenderer();
        depthMapDebugger.initDebugShader(debugDepthMapProgramHandle);

        prepareGlSurface();
        getGlUniforms();

        renderState = TITLE;
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
        setProjection();
        createDepthMapFBO(width, height);

        depthMapDebugger.setScaleFactor(scaleFactor);
        depthMapDebugger.prepareFullScreenRender((float) width, (float) height);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        switch (renderState) {
            case NONE:
                uiController.addSplashText("Render state = NONE");
                break;

            case TITLE:
                uiController.addSplashText("Loading textures...");
                break;

            case MENU:
                uiController.addSplashText("Touch to start");
                break;

            case SPLASH:
                // renderTransition();
                uiController.addSplashText("Generating terrain...");
                break;

            case GAME:
                if (hasGameData) {
                    endTime = System.currentTimeMillis();

                    long dt = endTime - startTime;

                    // Lazy fix for first call of onDrawFrame (where startTime == 0)
                    if (dt > 100000) {
                        dt = FRAME_TIME;
                    }

                    startTime = endTime;

                    checkElapsedTime(dt);
                    renderGameContent(dt);
                    addUiText();
                    uiController.render(dt);
                }

                break;
        }
    }

    private int debugDepthMapProgramHandle;

    private void loadShaders() {
        GLShaderLoader loader = new GLShaderLoader(context);
        cubeProgramHandle = loader.compileCubeShader();
        spriteProgramHandle = loader.compileSpriteShader();
        depthMapProgramHandle = loader.compileDepthMapShader();
        debugDepthMapProgramHandle = loader.compileDebugDepthMapShader();
    }

    private void loadResources() {
        imageLoader = new ImageLoader();
        imageLoader.loadImagesFromDisk(gameInterface.getAssets());
        spriteIndexes = imageLoader.getSpriteIndexes();
        textureHandles = imageLoader.getTextureHandles();
        spriteSheetHandle = textureHandles.get("sprite_sheets/sheet.png");
        fontHandle = textureHandles.get("fonts/ccra_font.png");
    }

    private void setCameraPosition() {
        float eyeX = x;
        float eyeY = y;
        float eyeZ = z;

        float lookX = x + lx;
        float lookY = y + ly;
        float lookZ = z + lz;

        float upX = 0.0f;
        float upY = 1.0f;
        float upZ = 0.0f;

        Matrix.setLookAtM(viewMatrix, 0,
                eyeX, eyeY, eyeZ,
                lookX, lookY, lookZ,
                upX, upY, upZ);
    }

    private float[] actualLightPosition = new float[4];

    private void setLightViewMatrix() {
        long rotationCounter = (long) 1 % 12000L;

        float lightRotationDegree = (360.0f / 12000.0f) * ((int)rotationCounter);

        float[] rotationMatrix = new float[16];

        Matrix.setIdentityM(rotationMatrix, 0);

        Matrix.rotateM(rotationMatrix, 0, lightRotationDegree, 1f, 0f, 0f);

        Matrix.multiplyMV(actualLightPosition, 0, rotationMatrix, 0, lightPosInModelSpace, 0);

        Matrix.setIdentityM(modelMatrix, 0);

        //Set view matrix from light source position
        Matrix.setLookAtM(lightViewMatrix, 0,
                lightPosInModelSpace[0], lightPosInModelSpace[1], lightPosInModelSpace[2],
                lightPosInModelSpace[0] * 2, 0f, lightPosInModelSpace[2],
                0f, 1f, 0f);
    }

    private float near = 1.0f;
    private float far = 1000.0f;

    private void setProjection() {
        // Note: to zoom, multiply near by zoom level & then divide left/right/top/bottom by near
        final float ratio = (float) screenWidth / screenHeight;
        final float left = -ratio;
        final float right = ratio;

        Log.v(LOG_TAG, "ratio: " + ratio);
        final float bottom = -1.0f;
        final float top = 1.0f;

        // Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, near, far);
        Matrix.perspectiveM(projectionMatrix, 0, 90f, ratio, near, far);

        // For shadow rendering
        Matrix.perspectiveM(lightProjectionMatrix, 0, 45f, ratio, near, far);
    }

    private void prepareGlSurface() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        GLES20.glEnable(GLES20.GL_TEXTURE_2D);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearDepthf(1.0f);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDepthRangef(0.0f, 1.0f);
        GLES20.glDepthMask(true);

        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glCullFace(GL10.GL_BACK);
    }

    private int lightMvpMatrixHandle;
    private int depthMapTextureHandle;
    private int depthMapMVPMatrixHandle;
    private int viewPositionHandle;
    private int depthMapSamplerHandle;

    private void getGlUniforms() {
        mvpMatrixHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_MVPMatrix");
        mvMatrixHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_MVMatrix");
        normalMatrixHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_NormalMatrix");
        lightPosHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_lightPos");
        lightColourHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_lightColour");
        textureUniformHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_Texture");
        lightMvpMatrixHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_ShadowProjMatrix");
        viewPositionHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_ViewPos");
        depthMapTextureHandle = GLES20.glGetUniformLocation(cubeProgramHandle, "u_ShadowTexture");

        depthMapMVPMatrixHandle = GLES20.glGetUniformLocation(depthMapProgramHandle, "u_MVPMatrix");
        depthMapSamplerHandle = GLES20.glGetUniformLocation(depthMapProgramHandle, "u_Texture");
    }

    public void queueNarrationUpdate(ArrayList<TextObject> narrations) {
        uiController.queueNarrationUpdate(narrations);
    }

    public void queueNewStatus(Status status) {
        uiController.queueNewStatus(status);
    }

    public void setRenderState(int state) {
        renderState = state;
    }

    public int getRenderState() {
        return renderState;
    }

    private void checkElapsedTime(long dt) {
        currentFrameTime += dt;

        elapsedTimeSeconds += dt / 1000f;
        elapsedTimeMs += dt;

        frameCount++;

        timeManager.tick();

        // We periodically want to check narrations to see if we need to remove any of them.
        // Narrations are only updated once a second, so it's pointless checking every single frame
        if (currentFrameTime >= 500 && !halfSecPassed) {
            gameInterface.checkNarrations();
            halfSecPassed = true;
        }

        // Update FPS count
        if (currentFrameTime >= 1000) {
            fpsCount = frameCount;
            currentFrameTime = 0;
            frameCount = 0;
            if (!halfSecPassed) {
                gameInterface.checkNarrations();
            }

            halfSecPassed = false;
        }
    }

    private void renderGameContent(float dt) {
        setCameraPosition();
        prepareGlSurface();
        setLightViewMatrix();

        if (cubes != null && sprites != null) {
            renderShadowMap();
            renderScene();
        }
    }

    private void checkGlErrors() {
        int debugInfo = GLES20.glGetError();

        if (debugInfo != GLES20.GL_NO_ERROR) {
            String msg = "OpenGL error: " + GLU.gluErrorString(debugInfo);
            Log.w(LOG_TAG, msg);
        }
    }

    private void renderShadowMap() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, depthMapFrameBufferId);

        GLES20.glViewport(0, 0, shadowMapWidth, shadowMapHeight);

        // Clear color and buffers
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(depthMapProgramHandle);

        float[] lightMvMatrix = new float[16];

        // View matrix * Model matrix value is stored
        Matrix.multiplyMM(lightMvMatrix, 0, lightViewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(lightMvpMatrix, 0, lightProjectionMatrix, 0, lightMvMatrix, 0);

        GLES20.glUniformMatrix4fv(depthMapMVPMatrixHandle, 1, false, lightMvpMatrix, 0);
        GLES20.glUniform1i(depthMapSamplerHandle, 0);

        // Enable front face culling to prevent self-shadowing
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_FRONT);
        cubes.renderDepthMap();

        // We want sprites to be visible from both sides
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        sprites.renderDepthMap();
    }

    private boolean renderUsingDepthMap = false;

    private void renderScene() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glColorMask(true, true, true, true);

        GLES20.glUseProgram(cubeProgramHandle);

        GLES20.glViewport(0, 0, screenWidth, screenHeight);

        float bias[] = new float [] {
                0.5f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.5f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.5f, 0.0f,
                0.5f, 0.5f, 0.5f, 1.0f};

        float[] depthBiasMVP = new float[16];

        float[] tempResultMatrix = new float[16];

        //calculate MV matrix
        Matrix.multiplyMM(tempResultMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        System.arraycopy(tempResultMatrix, 0, mvMatrix, 0, 16);

        //pass in MV Matrix as uniform
        GLES20.glUniformMatrix4fv(mvMatrixHandle, 1, false, mvMatrix, 0);

        Matrix.invertM(tempResultMatrix, 0, mvMatrix, 0);
        Matrix.transposeM(normalMatrix, 0, tempResultMatrix, 0);

        //pass in Normal Matrix as uniform
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normalMatrix, 0);

        //calculate MVP matrix
        Matrix.multiplyMM(tempResultMatrix, 0, projectionMatrix, 0, mvMatrix, 0);
        System.arraycopy(tempResultMatrix, 0, mvpMatrix, 0, 16);

        //pass in MVP Matrix as uniform
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        Matrix.multiplyMV(lightPosInEyeSpace, 0, viewMatrix, 0, lightPosInModelSpace, 0);
        GLES20.glUniform3f(lightPosHandle, lightPosInEyeSpace[0], lightPosInEyeSpace[1], lightPosInEyeSpace[2]);

        // Matrix.multiplyMM(depthBiasMVP, 0, bias, 0, lightMvpMatrix, 0);
        // System.arraycopy(depthBiasMVP, 0, lightMvpMatrix, 0, 16);

        //MVP matrix that was used during depth map render
        GLES20.glUniformMatrix4fv(lightMvpMatrixHandle, 1, false, lightMvpMatrix, 0);

        // Pass in camera position

        float[] cameraPosInEyeSpace = new float[16];
        float[] actualCameraPosition = new float[] {x, y, z, 1.0f};

        Matrix.multiplyMV(cameraPosInEyeSpace, 0, viewMatrix, 0, actualCameraPosition, 0);
        GLES20.glUniform3f(viewPositionHandle, cameraPosInEyeSpace[0], cameraPosInEyeSpace[1], cameraPosInEyeSpace[2]);

        // Pass in global light colour
        float[] colour = timeManager.getAmbientTint();
        GLES20.glUniform3f(lightColourHandle, colour[0], colour[1], colour[2]);

        // Pass in depth map and sprite sheet textures to shader
        GLES20.glUniform1i(depthMapTextureHandle, 3);
        GLES20.glUniform1i(textureUniformHandle, 0);

        if (renderUsingDepthMap) {
            // Debugging - render depth map texture to screen
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            depthMapDebugger.renderDepthMap(near, far);
        }

        else {
            // We can safely cull back faces of cubes
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            cubes.render();

            // Make sure sprites are always rendered
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            sprites.render();
        }
    }

    public void setGameInterface(GameInterface gameInterface) {
        this.gameInterface = gameInterface;
        weatherManager = gameInterface.getWeatherManager();
        timeManager = gameInterface.getTimeManager();
    }

    public void setMapSize(int[] size) {
        mapGridWidth = size[0];
        mapGridHeight = size[1];
    }

    private void generateCubes() {
        singleThreadedExecutor.submit(new Runnable() {
            @Override
            public void run() {
                createEntityRendererData();
            }
        });
    }

    private int totalEntities;

    private int cubeCount = 0;
    private int spriteCount = 0;

    private void createEntityRendererData() {
        // X, Y, Z
        // The normal is used in light calculations and is a vector which points
        // orthogonal to the plane of the surface. For a cube model, the normals
        // should be orthogonal to the points of each face.
        final float[] CUBE_NORMAL_DATA = {
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

        // Todo: this is just a guesstimate
        totalEntities = (visibleGridWidth * visibleGridHeight) * 2;

        int cubePositionDataSize = CUBE_POSITION_SIZE * totalEntities;
        int cubeUvDataSize = CUBE_UV_SIZE * totalEntities;
        int cubeNormalDataSize = CUBE_NORMAL_SIZE * totalEntities;

        int spritePositionDataSize = SPRITE_POSITION_SIZE * totalEntities;
        int spriteUvDataSize = SPRITE_UV_SIZE * totalEntities;
        int spriteNormalDataSize = SPRITE_NORMAL_SIZE * totalEntities;

        try {
            float[] cubePositionData = new float[cubePositionDataSize];
            float[] cubeNormalData = new float[cubeNormalDataSize];
            float[] cubeUvData = new float[cubeUvDataSize];

            float[] spritePositionData = new float[spritePositionDataSize];
            float[] spriteNormalData = new float[spriteNormalDataSize];
            float[] spriteUvData = new float[spriteUvDataSize];

            final float size = 2f;

            int cubePositionDataOffset = 0;
            int cubeNormalDataOffset = 0;
            int cubeUvDataOffset = 0;

            int spritePositionDataOffset = 0;
            int spriteNormalDataOffset = 0;
            int spriteUvDataOffset = 0;

            RandomNumberGenerator rng = new RandomNumberGenerator();

            // For each position on grid, generate a cube and texture each side using Sprite object

            for (int gridX = 0; gridX < visibleGridWidth; gridX++) {
                for (int gridY = 0; gridY < visibleGridHeight; gridY++) {
                    Sprite sprite = currentFloorData.terrain[gridX][gridY];

                    float x = gridX * size;
                    float y = gridY * size;
                    float z = sprite.zLayer * size;

                    // Render as cube.
                    // Note that we use x,z to represent x,y in floor data, and use y for height

                    float x1 = x;
                    float x2 = x + size;

                    float y1 = z;
                    float y2 = z + size;

                    float z1 = y;
                    float z2 = y + size;

                    // Define points for a cube.
                    float[] p1p = { x1, y2, z2 };
                    float[] p2p = { x2, y2, z2 };
                    float[] p3p = { x1, y1, z2 };
                    float[] p4p = { x2, y1, z2 };
                    float[] p5p = { x1, y2, z1 };
                    float[] p6p = { x2, y2, z1 };
                    float[] p7p = { x1, y1, z1 };
                    float[] p8p = { x2, y1, z1 };

                    float[] thisCubePositionData = ShapeBuilder.generateCubeData(p1p, p2p, p3p, p4p, p5p, p6p, p7p, p8p, p1p.length);
                    System.arraycopy(thisCubePositionData, 0, cubePositionData, cubePositionDataOffset, thisCubePositionData.length);
                    cubePositionDataOffset += thisCubePositionData.length;

                    System.arraycopy(CUBE_NORMAL_DATA, 0, cubeNormalData, cubeNormalDataOffset, CUBE_NORMAL_DATA.length);
                    cubeNormalDataOffset += CUBE_NORMAL_DATA.length;

                    float[] thisCubeUv = cachedCubeUvs[spriteIndexes.get(sprite.path)];
                    System.arraycopy(thisCubeUv, 0, cubeUvData, cubeUvDataOffset, thisCubeUv.length);
                    cubeUvDataOffset += thisCubeUv.length;

                    cubeCount++;

                    for (Sprite object : currentFloorData.getObjects()[gridX][gridY]) {
                        z = object.zLayer * size;

                        if (object.wrapToCube) {
                            // Add to cube batch
                            x1 = x;
                            x2 = x + size;

                            y1 = z;
                            y2 = z + size;

                            z1 = y;
                            z2 = y + size;

                            float[] p1 = { x1, y2, z2 };
                            float[] p2 = { x2, y2, z2 };
                            float[] p3 = { x1, y1, z2 };
                            float[] p4 = { x2, y1, z2 };
                            float[] p5 = { x1, y2, z1 };
                            float[] p6 = { x2, y2, z1 };
                            float[] p7 = { x1, y1, z1 };
                            float[] p8 = { x2, y1, z1 };

                            thisCubePositionData = ShapeBuilder.generateCubeData(p1, p2, p3, p4, p5, p6, p7, p8, p1.length);
                            System.arraycopy(thisCubePositionData, 0, cubePositionData, cubePositionDataOffset, thisCubePositionData.length);
                            cubePositionDataOffset += thisCubePositionData.length;

                            System.arraycopy(CUBE_NORMAL_DATA, 0, cubeNormalData, cubeNormalDataOffset, CUBE_NORMAL_DATA.length);
                            cubeNormalDataOffset += CUBE_NORMAL_DATA.length;

                            thisCubeUv = cachedCubeUvs[spriteIndexes.get(object.path)];
                            System.arraycopy(thisCubeUv, 0, cubeUvData, cubeUvDataOffset, thisCubeUv.length);
                            cubeUvDataOffset += thisCubeUv.length;
                            cubeCount++;
                        }

                        else {
                            // Add to sprite batch

                            x1 = x;
                            x2 = x + size;

                            y1 = z;
                            y2 = z + size;

                            // z1 = y;
                            z2 = y + (size / 2f) + size;

                            float[] p1 = { x1, y2, z2 };
                            float[] p2 = { x2, y2, z2 };
                            float[] p3 = { x1, y1, z2 };
                            float[] p4 = { x2, y1, z2 };

                            float[] thisSpritePositionData = ShapeBuilder.generateSpriteData(p1, p2, p3, p4, p1.length);
                            System.arraycopy(thisSpritePositionData, 0, spritePositionData,
                                    spritePositionDataOffset, thisSpritePositionData.length);

                            spritePositionDataOffset += thisSpritePositionData.length;

                            System.arraycopy(SpriteBatch.SPRITE_FRONT_NORMAL_DATA, 0, spriteNormalData,
                                    spriteNormalDataOffset, SpriteBatch.SPRITE_FRONT_NORMAL_DATA.length);

                            spriteNormalDataOffset += SpriteBatch.SPRITE_FRONT_NORMAL_DATA.length;

                            float[] thisSpriteUvData = cachedSpriteUvs[spriteIndexes.get(object.path)];
                            System.arraycopy(thisSpriteUvData, 0, spriteUvData,
                                    spriteUvDataOffset, thisSpriteUvData.length);
                            spriteUvDataOffset += thisSpriteUvData.length;

                            spriteCount++;
                        }
                    }
                }
            }

            Log.v(LOG_TAG, "Rendering " + cubeCount + " cubes and " + spriteCount + " sprites");

            createCubeVBO(cubePositionData, cubeNormalData, cubeUvData, cubeCount);
            createSpriteVBO(spritePositionData, spriteNormalData, spriteUvData, spriteCount);

        } catch (OutOfMemoryError e) {
            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", e);
        }
    }

    private void createCubeVBO(final float[] cubePositionData, final float[] cubeNormalData,
                               final float[] cubeUvData, final int count) {

        gameSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (cubes != null) {
                    Log.d(LOG_TAG, "Releasing cubes");
                    cubes.release();
                    cubes = null;
                }

                // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
                System.gc();

                try {
                    cubes = new CubeBatch(cubePositionData, cubeNormalData, cubeUvData, count);

                } catch (OutOfMemoryError err) {
                    if (cubes != null) {
                        cubes.release();
                        cubes = null;
                    }

                    // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
                    System.gc();

                    Log.d(LOG_TAG, "Yikes", err);
                }
            }
        });
    }

    private void createSpriteVBO(final float[] spritePositionData, final float[] spriteNormalData,
                               final float[] spriteUvData, final int count) {

        gameSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (sprites != null) {
                    Log.d(LOG_TAG, "Releasing sprites");
                    sprites.release();
                    sprites = null;
                }

                // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
                System.gc();

                try {
                    sprites = new SpriteBatch(spritePositionData, spriteNormalData, spriteUvData, count);
                    // Note: we still use the cube shader to render these
                    sprites.setShaderHandles(cubeProgramHandle, textureUniformHandle);

                } catch (OutOfMemoryError err) {
                    if (sprites != null) {
                        sprites.release();
                        sprites = null;
                    }

                    // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
                    System.gc();

                    Log.d(LOG_TAG, "Yikes", err);
                }
            }
        });
    }

    private int shadowMapWidth;
    private int shadowMapHeight;
    private int depthMapTextureId;
    private int depthMapFrameBufferId;

    private void createDepthMapFBO(int width, int height) {
        shadowMapWidth = width;
        shadowMapHeight = height;

        int[] fboId = new int[1];
        int[] texId = new int[1];

        // create a framebuffer object
        GLES20.glGenFramebuffers(1, fboId, 0);
        depthMapFrameBufferId = fboId[0];

        // Generate a texture to use to store depth map
        GLES20.glGenTextures(1, texId, 0);
        depthMapTextureId = texId[0];

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthMapTextureId);

        // Use a depth texture
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT, shadowMapWidth, shadowMapHeight,
                0, GLES20.GL_DEPTH_COMPONENT, GLES20.GL_UNSIGNED_INT, null);

        // GL_LINEAR does not make sense for depth texture. However, next tutorial shows usage of GL_LINEAR and PCF. Using GL_NEAREST
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Remove artifact on the edges of the shadowmap
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Attach the depth texture to FBO depth attachment point
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, depthMapFrameBufferId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_TEXTURE_2D, depthMapTextureId, 0);

        // Unbind on completion
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // check FBO status
        int FBOstatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (FBOstatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(LOG_TAG, "GL_FRAMEBUFFER_COMPLETE failed, CANNOT use FBO");
            throw new RuntimeException("GL_FRAMEBUFFER_COMPLETE failed, CANNOT use FBO");
        }
    }

    private void addUiText() {
        // Component[] playerComponents = currentFloorData.getPlayer();
        // long entity = playerComponents[0].id;
        // ComponentManager componentManager = ComponentManager.getInstance();
        //  vitality = (Vitality) componentManager.getEntityComponent(entity, Vitality.class.getSimpleName());
        // String hp = "HP: " + vitality.hp;
        String hp = "(" + actualLightPosition[0] + ", " + actualLightPosition[1] + ", " + actualLightPosition[2] + ")";

        String worldState = timeManager.getTimeString() + " (" + weatherManager.getWeatherString() + ")";
        String cameraPos = ((int) x) + ", " + ((int) y) + ", " + ((int) z);
        String fps = fpsCount + " fps";

        uiController.setUiText(hp, worldState, cameraPos, fps);
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

        visibleGridWidth = 64;
        visibleGridHeight = 64;
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

    public void switchRenderMode() {
        renderUsingDepthMap = !renderUsingDepthMap;
    }

    public void setCameraRotationX(float deltaX) {
        this.deltaX = deltaX;
        xAngle += (deltaX * fraction);
        calculateCameraRotation();
    }

    public void setCameraRotationY(float deltaY) {
        this.deltaY = deltaY;
        yAngle += (deltaY * fraction);
        calculateCameraRotation();
    }

    public void setScaleDelta(float delta) {
        x += lx * (-delta / 10f);
        y += ly * (-delta / 10f);
        z += lz * (-delta / 10f);

        calculateCameraRotation();
    }

    private void calculateCameraRotation() {
        lz = (float) (Math.cos(yAngle) * Math.cos(xAngle));
        lx = (float) -(Math.sin(xAngle) * Math.cos(yAngle));
        ly = (float) -Math.sin(yAngle);
    }

    public void setZoom(float zoomLevel) {
        this.zoomLevel = zoomLevel;
        //setProjection();
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

    public void setFrame(Frame floorData) {
        currentFloorData = floorData;
        hasGameData = true;
        generateCubes();
        renderState = GAME;
    }

    private float[][] cachedSpriteUvs;

    private void precalculateSpriteUvs(int numberOfIndexes) {
        cachedSpriteUvs = new float[numberOfIndexes][CUBE_UV_SIZE];

        for (int i = 0; i < numberOfIndexes; i++) {
            int row = i / SPRITES_PER_ROW;
            int col = i % SPRITES_PER_ROW;

            float u = col * SPRITE_BOX_WIDTH;
            float u2 = u + SPRITE_BOX_WIDTH;
            float v = row * SPRITE_BOX_HEIGHT;
            float v2 = v + SPRITE_BOX_HEIGHT;

            float[] uv = {
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v
            };

            cachedSpriteUvs[i] = uv;
        }
    }

    private void precalculateCubeUvs(int numberOfIndexes) {
        cachedCubeUvs = new float[numberOfIndexes][CUBE_UV_SIZE];

        for (int i = 0; i < numberOfIndexes; i++) {
            int row = i / SPRITES_PER_ROW;
            int col = i % SPRITES_PER_ROW;

            float u = col * SPRITE_BOX_WIDTH;
            float u2 = u + SPRITE_BOX_WIDTH;
            float v = row * SPRITE_BOX_HEIGHT;
            float v2 = v + SPRITE_BOX_HEIGHT;

            // TODO: yikes
            float[] uv = {
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v,
                    u, v,
                    u, v2,
                    u2, v,
                    u, v2,
                    u2, v2,
                    u2, v
            };

            cachedCubeUvs[i] = uv;
        }
    }
}
