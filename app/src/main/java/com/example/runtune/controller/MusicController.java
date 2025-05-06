package com.example.runtune.controller;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.Nullable;

import java.io.IOException;

public class MusicController {
    private final MediaPlayer mediaPlayer;
    private final TextView statusView;
    private final Context context;
    private String currentPath;
    private boolean autoPlay = true;

    public MusicController(Context context, @Nullable TextView statusView) {
        this.context = context;
        this.statusView = statusView;
        this.mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnPreparedListener(mp -> {
            if (autoPlay) {
                mp.start();
                if (statusView != null) {
                    statusView.setText("Now Playing: " + getCurrentTitle());
                }
            } else {
                if (statusView != null) {
                    statusView.setText("Ready: " + getCurrentTitle());
                }
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            if (statusView != null) {
                statusView.setText("Error playing music");
            }
            Log.e("MusicController", "Error code: " + what + ", extra: " + extra);
            return true;
        });
    }

    public void setMusic(String uriString, boolean autoPlay) {
        try {
            this.autoPlay = autoPlay;
            mediaPlayer.reset();
            Uri uri = Uri.parse(uriString);
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.prepareAsync();
            currentPath = uriString;
        } catch (IOException e) {
            Log.e("MusicController", "Error setting data source", e);
            if (statusView != null) {
                statusView.setText("Invalid music file");
            }
        }
    }

    public void play() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            if (statusView != null) {
                statusView.setText("Now Playing: " + getCurrentTitle());
            }
        }
    }

    public void pause(String reason) {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (statusView != null) {
                statusView.setText("Paused: " + reason);
            }
        }
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public String getCurrentTitle() {
        if (currentPath == null) return "No track selected";
        int lastSlash = currentPath.lastIndexOf('/');
        return lastSlash >= 0 ? currentPath.substring(lastSlash + 1) : currentPath;
    }

    public void stop() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            if (statusView != null) {
                statusView.setText("Stopped");
            }
        }
    }

    public void release() {
        mediaPlayer.release();
    }
}
