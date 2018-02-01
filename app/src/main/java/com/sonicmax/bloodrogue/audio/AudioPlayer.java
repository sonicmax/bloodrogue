package com.sonicmax.bloodrogue.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.util.SparseBooleanArray;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Class which handles audio loading and playback. SoundPool is used to load and play sound effects.
 * MediaPlayer instances (via LoopingMusicPlayer) are used for background music due to the
 * file size limit of SoundPool.
 */

public class AudioPlayer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private final float SOUNDPOOL_MAX_VOL = 1f;
    private final int SOUNDPOOL_LIMIT = 1000000;
    private final int PLAY_ONCE = 0;
    private final int LOOP_FOREVER = -1;

    private SoundPool soundPool;
    private LoopingMusicPlayer loopingMediaPlayer;
    private Context context;

    private HashMap<String, Integer> soundPoolResources;
    private SparseBooleanArray resourceLoadStatus;

    private float volume;
    private int pendingLoads;
    private long loadStartTime;
    private boolean finishedLoading;

    public AudioPlayer(Context context) {
        this.context = context;
        this.soundPoolResources = new HashMap<>();
        this.resourceLoadStatus = new SparseBooleanArray();

        createSoundPool();

        this.pendingLoads = 0;
        loadSounds();
    }

    /**
     * Instantiates a new SoundPool, sets volume level and adds an OnLoadCompleteListener so
     * we can keep track of resource load status.
     */

    private void createSoundPool() {
        if (Build.VERSION.SDK_INT >= 21) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(10)
                    .setAudioAttributes(attributes)
                    .build();
        }
        else {
            // We have to use deprecated constructor
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        }

        // Make sure that SoundPool uses correct volume level
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (audioManager != null) {
            float streamVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

            // Volume needs to be expressed as value from 0 - 1.
            volume = streamVolume / maxVolume;
        }

        else {
            volume = SOUNDPOOL_MAX_VOL;
        }


        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {

            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    resourceLoadStatus.put(sampleId, true);
                    pendingLoads--;

                    if (pendingLoads == 0) {
                        finishedLoading = true;
                        Log.d(LOG_TAG, "Loaded audio in " + TimeUnit.MILLISECONDS.convert(System.nanoTime() - loadStartTime, TimeUnit.NANOSECONDS) + " ms");
                    }
                }
                else {
                    Log.e(LOG_TAG, "Error while loading " + sampleId + " (status " + status + ")");
                }
            }

        });
    }

    /**
     * Loads sound effects from assets/fx/ folder. The files need to be smaller than 1MB to work
     * correctly in SoundPool (preferably much smaller)
     */

    private void loadSounds() {
        final String FX_PATH = "fx/";

        loadStartTime = System.nanoTime();

        AssetManager assetManager = context.getAssets();
        try {
            String[] audioFiles = assetManager.list("fx");

            for (String file : audioFiles) {
                pendingLoads++;
                AssetFileDescriptor afd = assetManager.openFd(FX_PATH + file);

                if (afd.getLength() < SOUNDPOOL_LIMIT) {
                    int handle = soundPool.load(afd, 1);
                    soundPoolResources.put(file, handle);
                    resourceLoadStatus.put(handle, false);
                }

                else {
                    Log.w(LOG_TAG, "Skipping \"" + file + "\": too big (" + afd.getLength() + " bytes)");
                }

                afd.close();
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error loading audio", e);
        }
    }

    public void playSound(String filename) {
        int handle = soundPoolResources.get(filename);
        soundPool.play(handle, volume, volume, 0, PLAY_ONCE, 1);
    }

    public void playSound(String filename, int priority) {
        int handle = soundPoolResources.get(filename);
        soundPool.play(handle, volume, volume, 0, PLAY_ONCE, 1);
    }

    public void startLoop(String filename) {
        int handle = soundPoolResources.get(filename);
        soundPool.play(handle, volume, volume, 1, LOOP_FOREVER, 1);
    }

    public void startMusicLoop(String filename) {
        loopingMediaPlayer = new LoopingMusicPlayer(context, filename);
        loopingMediaPlayer.start();
    }

    public void startNewMusicLoop(String next) {
        if (loopingMediaPlayer == null) {
            startMusicLoop(next);
            return;
        }

        loopingMediaPlayer.queueAndStart(next);
    }

    public void stopAndReleaseResources() {
        soundPool.release();
        loopingMediaPlayer.release();
    }
}
