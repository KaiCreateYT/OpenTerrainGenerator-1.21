package com.pg85.otg.client.preview;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class OrbitCamera {

    private float theta;      // azimuth (radians)
    private float phi;        // elevation (radians)
    private float distance;   // radius from target
    private final Vector3f target = new Vector3f();

    private static final float PHI_MIN = 0.1f;
    private static final float PHI_MAX = (float) (Math.PI - 0.1);
    private static final float DIST_MIN = 1f;
    private static final float DIST_MAX = 5000f;
    private static final float FOV = (float) Math.toRadians(45);

    public OrbitCamera() {
        theta = (float) (Math.PI / 4);
        phi = (float) (Math.PI / 3);
        distance = 100f;
    }

    public void rotate(float dTheta, float dPhi) {
        theta += dTheta;
        phi = Math.clamp(phi + dPhi, PHI_MIN, PHI_MAX);
    }

    public void zoom(float delta) {
        distance = Math.clamp(distance * (1f - delta * 0.001f), DIST_MIN, DIST_MAX);
    }

    public void fitTo(Vector3f center, float radius) {
        target.set(center);
        distance = Math.clamp(radius / (float) Math.sin(FOV / 2), DIST_MIN, DIST_MAX);
    }

    public Vector3f getEyePosition() {
        float x = target.x + distance * (float)(Math.sin(phi) * Math.cos(theta));
        float y = target.y + distance * (float)(Math.cos(phi));
        float z = target.z + distance * (float)(Math.sin(phi) * Math.sin(theta));
        return new Vector3f(x, y, z);
    }

    public Matrix4f getViewMatrix() {
        Vector3f eye = getEyePosition();
        return new Matrix4f().lookAt(eye, target, new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjectionMatrix(float aspectRatio) {
        return new Matrix4f().perspective(FOV, aspectRatio, 0.1f, 10000f);
    }

    public Matrix4f getViewProjectionMatrix(float aspectRatio) {
        return getProjectionMatrix(aspectRatio).mul(getViewMatrix());
    }

    public void setTheta(float theta) { this.theta = theta; }

    public void setPhi(float phi) { this.phi = Math.max(PHI_MIN, Math.min(PHI_MAX, phi)); }
}
