package com.sonicmax.bloodrogue.generator.enemies;

import com.sonicmax.bloodrogue.renderer.Animation;
import com.sonicmax.bloodrogue.tilesets.AnimationTileset;

import java.util.ArrayList;

public class EnemyAnimator {
    public static Animation getIdleAnimation(String type) {
        Animation animation = new Animation(0, 0);
        animation.setRepeating(true);
        animation.setFrames(getIdleAnimationFrames(type));

        return animation;
    }

    public static ArrayList<String> getIdleAnimationFrames(String type) {
        ArrayList<String> frames = new ArrayList<>();

        switch (type) {
            case EnemyBlueprintKeys.GOAT:
                frames.add(AnimationTileset.GOAT_IDLE_1);
                frames.add(AnimationTileset.GOAT_IDLE_2);
                frames.add(AnimationTileset.GOAT_IDLE_3);
                break;
            case EnemyBlueprintKeys.TOAD:
                frames.add(AnimationTileset.TOAD_IDLE_1);
                frames.add(AnimationTileset.TOAD_IDLE_2);
                frames.add(AnimationTileset.TOAD_IDLE_3);
                frames.add(AnimationTileset.TOAD_IDLE_4);
                frames.add(AnimationTileset.TOAD_IDLE_5);
                break;
        }

        return frames;
    }
}
