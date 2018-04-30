package com.sonicmax.bloodrogue.utils.maths;

public class GeometryHelper {
    public static double getDistance(Vector2D a, Vector2D b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2));
    }

    public static int getAngle(Vector2D target, Vector2D origin) {
        int angle = (int) Math.toDegrees(Math.atan2(target.y - origin.y, target.x - origin.x));

        if (angle < 0){
            angle += 360;
        }

        return angle;
    }
}
