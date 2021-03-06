package com.sonicmax.bloodrogue.renderer.text;

public class TextObject {
    public String text;
    public int row;
    public float x;
    public float y;
    public float[] color;

    public float offsetX = 0f;
    public float offsetY = 0f;
    public float alphaModifier = 0f;
    public float scale = 1f;

    public TextObject(String txt, float x, float y) {
        this.text = txt;
        this.x = x;
        this.y = y;
        this.color = new float[] {1f, 1f, 1f, 1.0f};
        this.row = -1; // Signifies that we want exact position, not row
    }

    public TextObject(String txt, float x, float y, float[] color) {
        this.text = txt;
        this.x = x;
        this.y = y;
        this.color = color;
        this.row = -1;
    }

    public TextObject(String txt, int row) {
        this.text = txt;
        this.x = 0f;
        this.y = 0f;
        this.row = row;
        this.color = new float[] {1f, 1f, 1f, 1.0f};
    }

    public TextObject(String txt, int row, float[] color) {
        this.text = txt;
        this.x = 0f;
        this.y = 0f;
        this.row = row;
        this.color = color;
    }

    public TextObject(TextObject clone) {
        this.text = clone.text;
        this.x = clone.x;
        this.y = clone.y;
        this.row = clone.row;
        this.color = clone.color;
    }
}
