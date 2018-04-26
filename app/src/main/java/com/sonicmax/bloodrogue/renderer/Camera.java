package com.sonicmax.bloodrogue.renderer;

import android.opengl.Matrix;

/**
 * Class which allows us to define a camera view based on position vector and yaw/pitch angles,
 * instead of using Matrix.setLookAtM() method. Provides methods to modify position, change rotation
 * and move along up/right/forward vectors. Use getView() and getProjection() methods
 * to get matrices for rendering.
 */

public class Camera {
    private final float[] upVector = {0f, 1f, 0f, 1f};
    private final float[] rightVector = {1f, 0f, 0f, 1f};
    private final float[] forwardVector = {0f, 0f, -1f, 1f};

    private float eyeX;
    private float eyeY;
    private float eyeZ;

    private float yaw;
    private float pitch;

    private float screenRatio;
    private float fov;
    private float near;
    private float far;

    private float[] viewMatrix;
    private float[] projectionMatrix;
    private float[] translationMatrix;

    private boolean projectionDirty;

    public Camera() {
        eyeX = 0f;
        eyeY = 0f;
        eyeZ = 0f;

        yaw = 0f;
        pitch = 0f;

        viewMatrix = new float[16];
        projectionMatrix = new float[16];
        translationMatrix = new float[16];

        projectionDirty = false;
    }

    public void setPosition(float x, float y, float z) {
        eyeX = x;
        eyeY = y;
        eyeZ = z;
    }

    public void setProjection(int width, int height, float fov, float near, float far) {
        screenRatio = (float) width / height;
        this.fov = fov;
        this.near = near;
        this.far = far;

        // Set dirty flag so we know we need to calculate new matrix
        projectionDirty = true;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float[] getPosition() {
        return new float[] {eyeX, eyeY, eyeZ, 1f};
    }

    /**
     * Modifies camera pitch/yaw using given parameters.
     *
     * @param deltaX Amount to increase yaw by
     * @param deltaY Amount to increase pitch by
     */

    public void addRotation(float deltaX, float deltaY) {
        yaw += deltaX;
        pitch += deltaY;

        yaw = yaw % 360f;
        if (yaw < 0f) yaw += 360.0f;

        if (pitch < -90f) pitch = -90f;
        if (pitch > 90f) pitch = 90f;
    }

    /**
     * Calculates rotation matrix for camera's current orientation.
     *
     * @return Rotation matrix as float array
     */

    private float[] getOrientation() {
        float[] rotationMatrix = new float[16];
        Matrix.setIdentityM(rotationMatrix, 0);
        Matrix.rotateM(rotationMatrix, 0, pitch, 1f, 0f, 0f);
        Matrix.rotateM(rotationMatrix, 0, yaw, 0f, 1f, 0f);
        return rotationMatrix;
    }

    /**
     * Gets up vector for camera's current orientation.
     *
     * @return Up vector as float array
     */

    public float[] getUpVector() {
        float[] invertedRotation = new float[16];
        float[] up = new float[4];
        Matrix.invertM(invertedRotation, 0, getOrientation(), 0);
        Matrix.multiplyMV(up, 0, invertedRotation, 0, upVector, 0);
        return up;
    }

    /**
     * Gets forward vector for camera's current orientation.
     *
     * @return Forward vector as float array
     */

    public float[] getForwardVector() {
        float[] invertedRotation = new float[16];
        float[] forward = new float[4];
        Matrix.invertM(invertedRotation, 0, getOrientation(), 0);
        Matrix.multiplyMV(forward, 0, invertedRotation, 0, forwardVector, 0);
        return forward;
    }

    /**
     * Gets right vector for camera's current orientation.
     *
     * @return Right vector as float array
     */

    public float[] getRightVector() {
        float[] invertedRotation = new float[16];
        float[] right = new float[4];
        Matrix.invertM(invertedRotation, 0, getOrientation(), 0);
        Matrix.multiplyMV(right, 0, invertedRotation, 0, rightVector, 0);
        return right;
    }

    /**
     * Calculates view matrix using camera position and orientation.
     *
     * @return View matrix as float array
     */

    public float[] getView() {
        Matrix.setIdentityM(viewMatrix, 0);

        Matrix.setIdentityM(translationMatrix, 0);
        // Remember to negate position when translating
        Matrix.translateM(translationMatrix, 0, -eyeX, -eyeY, -eyeZ);

        Matrix.multiplyMM(viewMatrix, 0, getOrientation(), 0, translationMatrix, 0);

        return viewMatrix;
    }

    /**
     * Calculates view matrix using given position and camera orientation.
     *
     * @param position Camera position
     *
     * @return View matrix as float array
     */

    public float[] getView(float[] position) {
        Matrix.setIdentityM(viewMatrix, 0);

        Matrix.setIdentityM(translationMatrix, 0);
        Matrix.translateM(translationMatrix, 0, -position[0], -position[1], -position[2]);

        float[] rotationMatrix = getOrientation();

        Matrix.multiplyMM(viewMatrix, 0, rotationMatrix, 0, translationMatrix, 0);

        return viewMatrix;
    }

    /**
     * Calculates projection matrix using internal parameters (set with setProjection() method)
     *
     * @return Projection matrix as float array
     */

    public float[] getProjection() {
        if (projectionDirty) {
            Matrix.setIdentityM(projectionMatrix, 0);
            Matrix.perspectiveM(projectionMatrix, 0, fov, screenRatio, near, far);
            projectionDirty = false;
        }

        return projectionMatrix;
    }

    /**
     * Calculates forward vector and translates camera position in that direction.
     *
     * @param amount Amount to scale forward vector by
     */

    public void moveForwards(float amount) {
        float[] forwardVector = getForwardVector();

        eyeX += forwardVector[0] * amount;
        eyeY += forwardVector[1] * amount;
        eyeZ += forwardVector[2] * amount;
    }
}
