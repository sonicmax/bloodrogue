package com.sonicmax.bloodrogue.engine.objects;

import java.util.ArrayList;

public class AnimationFactory {
    public static GameObject getHitAnimation(int x, int y) {
        Animation hit = new Animation(x, y);
        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/blood_splat_a_1.png");
        frames.add("sprites/blood_splat_a_2.png");
        frames.add("sprites/blood_splat_a_3.png");
        frames.add("sprites/blood_splat_a_4.png");
        hit.setFrames(frames);

        return hit;
    }

    public static GameObject getDeathAnimation(int x, int y) {
        Animation death = new Animation(x, y);
        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/blood_splat_b_1.png");
        frames.add("sprites/blood_splat_b_2.png");
        frames.add("sprites/blood_splat_b_3.png");
        frames.add("sprites/blood_splat_b_4.png");
        frames.add("sprites/blood_splat_b_5.png");
        frames.add("sprites/blood_splat_b_6.png");
        death.setFrames(frames);

        return death;
    }

    public static GameObject getPlayerDeathAnimation(int x, int y) {
        Animation death = new Animation(x, y);

        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/death_anim_1.png");
        frames.add("sprites/death_anim_2.png");
        frames.add("sprites/death_anim_3.png");
        frames.add("sprites/death_anim_4.png");
        death.setFrames(frames);

        return death;
    }

    public static GameObject getChestItemRevealAnimation(int x, int y) {
        Animation anim = new Animation(x, y);

        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/chest_open_1.png");
        frames.add("sprites/chest_open_2.png");
        frames.add("sprites/chest_open_3.png");
        frames.add("sprites/chest_open_4.png");
        frames.add("sprites/chest_open_5.png");
        frames.add("sprites/chest_open_6.png");
        frames.add("sprites/chest_open_7.png");
        anim.setFrames(frames);

        return anim;
    }

    public static GameObject getPingAnimation(int x, int y) {
        Animation anim = new Animation(x, y);

        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/ping_1.png");
        frames.add("sprites/ping_2.png");
        frames.add("sprites/ping_3.png");
        frames.add("sprites/ping_4.png");
        frames.add("sprites/ping_5.png");
        frames.add("sprites/ping_6.png");
        anim.setFrames(frames);

        return anim;
    }
}
