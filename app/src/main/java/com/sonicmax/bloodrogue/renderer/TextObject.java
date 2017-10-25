package com.sonicmax.bloodrogue.renderer;

public class TextObject {
    public String text;
    public int row;
    public float x;
    public float y;
    public float[] color;

    public TextObject(String txt, float x, float y) {
        this.text = txt;
        this.x = x;
        this.y = y;
        this.color = new float[] {1f, 1f, 1f, 1.0f};
        this.row = -1; // Signifies that we want exact position, not row
    }

    public TextObject(String txt, int row) {
        this.text = txt;
        this.x = 0f;
        this.y = 0f;
        this.row = row;
        this.color = new float[] {1f, 1f, 1f, 1.0f};
    }
}
