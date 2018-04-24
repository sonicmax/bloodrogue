package com.sonicmax.bloodrogue.renderer.geometry;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ShapeBuilder {
    public static final float[] CUBE_NORMAL_DATA = {
            // Front face
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,

            // Top face
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,

            // Back face
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,

            // Bottom face
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,

            // Left face
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,

            // Right face
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f
    };

    public static final float[] SPRITE_FRONT_NORMAL_DATA = {
            // Front face
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f
    };

    public static final float[] SPRITE_TOP_NORMAL_DATA = {
            // Front face
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f
    };

    public static float[] generateCubeData(float[] point1,
                                           float[] point2,
                                           float[] point3,
                                           float[] point4,
                                           float[] point5,
                                           float[] point6,
                                           float[] point7,
                                           float[] point8,
                                           int elementsPerPoint) {

        // Given a cube with the points defined as follows:
        // front left top, front right top, front left bottom, front right bottom,
        // back left top, back right top, back left bottom, back right bottom,
        // return an array of 6 sides, 2 triangles per side, 3 vertices per triangle, and 4 floats per vertex.
        final int FRONT = 0;
        final int TOP = 1;
        final int BACK = 2;
        final int BOTTOM = 3;
        final int LEFT = 4;
        final int RIGHT = 5;

        final int size = elementsPerPoint * 6 * 6;
        final float[] cubeData = new float[size];

        for (int face = 0; face < 6; face ++) {
            // Relative to the side, p1 = top left, p2 = top right, p3 = bottom left, p4 = bottom right
            final float[] p1, p2, p3, p4;

            // Select the points for this face
            switch (face) {
                case FRONT:
                    p1 = point1; p2 = point2; p3 = point3; p4 = point4;
                    break;
                case TOP:
                    p1 = point5; p2 = point6; p3 = point1; p4 = point2;
                    break;
                case BACK:
                    p1 = point6; p2 = point5; p3 = point8; p4 = point7;
                    break;
                case BOTTOM:
                    p1 = point8; p2 = point7; p3 = point4; p4 = point3;
                    break;
                case LEFT:
                    p1 = point5; p2 = point1; p3 = point7; p4 = point3;
                    break;
                case RIGHT:
                default:
                    p1 = point2; p2 = point6; p3 = point4; p4 = point8;
                    break;
            }

            // In OpenGL counter-clockwise winding is default. This means that when we look at a triangle,
            // if the points are counter-clockwise we are looking at the "front". If not we are looking at
            // the back. OpenGL has an optimization where all back-facing triangles are culled, since they
            // usually represent the backside of an object and aren't visible anyways.

            // Build the triangles
            //  1---3,6
            //  | / |
            // 2,4--5
            int offset = face * elementsPerPoint * 6;

            for (int i = 0; i < elementsPerPoint; i++) { cubeData[offset++] = p1[i]; }
            for (int i = 0; i < elementsPerPoint; i++) { cubeData[offset++] = p3[i]; }
            for (int i = 0; i < elementsPerPoint; i++) { cubeData[offset++] = p2[i]; }
            for (int i = 0; i < elementsPerPoint; i++) { cubeData[offset++] = p3[i]; }
            for (int i = 0; i < elementsPerPoint; i++) { cubeData[offset++] = p4[i]; }
            for (int i = 0; i < elementsPerPoint; i++) { cubeData[offset++] = p2[i]; }
        }

        return cubeData;
    }

    public static float[] generateSpriteData(float[] point1,
                                             float[] point2,
                                             float[] point3,
                                             float[] point4,
                                             int elementsPerPoint) {

        final int elementsPerSprite = 6;
        final float[] spriteData = new float[elementsPerPoint * elementsPerSprite];

        int offset = 0;

        System.arraycopy(point1, 0, spriteData, offset, point1.length);
        offset += point1.length;
        System.arraycopy(point3, 0, spriteData, offset, point3.length);
        offset += point3.length;
        System.arraycopy(point2, 0, spriteData, offset, point2.length);
        offset += point2.length;
        System.arraycopy(point3, 0, spriteData, offset, point3.length);
        offset += point3.length;
        System.arraycopy(point4, 0, spriteData, offset, point4.length);
        offset += point4.length;
        System.arraycopy(point2, 0, spriteData, offset, point2.length);

        return spriteData;
    }

    public static float[] calculateQuadSurfaceNormals(float[] triangleA1, float[] triangleA2, float[] triangleA3,
                                                float[] triangleB1, float[] triangleB2, float[] triangleB3) {
        float[] normalA;
        float[] normalB;

        // Use first vector in triangle as reference point for each vertice
        float v1x = triangleA2[0] - triangleA1[0];
        float v1y = triangleA2[1] - triangleA1[1];
        float v1z = triangleA2[2] - triangleA1[2];

        float v2x = triangleA3[0] - triangleA1[0];
        float v2y = triangleA3[1] - triangleA1[1];
        float v2z = triangleA3[2] - triangleA1[2];

        // Surface normal is cross product of v1 and v2
        float vx = v1y * v2z - v1z * v2y;
		float vy = v1z * v2x - v1x * v2z;
		float vz = v1x * v2y - v1y * v2x;

		normalA = new float[] {vx, vy, vz};

		// Repeat process for second vertice
		v1x = triangleB2[0] - triangleB1[0];
		v1y = triangleB2[1] - triangleB1[1];
		v1z = triangleB2[2] - triangleB1[2];

		v2x = triangleB3[0] - triangleB1[0];
		v2y = triangleB3[1] - triangleB1[1];
		v2z = triangleB3[2] - triangleB1[2];

		vx = v1y * v2z - v1z * v2y;
		vy = v1z * v2x - v1x * v2z;
		vz = v1x * v2y - v1y * v2x;

        normalB = new float[] {vx, vy, vz};

        // Normalise the surface normals
        float aLength = (float) Math.sqrt((normalA[0] * normalA[0]) + (normalA[1] * normalA[1]) + (normalA[2] * normalA[2]));
        float bLength = (float) Math.sqrt((normalB[0] * normalB[0]) + (normalB[1] * normalB[1]) + (normalB[2] * normalB[2]));

        normalA[0] /= aLength;
        normalA[1] /= aLength;
        normalA[2] /= aLength;

        normalB[0] /= bLength;
        normalB[1] /= bLength;
        normalB[2] /= bLength;

        // We have to copy this for each vertice for our vertex arrays
        return new float[] {
                normalA[0], normalA[1], normalA[2],
                normalA[0], normalA[1], normalA[2],
                normalA[0], normalA[1], normalA[2],

                normalB[0], normalB[1], normalB[2],
                normalB[0], normalB[1], normalB[2],
                normalB[0], normalB[1], normalB[2]
        };
    }

    public static SphereData generateSphereData(float cx, float cy, float cz, float r, int p) {
        float theta1, theta2, theta3;
        float ex, ey, ez;
        float px, py, pz;

        int verticesLength = 0;
        int normalsLength = 0;
        int texCoordsLength = 0;

        // Todo: this is dumb but i'm too lazy to figure out this loop rn
        for (int i = 0; i < p / 2; ++i) {
            for (int j = 0; j <= p; ++j) {
                verticesLength += 6;
                normalsLength += 6;
                texCoordsLength += 4;
            }
        }

        float[] vertices = new float[verticesLength];
        float[] normals = new float[normalsLength];
        float[] texCoords = new float[texCoordsLength];

        if (r < 0) {
            r = -r;
        }

        if (p < 0) {
            p = -p;
        }

        float M_PI = (float) Math.PI;
        float M_PI_2 = (float) Math.PI / 2f;

        int vOffset = 0;
        int nOffset = 0;
        int uvOffset = 0;

        for (int i = 0; i < p / 2; ++i) {
            float[] v = new float[p*6+6];
            float[] n = new float[p*6+6];
            float[] uv = new float[p*4+4];

            theta1 = i * (M_PI*2) / p - M_PI_2;
            theta2 = (i + 1) * (M_PI*2) / p - M_PI_2;

            for (int j = 0; j <= p; ++j) {
                theta3 = j * (M_PI*2) / p;

                ex = (float) (Math.cos(theta2) * Math.cos(theta3));
                ey = (float) Math.sin(theta2);
                ez = (float) (Math.cos(theta2) * Math.sin(theta3));

                px = cx + r * ex;
                py = cy + r * ey;
                pz = cz + r * ez;

                v[(6*j)+(0%6)] = px;
                v[(6*j)+(1%6)] = py;
                v[(6*j)+(2%6)] = pz;

                n[(6*j)+(0%6)] = ex;
                n[(6*j)+(1%6)] = ey;
                n[(6*j)+(2%6)] = ez;

                uv[(4*j)+(0%4)] = -(j/(float)p);
                uv[(4*j)+(1%4)] = 2*(i+1)/(float)p;

                ex = (float) (Math.cos(theta1) * Math.cos(theta3));
                ey = (float) Math.sin(theta1);
                ez = (float) (Math.cos(theta1) * Math.sin(theta3));

                px = cx + r * ex;
                py = cy + r * ey;
                pz = cz + r * ez;

                v[(6*j)+(3%6)] = px;
                v[(6*j)+(4%6)] = py;
                v[(6*j)+(5%6)] = pz;

                n[(6*j)+(3%6)] = ex;
                n[(6*j)+(4%6)] = ey;
                n[(6*j)+(5%6)] = ez;

                uv[(4*j)+(2%4)] = -(j/(float)p);
                uv[(4*j)+(3%4)] = 2*i/(float)p;
            }

            System.arraycopy(v, 0, vertices, vOffset, v.length);
            System.arraycopy(n, 0, normals, nOffset, n.length);
            System.arraycopy(uv, 0, texCoords, uvOffset, uv.length);

            vOffset += v.length;
            nOffset += n.length;
            uvOffset += uv.length;
        }

        Log.v("ShapeBuilder", "p value: " + p);
        Log.v("ShapeBuilder", "total v length: " + vOffset);
        Log.v("ShapeBuilder", "total uv length: " + uvOffset);

        return new SphereData(vertices, normals, texCoords);
    }

    public static float[] generateSphereVertices(float cx, float cy, float cz, float r, int p) {
        float theta1, theta2, theta3;
        float ex, ey, ez;
        float px, py, pz;

        int verticesLength = 0;

        // Todo: this is dumb but i'm too lazy to figure out this loop rn
        for (int i = 0; i < p / 2; ++i) {
            for (int j = 0; j <= p; ++j) {
                verticesLength += 6;
            }
        }

        float[] vertices = new float[verticesLength];

        if (r < 0) {
            r = -r;
        }

        if (p < 0) {
            p = -p;
        }

        float M_PI = (float) Math.PI;
        float M_PI_2 = (float) Math.PI / 2f;

        int vOffset = 0;

        for (int i = 0; i < p / 2; ++i) {
            float[] v = new float[p*6+6];

            theta1 = i * (M_PI*2) / p - M_PI_2;
            theta2 = (i + 1) * (M_PI*2) / p - M_PI_2;

            for (int j = 0; j <= p; ++j) {
                theta3 = j * (M_PI*2) / p;

                ex = (float) (Math.cos(theta2) * Math.cos(theta3));
                ey = (float) Math.sin(theta2);
                ez = (float) (Math.cos(theta2) * Math.sin(theta3));

                px = cx + r * ex;
                py = cy + r * ey;
                pz = cz + r * ez;

                v[(6*j)+(0%6)] = px;
                v[(6*j)+(1%6)] = py;
                v[(6*j)+(2%6)] = pz;

                ex = (float) (Math.cos(theta1) * Math.cos(theta3));
                ey = (float) Math.sin(theta1);
                ez = (float) (Math.cos(theta1) * Math.sin(theta3));

                px = cx + r * ex;
                py = cy + r * ey;
                pz = cz + r * ez;

                v[(6*j)+(3%6)] = px;
                v[(6*j)+(4%6)] = py;
                v[(6*j)+(5%6)] = pz;
            }

            System.arraycopy(v, 0, vertices, vOffset, v.length);
            vOffset += v.length;
        }

        return vertices;
    }
}
