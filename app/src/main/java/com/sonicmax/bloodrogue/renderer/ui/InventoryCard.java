package com.sonicmax.bloodrogue.renderer.ui;


import com.sonicmax.bloodrogue.engine.components.Sprite;

public class InventoryCard {
    public final Sprite sprite;
    public final String name;
    public final String desc;
    public final String attributes;
    public final String weight;

    public InventoryCard(Sprite sprite, String name, String desc, String attributes, int weight) {
        this.sprite = sprite;
        this.name = name;
        this.desc = desc;
        this.attributes = attributes;
        this.weight = weight + " lbs";
    }
}
