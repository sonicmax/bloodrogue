package com.sonicmax.bloodrogue.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class which manages two instances of MediaPlayer to allow for gapless looping and changing
 * of audio tracks.
 */

public class LoopingMusicPlayer {
    private final String LOG_TAG = this.getClass().getSimpleName();

    private Context context;
    private MediaPlayer currentPlayer;
    private MediaPlayer nextPlayer;

    private String currentlyPlaying;
    private String queuedTrack;
    private AssetFileDescriptor currentFile;

    private int volume = 0;

    public LoopingMusicPlayer(Context context, String file) {
        this.context = context;
        this.currentlyPlaying = file;
        this.currentFile = null;

        this.currentPlayer = new MediaPlayer();
        this.currentPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        loadMusic(currentPlayer);

        createNextMediaPlayer();
    }

    private void createNextMediaPlayer() {
        nextPlayer = new MediaPlayer();
        nextPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        loadMusic(nextPlayer);

        // Todo: workaround when SDK_INT < 15?
        if (Build.VERSION.SDK_INT > 15) {
            currentPlayer.setNextMediaPlayer(nextPlayer);
        }

        currentPlayer.setOnCompletionListener(onCompletionListener);
    }

    private final MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.release();
            currentPlayer = nextPlayer;
            createNextMediaPlayer();
        }
    };

    public void loadMusic(MediaPlayer player) {
        final String MUSIC_PATH = "music/";

        AssetManager assetManager = context.getAssets();

        try {
            if (currentFile == null) {
                currentFile = assetManager.openFd(MUSIC_PATH + currentlyPlaying);
            }

            player.setDataSource(currentFile.getFileDescriptor(), currentFile.getStartOffset(), currentFile.getLength());
            player.prepare();

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error loading audio file \"" + currentlyPlaying + "\"", e);
        }
    }

    public boolean isPlaying() throws IllegalStateException {
        return currentPlayer.isPlaying();
    }

    public void setVolume(float leftVolume, float rightVolume) {
        currentPlayer.setVolume(leftVolume, rightVolume);
    }

    public void start() throws IllegalStateException {
        currentPlayer.start();
    }

    public void stop() throws IllegalStateException {
        currentPlayer.stop();
    }

    public void pause() throws IllegalStateException {
        currentPlayer.pause();
    }

    public void release() {
        currentPlayer.release();
        nextPlayer.release();
        currentPlayer = null;
        nextPlayer = null;
    }

    public void reset() {
        currentPlayer.reset();
    }

    /**
     * Queues next track, fades out current track and plays next track once fade is complete.
     *
     * @param next Filename of next music track to start
     */

    public void queueAndStart(String next) {
        queuedTrack = next;

        final int FADE_DURATION = 1000;
        final int FADE_INTERVAL = 50;
        final int MAX_VOLUME = 1;

        int numberOfSteps = FADE_DURATION / FADE_INTERVAL;

        final float delta = MAX_VOLUME / (float) numberOfSteps;

        final Timer timer = new Timer(true);

        // Use Timer to prevent fade out from blocking UI

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                fadeOutStep(delta);

                if (volume <= 0f){
                    timer.cancel();
                    timer.purge();

                    startQueuedTrack();
                }
            }
        };

        timer.schedule(timerTask,FADE_INTERVAL, FADE_INTERVAL);
    }

    private void fadeOutStep(float delta){
        currentPlayer.setVolume(volume, volume);
        volume += delta;
    }

    private void startQueuedTrack() {
        currentlyPlaying = queuedTrack;
        queuedTrack = null;
        currentFile = null;

        currentPlayer.stop();
        currentPlayer.reset();
        currentPlayer = new MediaPlayer();
        loadMusic(currentPlayer);
        createNextMediaPlayer();
        start();
    }
}
