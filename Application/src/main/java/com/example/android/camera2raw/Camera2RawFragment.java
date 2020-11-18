
package com.example.android.camera2raw;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.TonemapCurve;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;

/**
 * 演示使用Camera2 API捕获原始和JPEG照片的片段
 * <p/>
 * 在本例中，单个拍照请求的生命周期为:
 * <ul>
 * <li>
 * 用户按下“图片”按钮，结果调用{@link #takePicture()}
 * </li>
 * <li>
 * {@link #takePicture()}启动一个预捕获序列，触发相机的内置自动对焦、自动曝光和自动白平衡算法。
 * </li>
 * <li>
 * 预捕获序列完成后，通过调用{@link {CaptureRequest.builder #setTag(Object)}向相机发送一个{@link CaptureRequest}和
 * 一个{@link ImageSaver {}@link #CaptureRequest.builder setTag(Object)}设置一个单调递增的请求ID，
 * 开始JPEG和原始捕获序列。ImageSaverBuilder}将此请求存储在{@link #mJpegResultQueue}和{@link #mRawResultQueue}中
 * </li>
 * <li>
 * 当{@link CaptureResult}和{@link Image}通过后台线程中的回调变得可用时，{@link ImageSaver.ImageSaverBuilder}由{@link #mJpegResultQueue}和{@link #mRawResultQueue}中的请求ID查找并更新
 * </li>
 * <li>
 * 当保存图像所需的所有结果都可用时，{@link ImageSaver}由{@link ImageSaver}并传递给一个单独的后台线程以保存到一个文件中
 * </li>
 * </ul>
 */
