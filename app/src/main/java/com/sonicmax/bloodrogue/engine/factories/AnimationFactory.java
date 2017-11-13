package com.sonicmax.bloodrogue.engine.factories;

import com.sonicmax.bloodrogue.engine.components.Blood;
import com.sonicmax.bloodrogue.renderer.ui.Animation;
import com.sonicmax.bloodrogue.engine.objects.GameObject;

import java.util.ArrayList;

public class AnimationFactory {

    public static Animation getHitAnimation(Blood blood, int x, int y) {
        Animation hit = new Animation(x, y);
        ArrayList<String> frames = new ArrayList<>();

        // Todo: this sucks. It would be better to separate this from the code
        switch (blood.type) {
            case Blood.GREEN:
                frames.add("sprites/blood_splat_a_green_1.png");
                frames.add("sprites/blood_splat_a_green_2.png");
                frames.add("sprites/blood_splat_a_green_3.png");
                frames.add("sprites/blood_splat_a_green_4.png");
                break;

            case Blood.ECTOPLASM:
                frames.add("sprites/ecto_hit_1.png");
                frames.add("sprites/ecto_hit_2.png");
                frames.add("sprites/ecto_hit_3.png");
                frames.add("sprites/ecto_hit_4.png");
                frames.add("sprites/ecto_hit_5.png");
                // hit.setGasOrLiquid(true);
                break;

            case Blood.RED:
            default:
                frames.add("sprites/blood_splat_a_1.png");
                frames.add("sprites/blood_splat_a_2.png");
                frames.add("sprites/blood_splat_a_3.png");
                frames.add("sprites/blood_splat_a_4.png");
                break;
        }

        hit.setFrames(frames);

        return hit;
    }

    public static Animation getDeathAnimation(Blood blood, int x, int y) {
        Animation death = new Animation(x, y);
        ArrayList<String> frames = new ArrayList<>();
        // Todo: this also sucks
        switch (blood.type) {
            case Blood.GREEN:
                frames.add("sprites/blood_splat_b_green_1.png");
                frames.add("sprites/blood_splat_b_green_2.png");
                frames.add("sprites/blood_splat_b_green_3.png");
                frames.add("sprites/blood_splat_b_green_4.png");
                frames.add("sprites/blood_splat_b_green_5.png");
                frames.add("sprites/blood_splat_b_green_6.png");
                break;

            case Blood.ECTOPLASM:
                frames.add("sprites/ecto_hit_1.png");
                frames.add("sprites/ecto_hit_2.png");
                frames.add("sprites/ecto_hit_3.png");
                frames.add("sprites/ecto_hit_4.png");
                frames.add("sprites/ecto_hit_5.png");
               //  death.setGasOrLiquid(true);
                break;

            case Blood.RED:
            default:
                frames.add("sprites/blood_splat_b_1.png");
                frames.add("sprites/blood_splat_b_2.png");
                frames.add("sprites/blood_splat_b_3.png");
                frames.add("sprites/blood_splat_b_4.png");
                frames.add("sprites/blood_splat_b_5.png");
                frames.add("sprites/blood_splat_b_6.png");
                break;
        }

        death.setFrames(frames);

        return death;
    }

    public static Animation getPlayerDeathAnimation(int x, int y) {
        Animation death = new Animation(x, y);

        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/death_anim_1.png");
        frames.add("sprites/death_anim_2.png");
        frames.add("sprites/death_anim_3.png");
        frames.add("sprites/death_anim_4.png");
        death.setFrames(frames);

        return death;
    }

    public static Animation getChestItemRevealAnimation(int x, int y) {
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

    public static Animation getPingAnimation(int x, int y) {
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

    public static Animation getInventoryOpenAnimation(int x, int y) {
        Animation anim = new Animation(x, y);

        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/inventory_anim_1.png");
        frames.add("sprites/inventory_anim_2.png");
        frames.add("sprites/inventory_anim_3.png");
        frames.add("sprites/inventory_anim_4.png");
        frames.add("sprites/inventory_anim_5.png");
        frames.add("sprites/inventory_anim_6.png");

        anim.setFrames(frames);

        return anim;
    }

    public static Animation getInventoryCloseAnimation(int x, int y) {
        Animation anim = new Animation(x, y);

        ArrayList<String> frames = new ArrayList<>();
        frames.add("sprites/inventory_anim_6.png");
        frames.add("sprites/inventory_anim_5.png");
        frames.add("sprites/inventory_anim_4.png");
        frames.add("sprites/inventory_anim_3.png");
        frames.add("sprites/inventory_anim_2.png");
        frames.add("sprites/inventory_anim_1.png");
        anim.setFrames(frames);

        return anim;
    }
}
