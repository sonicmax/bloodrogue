package com.sonicmax.bloodrogue.engine.environment;


import android.util.Log;

import com.sonicmax.bloodrogue.renderer.FrameCounter;
import com.sonicmax.bloodrogue.utils.maths.RandomNumberGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Manages changing of weather state according to time/season and manages decal decay of puddles, snow cover, etc
 */

public class WeatherManager {
    private final String LOG_TAG = this.getClass().getSimpleName();

    public static final int FINE = 0;
    public static final int RAINING = 1;
    public static final int SNOWING = 2;
    public static final int FOGGY = 3;

    public static final int[] STATES = {FINE, RAINING, SNOWING, FOGGY};

    public static final int WEATHER_PERIOD_LENGTH = 120; // In minutes

    public static final int PUDDLE_LIMIT = 100; // Todo: this should be scaled by map size
    public static final int PUDDLE_DURATION_TICKS = 180; // In ticks
    public static final int PUDDLE_DURATION_MINUTES = 60;
    public static final int PUDDLE_DRY_THRESHOLD = 60; // In ticks

    public static final int SNOW_DECAL_LIMIT = 200;
    public static final int SNOW_DURATION_TICKS = 240; // In ticks
    public static final int SNOW_DURATION_MINUTES = 120;
    public static final int SNOW_MELT_THRESHOLD = 60;

    public static final int TRANSITION_END = 100;
    private int transitionProgress;

    private final int TICKS_PER_FADE = 1;

    private int currentWeatherState;
    private int nextWeatherState;
    private int weatherStartTime;
    private int rainIntensity;
    private boolean needsTransitionOut;
    private boolean needsTransitionIn;

    private ArrayList<Long> puddleEntities;
    private HashMap<Long, Integer> puddleState;

    private ArrayList<Long> snowCoverEntities;
    private HashMap<Long, Integer> snowCoverState;
    private HashMap<Long, Integer> treeSnowState;

    private FrameCounter fadeCounter;
    private RandomNumberGenerator rng;

    public WeatherManager() {
        currentWeatherState = 0;
        nextWeatherState = -1;
        rainIntensity = 100;
        needsTransitionOut = false;
        needsTransitionIn = true;
        transitionProgress = 0;

        puddleEntities = new ArrayList<>();
        puddleState = new HashMap<>();

        snowCoverEntities = new ArrayList<>();
        snowCoverState = new HashMap<>();
        treeSnowState = new HashMap<>();

        rng = new RandomNumberGenerator();
    }

    public void setWeatherState(int state, int time) {
        if (state < FINE || state > FOGGY) {
            Log.w(LOG_TAG, "Invalid weather state (" + state + ")");
            currentWeatherState = 0;
            weatherStartTime = time;
        }
        else {
            currentWeatherState = state;
            weatherStartTime = time;
        }
    }

    public String getWeatherString() {
        return getWeatherString(currentWeatherState);
    }

    public String getWeatherString(int state) {
        switch (state) {
            case FINE:
                return "Fine";
            case RAINING:
                return "Raining";
            case SNOWING:
                return "Snowing";
            case FOGGY:
                return "Foggy";
            default:
                return "Undefined";
        }
    }

    public int getCurrentWeatherState() {
        return currentWeatherState;
    }

    public float getIntensity() {
        if (needsTransitionOut) {
            return processOutTransition();
        }

        else if (needsTransitionIn) {
            return processInTransition();
        }

        else {
            return 1f;
        }
    }

    private float processOutTransition() {
        float intensity = 1f;

        if (fadeCounter == null) {
            fadeCounter = new FrameCounter(TICKS_PER_FADE);
        }

        boolean needChange = fadeCounter.tickAndCheckCount();

        if (needChange) {
            transitionProgress++;

            // Make sure that we still need to transition after advancing progress
            if (transitionProgress >= 100) {
                // Finished transition
                switchToNextWeatherState();

                transitionProgress = 0;
                needsTransitionOut = false;
                fadeCounter = null;
                return 0f;
            }
        }

        intensity -= transitionProgress * 0.01f;

        return intensity;
    }

