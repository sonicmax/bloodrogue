package com.sonicmax.bloodrogue.engine.environment;

import com.sonicmax.bloodrogue.renderer.colours.SkyColours;

/**
 * Keeps track of time and provides helper methods to determine things like light level/FOV radius/etc
 */

public class TimeManager {
    public static final int HOURS_IN_DAY = 24;
    public static final int MINUTES_IN_HOUR = 60;
    public static final int DAYS_IN_MONTH = 30;
    public static final int MONTHS_IN_YEAR = 12;
    public static final int DAYS_IN_YEAR = 360;
    public static final int SECONDS_IN_TICK = 15;

    // Note: all of these are 0-indexed.
    private int currentYear;
    private int currentMonth;
    private int currentDay;
    private int currentHour;
    private int currentMinute;

    private int currentTick;
    private int tickRate;

    private long totalTicks;

    public TimeManager() {
        currentYear = 1999;
        currentMonth = 0;
        currentDay = 0;
        currentHour = 0;
        currentMinute = 0;
        currentTick = 0;
        totalTicks = 0;
        tickRate = 3;
    }

    public void setTime(int hours, int minutes) {
        currentHour = hours;
        currentMinute = minutes;
        currentTick = 0;
    }

    public void setTickRate(int rate) {
        tickRate = rate;
    }

    public int getHour() {
        return currentHour;
    }

    public int getMinute() {
        return currentMinute;
    }

    public int getTotalTimeInMinutes() {
        return (currentDay * HOURS_IN_DAY * MINUTES_IN_HOUR) + getTimeOfDayInMinutes();
    }

