package com.sonicmax.bloodrogue.renderer.geometry;

import android.opengl.GLES20;
import android.opengl.GLU;
import android.util.Log;

import com.sonicmax.bloodrogue.utils.maths.Vector;

import java.util.Arrays;

/**
 * Contains methods to cast rays and test for intersections with world geometry.
 */

public class RayCaster {
    public static final String LOG_TAG = "RayCaster";

    /**
     * Intersect a ray with a triangle. Returns true if ray intersects triangle, and false if it does not
     * (also returns false if triangle is degenerate)
     *
     * @param rayP0 Start of ray
     * @param rayP1 End of ray
     * @param triangleV0 First triangle vertex
     * @param triangleV1 Second triangle vertex
     * @param triangleV2 Third triangle vertex
     * @param intersection Optional - used to pass intersection coords back to calling context.
     * @return Result of intersection test
     */

    public static boolean intersectRayAndTriangle(float[] rayP0, float[] rayP1, float[] triangleV0, float[] triangleV1, float[] triangleV2, float[] intersection) {
        if (intersection == null) {
            intersection = new float[3];
        }

        // Get triangle edge vectors and plane normal
        float[] u = Vector.subtract(triangleV1, triangleV0);
        float[] v = Vector.subtract(triangleV2, triangleV0);
        float[] n = Vector.cross(u, v);

        // Ignore degenerate triangles. We should log this because something went horribly wrong at some point
        if (Arrays.equals(n, new float[] {0.0f, 0.0f, 0.0f})) {
            Log.w(LOG_TAG, "Degenerate triangle in geometry: [" + Arrays.toString(triangleV0) + "]"
                    + " [" + Arrays.toString(triangleV1) + "]"
                    + " [" + Arrays.toString(triangleV2) + "]" );
            return false;
        }

        float[] rayDirection = Vector.subtract(rayP1, rayP0);
        float[] w0 = Vector.subtract(rayP0, triangleV0);
        float a = -Vector.dot(n, w0);
        float b = Vector.dot(n, rayDirection);

        final float SMALL_NUM =  0.00000001f;

        if (Math.abs(b) < SMALL_NUM) {     // ray is parallel to triangle plane
            if (a == 0) {                // ray lies in triangle plane
                return true;
            } else {
                return false;             // ray disjoint from plane
            }
        }

        // get intersect point of ray with triangle plane
        float r = a / b;
        if (r < 0.0f){                   // ray goes away from triangle
            return false;                  // => no intersect
        }
        // for a segment, also test if (r > 1.0) => no intersect

        float[] tempI = Vector.add(rayP0,  Vector.scale(rayDirection, r));           // intersect point of ray and plane
        intersection[0] = tempI[0];
        intersection[1] = tempI[1];
        intersection[2] = tempI[2];

        // is I inside T?
        float uu =  Vector.dot(u,u);
        float uv =  Vector.dot(u,v);
        float vv =  Vector.dot(v,v);
        float[] w = Vector.subtract(intersection, triangleV0);
        float wu =  Vector.dot(w,u);
        float wv = Vector.dot(w,v);
        float D = (uv * uv) - (uu * vv);

        // get and test parametric coords
        float s = ((uv * wv) - (vv * wu)) / D;
        if (s < 0.0f || s > 1.0f)        // I is outside T
            return false;
        float t = (uv * wu - uu * wv) / D;
        if (t < 0.0f || (s + t) > 1.0f)  // I is outside T
            return false;

        return true;                      // I is in T
    }

    public static boolean intersectRayWithPlane(float[] R1, float[] R2, float[] S1, float[] S2, float[] S3, float[] intersection) {
        // Compute the plane equation of the square
        float[] ds21 = Vector.subtract(S2, S1);
        float[] ds31 = Vector.subtract(S3, S1);
        float[] n = Vector.cross(ds21, ds31);

        // Intersect ray with plane
        float[] dR = Vector.subtract(R1, R2);
        float ndotdR = Vector.dot(n, dR);

        float tolerance = 1e-6f;
        if (Math.abs(ndotdR) < tolerance) {
            return false;
        }

        float t = -Vector.dot(n, Vector.subtract(R1, S1)) / ndotdR;
        float[] M = Vector.add(R1, Vector.scale(dR, t));

        intersection[0] = M[0];
        intersection[1] = M[1];
        intersection[2] = M[2];

        // Project intersection point on a local 2D basis in the plane of the square. This will give the 2D coordinates (u, v) of the point on the plane
        float[] dMS1 = Vector.subtract(M, S1);
        float u = Vector.dot(dMS1, ds21);
        float v = Vector.dot(dMS1, ds31);

        // If 2D coordinates (u, v) are within the square, then ray intersects with plane
        return (u >= 0.0f && u <= Vector.dot(ds21, ds21)
                && v >= 0.0f && v <= Vector.dot(ds31, ds31));
    }

    public static float[] castRayFromTouchCoords(float x, float y, int screenWidth, int screenHeight, float[] mvMatrix, float[] projectionMatrix) {
        float[] p1 = new float[3];
        float[] p0 = new float[3];
        float[] temp = new float[4];

        int[] viewport = {0, 0, screenWidth, screenHeight};

        // Android uses top-left as origin, so we need to invert this for OpenGL
        y = screenHeight - y;

        // Unproject xy coords twice - once at min depth, once at max depth.
        // The ray we will test will travel between these two points
        int result = GLU.gluUnProject(x, y, 0.0f,
                mvMatrix, 0, projectionMatrix, 0,
                viewport, 0, temp, 0);

        if (result == GLES20.GL_TRUE) {
            p0[0] = temp[0] / temp[3];
            p0[1] = temp[1] / temp[3];
            p0[2] = temp[2] / temp[3];
        }

        result = GLU.gluUnProject(x, y, 1.0f,
                mvMatrix, 0, projectionMatrix, 0,
                viewport, 0, temp, 0);

        if (result == GLES20.GL_TRUE) {
            p1[0] = temp[0] / temp[3];
            p1[1] = temp[1] / temp[3];
            p1[2] = temp[2] / temp[3];
        }

        return new float[] {p0[0], p0[1], p0[2], p1[0], p1[1], p1[2]};
    }
}
