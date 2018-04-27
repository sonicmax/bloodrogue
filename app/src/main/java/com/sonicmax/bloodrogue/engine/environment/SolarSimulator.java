package com.sonicmax.bloodrogue.engine.environment;

import android.opengl.Matrix;

/**
 * Contains methods to simulate various elements of the Solar System for in-game use
 * (eg. calculating position of moon, current moon phase, etc).
 */

public class SolarSimulator {
    private final float[] SUN_START_POS = new float[] {0f, -5f, 0f, 0f};

    private float[] moonPosInSpace;
    private float[] moonPosInGame;
    private float[] moonPosInSkybox;

    private float[] sunPosInSkybox;

    private float[] moonRotationMatrix;
    private float[] compassRotationMatrix;

    public SolarSimulator() {
        moonPosInSpace = new float[4];
        moonPosInGame = new float[4];
        moonPosInSkybox = new float[4];

        sunPosInSkybox = new float[4];

        compassRotationMatrix = new float[16];
        Matrix.setIdentityM(compassRotationMatrix, 0);
        Matrix.rotateM(compassRotationMatrix, 0, 90f, 0f, 1f, 0f);

        moonRotationMatrix = new float[16];
        Matrix.setIdentityM(moonRotationMatrix, 0);
    }

    /**
     * Calculates geocentric coordinates for sun given current time. Not accurate at all. ¯\_(ツ)_/¯
     */

    public float[] getCurrentSunPosition(TimeManager timeManager) {
        final long ticksPerDay = 5760L;

        // One full rotation = 360 degrees / 5760 ticks (1440 minutes, 4 ticks per minute)
        long rotationCounter = timeManager.getTotalTicks() % ticksPerDay;
        float lightRotationDegree = (360.0f / 5760.0f) * ((int) rotationCounter);

        // Rotate sun around x axis of skybox origin
        float[] rotationMatrix = new float[16];
        Matrix.setIdentityM(rotationMatrix, 0);
        Matrix.translateM(rotationMatrix, 0, 0f, -SUN_START_POS[1], 0f);
        Matrix.rotateM(rotationMatrix, 0, lightRotationDegree, 1f, 0f, 0f);

        // Find new position of sun in skybox.
        Matrix.multiplyMV(sunPosInSkybox, 0, rotationMatrix, 0, SUN_START_POS, 0);

        return sunPosInSkybox;
    }

    /**
     * Calculates geocentric coordinates for moon given current date and time, and rotates
     * to account for in-game compass directions and rotation of the Earth.
     *
     * Algorithm from http://www.stjarnhimlen.se/comp/ppcomp.html
     */

