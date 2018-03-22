package com.sonicmax.bloodrogue.renderer.textures;

public class UvHelper {
    public static final float SPRITE_BOX_WIDTH = 0.03125f; // 1f / 32 sprites per row
    public static final float SPRITE_BOX_HEIGHT = 0.03125f; // 1f / 32 sprites per column
    public static final int SPRITES_PER_ROW = 32;

    public static final int CUBE_UV_SIZE = 72;
    public static final int SPRITE_UV_SIZE = 12;

    public static float[][] precalculateSpriteUvs(int numberOfIndexes) {
        float[][] cachedSpriteUvs = new float[numberOfIndexes][SPRITE_UV_SIZE];

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

        return cachedSpriteUvs;
    }

    public static float[][] precalculateCubeUvs(int numberOfIndexes) {
        float[][] cachedCubeUvs = new float[numberOfIndexes][CUBE_UV_SIZE];

        for (int i = 0; i < numberOfIndexes; i++) {
            int row = i / SPRITES_PER_ROW;
            int col = i % SPRITES_PER_ROW;

            float u = col * SPRITE_BOX_WIDTH;
            float u2 = u + SPRITE_BOX_WIDTH;
            float v = row * SPRITE_BOX_HEIGHT;
            float v2 = v + SPRITE_BOX_HEIGHT;

            // TODO: this is just the same set of UV coords repeated 6 times. we probably want to align these per side

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

        return cachedCubeUvs;
    }
}
