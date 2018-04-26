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
import com.sonicmax.bloodrogue.generator.Chunk;
import com.sonicmax.bloodrogue.generator.tools.SimplexNoiseGenerator;
import com.sonicmax.bloodrogue.renderer.vbos.CubeBatch;
import com.sonicmax.bloodrogue.renderer.geometry.ShapeBuilder;
import com.sonicmax.bloodrogue.renderer.shaders.GLShaderLoader;
import com.sonicmax.bloodrogue.renderer.textures.TextureLoader;
import com.sonicmax.bloodrogue.renderer.vbos.LineBatch;
import com.sonicmax.bloodrogue.renderer.vbos.SpriteBatch;
import com.sonicmax.bloodrogue.renderer.sprites.SpriteRenderer;
import com.sonicmax.bloodrogue.renderer.text.Status;
import com.sonicmax.bloodrogue.renderer.text.TextObject;
import com.sonicmax.bloodrogue.renderer.textures.UvHelper;
import com.sonicmax.bloodrogue.tilesets.ExteriorTileset;
import com.sonicmax.bloodrogue.ui.UserInterfaceController;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

public class GameRenderer3D implements GLSurfaceView.Renderer {
    private final String LOG_TAG = this.getClass().getSimpleName();

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

    // For debugging - switches between full render and FBO contents
    private final int FULL_RENDER = 0;
    private final int REFLECTION_TEX = 1;
    private final int DEPTH_MAP = 2;
    private final int MAX_RENDER_MODES = 2;

    private HashMap<String, Integer> textureHandles; // Texture handles for loaded textures
    private HashMap<String, Integer> spriteIndexes; // Index on sprite sheet for particular texture
    private float[][] cachedCubeUvs;
    private float[][] cachedSpriteUvs;

    // Matrices for OpenGL rendering
    private float[] modelMatrix;
    private float[] viewMatrix;
    private float[] projectionMatrix;
    private float[] mvMatrix;
    private float[] mvpMatrix;
    private float[] normalMatrix;
    private float[] sunViewMatrix;
    private float[] sunProjMatrix;
    private float[] lightMvpMatrix;
    private float[] billboardMvMatrix;
    private float[] billboardMvpMatrix;
    private float[] skyViewMatrix;
    private float[] skyMvMatrix;
    private float[] skyMvpMatrix;

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
    private int cubeProgramHandle;
    private int spriteProgramHandle;
    private int depthMapProgramHandle;
    private int debugDepthMapProgramHandle;
    private int skyboxProgramHandle;
    private int waterProgramHandle;
    private int debugTexProgramHandle;

    // Uniform handles
    private int mvpMatrixUniform;
    private int mvMatrixUniform;
    private int lightPosUniform;
    private int lightColourUniform;
    private int textureUniform;
    private int normalMatrixUniform;
    private int startFadeUniform;
    private int endFadeUniform;
    private int farUniform;
    private int clippingPlaneUniform;
    private int lightMvpMatrixUniform;
    private int depthMapTextureUniform;
    private int depthMapMVPMatrixUniform;
    private int viewPositionUniform;
    private int depthMapSpriteSheetUniform;
    private int skyboxMvpMatrixUniform;
    private int skyboxSkySunColourTexUniform;
    private int skyboxSunPosUniform;
    private int skyColourUniform;
    private int waterMvMatrixUniform;
    private int waterMvpMatrixUniform;
    private int waterLightPosUniform;
    private int waterLightColourUniform;
    private int waterViewPosUniform;
    private int waterTimeUniform;
    private int waterReflectiveTextureUniform;
    private int waterNormalMatrixUniform;
    private int waterSkyBoxMatrixUniform;
    private int waterEyePosModelUniform;
    private int waterSunPosModelUniform;
    private int waterSkyColourUniform;
    private int waterDuDvMapUniform;
    private int waterNormalMapUniform;

    public int renderState;

    private float deltaX;
    private float deltaY;

    private ExecutorService singleThreadedExecutor;
    private UserInterfaceController uiController;
    private GLSurfaceView gameSurfaceView;
    private Context context;
    private GameInterface gameInterface;
    private TextureLoader textureLoader;
    private TimeManager timeManager;
    private WeatherManager weatherManager;
    private SpriteRenderer depthDebugger;
    private SpriteRenderer texDebugger;

    // VBOs
    private CubeBatch cubes;
    private SpriteBatch sprites;
    private CubeBatch skyBox;
    private SpriteBatch ground;
    private SpriteBatch water;
    private SpriteBatch sun;

    // Resolution and scaling
    private int screenWidth;
    private int screenHeight;
    private float screenRatio;
    private float zoomLevel = 1f;

    // Frame timing
    private long currentFrameTime;
    private long startTime;
    private long endTime;
    private int frameCount;
    private boolean halfSecPassed;
    private int fpsCount;

    private int depthMapWidth;
    private int depthMapHeight;
    private int depthMapTextureId;
    private int depthMapFrameBufferId;

    private Frame currentFloorData;
    private boolean hasGameData = false;

    private float[] sunStartingPos;
    private float[] sunPosInModelSpace;
    private float[] sunPosInSkybox;
    private float[] sunPosInEyeSpace;

    private float[] cameraPosInModelSpace;
    private float[] cameraPosInEyeSpace;

    private float[] moonStartingPos;
    private float[] moonPosInModelSpace;
    private float[] moonPosInEyeSpace;
    private float[] moonPosInSkybox;

    // Camera rotation
    private float elapsedTimeSeconds = 0f;
    private float elapsedTimeMs = 0f;

    private boolean hasDepthTextureExtension;

    private final float near;
    private final float far;

    private int cubeCount = 0;
    private int spriteCount = 0;

    private int renderMode = 0;

    private int reflectionFrameBufferId;
    private int reflectionTextureId;
    private int reflectionTextureWidth;
    private int reflectionTextureHeight;

    private final float worldGridSize;
    private float seaLevel;

    private final float WAVE_SPEED = 0.001f;
    private float waterMoveFactor = 0.0f;

    private boolean renderDataReady = false;

    private float[] lightColour;
    private float[] skyColour;
    private Camera camera;

    public GameRenderer3D(GameSurfaceView gameSurfaceView, Context context) {
        this.gameSurfaceView = gameSurfaceView;
        this.context = context;
        singleThreadedExecutor = Executors.newSingleThreadExecutor();
        hasDepthTextureExtension = false;
        renderState = NONE;

        modelMatrix = new float[16];
        viewMatrix = new float[16];
        projectionMatrix = new float[16];
        mvMatrix = new float[16];
        mvpMatrix = new float[16];
        normalMatrix = new float[16];
        sunViewMatrix = new float[16];
        sunProjMatrix = new float[16];
        lightMvpMatrix = new float[16];
        billboardMvMatrix = new float[16];
        billboardMvpMatrix = new float[16];
        skyViewMatrix = new float[16];
        skyMvMatrix = new float[16];
        skyMvpMatrix = new float[16];

        // Initialise sun, moon and camera vectors.
        sunStartingPos = new float[] {0f, -5f, 0f, 0f};
        sunPosInModelSpace = new float[4];
        sunPosInEyeSpace = new float[4];
        sunPosInSkybox = new float[4];
        moonStartingPos = new float[] {0f, -5f, 0f, 0f};
        moonPosInModelSpace = new float[4];
        moonPosInEyeSpace = new float[4];
        moonPosInSkybox = new float[4];
        cameraPosInModelSpace = new float[] {192f, 40f, 192f, 1f};
        cameraPosInEyeSpace = new float[4];

        // Near and far values for camera view frustum
        near = 1.0f;
        far = 10000.0f;

        // The size of a cell in world space
        worldGridSize = 16f;

        // Height to render our water quad
        seaLevel = worldGridSize * 3f;

        // Depth map resolution. Doesn't have to match screen resolution as we render this to a framebuffer
        depthMapWidth = 1024;
        depthMapHeight = 1024;

        camera = new Camera();
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        checkGlExtensions();
        loadShaders();
        loadResources();
        calculateMatrices();

        cachedCubeUvs = UvHelper.precalculateCubeUvs(spriteIndexes.size());
        cachedSpriteUvs = UvHelper.precalculateSpriteUvs(spriteIndexes.size());

        uiController = new UserInterfaceController(gameInterface, spriteIndexes);
        int spriteSheetHandle = textureHandles.get("sprite_sheets/sheet.png");
        int fontHandle = textureHandles.get("fonts/ccra_font.png");
        uiController.prepareUiRenderer(spriteProgramHandle, spriteSheetHandle, fontHandle);
        uiController.calculateTextRowHeight();

        // Debugging: create a renderer to output texture to screen
        texDebugger = new SpriteRenderer();
        texDebugger.initShader(debugTexProgramHandle);

        // Debugging: create a renderer to output depth map texture to screen
        depthDebugger = new SpriteRenderer();
        depthDebugger.initShader(debugDepthMapProgramHandle);

        prepareGlSurface();
        getGlUniforms();

        renderState = TITLE;
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        Log.v(LOG_TAG, "Device resolution: " + width + "x" + height);

        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        screenWidth = width;
        screenHeight = height;

        scaleContent();
        calculateContentGrid();
        setGridChunkToRender();
        camera.setProjection(width, height, FOV, near, far);
        createDepthMapFBO();
        createWaterReflectionFBO(width, height);

        // For debugging: pass screen resolution to texture/depth map renderers so we can

        texDebugger.setScaleFactor(scaleFactor);
        texDebugger.prepareFullScreenRender((float) width, (float) height);

        depthDebugger.setScaleFactor(scaleFactor);
        depthDebugger.prepareFullScreenRender((float) width, (float) height);
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

    /*
    ------------------------------------------------------------------------------------------
    Getters and setters
    ------------------------------------------------------------------------------------------
    */

    public void setRenderState(int state) {
        renderState = state;
    }

    public int getRenderState() {
        return renderState;
    }

    public HashMap<String, Integer> getSpriteIndexes() {
        return spriteIndexes;
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

    /*
    ------------------------------------------------------------------------------------------
    Resource and renderer preparation
    ------------------------------------------------------------------------------------------
    */

    private void checkGlExtensions() {
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);

        if (extensions.contains("OES_depth_texture")) {
            hasDepthTextureExtension = true;
        }
        else {
            // Todo: create depth map shader for platforms without OES_depth_texture extension
            throw new RuntimeException("Didn't have OES_depth_texture extension");
        }

        if (extensions.contains("GL_EXT_shadow_samplers")) {
            // Todo: separate shader using this extension? + figure out coverage to see if it's worth it
            Log.v(LOG_TAG, "Supports shadow samplers");
        }
        else {
            Log.v(LOG_TAG, "Doesn't support shadow samplers");
        }

        if (extensions.contains("GL_IMG_texture_compression_pvrtc")){
            //Use PVR compressed textures
            Log.v(LOG_TAG, "Supports PVR compression");
        }

        else if (extensions.contains("GL_AMD_compressed_ATC_texture") || extensions.contains("GL_ATI_texture_compression_atitc")){
            Log.v(LOG_TAG, "Supports ATI compression");
        }

        else if (extensions.contains("GL_OES_texture_compression_S3TC") || extensions.contains("GL_EXT_texture_compression_s3tc")){
            Log.v(LOG_TAG, "Supports DTX compression");
        }

        else {
            Log.v(LOG_TAG, "Doesn't support texture compression");
        }

        int[] max = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, max, 0);

        Log.v(LOG_TAG, "Max texture units: " + max[0]);
    }

