package com.intel.example.vivcamera;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraCore {
    private static final String TAG = "CameraCore";
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private List<SurfaceTexture> mSurfaces = new ArrayList<>();
    private CameraManager mCameraManager;
    private CameraDevice[] mCameraDevice = new CameraDevice[2];
    private CameraCaptureSession[] mCameraCaptureSession = new CameraCaptureSession[2];
    private String Rear_Facing_Camera = null, Front_Facing_Camera = null;

    public static final int REAR_FACING_CAMERA = 0;
    public static final int FRONT_FACING_CAMERA = 1;

    public CameraCore(CameraManager cameraManager) throws CameraAccessException {
        mCameraManager = cameraManager;
        mHandlerThread = new HandlerThread("CameraHandlerThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        for (String cameraId : mCameraManager.getCameraIdList()) {
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK) {
                Rear_Facing_Camera = cameraId;
            } else if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT) {
                Front_Facing_Camera = cameraId;
            }
        }
    }

    public void Open(int cameraId, SurfaceTexture surface, int width, int height) throws CameraAccessException {
        String id = toIdString(cameraId);
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
        StreamConfigurationMap info = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size optimalSize = chooseBigEnoughSize(info.getOutputSizes(surface.getClass()), width, height);
        surface.setDefaultBufferSize(optimalSize.getWidth(), optimalSize.getHeight());
        mSurfaces.add(surface);
        mCameraManager.openCamera(id, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCameraDevice[getDeviceId(camera)] = camera;
                List<Surface> outputSurfaces = new ArrayList<>();
                outputSurfaces.add(new Surface(mSurfaces.get(getDeviceId(camera))));
                try {
                    camera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mCameraCaptureSession[getSessionId(session)] = session;
                            try {
                                CaptureRequest.Builder requestBuilder = mCameraDevice[getSessionId(session)].createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                requestBuilder.addTarget(new Surface(mSurfaces.get(getSessionId(session))));
                                session.setRepeatingRequest(requestBuilder.build(), null, mHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    }, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(CameraDevice camera) {

            }

            @Override
            public void onError(CameraDevice camera, int error) {

            }
        }, mHandler);
    }

    public void Close() {
        for (int cameraId = 0; cameraId < mSurfaces.size(); cameraId++) {
            if (mCameraCaptureSession[cameraId] != null) {
                mCameraCaptureSession[cameraId].close();
                mCameraCaptureSession[cameraId] = null;
            }
            if (mCameraDevice[cameraId] != null) {
                mCameraDevice[cameraId].close();
                mCameraDevice[cameraId] = null;
            }
        }
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String toIdString(int cameraId) {
        switch (cameraId) {
            case REAR_FACING_CAMERA:
                return Rear_Facing_Camera;
            case FRONT_FACING_CAMERA:
                return Front_Facing_Camera;
            default:
                throw new RuntimeException("Illegal Camera ID!");
        }
    }

    private int toIdInt(String cameraId) {
        Integer lensFacing = 0;
        try {
            lensFacing = mCameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        switch (lensFacing) {
            case CameraCharacteristics.LENS_FACING_BACK:
                return REAR_FACING_CAMERA;
            case CameraCharacteristics.LENS_FACING_FRONT:
                return FRONT_FACING_CAMERA;
            default:
                throw new RuntimeException("Illegal Camera ID!");
        }
    }

    private int getDeviceId(CameraDevice cameraDevice) {
        return toIdInt(cameraDevice.getId());
    }

    private int getSessionId(CameraCaptureSession session) {
        return toIdInt(session.getDevice().getId());
    }

    static Size chooseBigEnoughSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            Log.d(TAG, "option size: " + option.getWidth() + "x" + option.getHeight());
            /*Attention: As internal format's sizes are different
              with android view size,so wo need to invert this*/
            if (option.getWidth() >= height && option.getHeight() >= width) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long)lhs.getWidth()*lhs.getHeight()-(long)rhs.getWidth()*rhs.getHeight());
        }
    }
}
