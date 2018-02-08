package com.sonicmax.bloodrogue.utils.maths;

public class GeometryHelper {
    public static double getDistance(Vector a, Vector b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2));
    }

    public static int getAngle(Vector target, Vector origin) {
        int angle = (int) Math.toDegrees(Math.atan2(target.y - origin.y, target.x - origin.x));

        if (angle < 0){
            angle += 360;
        }

        return angle;
    }
}
