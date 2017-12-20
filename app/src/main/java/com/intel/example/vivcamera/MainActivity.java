package com.intel.example.vivcamera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, Button.OnClickListener, GLRenderThread.SurfaceAvailableListener {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mPreviewView = new TextureView(this);
        mPreviewView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 5));
        mPreviewView.setSurfaceTextureListener(this);
        layout.addView(mPreviewView);

        mButton = new Button(this);
        mButton.setText("start");
        mButton.setOnClickListener(this);
        mButton.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 95));
        mButton.setGravity(TextView.TEXT_ALIGNMENT_GRAVITY);
        layout.addView(mButton);
        setContentView(layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mVideoRecorderWidth = size.x;
        mVideoRecorderHeight = size.y;
    }

    @Override
    protected void onDestroy() {
        if (mCameraCore != null) {
            mCameraCore.Close();
            mCameraCore = null;
        }
        if (mVideoPlayer != null) {
            mVideoPlayer.TerminateMP();
            mVideoPlayer = null;
        }
        if (mGLRenderThread != null) {
            mGLRenderThread.StopRendering();
            try {
                mGLRenderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mGLRenderThread.TerminateGL();
            mGLRenderThread = null;
        }
        //mTimer.cancel();
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        mGLRenderThread = new GLRenderThread(surface, width, height);
        mGLRenderThread.setSurfaceAvailableListener(this);
        mGLRenderThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onClick(View v) {
        if (mStatus){
            mGLRenderThread.RemoveVideoRecorderSurface();
            mVideoRecorder.stop();
            mVideoRecorder = null;
            mButton.setText("start");
        } else {
            mVideoRecorder = new VideoRecorder(mVideoRecorderWidth, mVideoRecorderHeight, 30, 4000000, setVideoRecorderPath());
            mGLRenderThread.AddVideoRecorderSurface(mVideoRecorder.requestRecordingSurface(), mVideoRecorderWidth, mVideoRecorderHeight);
            mVideoRecorder.start();
            mButton.setText("stop");
        }
        mStatus = !mStatus;
    }

    @Override
    public void onSurfaceAvailable() {
        SurfaceTexture mSurface0 = mGLRenderThread.getCameraSurface0();
        SurfaceTexture mSurface1 = mGLRenderThread.getCameraSurface1();
        CameraManager cameraManager = (CameraManager)getSystemService(CAMERA_SERVICE);
        try {
            mCameraCore = new CameraCore(cameraManager);
            mCameraCore.Open(CameraCore.REAR_FACING_CAMERA, mSurface0, mPreviewView.getWidth(), mPreviewView.getHeight());
            mCameraCore.Open(CameraCore.FRONT_FACING_CAMERA, mSurface1, mPreviewView.getWidth(), mPreviewView.getHeight());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        /*
        if (getLocalVideoPath() != null) {
            mVideoPlayer = new VideoPlayer(mSurface1, getLocalVideoPath());
            mVideoPlayer.start();
        }*/
        mTimer.schedule(mTimerTask, 0, 1000);
    }

    @SuppressLint("HandlerLeak")
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EMPTY_LOCAL_VIDEO:
                    Toast.makeText(mContext, "Please use system Camera app record a video firstly", Toast.LENGTH_LONG).show();
                    break;
                case TIMER_MESSAGE:
                    if (mGLRenderThread != null) {
                        Log.d(TAG, "The camera0 frame rate is: " + mGLRenderThread.mCamera0FrameRate);
                        Log.d(TAG, "The camera1 frame rate is: " + mGLRenderThread.mCamera1FrameRate);
                        Log.d(TAG, "The composite frame rate is: " + mGLRenderThread.mCompositeFrameRate);
                        mGLRenderThread.mCamera0FrameRate = 0;
                        mGLRenderThread.mCamera1FrameRate = 0;
                        mGLRenderThread.mCompositeFrameRate = 0;
                    }
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    Timer mTimer = new Timer();
    TimerTask mTimerTask = new TimerTask() {
        @Override
        public void run() {
            Message message = new Message();
            message.what = TIMER_MESSAGE;
            mHandler.sendMessage(message);
        }
    };

    private String getLocalVideoPath() {
        String outputPath = null;
        File ExtSD = Environment.getExternalStorageDirectory();
        File CameraDirectory = new File(ExtSD.toString(), "/DCIM/Camera");
        if (CameraDirectory.exists()) {
            String extention = ".mp4";
            File[] files = CameraDirectory.listFiles();
            for (File file : files) {
                if (file.isFile()) {
                    if (file.getPath().substring(file.getPath().length()
                            -extention.length()).equals(extention)) {
                        outputPath = file.getAbsolutePath();
                        break;
                    }
                }
            }
            if (outputPath == null) {
                Message message = new Message();
                message.what = EMPTY_LOCAL_VIDEO;
                mHandler.sendMessage(message);
                return null;
            }
        } else {
            Message message = new Message();
            message.what = EMPTY_LOCAL_VIDEO;
            mHandler.sendMessage(message);
            return null;
        }
        return outputPath;
    }

    private String setVideoRecorderPath() {
        File ExtSD = Environment.getExternalStorageDirectory();
        File destPath = new File(ExtSD, "test.mp4");
        return destPath.toString();
    }

    private static final String TAG = "MainActivity";
    private TextureView mPreviewView;
    private Context mContext;
    private Button mButton;

    private GLRenderThread mGLRenderThread;
    private CameraCore mCameraCore;
    private VideoPlayer mVideoPlayer;
    private VideoRecorder mVideoRecorder;

    private boolean mStatus = false;

    private int mVideoRecorderWidth, mVideoRecorderHeight;

    private static final int EMPTY_LOCAL_VIDEO = 1;
    private static final int TIMER_MESSAGE = 2;
}
