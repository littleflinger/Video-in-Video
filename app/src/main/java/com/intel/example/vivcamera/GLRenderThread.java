package com.intel.example.vivcamera;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class GLRenderThread extends Thread {

    public GLRenderThread(SurfaceTexture surface, int width, int height) {
        mPreviewWindowSurface = surface;
        mPreviewSurfaceWidth = width;
        mPreviewSurfaceHeight = height;

        Matrix.setIdentityM(mSTMatrix, 0);
        Matrix.setIdentityM(mMMatrix, 0);

        mPreviewEGLSurface = EGL10.EGL_NO_SURFACE;
        mRecorderEGLSurface = EGL10.EGL_NO_SURFACE;

        mCamera0FrameRate = 0;
        mCamera1FrameRate = 0;
        mCompositeFrameRate = 0;
    }

    @Override
    public void run() {
        prepare();
        mListener.onSurfaceAvailable();
        while(isPreviewing) {
            RenderProcessing();
        }
    }

    private void prepare() {
        InitiateEGL();
        InitiateGLES();
    }

    public void setSurfaceAvailableListener(SurfaceAvailableListener listener) {
        mListener = listener;
    }

    private void InitiateEGL() {
        Log.d(TAG, "start initialize EGL");
        mEGL = (EGL10)EGLContext.getEGL();

        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed "
                    + GLUtils.getEGLErrorString(mEGL.eglGetError()));
        }

        int[] major_minor = new int[2];
        if (!mEGL.eglInitialize(mEGLDisplay,major_minor)) {
            throw new RuntimeException("eglInitialize failed "
                    + GLUtils.getEGLErrorString(mEGL.eglGetError()));
        }

        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] attribList = {
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL_RECORDABLE_ANDROID, 1,
                EGL10.EGL_NONE
        };

        EGLConfig eglConfig;
        if (!mEGL.eglChooseConfig(mEGLDisplay, attribList, configs, 1, numConfigs)) {
            throw new RuntimeException("eglChooseConfig failed "
                    + GLUtils.getEGLErrorString(mEGL.eglGetError()));
        } else if (numConfigs[0] > 0) {
            eglConfig = configs[0];
        } else {
            throw new RuntimeException("optimized eglConfig have not found.");
        }

        mPreviewEGLSurface = mEGL.eglCreateWindowSurface(mEGLDisplay, eglConfig, mPreviewWindowSurface, null);
        checkEGLError("eglCreateWindowSurface");

        int[] attrib_list = {
                EGL_CONTEXT_CLIENT_VERSTION, 2,
                EGL10.EGL_NONE
        };
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        checkEGLError("eglCreateContext");

        mEGLConfig = eglConfig;

        mEGL.eglMakeCurrent(mEGLDisplay, mPreviewEGLSurface, mPreviewEGLSurface, mEGLContext);
        checkEGLError("eglMakeCurrent");

        Log.d(TAG, "finish initialize EGL");
    }

    private void InitiateGLES() {
        Log.d(TAG, "start initialize OPenGL ES2.0");
        createVBO();

        int program = makeProgram(mVertexShader, mFragmentShader);
        if (program < 0) {
            Log.e(TAG, "make mProgram failed");
            return;
        }
        mProgram = program;

        // mProgram
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGLError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGLError("glGetAttribLocation aTextureCoord");
        if (maTextureCoordHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }
        muMPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMPMatrix");
        checkGLError("glGetUniformLocation uMPMatrix");
        if (muMPMatrixHandle == -1) {
            throw new RuntimeException("Could not get uniform location for uMPMatrix");
        }
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGLError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get uniform location for uSTMatrix");
        }
        muRatioHandle = GLES20.glGetUniformLocation(mProgram, "uRatio");
        checkGLError("glGetUniformLocation uRatio");
        if (muRatioHandle == -1) {
            throw new RuntimeException("Could not get uniform location for uRatio");
        }
        muFlagHandle = GLES20.glGetUniformLocation(mProgram, "uFlag");
        checkGLError("glGetUniformLocation uFlag");
        if (muFlagHandle == -1) {
            throw new RuntimeException("Could not get uniform location for uFlag");
        }
        msTexture0Handle = GLES20.glGetUniformLocation(mProgram, "sTexture0");
        checkGLError("glGetUniformLocation sTexture0");
        if (msTexture0Handle == -1) {
            throw new RuntimeException("Could not get uniform location for sTexture0");
        }
        msTexture1Handle = GLES20.glGetUniformLocation(mProgram, "sTexture1");
        checkGLError("glGetUniformLocation sTexture1");
        if (msTexture1Handle == -1) {
            throw new RuntimeException("Could not get uniform location for sTexture1");
        }

        GLES20.glGenTextures(2, textures, 0);
        mTextureID0 = textures[0];
        mTextureID1 = textures[1];
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID0);
        checkGLError("glBindTexture mTextureID0");
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        mCameraSurface0 = new SurfaceTexture(mTextureID0);
        mCameraSurface0.setOnFrameAvailableListener(
                new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        synchronized (lock0) {
                            updateCameraSurface0 = true;
                            //Log.d(TAG, "CameraFrameAvailable!");
                        }
                    }
                }
        );

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID1);
        checkGLError("glBindTexture mTextureID1");
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        mCameraSurface1 = new SurfaceTexture(mTextureID1);
        mCameraSurface1.setOnFrameAvailableListener(
                new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        synchronized (lock1) {
                            updateCameraSurface1 = true;
                            //Log.d(TAG, "VideoFrameAvailable!");
                        }
                    }
                }
        );

        //Viewport Transform
        GLES20.glViewport(0, 0, mPreviewSurfaceWidth, mPreviewSurfaceHeight);
        //Projection Transform
        Matrix.frustumM(mProjMatrix, 0, -mRatio, mRatio, -1.0f, 1.0f, 1.0f, 300.0f);
        /* set the LookAt's up direction to negative, since
         * the textureView orientation is upside-down
         */
        //Model-View Transform
        Matrix.setLookAtM(mVMatrix, 0, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClearColor(0.643f, 0.776f, 0.223f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);
        checkGLError("glUseProgram");
    }

    public SurfaceTexture getCameraSurface0() {
        return mCameraSurface0;
    }

    public SurfaceTexture getCameraSurface1() {
        return mCameraSurface1;
    }

    public void AddVideoRecorderSurface(Surface surface, int width, int height) {
        mRecorderEGLSurface = mEGL.eglCreateWindowSurface(mEGLDisplay,
                mEGLConfig, surface, null);
        checkEGLError("eglCreateWindowSurface : mVideoEncoderEGLSurface");
        mRecorderSurfaceWidth = width;
        mRecorderSurfaceHeight = height;
    }

    public void RemoveVideoRecorderSurface() {
        synchronized (recording) {
            if (mRecorderEGLSurface != EGL10.EGL_NO_SURFACE) {
                mEGL.eglDestroySurface(mEGLDisplay, mRecorderEGLSurface);
                mRecorderEGLSurface = EGL10.EGL_NO_SURFACE;
            }
        }
    }

    public void StopRendering() {
        isPreviewing = false;
    }

    public void TerminateGL() {
        GLES20.glDeleteBuffers(2, vboIds, 0);
        GLES20.glDeleteTextures(2, textures, 0);
        GLES20.glDeleteProgram(mProgram);
        mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
        mEGL.eglDestroySurface(mEGLDisplay, mPreviewEGLSurface);
        mEGLContext = EGL10.EGL_NO_CONTEXT;
        mPreviewEGLSurface = EGL10.EGL_NO_SURFACE;
    }

    private void RenderProcessing() {
        synchronized (lock0) {
            if (updateCameraSurface0) {
                mCameraSurface0.updateTexImage();
                mCameraSurface0.getTransformMatrix(mSTMatrix);
                updateCameraSurface0 = false;
                mCamera0FrameRate++;
            }
        }
        synchronized (lock1) {
            if (updateCameraSurface1) {
                mCameraSurface1.updateTexImage();
                updateCameraSurface1 = false;
                mCamera1FrameRate++;
            }
        }
        GLES20.glViewport(0, 0, mPreviewSurfaceWidth, mPreviewSurfaceHeight);
        doComposite();

        synchronized(recording) {
            if (mRecorderEGLSurface != EGL10.EGL_NO_SURFACE) {
                mEGL.eglMakeCurrent(mEGLDisplay, mRecorderEGLSurface, mPreviewEGLSurface, mEGLContext);
                GLES20.glViewport(0, 0, mRecorderSurfaceWidth, mRecorderSurfaceHeight);
                GLES30.glBlitFramebuffer(0, 0, mPreviewSurfaceWidth, mPreviewSurfaceHeight,
                        0, 0, mPreviewSurfaceWidth, mPreviewSurfaceHeight, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
                SwapBuffer(mRecorderEGLSurface);
                mEGL.eglMakeCurrent(mEGLDisplay, mPreviewEGLSurface, mPreviewEGLSurface, mEGLContext);
            }
        }
        SwapBuffer(mPreviewEGLSurface);
        mCompositeFrameRate++;
    }

    private void doComposite() {
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glUniform1f(muRatioHandle, mRatio);

        //First step: render texture0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID0);
        GLES20.glUniform1i(msTexture0Handle, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBigRect);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_STRIDE, 0);
        checkGLError("glVertexAttribPointer maPositionHandle");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGLError("glEnableVertexAttribArray maPositionHandle");
        GLES20.glVertexAttribPointer(maTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_STRIDE, 3 * FLOAT_SIZE);
        checkGLError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureCoordHandle);
        checkGLError("glEnableVertexAttribArray maTextureHandle");

        Matrix.setIdentityM(mMMatrix, 0);
        Matrix.multiplyMM(mMPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
        Matrix.multiplyMM(mMPMatrix, 0, mProjMatrix, 0, mMPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMPMatrixHandle, 1, false, mMPMatrix, 0);
        GLES20.glUniform1i(muFlagHandle, 1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGLError("glDrawArrays0");

        //Second step: render texture1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID1);
        GLES20.glUniform1i(msTexture1Handle, 1);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mSmallRect);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_STRIDE, 0);
        checkGLError("glVertexAttribPointer maPositionHandle");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGLError("glEnableVertexAttribArray maPositionHandle");
        GLES20.glVertexAttribPointer(maTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_STRIDE, 3 * FLOAT_SIZE);
        checkGLError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureCoordHandle);
        checkGLError("glEnableVertexAttribArray maTextureHandle");

        /*Matrix.scaleM(mMMatrix, 0, mScaleX, mScaleY, 0);
        Matrix.translateM(mMMatrix, 0, mTransplateX / mScaleX, mTransplateY / mScaleY, 0.0f);
        Matrix.rotateM(mMMatrix, 0, mRotate, 0, 0, 1);*/

        Matrix.multiplyMM(mMPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
        Matrix.multiplyMM(mMPMatrix, 0, mProjMatrix, 0, mMPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMPMatrixHandle, 1, false, mMPMatrix, 0);
        GLES20.glUniform1i(muFlagHandle, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGLError("glDrawArrays1");
    }

    private void SwapBuffer(EGLSurface eglSurface) {
        if (! mEGL.eglSwapBuffers(mEGLDisplay, eglSurface)) {
            Log.e(TAG, "Fail to swap buffers!");
        }
        checkGLError("eglSwapBuffers");
    }

    private void createVBO() {
        FloatBuffer mBigTriangleVertices = ByteBuffer.allocateDirect(mBigTriangleVerticesData.length
                * FLOAT_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mBigTriangleVertices.put(mBigTriangleVerticesData).position(0);

        FloatBuffer mSmallTriangleVertices = ByteBuffer.allocateDirect(mSmallTriangleVerticesData.length
                * FLOAT_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mSmallTriangleVertices.put(mSmallTriangleVerticesData).position(0);

        GLES20.glGenBuffers(2, vboIds, 0);
        mBigRect = vboIds[0];
        mSmallRect = vboIds[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBigRect);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mBigTriangleVerticesData.length * FLOAT_SIZE,
                mBigTriangleVertices, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mSmallRect);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mSmallTriangleVerticesData.length*FLOAT_SIZE,
                mSmallTriangleVertices, GLES20.GL_STATIC_DRAW);
    }

    private int makeProgram(String vertexShaderSource, String fragmentShaderSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource);
        if (vertexShader == 0) {
            return -1;
        }
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource);
        if (fragmentShader == 0) {
            return -1;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program : ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                return -1;
            }
        }
        return program;
    }
    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] isCompiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, isCompiled, 0);
            if (isCompiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    private void checkEGLError(String op) {
        Log.d(TAG, op);
        if (mEGL.eglGetError() != EGL10.EGL_SUCCESS) {
            throw new RuntimeException(op + ": EGLError: " + GLUtils.getEGLErrorString(mEGL.eglGetError()));
        }
    }
    private void checkGLError(String op) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": GLError " + error);
        }
    }

    private static final String TAG = "GLRenderThread";
    private SurfaceTexture mPreviewWindowSurface;
    private int mPreviewSurfaceWidth, mPreviewSurfaceHeight, mRecorderSurfaceWidth, mRecorderSurfaceHeight;

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    private static final int EGL_CONTEXT_CLIENT_VERSTION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;

    private static final int FLOAT_SIZE = 4;
    private static final int TRIANGLE_VERTICES_STRIDE = 5 * FLOAT_SIZE;
    private int mBigRect, mSmallRect;
    private static final float[] mBigTriangleVerticesData = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0, 0.f, 0.f,
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f,  1.0f, 0, 0.f, 1.f,
            1.0f,  1.0f, 0, 1.f, 1.f,
    };
    private static final float[] mSmallTriangleVerticesData = {
            // X, Y, Z, U, V
            -0.25f, -0.25f, 0, 0.f, 0.f,
            0.25f, -0.25f, 0, 1.f, 0.f,
            -0.25f,  0.25f, 0, 0.f, 1.f,
            0.25f,  0.25f, 0, 1.f, 1.f,
    };
    private static final String mVertexShader =
            "uniform mat4 uMPMatrix;                            \n" +
                    "uniform mat4 uSTMatrix;                            \n" +
                    "uniform float uRatio;                              \n" +
                    "attribute vec4 aPosition;                          \n" +
                    "attribute vec4 aTextureCoord;                      \n" +
                    "varying vec2 vTextureCoord;                        \n" +
                    "void main() {                                      \n" +
                    "   vec4 scaledPos = aPosition;                     \n" +
                    "   scaledPos.x = scaledPos.x * uRatio;             \n" +
                    "   gl_Position = uMPMatrix * scaledPos;            \n" +
                    "   vTextureCoord = (uSTMatrix * aTextureCoord).xy; \n" +
                    "}                                                  \n";

    private static final String mFragmentShader =
            "#extension GL_OES_EGL_image_external : require     \n" +
                    "precision mediump float;                           \n" +
                    "varying vec2 vTextureCoord;                        \n" +
                    "uniform int  uFlag;                                \n" +
                    "uniform samplerExternalOES sTexture0;              \n" +
                    "uniform samplerExternalOES sTexture1;              \n" +
                    "void main() {                                      \n" +
                    "   vec4 baseColor;                                 \n" +
                    "   vec4 topColor;                                  \n" +
                    "   baseColor = texture2D(sTexture0, vTextureCoord);\n" +
                    "   topColor = texture2D(sTexture1, vTextureCoord); \n" +
                    "   if(uFlag == 1){                                 \n" +
                    "       gl_FragColor = baseColor;                   \n" +
                    "   } else {                                        \n" +
                    "       gl_FragColor = topColor;                    \n" +
                    "   }                                               \n" +
                    "}                                                  \n";

    private float[] mMPMatrix = new float[16];
    private float[] mProjMatrix = new float[16];
    private float[] mMMatrix = new float[16];
    private float[] mVMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private float mRatio = 1.0f;

    private int maPositionHandle;
    private int maTextureCoordHandle;
    private int muMPMatrixHandle;
    private int muSTMatrixHandle;
    private int muRatioHandle;
    private int muFlagHandle;
    private int msTexture0Handle;
    private int msTexture1Handle;

    private EGL10 mEGL;
    private EGLDisplay mEGLDisplay;
    private EGLSurface mPreviewEGLSurface, mRecorderEGLSurface;
    private EGLContext mEGLContext;
    private EGLConfig mEGLConfig;

    private SurfaceTexture mCameraSurface0, mCameraSurface1;
    private int[] textures = new int[2];
    private int[] vboIds = new int[2];
    private int mTextureID0, mTextureID1;
    private int mProgram;
    private final String lock0 = "lock0";
    private final String lock1 = "lock1";
    private final String recording = "recording";
    private boolean updateCameraSurface0 = false, updateCameraSurface1 = false;
    private boolean isPreviewing = true;

    public int mCamera0FrameRate, mCamera1FrameRate, mCompositeFrameRate;

    public interface SurfaceAvailableListener {

        void onSurfaceAvailable();

    }
    private SurfaceAvailableListener mListener;
}
