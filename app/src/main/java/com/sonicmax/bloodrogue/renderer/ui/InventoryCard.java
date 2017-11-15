package com.sonicmax.bloodrogue.renderer.ui;


public class InventoryCard {
    public final String name;
    public final String desc;
    public final String attributes;
    public final String weight;

    public InventoryCard(String name, String desc, String attributes, int weight) {
        this.name = name;
        this.desc = desc;
        this.attributes = attributes;
        this.weight = weight + " lbs";
    }
}