    private float processInTransition() {
        float intensity = 0f;

        if (fadeCounter == null) {
            fadeCounter = new FrameCounter(TICKS_PER_FADE);
        }

        boolean needChange = fadeCounter.tickAndCheckCount();

        if (needChange) {
            transitionProgress++;

            // Make sure that we still need to transition after advancing progress
            if (transitionProgress >= 100) {
                // Finished transition
                transitionProgress = 0;
                needsTransitionIn = false;
                fadeCounter = null;
                return 1f;
            }
        }

        intensity += transitionProgress * 0.01f;

        return intensity;
    }

    public void switchToNextWeatherState() {
        if (nextWeatherState > -1) {
            Log.v(LOG_TAG, "switching to next weather state: " + getWeatherString(nextWeatherState));
            currentWeatherState = nextWeatherState;
        }
    }

    public void checkWeather(int currentTime) {
        int difference = currentTime - weatherStartTime;
        if (difference >= WEATHER_PERIOD_LENGTH) {
            // Chance for weather change increases with each hour
            // (50% after 1st hour, 33% after 2nd hour, 20% after 3rd hour, etc)

            int timesExceeded = difference / WEATHER_PERIOD_LENGTH;
            boolean shouldChange = (rng.getRandomInt(0, timesExceeded) == 0);
            if (shouldChange) {
                nextWeatherState = getRandomWeatherState();

                Log.v(LOG_TAG, "current state: " + getWeatherString(currentWeatherState));
                Log.v(LOG_TAG, "next state: " + getWeatherString(nextWeatherState));

                weatherStartTime = currentTime;

                needsTransitionOut = true;
                needsTransitionIn = true;
            }
        }
    }

    public int getRandomWeatherState() {
        // Sloppy way to make sure we switch to different state
        ArrayList<Integer> possibleStates = new ArrayList<>();

        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i] != currentWeatherState) {
                possibleStates.add(STATES[i]);
            }
        }

        return possibleStates.get(rng.getRandomInt(0, possibleStates.size() - 1));
    }

    public void addPuddle(long entity) {
        puddleEntities.add(entity);
        puddleState.put(entity, PUDDLE_DURATION_TICKS);
    }

    public ArrayList<Long> getPuddlesToRemove(int ticks) {
        ArrayList<Long> puddlesToRemove = new ArrayList<>();

        for (Long entity : puddleEntities) {
            int time = puddleState.get(entity);

            if (time <= 0) {
                puddlesToRemove.add(entity);
            }

            else if (time - PUDDLE_DRY_THRESHOLD <= 0 && rng.d6(1, 1)) {
                puddlesToRemove.add(entity);
            }

            else {
                puddleState.put(entity, time - ticks);
            }
        }

        puddleEntities.removeAll(puddlesToRemove);

        return puddlesToRemove;
    }

    public ArrayList<Long> getMeltedTreeSnow(int ticks) {
        ArrayList<Long> snowToMelt = new ArrayList<>();

        Log.v(LOG_TAG, "tree snow size: " + treeSnowState.size());

        for (Long entity : treeSnowState.keySet()) {
            int time = treeSnowState.get(entity);

            if (time <= 0) {
                snowToMelt.add(entity);
            }

            else if (time - SNOW_MELT_THRESHOLD <= 0 && rng.d6(1, 1)) {
                snowToMelt.add(entity);
            }

            else {
                treeSnowState.put(entity, time - ticks);
            }
        }

        for (Long entity : snowToMelt) {
            treeSnowState.remove(entity);
        }

        return snowToMelt;
    }

    public ArrayList<Long> getPuddleEntities() {
        return puddleEntities;
    }

    public ArrayList<Long> getSnowCoverEntities() {
        return snowCoverEntities;
    }

    public void startTreeSnowTimer(long entity) {
        treeSnowState.put(entity, SNOW_DURATION_TICKS);
    }

    public void clearPuddleEntities() {
        puddleEntities.clear();
    }

    public int getRainIntensity() {
        return rainIntensity;
    }

    public void setIntensity(int intensity) {
        rainIntensity = intensity;
    }
}
