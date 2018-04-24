package com.sonicmax.bloodrogue.renderer.textures;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 *  Handles the loading and management of image assets. Allows us to load files from disk, get indexes
 *  for texture atlas, and bind textures to our rendering context
 */

public class TextureLoader {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final HashMap<String, Integer> spriteAtlasIndexes;
    private final HashMap<String, Integer> textureHandles;

    public TextureLoader() {
        spriteAtlasIndexes = new HashMap<>();
        textureHandles = new HashMap<>();
    }

    public void loadImagesFromDisk(AssetManager assetManager) {
        // Iterate over all paths in /assets/img and create SpriteLoader object with handle to loaded texture
        long startTime = System.nanoTime();

        final String IMG_PATH = "sprites/";
        final String SHEET_PATH = "sprite_sheets/";
        final String FONT_PATH = "fonts/";
        final String SKYBOX_PATH = "skyboxes/";
        final String GL_PATH = "gl/";

        try {
            String[] images = assetManager.list("sprites");
            int index = 0;

            // As AssetManager.list() returns alphabetically sorted list, and sprite sheet is also
            // ordered alphabetically, we can just increment the index on each filename to get the position
            // on sprite sheet. These will be used later when generating/retrieving UV coords

            for (String image : images) {
                spriteAtlasIndexes.put(IMG_PATH + image, index);
                index++;
            }

            // Now we can load our sprite sheet and fonts

            String[] sheets = assetManager.list("sprite_sheets");

            for (String sheet : sheets) {
                InputStream is = assetManager.open(SHEET_PATH + sheet);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE0);
                textureHandles.put(SHEET_PATH + sheet, textureHandle);
            }

            String[] fontPaths = assetManager.list("fonts");

            for (String path : fontPaths) {
                InputStream is = assetManager.open(FONT_PATH + path);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                int textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE1);
                textureHandles.put(FONT_PATH + path, textureHandle);
            }

            // Todo: figure out which of these is best!
            String oceanStrongDuDv = "ocean_strong_dudv.jpg";
            String oceanStrongNormal = "ocean_strong_normal.jpg";
            String seaDuDv = "sd_water_dudv.png";
            String seaNormal = "sd_water_normal.png";
            String waterDuDv;
            String waterNormal;

            // Load normal maps, specular maps, etc
            String waterDuDvMap = GL_PATH + seaDuDv;
            String waterNormalMap = GL_PATH + seaNormal;
            String skyGradientWithSun = GL_PATH + "skygradient.png";
            String skyGradient = GL_PATH + "skygradient2.png";
            String whiteTest = GL_PATH + "white.png";

            InputStream is = assetManager.open(waterDuDvMap);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
            int textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE5,
                    GLES20.GL_REPEAT, GLES20.GL_REPEAT,
                    GLES20.GL_NEAREST, GLES20.GL_NEAREST);

            textureHandles.put(waterDuDvMap, textureHandle);

            is = assetManager.open(waterNormalMap);
            opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeStream(is, null, opts);
            textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE6,
                    GLES20.GL_REPEAT, GLES20.GL_REPEAT,
                    GLES20.GL_NEAREST, GLES20.GL_NEAREST);

            textureHandles.put(waterNormalMap, textureHandle);

            is = assetManager.open(skyGradientWithSun);
            opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeStream(is, null, opts);
            textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE7,
                    GLES20.GL_REPEAT, GLES20.GL_REPEAT,
                    GLES20.GL_NEAREST, GLES20.GL_NEAREST);

            textureHandles.put(skyGradientWithSun, textureHandle);


            is = assetManager.open(skyGradient);
            opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeStream(is, null, opts);
            textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE8,
                    GLES20.GL_REPEAT, GLES20.GL_REPEAT,
                    GLES20.GL_NEAREST, GLES20.GL_NEAREST);

            textureHandles.put(skyGradient, textureHandle);

            is = assetManager.open(whiteTest);
            opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeStream(is, null, opts);
            textureHandle = loadTexture(bitmap, GLES20.GL_TEXTURE9,
                    GLES20.GL_REPEAT, GLES20.GL_REPEAT,
                    GLES20.GL_NEAREST, GLES20.GL_NEAREST);

            textureHandles.put(whiteTest, textureHandle);

            // createSkyBoxTexture(assetManager);

            long stopTime = System.nanoTime();
            Log.v(LOG_TAG, "Loaded images in " + TimeUnit.MILLISECONDS.convert(stopTime - startTime, TimeUnit.NANOSECONDS) + " ms");

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private int cubeMapHandle;

    private void createSkyBoxTexture(AssetManager assetManager) throws IOException {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        // Make sure GL_TEXTURE constants and file paths match up correctly
        int[] cubeMapGlTargets = {GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z,
                GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y,
                GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X};

        String[] cubeMapPaths = {"skyboxes/negz.jpg", "skyboxes/posz.jpg",
                "skyboxes/negy.jpg", "skyboxes/posy.jpg",
                "skyboxes/negx.jpg", "skyboxes/posx.jpg"};

        if (textureHandle[0] != 0) {
            cubeMapHandle = textureHandle[0];

            // Bind to the texture in OpenGL
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, cubeMapHandle);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            for (int i = 0; i < cubeMapGlTargets.length; i++) {
                InputStream is = assetManager.open(cubeMapPaths[i]);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);

                GLUtils.texImage2D(cubeMapGlTargets[i], 0, bitmap, 0);
            }
        }
        else {
            throw new RuntimeException("Couldn't generate cube map texture");
        }
    }

    public HashMap<String, Integer> getSpriteIndexes() {
        return this.spriteAtlasIndexes;
    }

    public HashMap<String, Integer> getTextureHandles() {
        return this.textureHandles;
    }

    /**
     * Load texture with default values
     *
     * @param bitmap
     * @param textureUnit
     * @return
     */

    private int loadTexture(Bitmap bitmap, int textureUnit) {
        return loadTexture(bitmap, textureUnit, GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_NEAREST, GLES20.GL_NEAREST);
    }

    private int loadTexture(Bitmap bitmap, int textureUnit, int wrapS, int wrapT, int minFilter, int magFilter) {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            // Bind to the texture in OpenGL
            GLES20.glActiveTexture(textureUnit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrapS);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrapT);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, minFilter);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, magFilter);

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
