package com.intel.example.vivcamera;

import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

public class VideoRecorder {
    private static final String TAG = "VideoRecorder";
    private MediaRecorder mMediaRecorder;
    private Surface mSurface;

    public VideoRecorder(int width, int height, int frameRate, int bitRate, String path) {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(path);
        mMediaRecorder.setVideoEncodingBitRate(bitRate);
        mMediaRecorder.setVideoFrameRate(frameRate);
        mMediaRecorder.setVideoSize(width, height);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "MediaRecorder prepare failed!!!");
            e.printStackTrace();
        }
        mSurface = mMediaRecorder.getSurface();
    }

    public Surface requestRecordingSurface() {
        return mSurface;
    }

    public void start() {
        try {
            mMediaRecorder.start();
        } catch (IllegalStateException ex) {
            Log.e(TAG, "start recording failed!");
            ex.printStackTrace();
        }
    }

    public void stop() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mMediaRecorder = null;
    }
}
