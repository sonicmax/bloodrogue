package com.sonicmax.bloodrogue.renderer.geometry;

public class SphereData {
    public final float[] vertices;
    public final float[] normals;
    public final float[] texCoords;

    public SphereData(float[] vertices, float[] normals, float[] texCoords) {
        this.vertices = vertices;
        this.normals = normals;
        this.texCoords = texCoords;
    }
}
