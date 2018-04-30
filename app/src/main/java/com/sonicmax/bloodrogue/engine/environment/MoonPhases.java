package com.sonicmax.bloodrogue.engine.environment;

public class MoonPhases {
    public static final int NEW_MOON = 0;
    public static final int WAXING_CRESCENT = 1;
    public static final int FIRST_QUARTER = 2;
    public static final int WAXING_GIBBOUS = 3;
    public static final int FULL_MOON = 4;
    public static final int WANING_GIBBOUS = 5;
    public static final int LAST_QUARTER = 6;
    public static final int WANING_CRESCENT = 7;

    public static String toString(int phase) {
        if (phase < 0 || phase > 7) {
            throw new IllegalArgumentException("Error: phase < 0 || phase > 7 (phase = " + phase + ")");
        }

        switch (phase) {
            case NEW_MOON:
                return "New moon";
            case WAXING_CRESCENT:
                return "Waxing crescent";
            case FIRST_QUARTER:
                return "First quarter";
            case WAXING_GIBBOUS:
                return "Waxing gibbous";
            case FULL_MOON:
                return "Full moon";
            case WANING_GIBBOUS:
                return "Waning gibbous";
            case LAST_QUARTER:
                return "Last quarter";
            case WANING_CRESCENT:
                return "Waning crescent";
            default:
                // This should be unreachable, but you never know
                return "Out of range: " + phase;
        }
    }
}
