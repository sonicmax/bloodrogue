package com.sonicmax.bloodrogue.renderer;

import android.opengl.Matrix;
import android.util.Log;

/**
 * Class which allows us to define a camera view with various different modes of control.
 * Use getViewMatrix() and getProjectionMatrix() methods to get matrices for rendering.
 */

public class Camera {
    private final String LOG_TAG = this.getClass().getSimpleName();

    public static final int FREE_CAMERA = 0;
    public static final int THIRD_PERSON = 1;
    public static final int FIRST_PERSON = 2;

    private final float[] UP_VECTOR = {0f, 1f, 0f, 1f};
    private final float[] RIGHT_VECTOR = {1f, 0f, 0f, 1f};
    private final float[] FORWARD_VECTOR = {0f, 0f, -1f, 1f};

    private float eyeX;
    private float eyeY;
    private float eyeZ;

    private float lookX;
    private float lookY;
    private float lookZ;

    private float viewDistance;

    private float yaw;
    private float pitch;

    private float screenRatio;
    private float fov;
    private float near;
    private float far;

    private float[] viewMatrix;
    private float[] projectionMatrix;
    private float[] translationMatrix;

    private int cameraMode;

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

        // Default has to be free camera as we don't know what to focus on
        cameraMode = FREE_CAMERA;
    }

    public void setMode(int cameraMode) {
        this.cameraMode = cameraMode;
    }

    public void setPosition(float x, float y, float z) {
        eyeX = x;
        eyeY = y;
        eyeZ = z;
    }

    public void setPosition(float[] coords) {
        setPosition(coords[0], coords[1], coords[2]);
    }

    public void setProjection(int width, int height, float fov, float near, float far) {
        screenRatio = (float) width / height;
        this.fov = fov;
        this.near = near;
        this.far = far;

        // Set dirty flag so we know we need to calculate new matrix
        projectionDirty = true;
    }

    public float[] getPosition() {
        return new float[] {eyeX, eyeY, eyeZ, 1f};
    }

    ///////////////////////////////////////////////////////////////////////////
    // Methods for third person camera
    ///////////////////////////////////////////////////////////////////////////

    public void setLookAt(float x, float y, float z) {
        lookX = x;
        lookY = y;
        lookZ = z;
    }

    public void setLookAt(float[] lookAt) {
        setLookAt(lookAt[0], lookAt[1], lookAt[2]);
    }

    public void setViewDistance(float distance) {
        viewDistance = distance;
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

    public float[] getOrientation() {
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
        Matrix.multiplyMV(up, 0, invertedRotation, 0, UP_VECTOR, 0);
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
        Matrix.multiplyMV(forward, 0, invertedRotation, 0, FORWARD_VECTOR, 0);
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
        Matrix.multiplyMV(right, 0, invertedRotation, 0, RIGHT_VECTOR, 0);
        return right;
    }

    /**
     * Calculates view matrix using camera position and view distance.
     *
     * @return View matrix as float array
     */

    public float[] getViewMatrix() {
        Matrix.setIdentityM(viewMatrix, 0);

        switch (cameraMode) {
            case FREE_CAMERA:
            case FIRST_PERSON:
                Matrix.setIdentityM(translationMatrix, 0);

                Matrix.translateM(translationMatrix, 0, -eyeX, -eyeY, -eyeZ);
                Matrix.multiplyMM(viewMatrix, 0, getOrientation(), 0, translationMatrix, 0);
                break;

            case THIRD_PERSON:
                setThirdPersonCameraPos();

                Matrix.setLookAtM(viewMatrix, 0,
                        eyeX, eyeY, eyeZ,
                        lookX, lookY, lookZ,
                        UP_VECTOR[0], UP_VECTOR[1], UP_VECTOR[2]);

                break;

            default:
                Log.e(LOG_TAG, "Camera mode not in range");
        }

        return viewMatrix;
    }

    public float[] getViewMatrix(float[] position) {
        return getViewMatrix(position[0], position[1], position[2]);
    }

    /**
     * Returns view matrix for camera translated to given position, but maintaining same
     * orientation.
     */

    public float[] getViewMatrix(float eyeX, float eyeY, float eyeZ) {
        Matrix.setIdentityM(viewMatrix, 0);

        switch (cameraMode) {
            case FREE_CAMERA:
            case FIRST_PERSON:
                Matrix.setIdentityM(translationMatrix, 0);

                Matrix.translateM(translationMatrix, 0, -eyeX, -eyeY, -eyeZ);
                Matrix.multiplyMM(viewMatrix, 0, getOrientation(), 0, translationMatrix, 0);
                break;

            case THIRD_PERSON:
                float[] lookAt = getThirdPersonLookAt(eyeX, eyeY, eyeZ);

                Matrix.setLookAtM(viewMatrix, 0,
                        eyeX, eyeY, eyeZ,
                        lookAt[0], lookAt[1], lookAt[2],
                        UP_VECTOR[0], UP_VECTOR[1], UP_VECTOR[2]);

                break;
        }


        return viewMatrix;
    }

    /**
     * Sets position of camera based on pitch/yaw and view distance.
     * Note that this will overwrite whatever the previous values were.
     */

    private void setThirdPersonCameraPos() {
        float cameraHeight = viewDistance * (float) Math.sin(Math.toRadians(pitch));
        float cameraLength = viewDistance * (float) Math.cos(Math.toRadians(pitch));

        float offsetX = cameraLength * (float) Math.sin(Math.toRadians(yaw));
        float offsetZ = cameraLength * (float) Math.cos(Math.toRadians(yaw));

        eyeX = lookX - offsetX;
        eyeY = lookY + cameraHeight;
        eyeZ = lookZ - offsetZ;
    }

    /**
     * Returns lookAt position for given eye position, using camera's stored values for
     * pitch/yaw/view distance. Helpful when we want to translate the camera position but
     * maintain the same orientation (eg. for skybox rendering)
     *
     * @param eyeX
     * @param eyeY
     * @param eyeZ
     * @return lookAt values in float array {x, y, z}
     */

    private float[] getThirdPersonLookAt(float eyeX, float eyeY, float eyeZ) {
        float cameraHeight = viewDistance * (float) Math.sin(Math.toRadians(pitch));
        float cameraLength = viewDistance * (float) Math.cos(Math.toRadians(pitch));

        float offsetX = cameraLength * (float) Math.sin(Math.toRadians(yaw));
        float offsetZ = cameraLength * (float) Math.cos(Math.toRadians(yaw));

        // Switch around calculations from setThirdPersonCameraPos() to get lookX/lookY/lookZ for eye pos
        return new float[] {eyeX + offsetX, eyeY - cameraHeight, eyeZ + offsetZ};
    }

    /**
     * Calculates projection matrix (or returns pre-calculated matrix, if no changes were made)
     *
     * @return Proj matrix as float array
     */

    public float[] getProjectionMatrix() {
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
        switch (cameraMode) {
            case FREE_CAMERA:
                float[] forwardVector = getForwardVector();

                eyeX += forwardVector[0] * amount;
                eyeY += forwardVector[1] * amount;
                eyeZ += forwardVector[2] * amount;
                break;

            case THIRD_PERSON:
                viewDistance += amount;
                break;

            case FIRST_PERSON:
                // Position for 1st person camera shouldn't be modified directly like this
                break;
        }
    }

    public void pan(float x, float y) {
        eyeX += x;
        eyeY += y;
    }
}