    private int debugLineProgramHandle;

    private void loadShaders() {
        GLShaderLoader loader = new GLShaderLoader(context);
        cubeProgramHandle = loader.compileCubeShader();
        spriteProgramHandle = loader.compileSpriteShader();
        depthMapProgramHandle = loader.compileDepthMapShader();
        debugTexProgramHandle = loader.compileDebugTexShader();
        debugDepthMapProgramHandle = loader.compileDebugDepthMapShader();
        skyboxProgramHandle = loader.compileSkyBoxShader();
        waterProgramHandle = loader.compileWaterShader();
        debugLineProgramHandle = loader.compileDebugLineShader();
    }

    private void loadResources() {
        textureLoader = new TextureLoader();
        textureLoader.loadImagesFromDisk(gameInterface.getAssets());
        spriteIndexes = textureLoader.getSpriteIndexes();
        textureHandles = textureLoader.getTextureHandles();
        gameInterface.setSpriteIndexes(spriteIndexes);
    }

    private void prepareGlSurface() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearDepthf(1.0f);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDepthRangef(0.0f, 1.0f);
        GLES20.glDepthMask(true);
    }

    private int debugLineMvpMatrixUniform;
    private int checkBackFaceUniform;
    private int skyboxTimeUniform;
    private int skyboxSkyColourTexUniform;

    private int sunPosModelUniform;

    private int skyColourTexUniform;
    private int skyColourWithSunTexUniform;
    private int timeOfDayUniform;

    private void getGlUniforms() {
        mvpMatrixUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_MVPMatrix");
        mvMatrixUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_MVMatrix");
        normalMatrixUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_NormalMatrix");
        lightPosUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_SunPos");
        lightColourUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_lightColour");
        skyColourUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_skyColour");
        textureUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_Texture");
        depthMapTextureUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_DepthMap");
        lightMvpMatrixUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_LightMvpMatrix");
        viewPositionUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_ViewPos");
        startFadeUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_StartFadeDistance");
        endFadeUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_EndFadeDistance");
        farUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_Far");
        clippingPlaneUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_ClippingPlane");
        checkBackFaceUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_CheckBackFace");
        skyColourWithSunTexUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_SkyGradientWithSun");
        skyColourTexUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_SkyGradient");
        timeOfDayUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_TimeOfDay");
        sunPosModelUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_SunPosModel");


        depthMapMVPMatrixUniform = GLES20.glGetUniformLocation(depthMapProgramHandle, "u_MVPMatrix");
        depthMapSpriteSheetUniform = GLES20.glGetUniformLocation(depthMapProgramHandle, "u_Texture");

        skyboxMvpMatrixUniform = GLES20.glGetUniformLocation(skyboxProgramHandle, "u_MVPMatrix");
        skyboxSkySunColourTexUniform = GLES20.glGetUniformLocation(skyboxProgramHandle, "u_SkyGradientWithSun");
        skyboxSkyColourTexUniform = GLES20.glGetUniformLocation(skyboxProgramHandle, "u_SkyGradient");
        skyboxSunPosUniform = GLES20.glGetUniformLocation(skyboxProgramHandle, "u_SunPosition");
        skyboxTimeUniform = GLES20.glGetUniformLocation(skyboxProgramHandle, "u_TimeOfDay");

        waterMvMatrixUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_MVMatrix");
        waterMvpMatrixUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_MVPMatrix");
        waterViewPosUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_EyePos");
        waterLightPosUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_SunPos");
        waterEyePosModelUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_EyeModelPos");
        waterSunPosModelUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_SunModelPos");
        waterLightColourUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_LightColour");
        waterSkyColourUniform = GLES20.glGetUniformLocation(cubeProgramHandle, "u_skyColour");
        waterTimeUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_Time");
        waterReflectiveTextureUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_ReflectiveTexture");
        waterNormalMatrixUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_NormalMatrix");
        waterSkyBoxMatrixUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_SkyBoxMvpMatrix");
        waterDuDvMapUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_DuDvMap");
        waterNormalMapUniform = GLES20.glGetUniformLocation(waterProgramHandle, "u_NormalMap");

        debugLineMvpMatrixUniform = GLES20.glGetUniformLocation(debugLineProgramHandle, "u_MVPMatrix");
    }

    /*
    ------------------------------------------------------------------------------------------
    Camera
    ------------------------------------------------------------------------------------------
    */

    private void calculateMatrices() {
        Matrix.setIdentityM(modelMatrix, 0);

        cameraPosInModelSpace = camera.getPosition();
        viewMatrix = camera.getView();
        projectionMatrix = camera.getProjection();

        // Calculate model-view, model-view-projection and normal matrices
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Calculate inverted-transposed normal matrix
        float[] tempResultMatrix = new float[16];
        Matrix.invertM(tempResultMatrix, 0, mvMatrix, 0);
        Matrix.transposeM(normalMatrix, 0, tempResultMatrix, 0);

        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);

        // While we're here we may as well calculate the camera position in eye space
        cameraPosInEyeSpace = new float[4];
        Matrix.multiplyMV(cameraPosInEyeSpace, 0, mvMatrix, 0, cameraPosInModelSpace, 0);
    }

    public void setCameraRotation(float deltaX, float deltaY) {
        final float fraction = 1.0f;
        camera.addRotation(deltaX * fraction, deltaY * fraction);
    }

    public void setScaleDelta(float delta) {
        // Negate scale delta - we want inward movement of scale to move camera forwards
        camera.moveForwards(-delta);
    }


    /*
    ------------------------------------------------------------------------------------------
    Skybox
    ------------------------------------------------------------------------------------------
    */

    /**
     * Calculates geocentric coordinates for sun given current time. Not accurate at all. ¯\_(ツ)_/¯
     */

    private void calculateSunPosition() {
        final long ticksPerDay = 5760L;

        // One full rotation = 360 degrees / 5760 ticks (1440 minutes, 4 ticks per minute)
        long rotationCounter = timeManager.getTotalTicks() % ticksPerDay;
        float lightRotationDegree = (360.0f / 5760.0f) * ((int) rotationCounter);

        // First, calculate position of sun in skybox:

        // Rotate sun around x axis of skybox origin
        float[] rotationMatrix = new float[16];
        Matrix.setIdentityM(rotationMatrix, 0);
        Matrix.translateM(rotationMatrix, 0, 0f, 5f, 0f);
        Matrix.rotateM(rotationMatrix, 0, lightRotationDegree, 1f, 0f, 0f);

        // Todo: billboarding

        // Find new position of sun in skybox.
        Matrix.multiplyMV(sunPosInSkybox, 0, rotationMatrix, 0, sunStartingPos, 0);

        // Now we can find position of sun relative to world:

        // Push position of sun in skybox into distance. We will use this vector for our diffuse/specular lighting calculations

        float[] actualSunPosition = new float[4];
        float scalar = 100000.0f;
        actualSunPosition[0] = sunPosInSkybox[0] * scalar;
        actualSunPosition[1] = sunPosInSkybox[1] * scalar;
        actualSunPosition[2] = sunPosInSkybox[2] * scalar;
        actualSunPosition[3] = 1.0f;

        // For our depth map render, translate skybox position by camera position.
        float depthScalar = 100f;
        sunPosInModelSpace[0] = cameraPosInModelSpace[0] + (sunPosInSkybox[0] * depthScalar);
        sunPosInModelSpace[1] = cameraPosInModelSpace[1] + (sunPosInSkybox[1] * depthScalar);
        sunPosInModelSpace[2] = cameraPosInModelSpace[2] + (sunPosInSkybox[2] * depthScalar);
        sunPosInModelSpace[3] = 1.0f;

        // Find sun position in eye space.
        Matrix.multiplyMV(sunPosInEyeSpace, 0, mvMatrix, 0, actualSunPosition, 0);

        // Now we can set view and projection matrices for depth map generation:

        // Todo: we should calculate a bounding box for the camera view frustum and base the depth map view/proj matrices on that

        // Set view matrix so that translated sun position is looking at eye position of camera.
        Matrix.setLookAtM(sunViewMatrix, 0,
                sunPosInModelSpace[0], sunPosInModelSpace[1], sunPosInModelSpace[2],
                cameraPosInModelSpace[0], cameraPosInModelSpace[1], cameraPosInModelSpace[2],
                0f, 1f, 0f);

        // Set light projection matrix. We use ortho here as we want a directional light effect
        Matrix.orthoM(sunProjMatrix, 0,
                -depthMapWidth / 2, depthMapWidth / 2,
                -depthMapHeight / 2, depthMapHeight / 2,
                near, far);

        // Calculate model-view-proj for depth map rendering
        float[] lightMvMatrix = new float[16];
        Matrix.multiplyMM(lightMvMatrix, 0, sunViewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(lightMvpMatrix, 0, sunProjMatrix, 0, lightMvMatrix, 0);

        // Update position of sun in VBO
        float[] newPosition = getSunVertices();

        if (sun != null) {
            sun.updatePosition(0, newPosition);
        }
    }

    /**
     * Calculates geocentric coordinates for moon given current date and time.
     * Needs to be rotated to account for rotation of Earth.
     *
     * Algorithm from http://www.stjarnhimlen.se/comp/ppcomp.html
     */

    private void calculateMoonPosition() {
        int year = timeManager.getYear() + 1;
        int month = timeManager.getMonth() + 1;
        int date = timeManager.getDay() + 1;

        // Get minutes/hours as decimal of day. Note: Day 0.0 occurs at 2000 Jan 0.0 UT
        int dayComponent = (367 * year) - 7 * (year + (month + 9) / TimeManager.MONTHS_IN_YEAR) / 4 + (275 * month / 9) + date - 730530;
        float decimalHours = (timeManager.getTimeOfDayInMinutes() / 60f) / 24f;
        int seconds = timeManager.getCurrentTick() * TimeManager.SECONDS_IN_TICK;
        decimalHours += (seconds / 86400f);

        float ut = dayComponent + decimalHours;

        // Orbital elements of the moon:
        double ascendingNodeLongitude = (125.1228 - 0.0529538083 * ut);
        double perihelionArgument = (318.0634 + 0.1643573223 * ut);
        double meanAnomaly = (115.3654 + 13.0649929509 * ut);
        double eclipticInclination = 5.1454;
        double semiMajorAxis = 5.0; // Distance from earth to moon. Scaled down to fit skybox
        double eccentricity = 0.054900;

        ascendingNodeLongitude = ascendingNodeLongitude % 360.0;
        perihelionArgument = perihelionArgument % 360.0;
        meanAnomaly = meanAnomaly % 360.0;

        if (ascendingNodeLongitude < 0.0) ascendingNodeLongitude += 360.0;
        if (perihelionArgument < 0.0) perihelionArgument += 360.0;
        if (meanAnomaly < 0.0) meanAnomaly += 360.0;

        ascendingNodeLongitude = Math.toRadians(ascendingNodeLongitude);
        perihelionArgument = Math.toRadians(perihelionArgument);
        meanAnomaly = Math.toRadians(meanAnomaly);
        eccentricity = Math.toRadians(eccentricity);
        eclipticInclination = Math.toRadians(eclipticInclination);

        double eccentricAnomaly = meanAnomaly + eccentricity * Math.sin(meanAnomaly) * (1.0 + eccentricity * Math.cos(meanAnomaly));

        // It's suggested to perform a second calculation to improve accuracy when eccentricity is > 0.05
        // however in testing the difference between initial + improved calculation was typically less than 0.0001

        // Compute distance and true anomaly:
        double xv = semiMajorAxis * (Math.cos(eccentricAnomaly) - eccentricity);
        double yv = semiMajorAxis * (Math.sqrt(1.0 - eccentricity * eccentricity) * Math.sin(eccentricAnomaly));
        double trueAnomaly = Math.atan2(yv, xv);
        double distance = Math.sqrt(xv*xv + yv*yv);

        // Compute position in 3-dimensional space. These are already geocentric
        double xh = distance * (Math.cos(ascendingNodeLongitude) * Math.cos(trueAnomaly+perihelionArgument)
                - Math.sin(ascendingNodeLongitude) * Math.sin(trueAnomaly+perihelionArgument) * Math.cos(eclipticInclination));

        double yh = distance * (Math.sin(ascendingNodeLongitude) * Math.cos(trueAnomaly+perihelionArgument)
                + Math.cos(ascendingNodeLongitude) * Math.sin(trueAnomaly+perihelionArgument) * Math.cos(eclipticInclination));

        double zh = distance * (Math.sin(trueAnomaly+perihelionArgument) * Math.sin(eclipticInclination));

        float[] moonPosInSpace = new float[4];

        moonPosInSpace[0] = (float) xh;
        moonPosInSpace[1] = (float) yh;
        moonPosInSpace[2] = (float) zh;
        moonPosInSpace[3] = 1f;

        final long ticksPerDay = 5760L;
        long rotationCounter = timeManager.getTotalTicks() % ticksPerDay;
        float lightRotationDegree = (360.0f / 5760.0f) * ((int) rotationCounter);
        float[] rotationMatrix = new float[16];
        Matrix.setIdentityM(rotationMatrix, 0);
        Matrix.translateM(rotationMatrix, 0, -moonPosInSpace[0], -moonPosInSpace[1], -moonPosInSpace[2]);
        Matrix.rotateM(rotationMatrix, 0, lightRotationDegree, 1f, 0f, 0f);
        Matrix.translateM(rotationMatrix, 0, moonPosInSpace[0], moonPosInSpace[1], moonPosInSpace[2]);
        Matrix.multiplyMV(moonPosInSkybox, 0, rotationMatrix, 0, moonPosInSpace, 0);

        moonPosInSkybox[0] = (float) xh;
        moonPosInSkybox[1] = (float) yh;
        moonPosInSkybox[2] = (float) zh;

        // Update position of moon in VBO
        float[] newVertices = getMoonVertices();

        if (moon != null) {
            moon.updatePosition(0, newVertices);
        }
    }

    private final float SHADOW_VISIBILITY = 100f; // Todo: make configurable
    private final float FOV = 80f; // Todo: make configurable

    private void calculateViewFrustumBoundingBox() {
        float farWidth = (float) (SHADOW_VISIBILITY * Math.tan(Math.toRadians(FOV)));
        float nearWidth = (float) (near * Math.tan(Math.toRadians(FOV)));
        float farHeight = farWidth / screenRatio;
        float nearHeight = nearWidth / screenRatio;


    }

    /*
    ------------------------------------------------------------------------------------------
    Main render loop
    ------------------------------------------------------------------------------------------
    */

    private int timeInMinutes = 0;

    private void checkElapsedTime(long dt) {
        currentFrameTime += dt;

        elapsedTimeSeconds += dt / 1000f;
        elapsedTimeMs += dt;

        frameCount++;

        timeManager.tick();

        int currentTime = timeManager.getTimeOfDayInMinutes();

        if (currentTime != timeInMinutes) {
            timeInMinutes = currentTime;
        }

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
        // Todo: after setting mvp matrices we should probably make sure camera position doesn't change
        calculateMatrices();
        prepareGlSurface();
        calculateSunPosition();
        calculateMoonPosition();

        lightColour = timeManager.getSunlightColour();
        skyColour = timeManager.getSkyColour();

        if (renderDataReady) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            // Render depth map to framebuffer
            renderDepthMap();

            // Render reflection texture to framebuffer
            // Todo: why does commenting this out break everything?
            renderWaterReflectionTexture();

            // GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            // GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            // Now we are actually rendering to screen, so we can set viewport to screen resolution
            GLES20.glViewport(0, 0, screenWidth, screenHeight);

            switch (renderMode) {
                case FULL_RENDER:
                    renderSkybox();
                    renderScene();
                    break;

                // For debugging:
                case REFLECTION_TEX:
                    texDebugger.renderTexture(4);
                    break;

                case DEPTH_MAP:
                    depthDebugger.renderDepthMap(near, far);
                    break;
            }

        } else {
            // Technically in-game but waiting for VBOs
            // Todo: this is probably bad?
            uiController.addSplashText("Loading...");
        }
    }

    private void renderDepthMap() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, depthMapFrameBufferId);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        // For depth map rendering, set viewport to depth map resolution and disable colour masking.
        // (note: we have to do this AFTER binding framebuffer)
        GLES20.glViewport(0, 0, depthMapWidth, depthMapHeight);
        GLES20.glColorMask(false, false, false, false);

        GLES20.glUseProgram(depthMapProgramHandle);

        GLES20.glUniformMatrix4fv(depthMapMVPMatrixUniform, 1, false, lightMvpMatrix, 0);
        GLES20.glUniform1i(depthMapSpriteSheetUniform, 0);

        if (cubes != null) {
            // Enable front face culling to prevent self-shadowing
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_FRONT);
            cubes.renderDepthMap();
        }

        if (sprites != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            sprites.renderDepthMap();
        }

        if (terrain != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            terrain.renderDepthMap();
        }

        // Note: we don't care about distant terrain when rendering depth map. Just ignore for now

        if (ground != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            ground.renderDepthMap();
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void renderWaterReflectionTexture() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, reflectionFrameBufferId);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Set viewport to reflection texture resolution, and make sure colour mask is enabled
        GLES20.glViewport(0, 0, reflectionTextureWidth, reflectionTextureHeight);
        GLES20.glColorMask(true, true, true, true);

        // If we don't bind a texture then we would be rendering into the void
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reflectionTextureId);

        // For reflective texture, we want to move camera to appropriate position
        // and create modified view matrix

        float[] reflectionViewMatrix = new float[16];
        float[] reflectionMvMatrix = new float[16];
        float[] reflectionMvpMatrix = new float[16];
        float[] reflectionNormalMatrix = new float[16];

        // Invert the camera position around the water surface y axis and invert the camera pitch.
        System.arraycopy(viewMatrix, 0, reflectionViewMatrix, 0, viewMatrix.length);
        Matrix.scaleM(reflectionViewMatrix, 0, 1f, -1f, 1f);
        Matrix.translateM(reflectionViewMatrix, 0, 0f, -seaLevel * 2, 0f);

        // RENDER SKYBOX

        GLES20.glUseProgram(skyboxProgramHandle);

        // Disable depth masking to make sure skybox is always behind scene
        GLES20.glDepthMask(false);

        float[] skyModelMatrix = new float[16];
        System.arraycopy(modelMatrix, 0, skyModelMatrix, 0, modelMatrix.length);

        // Flip the x axis of model matrix otherwise texture will be mirrored (as we are inside cube)
        Matrix.scaleM(skyModelMatrix, 0, -1f, 1f, 1f);

        // Sky box view matrix is looking in same direction as camera, but translated to 0,0,0 (centre of sky box)
        skyViewMatrix = camera.getView(new float[] {0f, 0f, 0f});

        // Flip upside down for reflection
        Matrix.scaleM(skyViewMatrix, 0, 1f, -1f, 1f);

        Matrix.multiplyMM(skyMvMatrix, 0, skyViewMatrix, 0, skyModelMatrix, 0);
        Matrix.multiplyMM(skyMvpMatrix, 0, projectionMatrix, 0, skyMvMatrix, 0);

        GLES20.glUniformMatrix4fv(skyboxMvpMatrixUniform, 1, false, skyMvpMatrix, 0);
        GLES20.glUniform1i(skyboxSkySunColourTexUniform, 7);
        GLES20.glUniform1i(skyboxSkyColourTexUniform, 8);
        GLES20.glUniform3f(skyboxSunPosUniform, sunPosInSkybox[0], sunPosInSkybox[1], sunPosInSkybox[2]);

        if (skyBox != null) {
            skyBox.renderSkyBox();
        }

        // RENDER SCENE

        // Use cube shader for other render calls
        GLES20.glUseProgram(cubeProgramHandle);

        float[] tempResultMatrix = new float[16];

        //calculate MV matrix
        Matrix.multiplyMM(reflectionMvMatrix, 0, reflectionViewMatrix, 0, modelMatrix, 0);

        Matrix.invertM(tempResultMatrix, 0, reflectionMvMatrix, 0);
        Matrix.transposeM(reflectionNormalMatrix, 0, tempResultMatrix, 0);

        //pass in Normal Matrix as uniform
        GLES20.glUniformMatrix4fv(normalMatrixUniform, 1, false, reflectionNormalMatrix, 0);

        //calculate MVP matrix
        Matrix.multiplyMM(reflectionMvpMatrix, 0, projectionMatrix, 0, reflectionMvMatrix, 0);

        //pass in MVP Matrix as uniform
        GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, reflectionMvpMatrix, 0);

        float[] sunPosInReflectiveSpace = new float[4];
        Matrix.multiplyMV(sunPosInReflectiveSpace, 0, reflectionViewMatrix, 0, sunPosInSkybox, 0);
        GLES20.glUniform3f(lightPosUniform, sunPosInReflectiveSpace[0], sunPosInReflectiveSpace[1], sunPosInReflectiveSpace[2]);

        float bias[] = new float [] {
                0.5f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.5f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.5f, 0.0f,
                0.5f, 0.5f, 0.5f, 1.0f};

        float[] depthBiasMVP = new float[16];

        Matrix.multiplyMM(depthBiasMVP, 0, bias, 0, lightMvpMatrix, 0);
        System.arraycopy(depthBiasMVP, 0, lightMvpMatrix, 0, 16);

        // Todo: we should disable this...?
        //MVP matrix that was used during depth map render
        GLES20.glUniformMatrix4fv(lightMvpMatrixUniform, 1, false, lightMvpMatrix, 0);

        // Pass in camera position

        cameraPosInEyeSpace = new float[4];
        Matrix.multiplyMV(cameraPosInEyeSpace, 0, reflectionViewMatrix, 0, cameraPosInModelSpace, 0);
        GLES20.glUniform3f(viewPositionUniform, cameraPosInEyeSpace[0], cameraPosInEyeSpace[1], cameraPosInEyeSpace[2]);

        // Pass in global light colour
        GLES20.glUniform3f(lightColourUniform, lightColour[0], lightColour[1], lightColour[2]);
        GLES20.glUniform3f(skyColourUniform, skyColour[0], skyColour[1], skyColour[2]);

        // Pass in texture handles
        GLES20.glUniform1i(textureUniform, 0);
        GLES20.glUniform1i(depthMapTextureUniform, 3);

        GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, reflectionMvMatrix, 0);
        GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, reflectionMvpMatrix, 0);

        // Misc stuff
        GLES20.glUniform1f(startFadeUniform, far - 500f);
        GLES20.glUniform1f(endFadeUniform, far);
        GLES20.glUniform1f(farUniform, far);
        GLES20.glUniform1i(checkBackFaceUniform, 0);

        /*if (sun != null) {
            GLES20.glDepthMask(false);

            // Use skybox matrices when rendering sun, to give illusion of distance
            GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, skyMvMatrix, 0);
            GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, skyMvpMatrix, 0);

            // Because sun is rendered close to camera position, we need to set appropriate clipping plane
            GLES20.glUniform1f(clippingPlaneUniform, -10f);

            GLES20.glDisable(GLES20.GL_CULL_FACE);
            sun.render();
        }

        // Set depth mask back to true after rendering sun
        GLES20.glDepthMask(true);*/

        // Pass in normal MV/MVP matrices
        GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, reflectionMvMatrix, 0);
        GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, reflectionMvpMatrix, 0);

        // Clip everything below water height
        GLES20.glUniform1f(clippingPlaneUniform, seaLevel);

        if (cubes != null) {
            // Reverse culling for mirrored render
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_FRONT);

            cubes.render();
        }

        if (sprites != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUniform1f(startFadeUniform, 1250f);
            GLES20.glUniform1f(endFadeUniform, 1500f);
            GLES20.glUniform1i(checkBackFaceUniform, 1);
            sprites.render();
            GLES20.glUniform1i(checkBackFaceUniform, 0);
        }

        if (terrain != null) {
            // Note: when rendering terrain mesh for reflection texture, we want to cull the front faces
            // of the terrain - otherwise you will see this in the reflection at certain angles
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_FRONT);

            GLES20.glUniform1f(startFadeUniform, 1250f);
            GLES20.glUniform1f(endFadeUniform, 1500f);
            terrain.render();
        }

        if (distantTerrain != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUniform1f(startFadeUniform, 1250f);
            GLES20.glUniform1f(endFadeUniform, 1500f);
            distantTerrain.render();
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void renderSkybox() {
        GLES20.glUseProgram(skyboxProgramHandle);

        // Disable depth masking to make sure skybox is always behind scene
        GLES20.glDepthMask(false);

        float[] skyModelMatrix = new float[16];
        System.arraycopy(modelMatrix, 0, skyModelMatrix, 0, modelMatrix.length);

        // Flip the x axis of model matrix otherwise texture will be mirrored (as we are inside cube)
        Matrix.scaleM(skyModelMatrix, 0, -1f, 1f, 1f);

        // Sky box view matrix is same as view matrix but translated to (0,0,0)
        skyViewMatrix = camera.getView(new float[] {0f, 0f, 0f});
        skyViewMatrix = camera.getView(new float[] {0f, 0f, 0f});

        Matrix.multiplyMM(skyMvMatrix, 0, skyViewMatrix, 0, skyModelMatrix, 0);
        Matrix.multiplyMM(skyMvpMatrix, 0, projectionMatrix, 0, skyMvMatrix, 0);

        GLES20.glUniformMatrix4fv(skyboxMvpMatrixUniform, 1, false, skyMvpMatrix, 0);
        GLES20.glUniform1i(skyboxSkySunColourTexUniform, 7);
        GLES20.glUniform1i(skyboxSkyColourTexUniform, 8);
        GLES20.glUniform3f(skyboxSunPosUniform, sunPosInSkybox[0], sunPosInSkybox[1], sunPosInSkybox[2]);

        // We need to pass time of day in minutes as float ranging from [0, 1]
        // This makes it easier to sample our sky gradient texture
        final int MINUTES_IN_DAY = 1440;
        float time = (float) timeManager.getTimeOfDayInMinutes() / MINUTES_IN_DAY;

        GLES20.glUniform1f(skyboxTimeUniform, time);

        if (skyBox != null) {
            skyBox.renderSkyBox();
        }
    }

    private LineBatch lmao = null;

    private void renderScene() {
        GLES20.glUseProgram(cubeProgramHandle);

        passUniformsToCubeShader();

        // Note: we want to cull back faces when rendering cubes, and disable face culling when rendering sprites

        if (sun != null && moon != null) {
            // Make sure depth mask is disabled
            GLES20.glDepthMask(false);

            // Use skybox matrices when rendering sun and moon, to give illusion of distance
            GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, skyMvMatrix, 0);
            GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, skyMvpMatrix, 0);

            GLES20.glUniform1f(clippingPlaneUniform, -10f);

            GLES20.glDisable(GLES20.GL_CULL_FACE);
            sun.render();
            moon.render();
        }

        // Pass in normal MV/MVP matrices
        GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, mvMatrix, 0);
        GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, mvpMatrix, 0);

        // Make sure we render everything below water
        GLES20.glUniform1f(clippingPlaneUniform, 0f);

        // Re-enable depth masking and render rest of scene
        GLES20.glDepthMask(true);

        if (ground != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            ground.render();
        }

        if (terrain != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUniform1f(startFadeUniform, 1250f);
            GLES20.glUniform1f(endFadeUniform, 1500f);
            terrain.render();
        }

        if (distantTerrain != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUniform1f(startFadeUniform, 1250f);
            GLES20.glUniform1f(endFadeUniform, 1500f);
            distantTerrain.render();
        }

        if (sprites != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUniform1f(startFadeUniform, 1250f);
            GLES20.glUniform1f(endFadeUniform, 1500f);

            // To correctly calculate lighting for sprites, we need to tell the shader to
            // reverse the normals for back faces. Disable this check after sprites have been rendered
            GLES20.glUniform1i(checkBackFaceUniform, 1);
            sprites.render();
            GLES20.glUniform1i(checkBackFaceUniform, 0);
        }

        if (cubes != null) {
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);

            cubes.render();
        }

        if (water != null) {
            GLES20.glUseProgram(waterProgramHandle);

            advanceWaterMovement();
            passUniformsToWaterShader();

            GLES20.glDisable(GLES20.GL_CULL_FACE);
            water.render();
        }

        if (debugNormals != null) {
            // FOR DEBUGGING ONLY!
            GLES20.glUseProgram(debugLineProgramHandle);
            GLES20.glUniformMatrix4fv(debugLineMvpMatrixUniform, 1, false, mvpMatrix, 0);
            debugNormals.draw();
        }
    }

    private void advanceWaterMovement() {
        waterMoveFactor += WAVE_SPEED;

        if (waterMoveFactor >= 1) {
            // Wrap move factor from 0.999f (or whatever) to 0.0f
            waterMoveFactor = 0;
        }
    }

    private void passUniformsToCubeShader() {
        GLES20.glUniformMatrix4fv(mvMatrixUniform, 1, false, mvMatrix, 0);
        GLES20.glUniformMatrix4fv(mvpMatrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(normalMatrixUniform, 1, false, normalMatrix, 0);

        GLES20.glUniformMatrix4fv(lightMvpMatrixUniform, 1, false, lightMvpMatrix, 0);

        GLES20.glUniform3f(viewPositionUniform, cameraPosInEyeSpace[0], cameraPosInEyeSpace[1], cameraPosInEyeSpace[2]);
        GLES20.glUniform3f(lightPosUniform, sunPosInEyeSpace[0], sunPosInEyeSpace[1], sunPosInEyeSpace[2]);
        GLES20.glUniform3f(sunPosModelUniform, sunPosInSkybox[0], sunPosInSkybox[1], sunPosInSkybox[2]);

        GLES20.glUniform3f(lightColourUniform, lightColour[0], lightColour[1], lightColour[2]);
        GLES20.glUniform3f(skyColourUniform, skyColour[0], skyColour[1], skyColour[2]);

        GLES20.glUniform1i(textureUniform, 0);
        GLES20.glUniform1i(depthMapTextureUniform, 3);

        GLES20.glUniform1f(startFadeUniform, far - 500f);
        GLES20.glUniform1f(endFadeUniform, far);
        GLES20.glUniform1f(farUniform, far);

        GLES20.glUniform1i(skyColourWithSunTexUniform, 7);
        GLES20.glUniform1i(skyColourTexUniform, 8);

        // We need to pass time of day in minutes as float ranging from [0, 1]
        // This makes it easier to sample our sky gradient texture
        final int MINUTES_IN_DAY = 1440;
        float time = (float) timeManager.getTimeOfDayInMinutes() / MINUTES_IN_DAY;

        GLES20.glUniform1f(timeOfDayUniform, time);

        GLES20.glUniform1i(checkBackFaceUniform, 0);
    }

    private void passUniformsToWaterShader() {
        GLES20.glUniformMatrix4fv(waterSkyBoxMatrixUniform, 1, false, modelMatrix, 0);
        GLES20.glUniformMatrix4fv(waterMvMatrixUniform, 1, false, mvMatrix, 0);
        GLES20.glUniformMatrix4fv(waterMvpMatrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(waterNormalMatrixUniform, 1, false, normalMatrix, 0);

        GLES20.glUniform1f(waterTimeUniform, waterMoveFactor);

        // Pass in our reflective/refractive textures, du/dv map and normal map
        GLES20.glUniform1i(waterReflectiveTextureUniform, 4);
        GLES20.glUniform1i(waterDuDvMapUniform, 5);
        GLES20.glUniform1i(waterNormalMapUniform, 6);

        // Pass in model space positions for certain calculations relating to skybox
        GLES20.glUniform3f(waterEyePosModelUniform, cameraPosInModelSpace[0], cameraPosInModelSpace[1], cameraPosInModelSpace[2]);
        GLES20.glUniform3f(waterSunPosModelUniform, sunPosInSkybox[0], sunPosInSkybox[1], sunPosInSkybox[2]);

        GLES20.glUniform3f(waterViewPosUniform, cameraPosInEyeSpace[0], cameraPosInEyeSpace[1], cameraPosInEyeSpace[2]);
        GLES20.glUniform3f(waterLightPosUniform, sunPosInEyeSpace[0], sunPosInEyeSpace[1], sunPosInEyeSpace[2]);

        GLES20.glUniform3f(waterLightColourUniform, lightColour[0], lightColour[1], lightColour[2]);
        GLES20.glUniform3f(waterSkyColourUniform, skyColour[0], skyColour[1], skyColour[2]);
    }

    /*
    ------------------------------------------------------------------------------------------
    World geometry calculation
    ------------------------------------------------------------------------------------------
    */

    private SpriteBatch terrain;
    private int terrainCount;

    private void generateRendererData() {
        try {
            // We need to iterate over grid twice - once to get size for float arrays, and then
            // a second time when we want to generate renderer data

            // Todo: we could probably keep track of this when generating world data

            for (int gridX = 0; gridX < visibleGridWidth; gridX++) {
                for (int gridY = 0; gridY < visibleGridHeight; gridY++) {
                    int terrain = currentFloorData.terrain[gridX][gridY];

                    if (terrain > -1) {
                        terrainCount++;
                    }

                    for (Sprite object : currentFloorData.getObjects()[gridX][gridY]) {
                        if (object.wrapToCube) {
                            cubeCount++;
                        }

                        else {
                            spriteCount++;
                        }
                    }
                }
            }

            Log.v(LOG_TAG, "Counted " + cubeCount + " cubes and " + spriteCount + " sprites");

            // Init float arrays
            int cubePositionSize = cubeCount * CUBE_POSITION_SIZE;
            int cubeNormalSize = cubeCount * CUBE_NORMAL_SIZE;
            int cubeUvSize = cubeCount * CUBE_UV_SIZE;

            int terrainPositionSize = terrainCount * SPRITE_POSITION_SIZE;
            int terrainNormalSize = terrainCount * SPRITE_NORMAL_SIZE;
            int terrainUvSize = terrainCount * SPRITE_UV_SIZE;

            int spritePositionSize = spriteCount * SPRITE_POSITION_SIZE;
            int spriteNormalSize = spriteCount * SPRITE_NORMAL_SIZE;
            int spriteUvSize = spriteCount * SPRITE_UV_SIZE;

            final float[] cubePositionData = new float[cubePositionSize];
            final float[] cubeNormalData = new float[cubeNormalSize];
            final float[] cubeUvData = new float[cubeUvSize];

            final float[] spritePositionData = new float[spritePositionSize];
            final float[] spriteNormalData = new float[spriteNormalSize];
            final float[] spriteUvData = new float[spriteUvSize];

            final float[] terrainPositionData = new float[terrainPositionSize];
            final float[] terrainNormalData = new float[terrainNormalSize];
            final float[] terrainUvData = new float[terrainUvSize];

            int cubePositionDataOffset = 0;
            int cubeNormalDataOffset = 0;
            int cubeUvDataOffset = 0;

            int terrainPositionDataOffset = 0;
            int terrainNormalDataOffset = 0;
            int terrainUvDataOffset = 0;

            int spritePositionDataOffset = 0;
            int spriteNormalDataOffset = 0;
            int spriteUvDataOffset = 0;

            float x1, x2, y1, y2, z1, z2;

            for (int gridX = 0; gridX < visibleGridWidth; gridX++) {
                for (int gridY = 0; gridY < visibleGridHeight; gridY++) {
                    int terrain = currentFloorData.terrain[gridX][gridY];

                    // float elevation = (worldGridSize * (currentFloorData.heightMap[gridX][gridY] * 8));

                    float x = gridX * worldGridSize;
                    float y = gridY * worldGridSize;
                    float z;

                    // Find heights for terrain mesh.
                    float bottomLeft = worldGridSize * (1 + currentFloorData.heightMap[gridX][gridY] * 8);
                    float bottomRight = worldGridSize * (1 + currentFloorData.heightMap[gridX + 1][gridY] * 8);
                    float topLeft = worldGridSize * (1 + currentFloorData.heightMap[gridX][gridY + 1] * 8);
                    float topRight = worldGridSize * (1 + currentFloorData.heightMap[gridX + 1][gridY + 1] * 8);

                    float averageHeight = (currentFloorData.heightMap[gridX][gridY]
                            + currentFloorData.heightMap[gridX + 1][gridY]
                            + currentFloorData.heightMap[gridX][gridY + 1]
                            + currentFloorData.heightMap[gridX + 1][gridY + 1]) / 4;

                    float elevation = worldGridSize * (averageHeight * 8);

                    if (terrain > -1) {
                        x1 = x;
                        x2 = x + worldGridSize;

                        z1 = y;
                        z2 = y + worldGridSize;

                        float[] p1 = {x1, bottomLeft, z1};
                        float[] p2 = {x2, bottomRight, z1};
                        float[] p3 = {x1, topLeft, z2};
                        float[] p4 = {x2, topRight, z2};

                        float[] thisSpritePositionData = ShapeBuilder.generateSpriteData(p1, p2, p3, p4, p1.length);
                        System.arraycopy(thisSpritePositionData, 0, terrainPositionData,
                                terrainPositionDataOffset, thisSpritePositionData.length);

                        terrainPositionDataOffset += thisSpritePositionData.length;

                        // Note: use same winding order when calculating surface normals
                        // first vertice: p1, p3, p2
                        // second vertice: p3, p4, p2
                        float[] surfaceNormal = ShapeBuilder.calculateQuadSurfaceNormals(p1, p3, p2, p3, p4, p2);

                        System.arraycopy(surfaceNormal, 0, terrainNormalData,
                                terrainNormalDataOffset, surfaceNormal.length);

                        terrainNormalDataOffset += surfaceNormal.length;

                        float[] thisSpriteUvData = cachedSpriteUvs[terrain];
                        System.arraycopy(thisSpriteUvData, 0, terrainUvData,
                                terrainUvDataOffset, thisSpriteUvData.length);
                        terrainUvDataOffset += thisSpriteUvData.length;
                    }

                    for (Sprite object : currentFloorData.getObjects()[gridX][gridY]) {
                        z = (object.zLayer * worldGridSize) + elevation;

                        if (object.wrapToCube) {
                            // Add to cube batch
                            x1 = x;
                            x2 = x + worldGridSize;

                            y1 = z;
                            y2 = z + worldGridSize;

                            z1 = y;
                            z2 = y + worldGridSize;

                            float[] p1 = { x1, y2, z2 };
                            float[] p2 = { x2, y2, z2 };

                            float[] p3 = { x1, y1, z2 };
                            float[] p4 = { x2, y1, z2 };

                            float[] p5 = { x1, y2, z1 };
                            float[] p6 = { x2, y2, z1 };

                            float[] p7 = { x1, y1, z1 };
                            float[] p8 = { x2, y1, z1 };

                            float[] thisCubePositionData = ShapeBuilder.generateCubeData(p1, p2, p3, p4, p5, p6, p7, p8, p1.length);
                            System.arraycopy(thisCubePositionData, 0, cubePositionData, cubePositionDataOffset, thisCubePositionData.length);
                            cubePositionDataOffset += thisCubePositionData.length;

                            System.arraycopy(ShapeBuilder.CUBE_NORMAL_DATA, 0, cubeNormalData, cubeNormalDataOffset, ShapeBuilder.CUBE_NORMAL_DATA.length);
                            cubeNormalDataOffset += ShapeBuilder.CUBE_NORMAL_DATA.length;

                            float[] thisCubeUv = cachedCubeUvs[spriteIndexes.get(object.path)];
                            System.arraycopy(thisCubeUv, 0, cubeUvData, cubeUvDataOffset, thisCubeUv.length);
                            cubeUvDataOffset += thisCubeUv.length;
                        }

                        else {
                            // Add to sprite batch

                            x1 = x;
                            x2 = x + worldGridSize;

                            y1 = z;
                            y2 = z + worldGridSize;

                            // z1 = y;
                            z2 = y + (worldGridSize / 2f) + worldGridSize; // Move to centre of tile

                            float[] p1 = { x1, y2, z2 };
                            float[] p2 = { x2, y2, z2 };
                            float[] p3 = { x1, y1, z2 };
                            float[] p4 = { x2, y1, z2 };

                            float[] thisSpritePositionData = ShapeBuilder.generateSpriteData(p1, p2, p3, p4, p1.length);
                            System.arraycopy(thisSpritePositionData, 0, spritePositionData,
                                    spritePositionDataOffset, thisSpritePositionData.length);

                            spritePositionDataOffset += thisSpritePositionData.length;

                            System.arraycopy(ShapeBuilder.SPRITE_FRONT_NORMAL_DATA, 0, spriteNormalData,
                                    spriteNormalDataOffset, ShapeBuilder.SPRITE_FRONT_NORMAL_DATA.length);

                            spriteNormalDataOffset += ShapeBuilder.SPRITE_FRONT_NORMAL_DATA.length;

                            float[] thisSpriteUvData = cachedSpriteUvs[spriteIndexes.get(object.path)];
                            System.arraycopy(thisSpriteUvData, 0, spriteUvData,
                                    spriteUvDataOffset, thisSpriteUvData.length);
                            spriteUvDataOffset += thisSpriteUvData.length;
                        }
                    }
                }
            }

            Log.v(LOG_TAG, "Rendering " + cubeCount + " cubes and " + spriteCount + " sprites");

            // We need to run this code on GL thread
            gameSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    createSunVBO();
                    createMoonVBO();
                    createSkyBoxVBO();
                    createGroundVBO();
                    createWaterVBO();
                    createCubeVBO(cubePositionData, cubeNormalData, cubeUvData, cubeCount);
                    createTerrainVBO(terrainPositionData, terrainNormalData, terrainUvData, terrainCount);
                    createSpriteVBO(spritePositionData, spriteNormalData, spriteUvData, spriteCount);
                    // createDebugNormalVBO(spritePositionData, spriteNormalData, spriteCount);
                    createDistantTerrainVBO();
                    renderDataReady = true;
                }
            });

        } catch (OutOfMemoryError e) {
            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", e);
        } catch (Exception e2) {
            Log.d(LOG_TAG, "Some other yikes", e2);
        }
    }

    private float[] getSkyBoxPositions() {
        // It doesn't really matter what the exact dimensions are, as long as centre point is 0,0,0
        // and it is inside of view frustum (ie. not too small or too big)
        float skyboxHalfWidth = 5f;

        float x1 = -skyboxHalfWidth;
        float x2 = skyboxHalfWidth;

        float y1 = -skyboxHalfWidth;
        float y2 = skyboxHalfWidth;

        float z1 = -skyboxHalfWidth;
        float z2 = skyboxHalfWidth;

        float[] p1 = { x1, y2, z2 };
        float[] p2 = { x2, y2, z2 };
        float[] p3 = { x1, y1, z2 };
        float[] p4 = { x2, y1, z2 };
        float[] p5 = { x1, y2, z1 };
        float[] p6 = { x2, y2, z1 };
        float[] p7 = { x1, y1, z1 };
        float[] p8 = { x2, y1, z1 };

        return ShapeBuilder.generateCubeData(p1, p2, p3, p4, p5, p6, p7, p8, p1.length);
    }

    // Sun is rendered relatively close to camera, so it has to be small
    private final float SUN_RADIUS = 0.2f;
    private final float MOON_RADIUS = 0.2f;

    private float[] getSunVertices() {
        float x1 = sunPosInSkybox[0] - SUN_RADIUS;
        float x2 = sunPosInSkybox[0] + SUN_RADIUS;
        float y1 = sunPosInSkybox[1] - SUN_RADIUS;
        float y2 = sunPosInSkybox[1] + SUN_RADIUS;
        float z2 = sunPosInSkybox[2] + SUN_RADIUS;

        float[] p1 = { x1, y2, z2 };
        float[] p2 = { x2, y2, z2 };
        float[] p3 = { x1, y1, z2 };
        float[] p4 = { x2, y1, z2 };

        return ShapeBuilder.generateSpriteData(p1, p2, p3, p4, p1.length);
    }

    private float[] getMoonVertices() {
        float x1 = moonPosInSkybox[0] - MOON_RADIUS;
        float x2 = moonPosInSkybox[0] + MOON_RADIUS;
        float y1 = moonPosInSkybox[1] - MOON_RADIUS;
        float y2 = moonPosInSkybox[1] + MOON_RADIUS;
        float z2 = moonPosInSkybox[2] + MOON_RADIUS;

        float[] p1 = { x1, y2, z2 };
        float[] p2 = { x2, y2, z2 };
        float[] p3 = { x1, y1, z2 };
        float[] p4 = { x2, y1, z2 };

        return ShapeBuilder.generateSpriteData(p1, p2, p3, p4, p1.length);
    }

    private float[] getGroundQuadVertices() {
        float totalGridWidth = visibleGridWidth * worldGridSize;
        float totalGridHeight = visibleGridHeight * worldGridSize;

        float westBound = -totalGridWidth * 2;
        float eastBound = totalGridWidth * 3;
        float northBound = totalGridHeight * 3;
        float southBound = -totalGridHeight * 2;

        float x1, x2, y2, z1, z2;

        x1 = westBound;
        x2 = eastBound;
        y2 = worldGridSize;
        z1 = southBound;
        z2 = northBound;

        float[] p5 = { x1, y2, z1 };
        float[] p6 = { x2, y2, z1 };
        float[] p1 = { x1, y2, z2 };
        float[] p2 = { x2, y2, z2 };

        return ShapeBuilder.generateSpriteData(p5, p6, p1, p2, p1.length);
    }

    private float[] getWaterQuadVertices() {
        float totalGridWidth = visibleGridWidth * worldGridSize;
        float totalGridHeight = visibleGridHeight * worldGridSize;

        float westBound = -totalGridWidth * 2;
        float eastBound = totalGridWidth * 3;
        float northBound = totalGridHeight * 3;
        float southBound = -totalGridHeight * 2;

        float x1, x2, y2, z1, z2;

        x1 = westBound;
        x2 = eastBound;
        y2 = seaLevel;
        z1 = southBound;
        z2 = northBound;

        float[] p5 = { x1, y2, z1 };
        float[] p6 = { x2, y2, z1 };
        float[] p1 = { x1, y2, z2 };
        float[] p2 = { x2, y2, z2 };

        return ShapeBuilder.generateSpriteData(p5, p6, p1, p2, p1.length);
    }

    /*
    ------------------------------------------------------------------------------------------
    VBOs
    ------------------------------------------------------------------------------------------
    */


    private SpriteBatch moon;

    private void createSunVBO() {
        if (sun != null) {
            Log.d(LOG_TAG, "Releasing sun");
            sun.release();
            sun = null;
        }

        System.gc();

        try {
            // Use GL_DYNAMIC_DRAW as we will be updating the position data in VBO each frame
            sun = new SpriteBatch(getSunVertices(), ShapeBuilder.SPRITE_FRONT_NORMAL_DATA,
                    cachedSpriteUvs[spriteIndexes.get("sprites/sun.png")], 1, GLES20.GL_DYNAMIC_DRAW);

        } catch (OutOfMemoryError err) {
            if (sun != null) {
                sun.release();
                sun = null;
            }

            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    private void createMoonVBO() {
        if (moon != null) {
            Log.d(LOG_TAG, "Releasing sun");
            moon.release();
            moon = null;
        }

        System.gc();

        try {
            // Use GL_DYNAMIC_DRAW as we will be updating the position data in VBO each frame
            moon = new SpriteBatch(getMoonVertices(), ShapeBuilder.SPRITE_FRONT_NORMAL_DATA,
                    cachedSpriteUvs[spriteIndexes.get("sprites/moon.png")], 1, GLES20.GL_DYNAMIC_DRAW);

        } catch (OutOfMemoryError err) {
            if (moon != null) {
                moon.release();
                moon = null;
            }

            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    private void createSkyBoxVBO() {
        if (skyBox != null) {
            Log.d(LOG_TAG, "Releasing sky box");
            skyBox.release();
            skyBox = null;
        }

        // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
        System.gc();

        try {
            skyBox = new CubeBatch(getSkyBoxPositions(), ShapeBuilder.CUBE_NORMAL_DATA, cachedCubeUvs[spriteIndexes.get("sprites/sky.png")], 1);

        } catch (OutOfMemoryError err) {
            if (skyBox != null) {
                skyBox.release();
                skyBox = null;
            }

            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    private void createGroundVBO() {
        if (ground != null) {
            Log.d(LOG_TAG, "Releasing ground");
            ground.release();
            ground = null;
        }

        // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
        System.gc();

        try {
            ground = new SpriteBatch(getGroundQuadVertices(), ShapeBuilder.SPRITE_TOP_NORMAL_DATA,
                    cachedSpriteUvs[spriteIndexes.get(ExteriorTileset.SAND_DISTANT)], 1, GLES20.GL_STATIC_DRAW);

        } catch (OutOfMemoryError err) {
            if (ground != null) {
                ground.release();
                ground = null;
            }

            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    private void createWaterVBO() {
        if (water != null) {
            Log.d(LOG_TAG, "Releasing water");
            water.release();
            water = null;
        }

        // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
        System.gc();

        try {
            water = new SpriteBatch(getWaterQuadVertices(), ShapeBuilder.SPRITE_TOP_NORMAL_DATA,
                    cachedSpriteUvs[spriteIndexes.get(ExteriorTileset.WATER_1)], 1, GLES20.GL_STATIC_DRAW);

        } catch (OutOfMemoryError err) {
            if (water != null) {
                water.release();
                water = null;
            }

            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    private SpriteBatch distantTerrain;

    private void createDistantTerrainVBO() {
        long rngSeed = System.currentTimeMillis();
        RandomNumberGenerator rng = new RandomNumberGenerator(rngSeed);
        int numberOfIslands = 15;

        // Granularity defines the level of detail when rendering terrain mesh for distant terrain.
        int granularity = 4;

        // Actual width/height would be (length / granularity) * grid size
        final int minWidthFactor = 4;
        final int maxWidthFactor = 16;
        int width = granularity * rng.getRandomInt(minWidthFactor, maxWidthFactor);
        int height = granularity * rng.getRandomInt(minWidthFactor, maxWidthFactor);

        int count = numberOfIslands * ((width / granularity) * (height / granularity));

        Log.v(LOG_TAG, "Generating " + numberOfIslands + " pieces of distant terrain with " + count + " planes");

        int terrainPositionSize = count * SPRITE_POSITION_SIZE;
        int terrainNormalSize = count * SPRITE_NORMAL_SIZE;
        int terrainUvSize = count * SPRITE_UV_SIZE;

        float[] terrainPositionData = new float[terrainPositionSize];
        float[] terrainNormalData = new float[terrainNormalSize];
        float[] terrainUvData = new float[terrainUvSize];

        int terrainPositionDataOffset = 0;
        int terrainNormalDataOffset = 0;
        int terrainUvDataOffset = 0;

        for (int i = 0; i < numberOfIslands; i++) {
            // float baseElevation = 0.5f;
            // float coastHeight = 1.13f;
            // float dropOffFactor = 3.4f;

            // Todo: find acceptable ranges/ratios for these parameters
            float baseElevation = rng.getRandomFloat(0.3f, 1.0f);
            float coastHeight = rng.getRandomFloat(1.0f, 20.0f);
            float dropOffFactor = rng.getRandomFloat(1.0f, 20.0f);

            Chunk chunk = new Chunk(0, 0, width, height);
            long seed = System.currentTimeMillis();
            float[][] heightMap = generateIslandHeightMap(chunk, seed, baseElevation, coastHeight, dropOffFactor);

            // Place distant terrain at random points around centre of main island
            double r1 = rng.getRandomFloat(0.0f, 1.0f);
            double r2 = rng.getRandomFloat(0.0f, 1.0f);
            int minDist = 128;
            double radius = minDist * (r1 + 1);
            double angle = 2 * Math.PI * r2;
            int newX = 64 + (int) (radius * Math.cos(angle));
            int newY = 64 + (int) (radius * Math.sin(angle));

            float xOffset = worldGridSize * newX;
            float yOffset = worldGridSize * newY;

            for (int gridX = 0; gridX < width; gridX += granularity) {
                for (int gridY = 0; gridY < height; gridY += granularity) {
                    float x = xOffset + (gridX * worldGridSize);
                    float y = yOffset + (gridY * worldGridSize);

                    int xLookahead = gridX + granularity;
                    int yLookahead = gridY + granularity;

                    // Make sure that array index is in bounds
                    if (xLookahead > heightMap.length) {
                        xLookahead = width;
                    }

                    if (yLookahead > heightMap[0].length) {
                        yLookahead = height;
                    }

                    float bottomLeft = worldGridSize * (1 + heightMap[gridX][gridY] * 8);
                    float bottomRight = worldGridSize * (1 + heightMap[xLookahead][gridY] * 8);
                    float topLeft = worldGridSize * (1 + heightMap[gridX][yLookahead] * 8);
                    float topRight = worldGridSize * (1 + heightMap[xLookahead][yLookahead] * 8);

                    float x1 = x;
                    float x2 = x + (worldGridSize * granularity);

                    float z1 = y;
                    float z2 = y + (worldGridSize * granularity);

                    float[] p1 = {x1, bottomLeft, z1};
                    float[] p2 = {x2, bottomRight, z1};
                    float[] p3 = {x1, topLeft, z2};
                    float[] p4 = {x2, topRight, z2};

                    float[] thisSpritePositionData = ShapeBuilder.generateSpriteData(p1, p2, p3, p4, p1.length);
                    System.arraycopy(thisSpritePositionData, 0, terrainPositionData,
                            terrainPositionDataOffset, thisSpritePositionData.length);

                    terrainPositionDataOffset += thisSpritePositionData.length;

                    float[] surfaceNormal = ShapeBuilder.calculateQuadSurfaceNormals(p1, p3, p2, p3, p4, p2);

                    System.arraycopy(surfaceNormal, 0, terrainNormalData,
                            terrainNormalDataOffset, surfaceNormal.length);

                    terrainNormalDataOffset += surfaceNormal.length;

                    int texture = spriteIndexes.get(rng.getRandomItemFromStringArray(ExteriorTileset.GRASS));
                    float[] thisSpriteUvData = cachedSpriteUvs[texture];
                    System.arraycopy(thisSpriteUvData, 0, terrainUvData,
                            terrainUvDataOffset, thisSpriteUvData.length);
                    terrainUvDataOffset += thisSpriteUvData.length;
                }
            }
        }

        distantTerrain = new SpriteBatch(terrainPositionData, terrainNormalData, terrainUvData, count, GLES20.GL_STATIC_DRAW);
    }

    private float[][] generateIslandHeightMap(Chunk chunk, long seed, float baseElevation, float coastHeight, float dropOffFactor) {
        // For terrain mesh we need to add 1 to grid width/height
        int width = chunk.width + 1;
        int height = chunk.height + 1;

        float[][] heightMap = new float[width][height];

        SimplexNoiseGenerator generator = new SimplexNoiseGenerator(seed);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float nx = (float) x / width - 0.5f;
                float ny = (float) y / height - 0.5f;

                // Generate elevation using three octaves of noise
                float noise = (1 * generator.noise2D(1 * nx, 1 * ny))
                        + (0.5f * generator.noise2D(2 * nx, 2 * ny))
                        + (0.25f * generator.noise2D(4 * nx, 2 * ny));

                // Convert simplex noise to be within range of 0 to 1
                // (add 1 to convert from -1,1 to 0,2, then halve to get 0,1)
                // noise = (noise + 1) / 2;

                float distance;

                // Use Manhattan distance from centre of map to determine coastline
                // distance = 2 * (Math.max(Math.abs(nx), Math.abs(ny)));

                // Use Euclidian distance from centre of map to determine coastline
                distance = 2 * (float) (Math.sqrt(nx * nx + ny * ny));

                // float elevation = (baseElevation + noise) * (1 - coastHeight * (float) Math.pow(distance, dropOffFactor));
                float elevation = (baseElevation + noise) - coastHeight * (float) Math.pow(distance, dropOffFactor);

                // Add 1 to convert from range of [-1,1] to [0, 2], then halve to get [0, 1]
                elevation = (elevation + 1) / 2;

                // For terracing?
                // elevation = Math.round(elevation * 4) / 4;

                heightMap[x][y] = elevation;
            }
        }

        return heightMap;
    }

    private void createCubeVBO(float[] cubePositionData, float[] cubeNormalData, float[] cubeUvData, int count) {
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

    private void createTerrainVBO(float[] spritePositionData, float[] spriteNormalData, float[] spriteUvData, int count) {
        if (terrain != null) {
            Log.d(LOG_TAG, "Releasing terrain");
            terrain.release();
            terrain = null;
        }

        // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
        System.gc();

        try {
            terrain = new SpriteBatch(spritePositionData, spriteNormalData, spriteUvData, count, GLES20.GL_STATIC_DRAW);

        } catch (OutOfMemoryError err) {
            if (terrain != null) {
                terrain.release();
                terrain = null;
            }

            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    private void createSpriteVBO(float[] spritePositionData, float[] spriteNormalData, float[] spriteUvData, int count) {
        if (sprites != null) {
            Log.d(LOG_TAG, "Releasing sprites");
            sprites.release();
            sprites = null;
        }

        // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
        System.gc();

        try {
            sprites = new SpriteBatch(spritePositionData, spriteNormalData, spriteUvData, count, GLES20.GL_STATIC_DRAW);

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

    private LineBatch debugNormals;

    private void createDebugNormalVBO(float[] spritePositionData, float[] spriteNormalData, int count) {
        if (debugNormals != null) {
            Log.d(LOG_TAG, "Releasing sprites");
            debugNormals.release();
            debugNormals = null;
        }

        // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
        System.gc();

        try {
            // Array will be same size as sprite position data / 3
            float[] lineData = new float[spritePositionData.length / 3];

            int stride = 18;
            int lineDataIndex = 0;

            // To output the normals we will just draw a line from a corner of the sprite to the corner
            // position + the surface normal * world grid size (to make it more visible)
            for (int i = 0; i < spritePositionData.length; i += stride) {
                float v1 = spritePositionData[i];
                float v2 = spritePositionData[i + 1];
                float v3 = spritePositionData[i + 2];
                float n1 = spriteNormalData[i] * worldGridSize;
                float n2 = spriteNormalData[i + 1] * worldGridSize;
                float n3 = spriteNormalData[i + 2] * worldGridSize;

                lineData[lineDataIndex++] = v1;
                lineData[lineDataIndex++] = v2;
                lineData[lineDataIndex++] = v3;
                lineData[lineDataIndex++] = v1 + n1;
                lineData[lineDataIndex++] = v2 + n2;
                lineData[lineDataIndex++] = v3 + n3;
            }

            debugNormals = new LineBatch(lineData, count);

        } catch (OutOfMemoryError err) {
            if (debugNormals != null) {
                debugNormals.release();
                debugNormals = null;
            }

            // Not supposed to manually call this, but Dalvik sometimes needs some additional prodding to clean up the heap.
            System.gc();

            Log.d(LOG_TAG, "Yikes", err);
        }
    }

    /*
    ------------------------------------------------------------------------------------------
    FBOs
    ------------------------------------------------------------------------------------------
    */

    private void createDepthMapFBO() {
        int[] fboId = new int[1];
        int[] texId = new int[1];

        // create a framebuffer object
        GLES20.glGenFramebuffers(1, fboId, 0);
        depthMapFrameBufferId = fboId[0];

        Log.v(LOG_TAG, "Depth map framebuffer id: " + depthMapFrameBufferId);

        // Generate a texture to use to store depth map
        GLES20.glGenTextures(1, texId, 0);
        depthMapTextureId = texId[0];

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthMapTextureId);

        // Use a depth texture
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT, depthMapWidth, depthMapHeight,
                0, GLES20.GL_DEPTH_COMPONENT, GLES20.GL_UNSIGNED_INT, null);

        // GL_LINEAR does not make sense for depth texture
        // Todo: maybe it would with PCF
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Remove artifact on the edges of the depth map
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

    private void createWaterReflectionFBO(int width, int height) {
        reflectionTextureWidth = width;
        reflectionTextureHeight = height;

        int[] fbs = new int[1];
        int[] rbs = new int[1];
        int[] texs = new int[1];
        int offset = 0;

        // Create framebuffer to store colour information
        GLES20.glGenFramebuffers(fbs.length, fbs, offset);
        reflectionFrameBufferId = fbs[0];

        // Create renderbuffer to store depth information
        GLES20.glGenRenderbuffers(rbs.length, rbs, offset);
        int reflectionRenderBufferId = rbs[0];

        // Create texture object to output to
        GLES20.glGenTextures(texs.length, texs, offset);
        reflectionTextureId = texs[0];

        // Bind Frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, reflectionFrameBufferId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reflectionTextureId);

        // Set texture paramaters
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0,
                GLES20.GL_RGBA, reflectionTextureWidth, reflectionTextureHeight, 0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Bind render buffer and define buffer dimensions
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, reflectionRenderBufferId);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, reflectionTextureWidth, reflectionTextureHeight);

        // Attach texture FBO color attachment
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, reflectionTextureId, 0);

        // Attach render buffer to depth attachment
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, reflectionRenderBufferId);

        // Check FBO status
        int FBOstatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);

        if (FBOstatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(LOG_TAG, "GL_FRAMEBUFFER_COMPLETE failed when initialising reflection texture FBO");
            throw new RuntimeException("GL_FRAMEBUFFER_COMPLETE failed when initialising reflection texture FBO");
        }

        // Unbind everything
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    /*
    ------------------------------------------------------------------------------------------
    Misc. stuff
    ------------------------------------------------------------------------------------------
    */

    public static void checkGlErrors() {
        int debugInfo = GLES20.glGetError();

        if (debugInfo != GLES20.GL_NO_ERROR) {
            String msg = "OpenGL error: " + GLU.gluErrorString(debugInfo);
            Log.w("GameRenderer3D", msg);
        }
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

        visibleGridWidth = 128;
        visibleGridHeight = 128;
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

    public void setZoom(float zoomLevel) {
        this.zoomLevel = zoomLevel;
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
        singleThreadedExecutor.submit(new Runnable() {
            @Override
            public void run() {
                generateRendererData();
            }
        });
        renderState = GAME;
    }

    /*
    ------------------------------------------------------------------------------------------
    UI stuff
    ------------------------------------------------------------------------------------------
    */

    public void queueNarrationUpdate(ArrayList<TextObject> narrations) {
        uiController.queueNarrationUpdate(narrations);
    }

    public void queueNewStatus(Status status) {
        uiController.queueNewStatus(status);
    }

    private void addUiText() {
        // Component[] playerComponents = currentFloorData.getPlayer();
        // long entity = playerComponents[0].id;
        // ComponentManager componentManager = ComponentManager.getInstance();
        //  vitality = (Vitality) componentManager.getEntityComponent(entity, Vitality.class.getSimpleName());
        // String hp = "HP: " + vitality.hp;

        String hp;

        switch (renderMode) {
            case FULL_RENDER:
                hp = "Full";
                break;
            case REFLECTION_TEX:
                hp = "Reflect";
                break;
            case DEPTH_MAP:
                hp = "Depth";
                break;
            default:
                hp = "???";
                break;
        }

        hp = ((int) moonPosInSkybox[0]) + ", " + ((int) moonPosInSkybox[1]) + ", " + ((int) moonPosInSkybox[2]);
        // hp = ((int) sunPosInEyeSpace[0]) + ", " + ((int) sunPosInEyeSpace[1]) + ", " + ((int) sunPosInEyeSpace[2]);

        String worldState = timeManager.getTimeString() + " (" + weatherManager.getWeatherString() + ")";
        String cameraPos = ((int) cameraPosInModelSpace[0]) + ", " + ((int) cameraPosInModelSpace[1]) + ", " + ((int) cameraPosInModelSpace[2]);
        String fps = fpsCount + " fps";

        uiController.setUiText(hp, worldState, cameraPos, fps);
    }

    public void cycleRenderModes() {
        if (renderMode == MAX_RENDER_MODES) {
            renderMode = 0;
        }
        else {
            renderMode++;
        }
    }
}
