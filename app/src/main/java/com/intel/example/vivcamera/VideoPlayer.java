package com.intel.example.vivcamera;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;

import java.io.IOException;

public class VideoPlayer {
    private static final String TAG = "VideoPlayer";
    private MediaPlayer mMediaPlayer;

    public VideoPlayer(SurfaceTexture surface, String videoPath) {
        mMediaPlayer = new MediaPlayer();
        try {
            mMediaPlayer.setDataSource(videoPath);
            mMediaPlayer.setSurface(new Surface(surface));
            mMediaPlayer.prepare();
            mMediaPlayer.setLooping(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        mMediaPlayer.start();
    }

    public void TerminateMP() {
        mMediaPlayer.stop();
        mMediaPlayer.reset();
        mMediaPlayer.release();
        mMediaPlayer = null;
    }
}