    public float[] getCurrentMoonPosition(TimeManager timeManager) {
        int year = timeManager.getYear();
        int month = timeManager.getMonth();
        int date = timeManager.getDay();

        // Get minutes/hours as decimal of day. Note: Day 0.0 occurs at 2000 Jan 0.0 UT
        int dayComponent = (367 * year) - 7 * (year + (month + 9) / TimeManager.MONTHS_IN_YEAR) / 4 + (275 * month / 9) + date - 730530;
        float decimalHours = (timeManager.getTimeOfDayInMinutes() / 60f) / 24f;
        int seconds = timeManager.getCurrentTick() * TimeManager.SECONDS_PER_TICK;
        decimalHours += (seconds / 86400f);

        float ut = dayComponent + decimalHours;

        // Orbital elements of the moon:
        double ascendingNodeLongitude = (125.1228 - 0.0529538083 * ut);
        double perihelionArgument = (318.0634 + 0.1643573223 * ut);
        double meanAnomaly = (115.3654 + 13.0649929509 * ut);
        double eclipticInclination = 5.1454;
        double semiMajorAxis = 5.0; // Distance from earth to moon. Scaled down to fit skybox
        double eccentricity = 0.054900;

        ascendingNodeLongitude = ascendingNodeLongitude % 360.0;
        perihelionArgument = perihelionArgument % 360.0;
        meanAnomaly = meanAnomaly % 360.0;

        if (ascendingNodeLongitude < 0.0) ascendingNodeLongitude += 360.0;
        if (perihelionArgument < 0.0) perihelionArgument += 360.0;
        if (meanAnomaly < 0.0) meanAnomaly += 360.0;

        ascendingNodeLongitude = Math.toRadians(ascendingNodeLongitude);
        perihelionArgument = Math.toRadians(perihelionArgument);
        meanAnomaly = Math.toRadians(meanAnomaly);
        eccentricity = Math.toRadians(eccentricity);
        eclipticInclination = Math.toRadians(eclipticInclination);

        double eccentricAnomaly = meanAnomaly + eccentricity * Math.sin(meanAnomaly) * (1.0 + eccentricity * Math.cos(meanAnomaly));

        // It's suggested to perform a second calculation to improve accuracy when eccentricity is > 0.05
        // however in testing the difference between initial + improved calculation was typically less than 0.0001

        // Compute distance and true anomaly:
        double xv = semiMajorAxis * (Math.cos(eccentricAnomaly) - eccentricity);
        double yv = semiMajorAxis * (Math.sqrt(1.0 - eccentricity * eccentricity) * Math.sin(eccentricAnomaly));
        double trueAnomaly = Math.atan2(yv, xv);
        double distance = Math.sqrt(xv*xv + yv*yv);

        // Compute position in 3-dimensional space. These are already geocentric
        double xh = distance * (Math.cos(ascendingNodeLongitude) * Math.cos(trueAnomaly+perihelionArgument)
                - Math.sin(ascendingNodeLongitude) * Math.sin(trueAnomaly+perihelionArgument) * Math.cos(eclipticInclination));

        double yh = distance * (Math.sin(ascendingNodeLongitude) * Math.cos(trueAnomaly+perihelionArgument)
                + Math.cos(ascendingNodeLongitude) * Math.sin(trueAnomaly+perihelionArgument) * Math.cos(eclipticInclination));

        double zh = distance * (Math.sin(trueAnomaly+perihelionArgument) * Math.sin(eclipticInclination));

        moonPosInSpace[0] = (float) xh;
        moonPosInSpace[1] = (float) yh;
        moonPosInSpace[2] = (float) zh;
        moonPosInSpace[3] = 1f;

        // Rotate the moon 90 degrees to match compass orientation of Earth in-game.
        Matrix.multiplyMV(moonPosInGame, 0, compassRotationMatrix, 0, moonPosInSpace, 0);

        // Now rotate the moon around origin of skybox to account for rotation of Earth.
        final long ticksPerDay = 5760L;
        long rotationCounter = timeManager.getTotalTicks() % ticksPerDay;
        float lightRotationDegree = (360.0f / 5760.0f) * ((int) rotationCounter);

        Matrix.setIdentityM(moonRotationMatrix, 0);
        Matrix.rotateM(moonRotationMatrix, 0, lightRotationDegree, 1f, 0f, 0f);
        Matrix.multiplyMV(moonPosInSkybox, 0, moonRotationMatrix, 0, moonPosInGame, 0);

        return moonPosInSkybox;
    }

    /**
     * Calculates the moon phase (0-7), accurate to 1 segment. 0 = > new moon. 4 => full moon.
     * Check against MoonPhases constants to find corresponding phase for int. We can use this
     * to determine hours that moon is visible + the texture to use.
     *
     * Code taken from http://www.voidware.com/moon_phase.htm
     *
     * @param timeManager
     * @return
     */

    public int getCurrentMoonPhase(TimeManager timeManager) {
        int y = timeManager.getYear();
        int m = timeManager.getMonth();
        int d = timeManager.getDay();

        if (m < 3) {
            y--;
            m += 12;
        }

        ++m;
        double c = 365.25 * y;
        double e = 30.6 * m;
        double jd = c + e + d - 694039.09;  /* jd is total days elapsed */
        jd /= 29.53;                        /* divide by the moon cycle (29.53 days) */
        int b = (int) jd;		            /* int(jd) -> b, take integer part of jd */
        jd -= b;                            /* subtract integer part to leave fractional part of original jd */
        b = (int) (jd * 8 + 0.5);	        /* scale fraction from 0-8 and round by adding 0.5 */
        b = b & 7;		                    /* 0 and 8 are the same so turn 8 into 0 */

        return b;
    }
}