public class Camera2RawFragment extends Fragment implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

     //从屏幕旋转到JPEG方向的转换
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private boolean isLock = false;         //是否为手动模式
    private final RunOnUiThread runOnUiThread = new RunOnUiThread();
    private TextView iso;
    private TextView speed;
    private TextView wb;
    private TextView intervalText;
    private TextView photoNum;
    private TextView time_I;
    private static final int[] ISO = {50,100,200,400,800,1600,3200,6400};
    private static final String[] WB = {"","自动","白炽灯","荧光灯","暖荧光","日光","多云","黄昏","阴天"};
    private int iso_now_index = 0;
    private int wb_now = 1;
    private int posX,posY,lastX,lastY;
    //private Long SPEED[] = {1000000000L,500000000L,250000000L,125000000L,62500000L,40000000L,20000000L,10000000L,5000000L,2500000L,1250000L,1000000L,625000L,500000L,400000L,250000L};
    //private String SPEED_STRING[] = {"1","1/2","1/4","1/8","1/16","1/25","1/50","1/100","1/200","1/400","1/800","1/1000","1/1600","1/2000","1/2500","1/4000"};
    private Long SPEED = 40000000L;
    private float nowFocusDistant = 10.0f;
    //private int speed_now_index = 5;
    public boolean shoot = false;
    private int photoCount;
    private int interval = 1;
    private boolean saveJPEG = false;
    private boolean saveRAW = true;
    private boolean isTakePhoto = false;
    private static String savePath = "/storage/emulated/0/DCIM";
    private Date dataNow = new Date();
    private BigInteger time_of_once_shoot = BigInteger.valueOf(dataNow.getTime());
    private BigInteger time_tmp = time_of_once_shoot;
    private BigInteger time_remove = time_of_once_shoot;
    private boolean isSaveFinished = false;
    private StatFs sf;
    private long availableBytesSize = 0L;
    //private long tmpleByteSize = 0l;
    private Button StartAndStopButton;
    private ImageButton AutoOrManual;
    //private File isSaveDngFile;
    private static boolean saveDng = false;
    //public Activity C2RF = getActivity();
    private Size jpegSize;
    private TextView t_size;
    private TextView t_format;
    private ImageButton whitebalance_btn;
    private boolean isExtremeMode = false;
    private TextView t_mode;
    private long lastButtonClickTime = 0L;
    //private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> tmpRawQueue = new TreeMap<>();
    private static Size maxRawSize;
    private static int[] rawSizeRange;
    private static int rawSizeIndex = 0;
    private static int realPixelDepth = 0;
    private String[] JPEGSize;
    private int format_now = 1;
    private int size_now = 0;
    private static int saveBitDepth = 16;
    private TextView t_bitDepth;
    private int photoBitSizeNow = 0;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

     //请求摄像头权限代码
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;
     //拍照需要获得许可
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
     // 捕获前序列的超时
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;

    /**
     * Tolerance when comparing aspect ratios.
     */
    private static final double ASPECT_RATIO_TOLERANCE = 0.005;

    /**
     * 由Camera2 API保证的最大预览宽度高度
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static final String TAG = "Camera2RawFragment";
    private static final int STATE_CLOSED = 0;  //摄像头状态:设备关闭
    private static final int STATE_OPENED = 1;  //相机状态:设备已打开，但未被捕获
    private static final int STATE_PREVIEW = 2;  //相机状态:显示相机预览
    private static final int STATE_WAITING_FOR_3A_CONVERGENCE = 3;  //相机状态:等待3A收敛后再拍照

    private OrientationEventListener mOrientationListener;  //用于确定设备何时发生旋转
    //当设备旋转180度时，这主要是必要的，在这种情况下，onCreate或onConfigurationChanged不会被调用，因为视图的尺寸保持不变，但是视图的方向已经改变，因此必须更新预览旋转。
    /*public class MyReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if("com.example.android.camera2raw.s".equals(intent.getAction())){
                Log.i(TAG,"接收到保存成功广播");
            }
        }
    }*/
    private void startTimelaps(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                takePicture();
                //dataNow = new Date();
                //Log.e(TAG,"开始时间"+Long.toString(dataNow.getTime()));
                //int i = photoCount;
                while (shoot){
                    /*sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
                    availableBytesSize = sf.getAvailableBytes();*/
                    //Log.e(TAG,"Byte Sub "+Long.toString(tmpleByteSize - availableBytesSize));
                    if(isExtremeMode){               //极限模式Extreme mode
                        if(saveDng){
                            File f = new File(savePath + "/DNG_" + getFrameNumString() + ".dng");
                            if(f.exists()){
                                FileInputStream file_size_test = null;
                                int size = 0;
                                try{
                                    file_size_test = new FileInputStream(f);
                                    size = file_size_test.available();
                                } catch (IOException e){
                                    e.printStackTrace();
                                } finally {
                                    closeInput(file_size_test);
                                    f = null;
                                }
                                if(size > 0){
                                    takePicture();
                                    Log.d(TAG,"触发");
                                }
                                /*sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
                                availableBytesSize = sf.getAvailableBytes();*/
                                /*if(saveJPEG)      //JPEG+DNG
                                    runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/28860416l));
                                else       //DNG
                                    runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/23617536l));*/
                            }
                        }
                        else if(isSaveFinished){
                            //tmpleByteSize = availableBytesSize;
                            /*sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
                            availableBytesSize = sf.getAvailableBytes();*/
                            //runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/4147200l));    //JPEG or RAW
                            isSaveFinished = false;
                            Log.d(TAG,"触发");
                            takePicture();
                        }
                        try{
                            //Thread.sleep(interval*1000);
                            Thread.sleep(10);
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                    else {                         //延时摄影模式Timelaps mode
                        /*sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
                        availableBytesSize = sf.getAvailableBytes();*/
                        /*if(saveJPEG && !saveRAW && !saveDng)
                            runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/4147200l));
                        else if(!saveJPEG && saveRAW && !saveDng)
                            runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/4147200l));
                        else if(saveJPEG && !saveRAW && saveDng)
                            runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/28860416l));
                        else if(!saveJPEG && !saveRAW && saveDng)
                            runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/23617536l));*/
                        try{
                            Thread.sleep(interval*1000);
                            //Thread.sleep(100);         //尝试10fps
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }
                        if(shoot)
                            takePicture();
                    }
                }
                //dataNow = new Date();
                //Log.e(TAG,"结束时间"+Long.toString(dataNow.getTime()));
                Thread.interrupted();
            }
        }).start();
    }
    private void countDown(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                int t = interval;
                while (shoot){
                    runOnUiThread.UpdateText(R.id.text_time,Integer.toString(t));
                    try {
                        Thread.sleep(1000);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    if(isTakePhoto){
                        t=interval;
                        isTakePhoto = false;
                    }else {
                        t--;
                    }
                }
                Thread.interrupted();
            }
        }).start();
    }

    private String LongToString(Long speed){        //快门速度转换
        //double s_d = 1000000000L/speed;
        //int s_i = (int)s_d;
        String s_s = Long.toString(1000000000L/speed);
        //String[] integer = s_s.split();
        /*if(s_i == 1){
            s_s = Integer.toString(s_i);
        }
        else{
            s_s = "1/" + Integer.toString(s_i);
        }*/
        Log.d(TAG,s_s);
        return "1/"+s_s;
    }

    /*public void clearMemory(Context context) {
        ActivityManager activityManger = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> list = activityManger.getRunningAppProcesses();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                ActivityManager.RunningAppProcessInfo apinfo = list.get(i);
                String[] pkgList = apinfo.pkgList;
                if (apinfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    for (int j = 0; j < pkgList.length; j++) {
                        //activityManger.killBackgroundProcesses(pkgList[j]);    //清理不可用的内容空间
                        //Log.d(TAG,pkgList[j]);
                    }
                }
            }
        }else {
            Log.d(TAG,"无法获取内存信息");
        }
    }*/

    public static void setPath(String path){
        savePath = path;
    }

    private void showOpenDocumentTree(){
        Intent intent = null;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            StorageManager sm = MyApplication.getContext().getSystemService(StorageManager.class);
            StorageVolume volume = sm.getStorageVolume(new File(savePath));
            if (volume != null){
                intent = volume.createAccessIntent(null);
            }
        }
        if (intent == null){
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        }
        startActivityForResult(intent,DocumentsUtils.OPEN_DOCUMENT_TREE_CODE);
    }

    /*
     *处理{@link TextureView}的几个生命周期事件
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            synchronized (mCameraStateLock) {
                mPreviewSize = null;
            }
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private AutoFitTextureView mTextureView;  //相机预览
    /**
     * 用于运行不应该阻塞UI的任务的附加线程。这用于{@link CameraDevice}和{@link CameraCaptureSession}的所有回调
     */
    private HandlerThread mBackgroundThread;
    /**
     *在{@link CameraCaptureSession}中跟踪相应的{@link CaptureRequest}和{@link CaptureResult}的计数器捕获回调
     */
    private final AtomicInteger mRequestCounter = new AtomicInteger();
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);  //在关闭摄像头之前防止应用程序退出
    private final Object mCameraStateLock = new Object();  //保护摄像机状态的锁

    // UI和后台线程都使用状态跟踪。名称中带有“Locked”的方法期望在调用时持有mCameraStateLock。
    private String mCameraId; //当前的{@link CameraDevice}的ID
    private CameraCaptureSession mCaptureSession;  //相机预览
    private CameraDevice mCameraDevice;  //对打开{@link CameraDevice}的引用
    private Size mPreviewSize; //相机预览尺寸
    private CameraCharacteristics mCharacteristics;  //配置当前摄像头设备
    private Handler mBackgroundHandler;  //用于在后台运行任务
    /**
     * A reference counted holder wrapping the {@link ImageReader} that handles JPEG image
     * captures. This is used to allow us to clean up the {@link ImageReader} when all background
     * tasks using its {@link Image}s have completed.
     */
    private RefCountedAutoCloseable<ImageReader> mJpegImageReader;  //包装处理JPEG图像捕获,
    /**
     * A reference counted holder wrapping the {@link ImageReader} that handles RAW image captures.
     * This is used to allow us to clean up the {@link ImageReader} when all background tasks using
     * its {@link Image}s have completed.
     */
    private RefCountedAutoCloseable<ImageReader> mRawImageReader;  //包装处理RAW图像捕获
    private boolean mNoAFRun = false; //当前配置的相机设备是否支持自动对焦
    private int mPendingUserCaptures = 0;  //捕获照片的未决用户请求数Number of pending user requests to capture a photo.
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mJpegResultQueue = new TreeMap<>(); //为正在进行的JPEG捕获请求ID到{@link ImageSaver.ImageSaverBuilder}的映射
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mRawResultQueue = new TreeMap<>(); ////为正在进行的RAW捕获请求ID到{@link ImageSaver.ImageSaverBuilder}的映射
    private CaptureRequest.Builder mPreviewRequestBuilder; //用于相机预览

    private int mState = STATE_CLOSED;
    private long mCaptureTimer; //定时器使用与预捕获序列，以确保及时捕获，避免3A收敛时间太长
    /**
     * {@link CameraDevice.StateCallback} is called when the currently active {@link CameraDevice}
     * changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here if
            // the TextureView displaying this has been set up.
            synchronized (mCameraStateLock) {
                mState = STATE_OPENED;
                mCameraOpenCloseLock.release();
                mCameraDevice = cameraDevice;

                // Start the preview session if the TextureView has been set up already.
                if (mPreviewSize != null && mTextureView.isAvailable()) {
                    createCameraPreviewSessionLocked();           //mState=STATE_PREVIEW
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, SPEED);    //单位:纳秒ns
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO[iso_now_index]);   //ISO2000
                    mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,nowFocusDistant);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, wb_now);
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                                mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Received camera device error: " + error);
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * JPEG image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnJpegImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            dequeueAndSaveImage(mJpegResultQueue, mJpegImageReader);
        }

    };

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * RAW image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            dequeueAndSaveImage(mRawResultQueue, mRawImageReader);
        }

    };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events for the preview and
     * pre-capture sequence.
     */
    private CameraCaptureSession.CaptureCallback mPreCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            synchronized (mCameraStateLock) {
                switch (mState) {
                    case STATE_PREVIEW: {
                        // We have nothing to do when the camera preview is running normally.
                        break;
                    }
                    case STATE_WAITING_FOR_3A_CONVERGENCE: {
                        boolean readyToCapture = true;
                        if (!mNoAFRun) {
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == null) {
                                break;
                            }

                            // 如果自动对焦已达到锁定状态，我们准备好进行捕捉
                            if(!isLock){
                                readyToCapture =
                                        (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                            }
                        }

                        // 如果我们在非旧版设备上运行，则还应该等到自动曝光和自动白平衡收敛后再拍照。
                        if (!isLegacyLocked() && !isLock) {
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
                            if (aeState == null || awbState == null) {
                                break;
                            }

                            readyToCapture = readyToCapture &&
                                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                                    awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED;
                        }

                        // 如果我们尚未完成捕获前的序列，但达到了最大等待超时，那就太糟糕了！ 无论如何都开始捕获。
                        if (!readyToCapture && hitTimeoutLocked()) {
                            Log.w(TAG, "Timed out waiting for pre-capture sequence to complete.");
                            readyToCapture = true;
                        }

                        if (readyToCapture && mPendingUserCaptures > 0) {
                            // 用户点击按钮捕获一次
                            while (mPendingUserCaptures > 0) {
                                captureStillPictureLocked();
                                mPendingUserCaptures--;
                            }
                            // 此后，相机将返回到预览的正常状态。
                            mState = STATE_PREVIEW;
                        }
                    }
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            process(result);
        }

    };
    private String getFrameNumString(){          //帧编号整形转字符串
        String count = Integer.toString(photoCount);
        String str = "";
        if(count.length() == 1){
            str = "0000" + count;
        }
        else if(count.length() == 2){
            str = "000" + count;
        }
        else if(count.length() == 3){
            str = "00" + count;
        }
        else if(count.length() == 4){
            str = "0" + count;
        }
        else if(count.length() == 5){
            str = count;
        }
        return str;
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles the still JPEG and RAW capture
     * request.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                     long timestamp, long frameNumber) {

            String currentFrame = getFrameNumString();
            int requestId = (int) request.getTag();
            if(saveJPEG){
                File jpegFile = new File(savePath + "/JPEG_" + currentFrame + ".jpg");
                // Look up the ImageSaverBuilder for this request and update it with the file name
                // based on the capture start time.
                ImageSaver.ImageSaverBuilder jpegBuilder;
                synchronized (mCameraStateLock) {
                    jpegBuilder = mJpegResultQueue.get(requestId);
                }
                if (jpegBuilder != null) jpegBuilder.setFile(jpegFile);
            }
            if (saveRAW || saveDng){
                File rawFile;
                if(saveDng)
                    rawFile = new File(savePath + "/DNG_" + currentFrame + ".dng");
                else
                    rawFile = new File(savePath + "/RAW_" + currentFrame + ".raw" + Integer.toString(saveBitDepth));
                // Look up the ImageSaverBuilder for this request and update it with the file name
                // based on the capture start time.
                ImageSaver.ImageSaverBuilder rawBuilder;
                synchronized (mCameraStateLock) {
                    rawBuilder = mRawResultQueue.get(requestId);
                }
                if (rawBuilder != null) rawBuilder.setFile(rawFile);
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            //isSaveFinished = true;
            int requestId = (int) request.getTag();
            if(saveJPEG){
                ImageSaver.ImageSaverBuilder jpegBuilder;
                // Look up the ImageSaverBuilder for this request and update it with the CaptureResult
                synchronized (mCameraStateLock) {
                    jpegBuilder = mJpegResultQueue.get(requestId);
                    if (jpegBuilder != null) {
                        jpegBuilder.setResult(result);
                        /*sb.append("Saving JPEG as: ");
                        sb.append(jpegBuilder.getSaveLocation());*/
                    }
                    // If we have all the results necessary, save the image to a file in the background.
                    handleCompletionLocked(requestId, jpegBuilder, mJpegResultQueue);
                }
            }
            if(saveRAW || saveDng){
                ImageSaver.ImageSaverBuilder rawBuilder;
                // Look up the ImageSaverBuilder for this request and update it with the CaptureResult
                synchronized (mCameraStateLock) {
                    rawBuilder = mRawResultQueue.get(requestId);
                    if (rawBuilder != null) {
                        rawBuilder.setResult(result);
                        /*if(saveRAW)
                            sb.append("Saving RAW as: ");
                        if(saveDng)
                            sb.append("Saving DNG as: ");
                        sb.append(rawBuilder.getSaveLocation());*/
                    }
                    // If we have all the results necessary, save the image to a file in the background.
                    handleCompletionLocked(requestId, rawBuilder, mRawResultQueue);
                    //Log.e(TAG,"handleCompletionLocked in onCaptureCompleted");          //second
                }
            }
            finishedCaptureLocked();
            //showToast(sb.toString());
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                    CaptureFailure failure) {
            int requestId = (int) request.getTag();
            if(saveJPEG){
                synchronized (mCameraStateLock) {
                    mJpegResultQueue.remove(requestId);

                }
            }
            if (saveRAW || saveDng){
                synchronized (mCameraStateLock) {
                    mRawResultQueue.remove(requestId);

                }
            }
            finishedCaptureLocked();
            showToast("Capture failed! Error Code: "+failure.getReason());  //0表示在框架中出错
            // 1表示由于 CameraCaptureSession.abortCaptures的执行而产生的错误
        }
    };

    /**
     * A {@link Handler} for showing {@link Toast}s on the UI thread.
     */
    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getActivity();
            if (activity != null) {
                Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
            }
        }
    };

    public static Camera2RawFragment newInstance() {
        return new Camera2RawFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        view.findViewById(R.id.iso_button_plus).setOnClickListener(this);
        view.findViewById(R.id.iso_button_sub).setOnClickListener(this);
        view.findViewById(R.id.wb_button).setOnClickListener(this);
        view.findViewById(R.id.interval_button_plus).setOnClickListener(this);
        view.findViewById(R.id.interval_button_sub).setOnClickListener(this);
        view.findViewById(R.id.menu_btn).setOnClickListener(this);
        StartAndStopButton = (Button) view.findViewById(R.id.picture);
        AutoOrManual = (ImageButton) view.findViewById(R.id.info);
        iso = (TextView)view.findViewById(R.id.iso_text);
        speed = (TextView)view.findViewById(R.id.speed_text);
        wb = (TextView)view.findViewById(R.id.wb_text);
        intervalText = (TextView)view.findViewById(R.id.interval_text);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        photoNum = (TextView)view.findViewById(R.id.text_pNum);
        time_I = (TextView)view.findViewById(R.id.text_time);
        sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
        availableBytesSize = sf.getAvailableBytes();
        t_format = (TextView)view.findViewById(R.id.text_format);
        t_size = (TextView)view.findViewById(R.id.text_size);
        whitebalance_btn = (ImageButton)view.findViewById(R.id.wb_button) ;
        t_mode = (TextView)view.findViewById(R.id.text_mode);
        t_bitDepth = (TextView)view.findViewById(R.id.text_depth);

        view.findViewById(R.id.texture).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(isLock){
                    switch (event.getAction()){
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_DOWN:
                            lastX = (int)event.getX();
                            lastY = (int)event.getY();
                            //Log.d(TAG,Integer.toString(posX)+","+Integer.toString(posY));
                            break;
                        case MotionEvent.ACTION_MOVE:
                            posX = (int)event.getX();
                            posY = (int)event.getY();
                            if(posY < 500){
                            /*if((posX - lastX) > 5 && speed_now_index > 0)
                                speed_now_index--;
                            else if((posX - lastX) < -5 && speed_now_index < SPEED.length-1)
                                speed_now_index++;
                            runOnUiThread.UpdateText(R.id.speed_text,SPEED_STRING[speed_now_index]);*/
                                if((posX - lastX) > 8 && SPEED < 1000000000L) {     //小于1s
                                    if(SPEED>10000000L)
                                        SPEED = SPEED + 1000000L;
                                    else
                                        SPEED = SPEED + 50000L;
                                }
                                else if ((posX - lastX) < -8 && SPEED > 250000L) {  //大于1/40000s
                                    if(SPEED>10000000L)
                                        SPEED = SPEED - 1000000L;
                                    else
                                        SPEED = SPEED - 50000L;
                                }
                                runOnUiThread.UpdateText(R.id.speed_text,LongToString(SPEED));
                                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,SPEED);
                            }else {
                                if((posX - lastX) > 1 && nowFocusDistant > 0.0f)
                                    nowFocusDistant -= 0.07f;
                                else if((posX - lastX) < -1 && nowFocusDistant < 10.0f)
                                    nowFocusDistant += 0.07f;
                                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,nowFocusDistant);
                            }
                            Log.d(TAG,"speed "+Long.toString(SPEED));
                            Log.d(TAG,"Distant "+Float.toString(nowFocusDistant));
                            lastX = posX;
                            lastY = posY;
                            try {
                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                                        mBackgroundHandler);
                                //mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
                                //Log.d(TAG,Float.toString(nowFocusDistant));
                            }catch (CameraAccessException e){
                                e.printStackTrace();
                            }
                            break;
                        default:
                            break;
                    }
                }
                return true;
            }
        });

        // Setup a new OrientationEventListener.  This is used to handle rotation events like a
        // 180 degree rotation that do not normally trigger a call to onCreate to do view re-layout
        // or otherwise cause the preview TextureView's size to change.
        mOrientationListener = new OrientationEventListener(getActivity(),
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (mTextureView != null && mTextureView.isAvailable()) {
                    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        shoot = false;
        closeCamera();
        stopBackgroundThread();
        super.onDestroy();
    }

    @Override
    public void onStart() {

        super.onStart();
    }

    @Override
    public void onResume() {
        if(!savePath.equals("/storage/emulated/0/DCIM")){
            if(DocumentsUtils.checkWritableRootPath(MyApplication.getContext(),savePath)){
                showOpenDocumentTree();
            }
        }
        //runOnUiThread.UpdateText(R.id.text_time,Integer.toString(interval));
        File file = new File("/storage/self/primary/DCIM/FrameCount.dat");
        if(!file.exists()){
            DataOutputStream out = null;
            try{
                out = new DataOutputStream(new FileOutputStream(file));
                out.writeInt(0);
                photoCount = 0;
                Log.d(TAG,"创建帧编号记录文件成功");          //初次使用创建记录文件
            }catch (IOException e){
                e.printStackTrace();
            }finally {
                try{
                    if(out != null)
                        out.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        DataInputStream in = null;
        try{
            in = new DataInputStream(new FileInputStream(file));
            photoCount = in.readInt();
            Log.d(TAG,"读取到帧编号"+Integer.toString(photoCount));                 //活动可见时读取张数
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            if(in != null){
                try{
                    in.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        file = null;

        SharedPreferences pref = getActivity().getSharedPreferences("menudata",MODE_PRIVATE);
        interval = pref.getInt("IntervalTime",1);
        format_now = pref.getInt("format",1);       //默认RAW
        saveBitDepth = pref.getInt("RawBitDepth",16);  //位深
        if(format_now == 0){
            saveJPEG = true;
            saveRAW = false;
            saveDng = false;
            runOnUiThread.UpdateText(R.id.text_format,"Save JPEG");
            runOnUiThread.UpdateText(R.id.text_depth,"8bit");
        }
        else if(format_now == 1){
            saveJPEG = false;
            saveRAW = true;
            saveDng = false;
            runOnUiThread.UpdateText(R.id.text_format,"Save RAW");
            if(saveBitDepth == 16)
                runOnUiThread.UpdateText(R.id.text_depth,"16bit");
            else if(saveBitDepth == 12)
                runOnUiThread.UpdateText(R.id.text_depth,"12bit");
            else if(saveBitDepth == 10)
                runOnUiThread.UpdateText(R.id.text_depth,"10bit");
            else if(saveBitDepth == 8)
                runOnUiThread.UpdateText(R.id.text_depth,"8bit");
        }
        else if(format_now == 2){
            saveJPEG = true;
            saveRAW = false;
            saveDng = true;
            runOnUiThread.UpdateText(R.id.text_format,"Save JPEG+DNG");
            runOnUiThread.UpdateText(R.id.text_depth,"8bit + 16bit");
        }
        else if(format_now == 3){
            saveJPEG = false;
            saveRAW = false;
            saveDng = true;
            runOnUiThread.UpdateText(R.id.text_format,"Save DNG");
            runOnUiThread.UpdateText(R.id.text_depth,"16bit");
        }

        size_now = pref.getInt("size",0);
        //path_now = pref.getInt("path",0);         //内部存储
        isExtremeMode = pref.getBoolean("extremeMode",false);
        if(isExtremeMode){
            runOnUiThread.UpdateText(R.id.text_mode,"Extreme Mode");
            runOnUiThread.UpdateText(R.id.interval_text,"极限模式");
        }
        else{
            runOnUiThread.UpdateText(R.id.text_mode,"Timelaps Mode");
            runOnUiThread.UpdateText(R.id.text_time,Integer.toString(interval));
            runOnUiThread.UpdateText(R.id.interval_text,"间隔"+Integer.toString(interval)+"s");
        }
        rawSizeIndex = pref.getInt("rawSizeArrayIndex",0);
        if(realPixelDepth == 0 && pref.getInt("realDepth",0) != 0)
            realPixelDepth = pref.getInt("realDepth",0);

        pref = null;
        Log.i(TAG,"onResume format="+format_now+"  size="+size_now);

        startBackgroundThread();
        openCamera();
        if (mTextureView.isAvailable()) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        shoot = false;
        File file_old = new File("/storage/self/primary/DCIM/FrameCount.dat");
        File file_new = new File("/storage/self/primary/DCIM/FrameCount.dat");
        DataOutputStream out = null;
        try{
            file_old.delete();  //删除旧文件
            file_old = null;
            out = new DataOutputStream(new FileOutputStream(file_new));
            out.writeInt(photoCount);
            Log.d(TAG,"写入帧编号"+Integer.toString(photoCount));
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try{
                file_new = null;
                if(out != null)
                    out.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        getActivity().finish();
        super.onPause();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showMissingPermissionError();
                    return;
                }
            }
            //onResume();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                if(System.currentTimeMillis() - lastButtonClickTime > 2000L){         //两次点击时间差超过2秒
                    lastButtonClickTime = System.currentTimeMillis();
                    final Activity activity = getActivity();
                    if(shoot && activity != null){
                        shoot = false;
                        StartAndStopButton.setText("START");
                        runOnUiThread.UpdateText(R.id.text_time,Integer.toString(interval));
                        Log.d(TAG,"停止服务意图");
                    }
                    else if(!shoot && activity != null){
                        shoot = true;
                        StartAndStopButton.setText("STOP");
                        startTimelaps();
                        if(!isExtremeMode)
                            countDown();
                        Log.d(TAG,"启动服务意图");
                    }
                }
                break;
            }
            case R.id.info: {
                if(System.currentTimeMillis() - lastButtonClickTime > 2000L){
                    lastButtonClickTime = System.currentTimeMillis();
                    Activity activity = getActivity();
                    if (null != activity ) {
                        if(!isLock){
                            AutoOrManual.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_a));
                            isLock = true;
                            showToast("手动模式");
                            runOnUiThread.UpdateText(R.id.iso_text,Integer.toString(ISO[iso_now_index]));
                            runOnUiThread.UpdateText(R.id.wb_text,WB[wb_now]);
                            runOnUiThread.UpdateText(R.id.speed_text,LongToString(SPEED));
                            createCameraPreviewSessionLocked();
                            try{
                                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, SPEED);    //单位:纳秒ns
                                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO[iso_now_index]);   //ISO2000
                                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,nowFocusDistant);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, wb_now);  //CONTROL_AWB_MODE_OFF
                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                                        mBackgroundHandler);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                        else{
                            AutoOrManual.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_m));
                            isLock = false;
                            showToast("自动模式");
                            runOnUiThread.UpdateText(R.id.iso_text,"");
                            runOnUiThread.UpdateText(R.id.wb_text,"");
                            runOnUiThread.UpdateText(R.id.speed_text,"");
                            createCameraPreviewSessionLocked();
                        }
                    }
                }
                break;
            }
            case R.id.iso_button_plus:{
                if(isLock){
                    if(iso_now_index < (ISO.length-1)){
                        iso_now_index++;
                        runOnUiThread.UpdateText(R.id.iso_text,Integer.toString(ISO[iso_now_index]));
                    }
                    try{
                        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO[iso_now_index]);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                                mBackgroundHandler);
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }
                break;
            }
            case R.id.iso_button_sub:{
                if(isLock)
                {
                    if(iso_now_index > 0){
                        iso_now_index--;
                        runOnUiThread.UpdateText(R.id.iso_text,Integer.toString(ISO[iso_now_index]));
                    }
                    try{
                        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO[iso_now_index]);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                                mBackgroundHandler);
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }
                break;
            }
            case R.id.wb_button:{
                if(isLock){
                    if(wb_now == 8)
                        wb_now = 1;
                    else
                        wb_now++;
                    runOnUiThread.UpdateText(R.id.wb_text,WB[wb_now]);
                    if(wb_now == 1)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_auto_button));
                    else if(wb_now == 2)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_lamp_button));
                    else if(wb_now == 3)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_fluorescent_button));
                    else if(wb_now == 4)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_warm_fluorescent_button));
                    else if(wb_now == 5)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_sun_button));
                    else if(wb_now == 6)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_cloud_button));
                    else if(wb_now == 7)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_sunset_button));
                    else if(wb_now == 8)
                        whitebalance_btn.setImageDrawable(getResources().getDrawable(R.drawable.ic_btn_capture_whitebalance_overcoat_button));
                    try{
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,wb_now);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                                mBackgroundHandler);
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }
                break;
            }
            case R.id.interval_button_plus: {
                if(!shoot && !isExtremeMode)
                    interval++;
                String s = "间隔" + Integer.toString(interval) + "s";
                runOnUiThread.UpdateText(R.id.interval_text, s);
                runOnUiThread.UpdateText(R.id.text_time, Integer.toString(interval));
                break;
            }
            case R.id.interval_button_sub: {
                if (interval > 1 && !shoot && !isExtremeMode)
                    interval--;
                String s = "间隔" + Integer.toString(interval) + "s";
                runOnUiThread.UpdateText(R.id.interval_text, s);
                runOnUiThread.UpdateText(R.id.text_time, Integer.toString(interval));
                break;
            }
            case R.id.menu_btn:{
                if(System.currentTimeMillis() - lastButtonClickTime > 2000L){
                    lastButtonClickTime = System.currentTimeMillis();
                    shoot = false;
                    Activity activity = getActivity();
                    Intent intent = new Intent(activity,Manu.class);
                    if(rawSizeRange != null && JPEGSize != null){
                        intent.putExtra("rawSizeArray",rawSizeRange);
                        intent.putExtra("realPixelDepth",realPixelDepth);
                        intent.putExtra("availableJPEGSize",JPEGSize);
                        intent.putExtra("Interval",interval);
                        startActivityForResult(intent,1);
                    }
                }
                break;
            }
            default:
                break;
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            /*case 1:
                if(resultCode == RESULT_OK){
                    int index = data.getIntExtra("data_return",1);
                    if(index == 0){
                        saveJPEG = true;
                        saveRAW = false;
                        showToast("saveJPEG");
                    }
                    else if(index == 1){
                        saveJPEG = false;
                        saveRAW = true;
                        showToast("saveRAW");
                    }
                    else if (index == 2){
                        saveRAW = true;
                        saveJPEG = true;
                        showToast("saveJPEG+DNG");
                    }
                    else if (index == 3){
                        saveRAW = true;
                        saveJPEG = false;
                        showToast("saveDNG");
                    }
                    setPath(data.getStringExtra("path_name"));
                    //showToast(savePath);
                }*/
            case DocumentsUtils.OPEN_DOCUMENT_TREE_CODE:
                if (data != null && data.getData() != null){
                    Uri uri = data.getData();
                    DocumentsUtils.saveTreeUri(MyApplication.getContext(),savePath,uri);
                    Log.d(TAG,"saveTreeUrl");
                }
                break;
            default:
                break;
        }
        if(!isLock){
            showToast("自动模式");
            runOnUiThread.UpdateText(R.id.iso_text,"");
            runOnUiThread.UpdateText(R.id.wb_text,"");
            runOnUiThread.UpdateText(R.id.speed_text,"");
        }
        else {
            showToast("手动模式");
            runOnUiThread.UpdateText(R.id.iso_text,Integer.toString(ISO[iso_now_index]));
            runOnUiThread.UpdateText(R.id.wb_text,WB[wb_now]);
            runOnUiThread.UpdateText(R.id.speed_text,LongToString(SPEED));
        }
    }
    class RunOnUiThread extends AppCompatActivity{
        private void UpdateText(final int Id,final String str){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (Id){
                        case R.id.iso_text:
                            iso.setText(str);
                            break;
                        case R.id.wb_text:
                            wb.setText(str);
                            break;
                        case R.id.speed_text:
                            speed.setText(str);
                            break;
                        case R.id.interval_text:
                            intervalText.setText(str);
                            break;
                        case R.id.text_pNum:
                            photoNum.setText("剩余拍摄张数"+str);
                            break;
                        case R.id.text_time:
                            if (isExtremeMode)
                                time_I.setText(str+"fps");
                            else
                                time_I.setText(str+"s");
                            break;
                        case R.id.text_format:
                            t_format.setText(str);
                            break;
                        case R.id.text_size:
                            t_size.setText(str);
                            break;
                        case R.id.text_mode:
                            t_mode.setText(str);
                            break;
                        case R.id.text_depth:
                            t_bitDepth.setText(str);
                            break;
                        default:
                            break;
                    }
                }
            });
        }
    }

    /**
     * Sets up state related to camera that is needed before opening a {@link CameraDevice}.
     */
    private boolean setUpCameraOutputs() {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            ErrorDialog.buildErrorDialog("This device doesn't support Camera2 API.").
                    show(getFragmentManager(), "dialog");
            return false;
        }
        try {
            // Find a CameraDevice that supports RAW captures, and configure state.
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We only use a camera that supports RAW in this sample.
                if (!contains(characteristics.get(
                                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size largestRaw = Collections.min(
                        Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                        new CompareSizesByArea());
                Size[] availableSize = map.getOutputSizes(ImageFormat.JPEG);

                synchronized (mCameraStateLock){
                    jpegSize = availableSize[size_now];
                    JPEGSize = new String[availableSize.length];
                    for(int i = 0; i < JPEGSize.length; i++){
                        JPEGSize[i] = Integer.toString(availableSize[i].getWidth()) + "*" + Integer.toString(availableSize[i].getHeight());
                        //Log.e(TAG,JPEGSize[i]);
                    }
                    maxRawSize = largestRaw;
                    int maxWidth = largestRaw.getWidth();
                    int tmp_width = maxWidth;
                    int i = 0,minPix = 0;
                    if((maxWidth > 1920 && maxWidth < 2100) || (maxWidth > 3840 && maxWidth < 4200) || (maxWidth > 7680 && maxWidth < 8400) || (maxWidth > 15360 && maxWidth < 16800))
                        minPix = 1920;
                    else
                        minPix = 1000;
                    while (tmp_width / 2 > minPix){
                        i++;
                        tmp_width = tmp_width / 2;
                    }
                    if (minPix == 1920)
                        i = i + 2;
                    else
                        i = i + 1;
                    rawSizeRange = new int[i];
                    rawSizeRange[0] = maxWidth;
                    tmp_width = maxWidth;
                    i = 1;
                    while (tmp_width / 2 > minPix){
                        rawSizeRange[i] = tmp_width / 2;
                        tmp_width = tmp_width / 2;
                        i++;
                    }
                    if(minPix == 1920)
                        rawSizeRange[i] = 1920;

                    if(saveJPEG){
                    /*Size largestJpeg = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new CompareSizesByArea());*/

                            // Set up ImageReaders for JPEG and RAW outputs.  Place these in a reference
                            // counted wrapper to ensure they are only closed when all background tasks
                            // using them are finished.
                        if (mJpegImageReader == null || mJpegImageReader.getAndRetain() == null) {
                            mJpegImageReader = new RefCountedAutoCloseable<>(
                                    ImageReader.newInstance(jpegSize.getWidth(),
                                            jpegSize.getHeight(), ImageFormat.JPEG, 5));
                        }
                        mJpegImageReader.get().setOnImageAvailableListener(mOnJpegImageAvailableListener, mBackgroundHandler);
                    }
                    if (saveRAW || saveDng){
                        if (mRawImageReader == null || mRawImageReader.getAndRetain() == null) {
                            mRawImageReader = new RefCountedAutoCloseable<>(
                                    ImageReader.newInstance(largestRaw.getWidth(),
                                            largestRaw.getHeight(), ImageFormat.RAW_SENSOR,  5));
                        }
                        mRawImageReader.get().setOnImageAvailableListener(mOnRawImageAvailableListener, mBackgroundHandler);
                    }
                    mCharacteristics = characteristics;
                    mCameraId = cameraId;
                }
                if(format_now == 0){   //JPEG
                    jpegSize = availableSize[size_now];
                    runOnUiThread.UpdateText(R.id.text_size,JPEGSize[size_now]);
                    photoBitSizeNow = (int)(jpegSize.getWidth() * jpegSize.getHeight() * 0.75);
                }
                else if (format_now == 2){   //JPEG+DNG
                    jpegSize = availableSize[size_now];
                    runOnUiThread.UpdateText(R.id.text_size,JPEGSize[size_now]);
                    photoBitSizeNow = (int)(jpegSize.getWidth() * jpegSize.getHeight() * 0.75 + largestRaw.getWidth() * largestRaw.getHeight() * 2);
                }
                else if (format_now == 1){    //RAW
                    if(rawSizeIndex == rawSizeRange.length - 1 && rawSizeRange[rawSizeIndex] == 1920) {     //支持1920的才显示
                        runOnUiThread.UpdateText(R.id.text_size, "1920*1080");      //更新尺寸在UI界面
                        photoBitSizeNow = (int)(1920 * 1080 * saveBitDepth / 8);
                    }
                    else{
                        runOnUiThread.UpdateText(R.id.text_size,Integer.toString(rawSizeRange[rawSizeIndex])
                                +"*"+Integer.toString(rawSizeRange[rawSizeIndex]/4*3));      //更新尺寸在UI界面
                        photoBitSizeNow = (int)(rawSizeRange[rawSizeIndex] * (rawSizeRange[rawSizeIndex]/4*3) * saveBitDepth / 8);
                    }
                }
                else {     //DNG
                    runOnUiThread.UpdateText(R.id.text_size,Integer.toString(largestRaw.getWidth())
                            +"*"+Integer.toString(largestRaw.getHeight()));      //更新尺寸在UI界面
                    photoBitSizeNow = largestRaw.getWidth() * largestRaw.getHeight() * 2;
                }
                sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
                availableBytesSize = sf.getAvailableBytes();
                runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/ photoBitSizeNow));
                return true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // If we found no suitable cameras for capturing RAW, warn the user.
        ErrorDialog.buildErrorDialog("This device doesn't support capturing RAW photos").
                show(getFragmentManager(), "dialog");
        return false;
    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera() {
        if (!setUpCameraOutputs()) {
            return;
        }
        if (!hasAllPermissionsGranted()) {
            requestCameraPermissions();
            return;
        }

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Wait for any previously running session to finish.
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String cameraId;
            Handler backgroundHandler;
            synchronized (mCameraStateLock) {
                cameraId = mCameraId;
                backgroundHandler = mBackgroundHandler;
            }

            // Attempt to open the camera. mStateCallback will be called on the background handler's
            // thread when this succeeds or fails.
            manager.openCamera(cameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Requests permissions necessary to use camera and save pictures.
     */
    private void requestCameraPermissions() {
        if (shouldShowRationale()) {
            PermissionConfirmationDialog.newInstance().show(getChildFragmentManager(), "dialog");
        } else {
            FragmentCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS);
        }
    }

    /**
     * Tells whether all the necessary permissions are granted to this app.
     *
     * @return True if all the required permissions are granted.
     */
    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets whether you should show UI with rationale for requesting the permissions.
     *
     * @return True if the UI should be shown.
     */
    private boolean shouldShowRationale() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shows that this app really needs the permission and finishes the app.
     */
    private void showMissingPermissionError() {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, R.string.request_permission, Toast.LENGTH_SHORT).show();
            activity.finish();   //--------------------------------修改
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {

                // Reset state and clean up resources used by the camera.
                // Note: After calling this, the ImageReaders will be closed after any background
                // tasks saving Images from these readers have been completed.
                mPendingUserCaptures = 0;
                mState = STATE_CLOSED;
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (null != mJpegImageReader) {
                    mJpegImageReader.close();
                    mJpegImageReader = null;
                }
                if (null != mRawImageReader) {
                    mRawImageReader.close();
                    mRawImageReader = null;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        synchronized (mCameraStateLock) {
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            synchronized (mCameraStateLock) {
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void createCameraPreviewSessionLocked() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);  //TEMPLATE_PREVIEW
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            if(saveJPEG && saveDng){
                mCameraDevice.createCaptureSession(Arrays.asList(surface,mRawImageReader.get().getSurface(),
                        mJpegImageReader.get().getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                synchronized (mCameraStateLock) {
                                    // The camera is already closed
                                    if (null == mCameraDevice) {
                                        return;
                                    }
                                    try {
                                        setup3AControlsLocked(mPreviewRequestBuilder);
                                        // Finally, we start displaying the camera preview.
                                        cameraCaptureSession.setRepeatingRequest(
                                                mPreviewRequestBuilder.build(),
                                                mPreCaptureCallback, mBackgroundHandler);
                                        mState = STATE_PREVIEW;
                                    } catch (CameraAccessException | IllegalStateException e) {
                                        e.printStackTrace();
                                        return;
                                    }
                                    // When the session is ready, we start displaying the preview.
                                    mCaptureSession = cameraCaptureSession;
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                                showToast("Failed to configure camera.");
                            }
                        }, mBackgroundHandler
                );
            }
            else if (saveRAW || saveDng){
                mCameraDevice.createCaptureSession(Arrays.asList(surface,
                        mRawImageReader.get().getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                synchronized (mCameraStateLock) {
                                    // The camera is already closed
                                    if (null == mCameraDevice) {
                                        return;
                                    }
                                    try {
                                        setup3AControlsLocked(mPreviewRequestBuilder);
                                        // Finally, we start displaying the camera preview.
                                        cameraCaptureSession.setRepeatingRequest(
                                                mPreviewRequestBuilder.build(),
                                                mPreCaptureCallback, mBackgroundHandler);
                                        mState = STATE_PREVIEW;
                                    } catch (CameraAccessException | IllegalStateException e) {
                                        e.printStackTrace();
                                        return;
                                    }
                                    // When the session is ready, we start displaying the preview.
                                    mCaptureSession = cameraCaptureSession;
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                                showToast("Failed to configure camera.");
                            }
                        }, mBackgroundHandler
                );
            }
            else if (saveJPEG){
                mCameraDevice.createCaptureSession(Arrays.asList(surface,
                        mJpegImageReader.get().getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                synchronized (mCameraStateLock) {
                                    // The camera is already closed
                                    if (null == mCameraDevice) {
                                        return;
                                    }
                                    try {
                                        setup3AControlsLocked(mPreviewRequestBuilder);
                                        // Finally, we start displaying the camera preview.
                                        cameraCaptureSession.setRepeatingRequest(
                                                mPreviewRequestBuilder.build(),
                                                mPreCaptureCallback, mBackgroundHandler);
                                        mState = STATE_PREVIEW;
                                    } catch (CameraAccessException | IllegalStateException e) {
                                        e.printStackTrace();
                                        return;
                                    }
                                    // When the session is ready, we start displaying the preview.
                                    mCaptureSession = cameraCaptureSession;
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                                showToast("Failed to configure camera.");
                            }
                        }, mBackgroundHandler
                );
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configure the given {@link CaptureRequest.Builder} to use auto-focus, auto-exposure, and
     * auto-white-balance controls if available.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param builder the builder to configure.
     */
    private void setup3AControlsLocked(CaptureRequest.Builder builder) {

        Float minFocusDist =
                mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);  //LENS_INFO_MINIMUM_FOCUS_DISTANCE
        //Log.d(TAG,Float.toString(minFocusDist));      //最小对焦距离10.0mm

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        mNoAFRun = (minFocusDist == null || minFocusDist == 0);

        /*if (!mNoAFRun) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            if (contains(mCharacteristics.get(
                            CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {  //CONTINUOUS_PICTURE
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);  //CONTINUOUS_PICTURE
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_AUTO);   //AUTO
            }
        }*/
        if(isLock){
            if (contains(mCharacteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_OFF)) {  //CONTINUOUS_PICTURE
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);  //CONTINUOUS_PICTURE
                //builder.set(CaptureRequest.LENS_FOCUS_DISTANCE,minFocusDist);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);   //AUTO
                //builder.set(CaptureRequest.LENS_FOCUS_DISTANCE,minFocusDist);
            }
        }
        else{
            if (contains(mCharacteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {  //CONTINUOUS_PICTURE
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);  //CONTINUOUS_PICTURE
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_AUTO);   //AUTO
            }
        }

        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        if(isLock){
            if (contains(mCharacteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AE_MODE_OFF)) {
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF);
                /*builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                        SPEED[speed_now_index]);    //单位:纳秒ns
                builder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO[iso_now_index]);   //ISO2000*/
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE,  //CaptureRequest.CONTROL_AE_MODE
                        CaptureRequest.CONTROL_AE_MODE_OFF);  //CaptureRequest.CONTROL_AE_MODE_OFF
                /*builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                        SPEED[speed_now_index]);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO[iso_now_index]);*/
            }
            //Range<Long> range = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            //Log.d(TAG,range.toString());
        }
        else{
            if (contains(mCharacteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AE_MODE_ON)) { //CONTROL_AE_MODE_ON_AUTO_FLASH)
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);  //CONTROL_AE_MODE_ON_AUTO_FLASH)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);  //CONTROL_AE_MODE_ON
            }
        }

        // If there is an auto-magical white balance control mode available, use it.
        /*if(isLock){
            if (contains(mCharacteristics.get(
                    CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AWB_MODE_OFF)) {
                // Allow AWB to run auto-magically if this device supports this
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);  //CONTROL_AWB_MODE_OFF
            }
        }else{
            if (contains(mCharacteristics.get(
                    CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
                // Allow AWB to run auto-magically if this device supports this
                builder.set(CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_AUTO);
            }
        }*/
        if (contains(mCharacteristics.get(
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            // Allow AWB to run auto-magically if this device supports this
            builder.set(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
        /*builder.set(CaptureRequest.SENSOR_SENSITIVITY,2000);
        Float minFocusDist = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE,minFocusDist);
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,100000L);*/
    }

    /**
     * Configure the necessary {@link android.graphics.Matrix} transformation to `mTextureView`,
     * and start/restart the preview capture session if necessary.
     * <p/>
     * This method should be called after the camera state has been initialized in
     * setUpCameraOutputs.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        synchronized (mCameraStateLock) {
            if (null == mTextureView || null == activity) {
                return;
            }

            StreamConfigurationMap map = mCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // For still image captures, we always use the largest available size.
            Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new CompareSizesByArea());

            // Find the rotation of the device relative to the native device orientation.
            int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

            // Find the rotation of the device relative to the camera sensor's orientation.
            int totalRotation = sensorToDeviceRotation(mCharacteristics, deviceRotation);

            // Swap the view dimensions for calculation as needed if they are rotated relative to
            // the sensor.
            boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;
            int rotatedViewWidth = viewWidth;
            int rotatedViewHeight = viewHeight;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedViewWidth = viewHeight;
                rotatedViewHeight = viewWidth;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            // Preview should not be larger than display size and 1080p.
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Find the best preview size for these view dimensions and configured JPEG size.
            Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedViewWidth, rotatedViewHeight, maxPreviewWidth, maxPreviewHeight,
                    largestJpeg);

            if (swappedDimensions) {
                mTextureView.setAspectRatio(
                        previewSize.getHeight(), previewSize.getWidth());
            } else {
                mTextureView.setAspectRatio(
                        previewSize.getWidth(), previewSize.getHeight());
            }

            // Find rotation of device in degrees (reverse device orientation for front-facing
            // cameras).
            int rotation = (mCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT) ?
                    (360 + ORIENTATIONS.get(deviceRotation)) % 360 :
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360;

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            // Initially, output stream images from the Camera2 API will be rotated to the native
            // device orientation from the sensor's orientation, and the TextureView will default to
            // scaling these buffers to fill it's view bounds.  If the aspect ratios and relative
            // orientations are correct, this is fine.
            //
            // However, if the device orientation has been rotated relative to its native
            // orientation so that the TextureView's dimensions are swapped relative to the
            // native device orientation, we must do the following to ensure the output stream
            // images are not incorrectly scaled by the TextureView:
            //   - Undo the scale-to-fill from the output buffer's dimensions (i.e. its dimensions
            //     in the native device orientation) to the TextureView's dimension.
            //   - Apply a scale-to-fill from the output buffer's rotated dimensions
            //     (i.e. its dimensions in the current device orientation) to the TextureView's
            //     dimensions.
            //   - Apply the rotation from the native device orientation to the current device
            //     rotation.
            if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.getHeight(),
                        (float) viewWidth / previewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);

            }
            matrix.postRotate(rotation, centerX, centerY);

            mTextureView.setTransform(matrix);

            // Start or restart the active capture session if the preview was initialized or
            // if its aspect ratio changed significantly.
            if (mPreviewSize == null || !checkAspectsEqual(previewSize, mPreviewSize)) {
                mPreviewSize = previewSize;
                if (mState != STATE_CLOSED) {
                    createCameraPreviewSessionLocked();
                }
            }
        }
    }

    /**
     * Initiate a still image capture.
     * <p/>
     * This function sends a capture request that initiates a pre-capture sequence in our state
     * machine that waits for auto-focus to finish, ending in a "locked" state where the lens is no
     * longer moving, waits for auto-exposure to choose a good exposure value, and waits for
     * auto-white-balance to converge.
     */
    public void takePicture() {
        isTakePhoto = true;
        if(photoCount != 99999){
            dataNow = new Date();
            time_of_once_shoot = BigInteger.valueOf(dataNow.getTime());
            Log.i(TAG,"触发时间 "+time_of_once_shoot.toString()+"ms");
            time_remove = time_of_once_shoot;
            int t = (time_of_once_shoot.subtract(time_tmp)).intValue();       //毫秒值
            BigDecimal bg = new BigDecimal(1000.0/t);
            Log.i(TAG,"每次拍摄的时间差 = "+Integer.toString(t)+"ms");
            if (isExtremeMode)
                runOnUiThread.UpdateText(R.id.text_time, Double.toString(bg.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue()));
            time_tmp = time_of_once_shoot;
            photoCount = photoCount + 1;
        }
        else
            photoCount = 0;

        sf = new StatFs(getContext().getCacheDir().getAbsolutePath());
        availableBytesSize = sf.getAvailableBytes();
        runOnUiThread.UpdateText(R.id.text_pNum,Long.toString(availableBytesSize/ photoBitSizeNow));

        synchronized (mCameraStateLock) {
            mPendingUserCaptures++;

            // 如果我们已经触发了捕获前的序列，或者处于无法执行捕获前的状态，请立即返回。
            if (mState != STATE_PREVIEW) {
                return;
            }
            try {
                // 如果具备摄像头功能，则触发自动对焦运行。 如果照相机已经对焦，则什么也不做。
                if (!mNoAFRun && !isLock) {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_START);
                }

                // If this is not a legacy device, we can also trigger an auto-exposure metering
                // run.
                if (!isLegacyLocked() && !isLock) {
                    // Tell the camera to lock focus.
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                }

                // 更新状态机以等待自动对焦，自动曝光和自动白平衡（也称为“ 3A”）收敛。
                mState = STATE_WAITING_FOR_3A_CONVERGENCE;

                // 为预捕获序列启动一个计时器，用于检测是否超时
                startTimerLocked();

                // 用更新的3A触发器替换现有的重复请求。
                if(!isLock)
                    mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                            mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a capture request to the camera device that initiates a capture targeting the JPEG and
     * RAW outputs.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void captureStillPictureLocked() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            if(saveJPEG){
                captureBuilder.addTarget(mJpegImageReader.get().getSurface());
                // Set orientation.
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                        sensorToDeviceRotation(mCharacteristics, rotation));
                // Create an ImageSaverBuilder in which to collect results, and add it to the queue
                // of active requests.
            }
            if(saveRAW || saveDng){
                captureBuilder.addTarget(mRawImageReader.get().getSurface());
                // Create an ImageSaverBuilder in which to collect results, and add it to the queue
                // of active requests.
            }
            setup3AControlsLocked(captureBuilder);         // Use the same AE and AF modes as the preview.
            captureBuilder.setTag(mRequestCounter.getAndIncrement());      // 设置请求标记以轻松跟踪回调中的结果。
            CaptureRequest request = captureBuilder.build();
            if(saveJPEG){
                ImageSaver.ImageSaverBuilder jpegBuilder = new ImageSaver.ImageSaverBuilder(activity)
                        .setCharacteristics(mCharacteristics);
                mJpegResultQueue.put((int) request.getTag(), jpegBuilder);
            }
            if(saveRAW || saveDng){
                ImageSaver.ImageSaverBuilder rawBuilder = new ImageSaver.ImageSaverBuilder(activity)
                        .setCharacteristics(mCharacteristics);
                mRawResultQueue.put((int) request.getTag(), rawBuilder);
            }
            mCaptureSession.capture(request, mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called after a RAW/JPEG capture has completed; resets the AF trigger state for the
     * pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void finishedCaptureLocked() {
        try {
            // Reset the auto-focus trigger in case AF didn't run quickly enough.
            if (!mNoAFRun && !isLock) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                        mBackgroundHandler);

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve the next {@link Image} from a reference counted {@link ImageReader}, retaining
     * that {@link ImageReader} until that {@link Image} is no longer in use, and set this
     * {@link Image} as the result for the next request in the queue of pending requests.  If
     * all necessary information is available, begin saving the image to a file in a background
     * thread.
     *
     * @param pendingQueue the currently active requests.
     * @param reader       a reference counted wrapper containing an {@link ImageReader} from which
     *                     to acquire an image.
     */
    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
                                     RefCountedAutoCloseable<ImageReader> reader) {
        synchronized (mCameraStateLock) {
            Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry =
                    pendingQueue.firstEntry();
            ImageSaver.ImageSaverBuilder builder = entry.getValue();     //entry.getValue()

            // 增量引用计数，以防止在后台线程中保存图像时关闭ImageReader(否则在写入文件时可能释放其资源)。
            if (reader == null || reader.getAndRetain() == null) {
                Log.e(TAG, "Paused the activity before we could save the image," +
                        " ImageReader already closed.");
                pendingQueue.remove(entry.getKey());
                return;
            }

            Image image;
            try {
                image = reader.get().acquireNextImage();
            } catch (IllegalStateException e) {         //排队等待保存的图像过多，需要删除图像
                Log.e(TAG, "Too many images queued for saving, dropping image for request: " +
                        entry.getKey());
                showToast("拍摄间隔太短 Shot interval is too short!");
                pendingQueue.remove(entry.getKey());
                return;
            }
            builder.setRefCountedReader(reader).setImage(image);            //给ImageSaver存储图片
            handleCompletionLocked(entry.getKey(), builder, pendingQueue);
            //Log.e(TAG,"handleCompletionLocked in dequeueAndSaveImage");        //first
        }
    }

    /**
     * Runnable that saves an {@link Image} into the specified {@link File}, and updates
     * {@link android.provider.MediaStore} to include the resulting file.
     * <p/>
     * This can be constructed through an {@link ImageSaverBuilder} as the necessary image and
     * result information becomes available.
     */
    private static class ImageSaver extends AppCompatActivity implements Runnable {

        /**
         * The image to save.
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        /**
         * The CaptureResult for this image capture.
         */
        private final CaptureResult mCaptureResult;

        /**
         * The CameraCharacteristics for this camera device.
         */
        private final CameraCharacteristics mCharacteristics;

        /**
         * The Context to use when updating MediaStore with the saved images.
         */
        private final Context mContext;

        /**
         * A reference counted wrapper for the ImageReader that owns the given image.
         */
        private final RefCountedAutoCloseable<ImageReader> mReader;

        private ImageSaver(Image image, File file, CaptureResult result,
                           CameraCharacteristics characteristics, Context context,
                           RefCountedAutoCloseable<ImageReader> reader) {
            mImage = image;
            mFile = file;
            mCaptureResult = result;
            mCharacteristics = characteristics;
            mContext = context;
            mReader = reader;
        }

        @Override
        public void run() {
            Date dataNow = new Date();
            //Log.i(TAG,"保存图片前时间 "+Long.toString(dataNow.getTime()));
            boolean success = false;
            int format = mImage.getFormat();
            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    if(savePath.equals("/storage/emulated/0/DCIM")){
                        FileOutputStream output = null;
                        try {
                            output = new FileOutputStream(mFile);
                            output.write(bytes);
                            success = true;
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            mImage.close();
                            buffer.clear();
                            closeOutput(output);
                        }
                    }
                    else {
                        OutputStream outputStream = null;
                        try{
                            outputStream = DocumentsUtils.getOutputStream(MyApplication.getContext(),mFile);
                            outputStream.write(bytes);
                            success = true;
                        }catch (IOException e){
                            e.printStackTrace();
                        }finally {
                            mImage.close();
                            buffer.clear();
                            closeOutput(outputStream);
                        }
                    }
                    break;
                }
                /*case ImageFormat.RAW_SENSOR: {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    byte[] bytes_low = new byte[bytes.length/4];
                    buffer.get(bytes);
                    for(int x = 0,i = 0;i < bytes_low.length;x += 8,i += 4){
                        if(x != 0 && x % 15872 == 0 && (x / 15872) % 2 == 1) {
                            if (x + 15872 < 23617536)
                                x += 15872;
                            else
                                break;
                        }
                        bytes_low[i] = bytes[x];
                        bytes_low[i+1] = bytes[x+1];
                        bytes_low[i+2] = bytes[x+2];
                        bytes_low[i+3] = bytes[x+3];
                    }
                    //Log.d(TAG,"Array Length="+Integer.toString(bytes.length));   23617536
                    if(savePath.equals("/storage/emulated/0/DCIM")){
                        FileOutputStream output = null;
                        try {
                            output = new FileOutputStream(mFile);
                            output.write(bytes_low);
                            success = true;
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            mImage.close();
                            closeOutput(output);
                        }
                    }
                    else {
                        OutputStream outputStream = null;
                        try{
                            outputStream = DocumentsUtils.getOutputStream(MyApplication.getContext(),mFile);
                            outputStream.write(bytes_low);
                            success = true;
                        }catch (IOException e){
                            e.printStackTrace();
                        }finally {
                            mImage.close();
                            closeOutput(outputStream);
                        }
                    }
                    break;
                }*/
                case ImageFormat.RAW_SENSOR: {
                    //isSaveDngFile = null;
                    if(saveDng){                         //保存DNG文件
                        DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                        /*  以下输出相机和捕获图像的一些信息和元数据         */
                        //Size s = new Size(3968,2976);
                        //SizeF sf = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    /*BlackLevelPattern blackLevelPattern = mCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
                    ColorSpaceTransform calibrationTransform1 = mCharacteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1);
                    ColorSpaceTransform calibrationTransform2 = mCharacteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1);
                    ColorSpaceTransform forwardMatrix1 = mCharacteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
                    ColorSpaceTransform forwardMatrix2 = mCharacteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
                    Integer filterArrangement = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
                    Integer whiteLevel = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
                    Integer referenceIlluminant1 = mCharacteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
                    Integer referenceIlluminant2 = mCharacteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
                    Log.i(TAG,"CameraCharacteristics:  blackLevelPattern = "+blackLevelPattern.toString()+"\n"
                            +"calibrationTransform1 = "+calibrationTransform1.toString()+"\n"
                            +"calibrationTransform2 = "+calibrationTransform2.toString()+"\n"
                            +"forwardMatrix1 = "+forwardMatrix1.toString()+"\n"
                            +"forwardMatrix2 = "+forwardMatrix2.toString()+"\n"
                            +"filterArrangement = "+filterArrangement.toString()+"\n"
                            +"whiteLevel = "+whiteLevel.toString()+"\n"
                            +"referenceIlluminant1 = "+referenceIlluminant1.toString()+"\n"
                            +"referenceIlluminant2 = "+referenceIlluminant2.toString()+"\n");
                    Integer testPatternMode = mCaptureResult.get(CaptureResult.SENSOR_TEST_PATTERN_MODE);
                    Integer dynamicWhiteLevel = mCaptureResult.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
                    Float greenSplit = mCaptureResult.get(CaptureResult.SENSOR_GREEN_SPLIT);
                    TonemapCurve tonemapCurve = mCaptureResult.get(CaptureResult.TONEMAP_CURVE);
                    Float toneMapGamma = mCaptureResult.get(CaptureResult.TONEMAP_GAMMA);
                    Integer toneMode = mCaptureResult.get(CaptureResult.TONEMAP_MODE);
                    Integer tonePresetCurve = mCaptureResult.get(CaptureResult.TONEMAP_PRESET_CURVE);
                    Rational[] neutralColorPoint = mCaptureResult.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
                    if(testPatternMode == null)
                        testPatternMode = new Integer(-1);
                    if(dynamicWhiteLevel == null)
                        dynamicWhiteLevel = new Integer(-1);
                    if(greenSplit == null)
                        greenSplit = new Float(-1.0f);
                    if(toneMapGamma == null)
                        toneMapGamma = new Float(-1.0f);
                    if(toneMode == null)
                        toneMode = new Integer(-1);
                    if(tonePresetCurve == null)
                        tonePresetCurve = new Integer(-1);

                    Log.i(TAG,"neutralColorPoint = [["+Integer.toString(neutralColorPoint[0].getNumerator())+", "
                            +Integer.toString(neutralColorPoint[0].getDenominator())+"], ["
                            +Integer.toString(neutralColorPoint[1].getNumerator())+", "
                            +Integer.toString(neutralColorPoint[1].getDenominator())+"], ["
                            +Integer.toString(neutralColorPoint[2].getNumerator())+", "
                            +Integer.toString(neutralColorPoint[2].getDenominator())+"]]\n");

                    Log.i(TAG,"testPatternMode = "+testPatternMode.toString()+"\n"
                            +"dynamicWhiteLevel = "+dynamicWhiteLevel.toString()+"\n"
                            +"greenSplit = "+greenSplit.toString()+"\n"
                            //+"tonemapCurve = "+tonemapCurve.toString()+"\n"
                            +"toneMapGamma = "+toneMapGamma.toString()+"\n"
                            +"toneMode = "+toneMode.toString()+"\n"
                            +"tonePresetCurve = "+tonePresetCurve.toString()+"\n");*/
                        //Size s = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                        //Rect r = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                        //Log.d(TAG,"Size="+s.toString()+"  SizeF="+sf.toString()+"  Rect="+r.toString());
                        if(savePath.equals("/storage/emulated/0/DCIM")){
                            FileOutputStream output = null;
                            try {
                                output = new FileOutputStream(mFile);
                                dngCreator.writeImage(output, mImage);
                                success = true;
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                mImage.close();
                                closeOutput(output);
                                dngCreator.close();
                            }
                        }else {
                            OutputStream outputStream = null;
                            try {
                                outputStream = DocumentsUtils.getOutputStream(MyApplication.getContext(),mFile);
                                dngCreator.writeImage(outputStream, mImage);
                                success = true;
                            }catch (IOException e){
                                e.printStackTrace();
                            }finally {
                                mImage.close();
                                closeOutput(outputStream);
                                dngCreator.close();
                            }
                        }
                    }
                    else{              //保存RAW文件
                        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        /*byte[] bytes_low = new byte[bytes.length/4];
                        for(int x = 0,i = 0;i < bytes_low.length;x += 8,i += 4){      //宽高的像素都减少一半，变成1984*1488
                            if(x != 0 && x % 15872 == 0 && (x / 15872) % 2 == 1) {
                                if (x + 15872 < 23617536)
                                    x += 15872;
                                else
                                    break;
                            }
                            bytes_low[i] = bytes[x];
                            bytes_low[i+1] = bytes[x+1];
                            bytes_low[i+2] = bytes[x+2];
                            bytes_low[i+3] = bytes[x+3];
                        }*/
                        buffer.clear();
                        buffer = null;
                        byte[] bytes_toSave = null;
                        if(realPixelDepth == 0)
                            realPixelDepth = DetectEffectiveDepth(bytes);
                        if(rawSizeIndex == 0)
                            bytes_toSave = bytes;
                        else if(rawSizeIndex == rawSizeRange.length-1 && rawSizeRange[rawSizeIndex] == 1920){
                            if(maxRawSize.getWidth() / rawSizeRange[rawSizeIndex-1] == 2)
                                bytes_toSave = ReducePixelsTo1920_1080(maxRawSize.getWidth()/2,maxRawSize.getHeight()/2,
                                        ReduceHalfPixels(maxRawSize.getWidth(),maxRawSize.getHeight(),bytes));
                            else if(maxRawSize.getWidth() / rawSizeRange[rawSizeIndex-1] == 4)
                                bytes_toSave = ReducePixelsTo1920_1080(maxRawSize.getWidth()/4,maxRawSize.getHeight()/4,
                                        ReduceHalfPixels(maxRawSize.getWidth()/2,maxRawSize.getHeight()/2,
                                                ReduceHalfPixels(maxRawSize.getWidth(),maxRawSize.getHeight(),bytes)));
                            else if(maxRawSize.getWidth() / rawSizeRange[rawSizeIndex-1] == 8)
                                bytes_toSave = ReducePixelsTo1920_1080(maxRawSize.getWidth()/8,maxRawSize.getHeight()/8,
                                        ReduceHalfPixels(maxRawSize.getWidth()/4,maxRawSize.getHeight()/4,
                                                ReduceHalfPixels(maxRawSize.getWidth()/2,maxRawSize.getHeight()/2,
                                                        ReduceHalfPixels(maxRawSize.getWidth(),maxRawSize.getHeight(),bytes))));
                        }
                        else {
                            if(maxRawSize.getWidth() / rawSizeRange[rawSizeIndex] == 2)
                                bytes_toSave = ReduceHalfPixels(maxRawSize.getWidth(),maxRawSize.getHeight(),bytes);
                            else if(maxRawSize.getWidth() / rawSizeRange[rawSizeIndex] == 4)
                                bytes_toSave = ReduceHalfPixels(maxRawSize.getWidth()/2,maxRawSize.getHeight()/2,
                                        ReduceHalfPixels(maxRawSize.getWidth(),maxRawSize.getHeight(),bytes));
                            else if(maxRawSize.getWidth() / rawSizeRange[rawSizeIndex] == 8)
                                bytes_toSave = ReduceHalfPixels(maxRawSize.getWidth()/4,maxRawSize.getHeight()/4,
                                        ReduceHalfPixels(maxRawSize.getWidth()/2,maxRawSize.getHeight()/2,
                                                ReduceHalfPixels(maxRawSize.getWidth(),maxRawSize.getHeight(),bytes)));
                        }
                        if(saveBitDepth == 8)
                            bytes_toSave = SwitchTo8Bit(bytes_toSave);
                        else if(saveBitDepth == 10)
                            bytes_toSave = SwitchTo10Bit(bytes_toSave);
                        else if(saveBitDepth == 12)
                            bytes_toSave = SwitchTo12Bit(bytes_toSave);
                        //bytes_toSave = SwitchTo14Bit(bytes_toSave);
                        bytes = null;
                        /*int i = 0;
                        for(int y = 204;y < 1284;y++){         //用于像素减少，从1984*1488像素减少到1920*1080像素，减少存储压力
                            for(int x = 32;x < 1952;x++){
                                if(i < bytes_1920_1080.length){
                                    bytes_1920_1080[i] = bytes_low[2 * (1984 * y + x)];         //一个像素两字节赋值
                                    bytes_1920_1080[i+1] = bytes_low[2 * (1984 * y + x) + 1];
                                    i += 2;
                                }
                            }
                        }
                        bytes_low = null;*/
                        dataNow = new Date();
                        BigInteger t0 = BigInteger.valueOf(dataNow.getTime());
                        BigInteger t1 = t0;
                        //Log.d(TAG,t.toString());

                        /* 以下保存raw文件  */
                        if(savePath.equals("/storage/emulated/0/DCIM")){
                            FileOutputStream output = null;
                            try {
                                output = new FileOutputStream(mFile);
                                output.write(bytes_toSave);
                                success = true;
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                mImage.close();
                                closeOutput(output);
                                //buffer.clear();
                                bytes_toSave = null;
                                dataNow = new Date();
                                t1 = BigInteger.valueOf(dataNow.getTime());
                                Log.d(TAG,"Save Time = "+(t1.subtract(t0).toString())+"ms");
                                //Log.e(TAG,mFile.getAbsolutePath()+"  "+t1.toString()+"ms");
                                t0 = null;
                                t1 = null;
                                dataNow = null;
                            }
                        }else {
                            OutputStream outputStream = null;
                            try {
                                outputStream = DocumentsUtils.getOutputStream(MyApplication.getContext(),mFile);
                                outputStream.write(bytes_toSave);
                                success = true;
                            }catch (IOException e){
                                e.printStackTrace();
                            }finally {
                                mImage.close();
                                closeOutput(outputStream);
                                //buffer.clear();
                                bytes_toSave = null;
                                dataNow = new Date();
                                t1 = BigInteger.valueOf(dataNow.getTime());
                                Log.d(TAG,"Save Time = "+(t1.subtract(t0).toString())+"ms");
                                //Log.e(TAG,mFile.getAbsolutePath()+"  "+t1.toString()+"ms");
                                t0 = null;
                                t1 = null;
                                dataNow = null;
                            }
                        }
                    }
                    break;
                }
                default: {
                    Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                    break;
                }
            }
            // 减少引用计数以允许关闭ImageReader以释放资源
            mReader.close();

            // If saving the file succeeded, update MediaStore.
            if (success) {
                MediaScannerConnection.scanFile(mContext, new String[]{mFile.getPath()},
                /*mimeTypes*/null, new MediaScannerConnection.MediaScannerConnectionClient() {
                    @Override
                    public void onMediaScannerConnected() {
                        // Do nothing
                    }

                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Scanned " + path + ":" + "-> uri=" + uri);
                        //Log.i(TAG, "-> uri=" + uri);
                    }
                });
            }
        }

        /**
         * Builder class for constructing {@link ImageSaver}s.
         * <p/>
         * This class is thread safe.
         */
        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private Context mContext;
            private RefCountedAutoCloseable<ImageReader> mReader;

            /**
             * Construct a new ImageSaverBuilder using the given {@link Context}.
             *
             * @param context a {@link Context} to for accessing the
             *                {@link android.provider.MediaStore}.
             */
            public ImageSaverBuilder(final Context context) {
                mContext = context;
            }

            public synchronized ImageSaverBuilder setRefCountedReader(
                    RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();

                mReader = reader;
                return this;
            }

            public synchronized ImageSaverBuilder setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
                return this;
            }

            public synchronized ImageSaverBuilder setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
                return this;
            }

            public synchronized ImageSaverBuilder setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
                return this;
            }

            public synchronized ImageSaverBuilder setCharacteristics(
                    final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mImage, mFile, mCaptureResult, mCharacteristics, mContext,
                        mReader);
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            private boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null
                        && mCharacteristics != null;
            }
        }
    }

    // Utility classes and methods:
    // *********************************************************************************************

    /**
     * Comparator based on area of the given {@link Size} objects.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * A dialog fragment for displaying non-recoverable errors; this {@ling Activity} will be
     * finished once the dialog has been acknowledged by the user.
     */
    public static class ErrorDialog extends DialogFragment {

        private String mErrorMessage;

        public ErrorDialog() {
            mErrorMessage = "Unknown error occurred!";
        }

        // Build a dialog with a custom message (Fragments require default constructor).
        public static ErrorDialog buildErrorDialog(String errorMessage) {
            ErrorDialog dialog = new ErrorDialog();
            dialog.mErrorMessage = errorMessage;
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(mErrorMessage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * A wrapper for an {@link AutoCloseable} object that implements reference counting to allow
     * for resource management.
     */
    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        /**
         * Wrap the given object.
         *
         * @param object an object to wrap.
         */
        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        /**
         * Return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T get() {
            return mObject;
        }

        /**
         * Decrement the reference count and release the wrapped object if there are no other
         * users retaining this object.
         */
        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
            int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                    option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Generate a string containing a formatted timestamp with the current date and time.
     *
     * @return a {@link String} representing a time.
     */
    private static String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * Cleanup the given {@link OutputStream}.
     *
     * @param outputStream the stream to close.
     */
    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void closeInput(InputStream inputStream) {
        if (null != inputStream) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return true if the given array contains the given integer.
     *
     * @param modes array to check.
     * @param mode  integer to get for.
     * @return true if the array contains the given integer, otherwise false.
     */
    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the two given {@link Size}s have the same aspect ratio.
     *
     * @param a first {@link Size} to compare.
     * @param b second {@link Size} to compare.
     * @return true if the sizes have the same aspect ratio, otherwise false.
     */
    private static boolean checkAspectsEqual(Size a, Size b) {
        double aAspect = a.getWidth() / (double) a.getHeight();
        double bAspect = b.getWidth() / (double) b.getHeight();
        return Math.abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE;
    }

    /**
     * Rotation need to transform from the camera sensor orientation to the device's current
     * orientation.
     *
     * @param c                 the {@link CameraCharacteristics} to query for the camera sensor
     *                          orientation.
     * @param deviceOrientation the current device orientation relative to the native device
     *                          orientation.
     * @return the total rotation from the sensor orientation to the current device orientation.
     */
    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation - deviceOrientation + 360) % 360;
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show.
     */
    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    /**
     * If the given request has been completed, remove it from the queue of active requests and
     * send an {@link ImageSaver} with the results from this request to a background thread to
     * save a file.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param requestId the ID of the {@link CaptureRequest} to handle.
     * @param builder   the {@link ImageSaver.ImageSaverBuilder} for this request.
     * @param queue     the queue to remove this request from, if completed.
     */
    private void handleCompletionLocked(int requestId, ImageSaver.ImageSaverBuilder builder,
                                        TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null){
            //Log.e(TAG,"builder == null");
            return;
        }
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            queue.remove(requestId);
            //Log.i(TAG,"从队列上移除当前请求");
            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);        //移除ImageSaver
            isSaveFinished = true;
            dataNow = new Date();
            Log.i(TAG,"从触发takePic到移除ImageSaver所用时间 = "
                    +(BigInteger.valueOf(dataNow.getTime()).subtract(time_remove)).toString()+"ms");
        }
        /*else
            Log.e(TAG,"saver == null");*/
    }

    /**
     * Check if we are using a device that only supports the LEGACY hardware level.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if this is a legacy device.
     */
    private boolean isLegacyLocked() {
        return mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    /**
     * Start the timer for the pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the timer for the pre-capture sequence has been hit.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if the timeout occurred.
     */
    private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;  //大于1000ms
    }

    /**
     * A dialog that explains about the necessary permissions.
     */
    public static class PermissionConfirmationDialog extends DialogFragment {

        public static PermissionConfirmationDialog newInstance() {
            return new PermissionConfirmationDialog();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, CAMERA_PERMISSIONS,
                                    REQUEST_CAMERA_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getActivity().finish();
                                }
                            })
                    .create();
        }

    }

    private static byte[] ReduceHalfPixels(int width,int height,byte[] bytes_source){
        int twoRowByteSize = width * 4;
        int totalByteSize = width * height * 2;
        byte[] bytes_result = new byte[bytes_source.length/4];
        for(int x = 0,i = 0;i < bytes_source.length;x += 8,i += 4){      //宽高的像素都减少一半
            if(x != 0 && x % twoRowByteSize == 0 && (x / twoRowByteSize) % 2 == 1) {
                if (x + twoRowByteSize < totalByteSize)
                    x += twoRowByteSize;
                else
                    break;
            }
            bytes_result[i] = bytes_source[x];
            bytes_result[i+1] = bytes_source[x+1];
            bytes_result[i+2] = bytes_source[x+2];
            bytes_result[i+3] = bytes_source[x+3];
        }
        return bytes_result;
    }

    private static byte[] ReducePixelsTo1920_1080(int width,int height,byte[] bytes_source){
        byte[] bytes_1920_1080 = new byte[1920*1080*2];
        int i = 0, UnAndDownMargin = (height - 1080)/2, LeftAndRightMargin = (width - 1920)/2;
        for(int y = UnAndDownMargin;y < (1080 + UnAndDownMargin);y++){         //用于像素减少，从1984*1488像素减少到1920*1080像素，减少存储压力
            for(int x = LeftAndRightMargin;x < (LeftAndRightMargin + 1920);x++){
                if(i < bytes_1920_1080.length){
                    bytes_1920_1080[i] = bytes_source[2 * (width * y + x)];         //一个像素两字节赋值
                    bytes_1920_1080[i+1] = bytes_source[2 * (width * y + x) + 1];
                    i += 2;
                }
            }
        }
        /*for(i = 0;i < 20; i = i + 2){
            int tmp = (bytes_1920_1080[i] & 0xFF) | (bytes_1920_1080[i+1] & 0xFF) << 8;
            Log.e(TAG,Integer.toString(tmp));       //真正有效位深10位
        }*/
        return bytes_1920_1080;
    }

    private static byte[] SwitchTo8Bit(byte[] bytes_source_16){
        byte[] bytes_8_bit = new byte[bytes_source_16.length / 2];
        for(int i = 0; i < bytes_8_bit.length; i++){
            int tmp = (bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8;
            if(realPixelDepth == 16)
                tmp = tmp / 256;
            else if(realPixelDepth == 14)
                tmp = tmp / 64;
            else if(realPixelDepth == 12)
                tmp = tmp / 16;
            else if(realPixelDepth == 10)
                tmp = tmp / 4;
            bytes_8_bit[i] = (byte)tmp;       //(byte)tmp
        }
        return bytes_8_bit;
    }

    private static byte[] SwitchTo10Bit(byte[] bytes_source_16){
        int size = (int)(bytes_source_16.length * 0.625);
        byte[] bytes_10_bit = new byte[size];
        int[] tmp_int = new int[bytes_source_16.length / 2];
        for(int i = 0; i < tmp_int.length; i++){
            if(realPixelDepth == 16)
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8) / 64;
            else if(realPixelDepth == 14)
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8) / 16;
            else if(realPixelDepth == 12)
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8) / 4;
            else
                tmp_int[i] = (bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8;
        }
        int j = 0, c = 0;
        for(int i = 0; i < bytes_10_bit.length; i++){
            if(c == 0)
                bytes_10_bit[i] = (byte)tmp_int[j];   //低8
            else if (c == 1)
                bytes_10_bit[i] = (byte)((tmp_int[j-1] & 0x300) >> 2 | (tmp_int[j] & 0x3F));  //高2低6
            else if (c == 2)
                bytes_10_bit[i] = (byte)((tmp_int[j-1] & 0x3C0) >> 2 | (tmp_int[j] & 0x0F));  //高4低4
            else if (c == 3)
                bytes_10_bit[i] = (byte)((tmp_int[j-1] & 0x3F0) >> 2 | (tmp_int[j] & 0x03));  //高6低2
            else if (c == 4)
                bytes_10_bit[i] = (byte)((tmp_int[j-1] & 0x3FC) >> 2);    //高8
            j++;
            c++;
            if(c == 5){
                c = 0;
                j--;
            }
        }
        tmp_int = null;
        return bytes_10_bit;
    }

    private static byte[] SwitchTo12Bit(byte[] bytes_source_16){
        int size = (int)(bytes_source_16.length * 0.75);
        byte[] bytes_12_bit = new byte[size];
        int[] tmp_int = new int[bytes_source_16.length / 2];
        for(int i = 0; i < tmp_int.length; i++){
            if(realPixelDepth == 16)
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8) / 16;
            else if(realPixelDepth == 14)
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8) / 4;
            else
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8);
        }
        int j = 0, c = 0;
        for(int i = 0; i < bytes_12_bit.length; i++){
            if(c == 0){
                bytes_12_bit[i] = (byte)tmp_int[j];   //低8位
                j++;
            }
            else if (c == 1)
                bytes_12_bit[i] = (byte)((tmp_int[j-1] & 0xF00) >> 4 | (tmp_int[j] & 0x00F));   //高4位低4位
            else if (c == 2){
                bytes_12_bit[i] = (byte)((tmp_int[j] & 0xFF0) >> 4);     //低8位
                j++;
            }
            c++;
            if(c == 3)
                c = 0;
        }
        tmp_int = null;
        return bytes_12_bit;
    }

    /*private static byte[] SwitchTo14Bit(byte[] bytes_source_16){
        int size = (int)(bytes_source_16.length * 0.875);
        byte[] bytes_14_bit = new byte[size];
        int[] tmp_int = new int[bytes_source_16.length / 2];
        for(int i = 0; i < tmp_int.length; i++){
            if(realPixelDepth == 16)
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8) / 4;
            else
                tmp_int[i] = ((bytes_source_16[2 * i] & 0xFF) | (bytes_source_16[2 * i + 1] & 0xFF) << 8);
        }
        int j = 0, c = 0;
        for(int i = 0; i < bytes_14_bit.length; i++){
            if(c == 0)
                bytes_14_bit[i] = (byte)(tmp_int[j] & 0x00FF);   //低8位
            else if (c == 1){
                bytes_14_bit[i] = (byte)((tmp_int[j] & 0x3F00) >> 6 | (tmp_int[j+1] & 0x0003));   //高6位低2位
                j++;
            }
            else if (c == 2)
                bytes_14_bit[i] = (byte)((tmp_int[j] & 0x03FC) >> 2);     //中8位
            else if (c == 3){
                bytes_14_bit[i] = (byte)((tmp_int[j] & 0x3C00) >> 6 | (tmp_int[j+1] & 0x000F));   //高4位低4位
                j++;
            }
            else if (c == 4)
                bytes_14_bit[i] = (byte)((tmp_int[j] & 0x03FC) >> 2);     //中8位
            else if (c == 5){
                bytes_14_bit[i] = (byte)((tmp_int[j] & 0x3000) >> 6 | (tmp_int[j+1] & 0x003F));   //高2位低6位
                j++;
            }
            else if (c == 6)
                bytes_14_bit[i] = (byte)((tmp_int[j] & 0x3FC0) >> 6);     //高8位
            c++;
            if(c == 7){
                c = 0;
                j++;
            }
        }
        tmp_int = null;
        return bytes_14_bit;
    }*/

    private static int DetectEffectiveDepth(byte[] bytes_check){
        int maxDepth = 0;
        for(int i = 0; i < bytes_check.length; i = i + 2){
            int tmp = (bytes_check[i] & 0xFF) | (bytes_check[i+1] & 0xFF) << 8;
            if(tmp > maxDepth)
                maxDepth = tmp;
        }
        if(maxDepth > 0 && maxDepth < 256)
            return 8;
        else if (maxDepth > 0 && maxDepth < 1024)
            return 10;
        else if (maxDepth > 0 && maxDepth < 4096)
            return 12;
        else if (maxDepth > 0 && maxDepth < 16384)
            return 14;
        else if (maxDepth > 0 && maxDepth < 65536)
            return 16;
        return 0;
    }
}