    public int getTimeOfDayInMinutes() {
        return (currentHour * MINUTES_IN_HOUR) + currentMinute;
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public long getTotalTicks() {
        return totalTicks;
        // return 2160;
        // return 1920;
    }

    public long getDebugTicks(int hours, int minutes) {
        final int ticksPerMin = 4;

        return (minutes + (hours * MINUTES_IN_HOUR)) * ticksPerMin;
    }

    /**
     * Instead of subdividing minutes into seconds, we split each minute into a number of "ticks"
     * (default is 3 ticks per minute). Typically this method would be called once for every
     * player turn.
     */

    public void tick() {
        currentTick++;
        totalTicks++;

        if (currentTick > tickRate) {
            currentTick = 0;
            advanceTime();
        }
    }


    /**
     * Advances time by one minute each method call.
     */

    public void advanceTime() {
        currentMinute++;

        // Roll back from 59 to 0
        if (currentMinute >= MINUTES_IN_HOUR) {
            currentMinute = 0;
            currentHour++;
        }

        if (currentHour >= HOURS_IN_DAY) {
            currentHour = 0;
            currentDay++;
        }

        if (currentDay >= DAYS_IN_MONTH) {
            currentDay = 0;
            currentMonth++;
        }

        if (currentMonth >= MONTHS_IN_YEAR) {
            currentMonth = 0;
            currentYear++;
        }

        // currentMinute = 0;
        // currentHour = 8;
    }

    public double getAmbientLighting() {
        double hourLighting = getLightingForHour(currentHour);
        double transition = getMinuteTransitionForHour(currentHour);

        return hourLighting + (currentMinute * transition);
    }

    public int getMonth() {
        return currentMonth;
    }

    public int getYear() {
        return currentYear;
    }

    public int getDay() {
        return currentDay;
    }

    public float[] getSunlightColour() {
        float[] tint = getSunlightForHour(currentHour);
        float[] gradiant = getSunlightTransitionForHour(currentHour);
        float[] tintWithGradiant = new float[3];

        tintWithGradiant[0] = tint[0] + (gradiant[0] * currentMinute);
        tintWithGradiant[1] = tint[1] + (gradiant[1] * currentMinute);
        tintWithGradiant[2] = tint[2] + (gradiant[2] * currentMinute);

        return tintWithGradiant;
    }

    public float[] getSkyColour() {
        float[] tint = getSkyColourForHour(currentHour);
        float[] gradiant = getSkyColourTransitionForHour(currentHour);
        float[] tintWithGradiant = new float[3];

        tintWithGradiant[0] = tint[0] + (gradiant[0] * currentMinute);
        tintWithGradiant[1] = tint[1] + (gradiant[1] * currentMinute);
        tintWithGradiant[2] = tint[2] + (gradiant[2] * currentMinute);

        return tintWithGradiant;
    }

    public String getTimeString() {
        String hour = Integer.toString(currentHour);

        if (hour.length() == 1) {
            hour = "0" + hour;
        }

        String minute = Integer.toString(currentMinute);

        if (minute.length() == 1) {
            minute = "0" + minute;
        }

        return hour + ":" + minute;
    }

    private float[] calculateColourTransition(float[] startColour, float[] endColour, int step) {
        float[] transition = new float[3];

        transition[0] = (endColour[0] - startColour[0]) / step;
        transition[1] = (endColour[1] - startColour[1]) / step;
        transition[2] = (endColour[2] - startColour[2]) / step;

        return transition;
    }

    private float[] getSkyColourForHour(int hour) {
        switch (hour) {
            // Night
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return SkyColours.NIGHT_SKY;

            // Sunrise
            case 6:
            case 7:
                return SkyColours.MIDNIGHT_BLUE;

            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                return SkyColours.DAY_SKY;

            // Sunset
            case 18:
            case 19:
                return SkyColours.MIDNIGHT_BLUE;

            // Night
            case 20:
            case 21:
            case 22:
            case 23:
                return SkyColours.NIGHT_SKY;

            // Hopefully this won't ever happen
            // TodO: return weird colour like green or something so we know something went wrong
            default:
                return SkyColours.DEBUG_GREEN;
        }
    }

    private float[] getSkyColourTransitionForHour(int hour) {
        final float[] NO_CHANGE = {0f, 0f, 0f};

        switch (hour) {
            // Night - no change
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                return NO_CHANGE;

            // Transition to SUNSET_ORANGE
            case 5:
                return calculateColourTransition(SkyColours.NIGHT_SKY, SkyColours.MIDNIGHT_BLUE, MINUTES_IN_HOUR);

            // Transition to SUNSET
            case 6:
                return NO_CHANGE;

            // No change
            case 7:
                return calculateColourTransition(SkyColours.MIDNIGHT_BLUE, SkyColours.DAY_SKY, MINUTES_IN_HOUR);

            // Day - no change
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
                return NO_CHANGE;

            // Transition to SUNSET_ORANGE
            case 17:
                return calculateColourTransition(SkyColours.DAY_SKY, SkyColours.MIDNIGHT_BLUE, MINUTES_IN_HOUR);

            // No change
            case 18:
                return NO_CHANGE;

            // Transition to NIGHT
            case 19:
                return calculateColourTransition(SkyColours.MIDNIGHT_BLUE, SkyColours.NIGHT_SKY, MINUTES_IN_HOUR);

            // Night - no change
            case 20:
            case 21:
            case 22:
            case 23:
                return NO_CHANGE;

            default:
                return NO_CHANGE;
        }
    }

    private float[] getSunlightForHour(int hour) {
        switch (hour) {
            // Night
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return SkyColours.NIGHT;

            // Sunrise
            case 6:
                return SkyColours.SUNSET_ORANGE;
            case 7:
            case 8:
                return SkyColours.SUNSET;

            // Daytime
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
                return SkyColours.DAY;

            case 17:
                return SkyColours.SUNSET;

            // Sunset
            case 18:
            case 19:
                return SkyColours.SUNSET_ORANGE;

            // Night
            case 20:
            case 21:
            case 22:
            case 23:
                return SkyColours.NIGHT;

            // Hopefully this won't ever happen
            default:
                return SkyColours.DEBUG_GREEN;
        }
    }

    private float[] getSunlightTransitionForHour(int hour) {
        final float[] NO_CHANGE = {0f, 0f, 0f};

        switch (hour) {
            // Night - no change
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                return NO_CHANGE;

            // Transition to SUNSET_ORANGE
            case 5:
                return calculateColourTransition(SkyColours.NIGHT, SkyColours.SUNSET_ORANGE, MINUTES_IN_HOUR);

            // Transition to SUNSET
            case 6:
                return calculateColourTransition(SkyColours.SUNSET_ORANGE, SkyColours.SUNSET, MINUTES_IN_HOUR);

            // No change
            case 7:
                return NO_CHANGE;

            // Transition to DAY
            case 8:
                return calculateColourTransition(SkyColours.SUNSET, SkyColours.DAY, MINUTES_IN_HOUR);

            // Day - no change
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                return NO_CHANGE;

            // Transition to SUNSET
            case 16:
                return calculateColourTransition(SkyColours.DAY, SkyColours.SUNSET, MINUTES_IN_HOUR);

            // Transition to SUNSET_ORANGE
            case 17:
                return calculateColourTransition(SkyColours.SUNSET, SkyColours.SUNSET_ORANGE, MINUTES_IN_HOUR);

            // No change
            case 18:
                return NO_CHANGE;

            // Transition to NIGHT
            case 19:
                return calculateColourTransition(SkyColours.SUNSET_ORANGE, SkyColours.NIGHT, MINUTES_IN_HOUR);

            // Night - no change
            case 20:
            case 21:
            case 22:
            case 23:
                return NO_CHANGE;

            default:
                return NO_CHANGE;
        }
    }

    private double getMinuteTransitionForHour(int hour) {
        switch (hour) {
            // Night - no change
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 0;

            // Sunrise - light increases by .2 each hour
            case 6:
            case 7:
            case 8:
                return 0.00333; // .2 / minutes per hour (rounded)

            // Daytime - no change
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                return 0;

            // Sunset - light decreases by .2 each hour
            case 18:
            case 19:
            case 20:
                return -0.00333;

            // Night - no change
            case 21:
            case 22:
            case 23:
                return 0;

            // Hopefully this won't ever happen
            default:
                return 0;
        }
    }

    private double getLightingForHour(int hour) {
        switch (hour) {
            // Night
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 0.1;

            // Sunrise
            case 6:
                return 0.3;
            case 7:
                return 0.5;
            case 8:
                return 0.7;

            // Daytime
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                return 0.9;

            // Sunset
            case 18:
                return 0.7;
            case 19:
                return 0.5;
            case 20:
                return 0.3;

            // Night
            case 21:
            case 22:
            case 23:
                return 0.1;

            // Hopefully this won't ever happen
            default:
                return 0.1;
        }
    }
}
