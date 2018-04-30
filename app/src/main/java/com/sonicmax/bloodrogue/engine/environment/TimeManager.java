package com.sonicmax.bloodrogue.engine.environment;

/**
 * Class which manages the progression of date/time in game. Instead of subdividing minutes
 * into seconds, we split each minute into a series of "ticks" (default is 4 ticks per minute).
 */

public class TimeManager {
    public static final int HOURS_IN_DAY = 24;
    public static final int MINUTES_IN_HOUR = 60;
    public static final int DAYS_IN_MONTH = 30;
    public static final int MONTHS_IN_YEAR = 12;
    public static final int DAYS_IN_YEAR = 360;
    public static final int SECONDS_PER_TICK = 15;
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int TICKS_PER_MINUTE = (SECONDS_PER_MINUTE / SECONDS_PER_TICK);

    // Note: all of these are 0-indexed. For month/day we add 1 to internal value for external use
    private int currentYear;
    private int currentMonth;
    private int currentDay;
    private int currentHour;
    private int currentMinute;

    private int currentTick;
    private int tickRate;
    private int tickDelta;
    private int tickSpeed;

    private long totalTicks;


    public TimeManager() {
        // Not really any significance to the year, but it made lunar calculations simpler to debug
        currentYear = 2000;
        currentMonth = 0;
        currentDay = 0;
        currentHour = 0;
        currentMinute = 0;
        currentTick = 0;
        totalTicks = 0;
        tickDelta = 0;
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
        long ticks = totalTicks;
        // ticks = 2160;
        // ticks = 1920;
        ticks = 1440;

        return ticks;
    }

    public void setTickSpeed(int speed) {
        tickSpeed = speed;
    }

    public long getDebugTicks(int hours, int minutes) {
        final int ticksPerMin = 4;

        return (minutes + (hours * MINUTES_IN_HOUR)) * ticksPerMin;
    }

    public void tick() {
        tickSpeed = 1;
        currentTick += tickSpeed;
        totalTicks += tickSpeed;
        tickDelta += tickSpeed;

        if (currentTick > tickRate) {
            currentTick = 0;
            advanceTime(tickDelta);
            tickDelta = 0;
        }
    }


    /**
     * Advances time depending on how many ticks have passed.
     */

    public void advanceTime(int tickDelta) {
        currentMinute += tickDelta / TICKS_PER_MINUTE;

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

        currentMinute = 0;
        currentHour = 6;
    }

    public int getMonth() {
        return currentMonth + 1;
    }

    public int getYear() {
        return currentYear;
    }

    public int getDay() {
        return currentDay + 1;
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
}
