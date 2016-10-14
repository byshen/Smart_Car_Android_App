package com.example.am.kc3c.master.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.am.kc3c.master.R;
import com.example.am.kc3c.master.menu.ActivityStandard;
import com.example.am.kc3c.master.util.Drawl;
import com.example.am.kc3c.master.util.MyApplication;
import com.example.am.kc3c.master.util.NetworkSingleton;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class RandomActivity extends ActivityStandard {

    private NetworkSingleton networkSingleton;

    private byte[][] videoYuvByteQueue = new byte[2][];
    private byte[] videoJpegFrame = null;
    private final Object videoYuvMutex = new Object();
    private final Object videoSurfaceMutex = new Object();
    private Thread videoCompressThread;
    private Thread viewInetFrameThread;
    private ByteArrayOutputStream byteArrayOutputStream;

    private Camera camera;
    private int cameraOrientationAngle = 90;
    private int cameraPreviewFPS = 50000; // fps*1000
    private int cameraPreviewWidth = 320;
    private int cameraPreviewHeight = 240;
    private CameraPreview cameraPreview;
    private SurfaceView inetCameraPreview;
    private SurfaceHolder surfaceHolder;
    private boolean isInetSurfaceViewChanged = true;

    private TextView textView_INFO;

    private BroadcastReceiver broadcastReceiver;

    ArrayList<Integer> routePointX;//czq_flag route_record
    ArrayList<Integer> routePointY;//czq_flag route_record
    int routelength=0;//czq_flag route_record

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);

        textView_INFO = (TextView)findViewById(R.id.textView_info_vedio5);

        networkSingleton = NetworkSingleton.getInstance();
        initBrocastReceiver();
        initCamera();
        networkSingleton.SendBeginVideoMessage(cameraOrientationAngle);
        initCompressThread();
        initSurfaceView();
        initViewInetFrameThread();
        routePointX=new ArrayList<Integer>();
        routePointY=new ArrayList<Integer>();
        final Drawl view=new Drawl(this);
        view.setMinimumHeight(500);
        view.setMinimumWidth(300);
        //通知view组件重绘
        view.invalidate();
        ((FrameLayout) findViewById(R.id.preview_frame5)).addView(view);

        timer = new Timer(true);
        timer.schedule(task, 1000, 500); //延时1000ms后执行，1000ms执行一次
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
        synchronized (videoYuvMutex) {
            videoYuvByteQueue[1] = data;
            videoYuvMutex.notify();
        }
        }
    };

    private void initBrocastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NetworkSingleton.ACTION_ONRECEIVE_VIDEO_BEGIN);
        intentFilter.addAction(NetworkSingleton.ACTION_ONRECEIVE_VIDEO_STOP);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NetworkSingleton.ACTION_ONRECEIVE_VIDEO_BEGIN:
                    break;
                case NetworkSingleton.ACTION_ONRECEIVE_VIDEO_STOP:
                    break;
            }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    private void initCamera() {
        camera = Camera.open(0);
        cameraOrientationAngle = setCameraDisplayOrientation(0, camera);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(cameraPreviewWidth, cameraPreviewHeight);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        // parameters.setPreviewFpsRange(cameraPreviewFPS, cameraPreviewFPS);
        camera.setParameters(parameters);
        // 需在stopPreview后，startPreview前设置有效
        // camera.setPreviewCallback(previewCallback);
    }

    private void initCompressThread() {
        videoYuvByteQueue[0] = videoYuvByteQueue[1] = null;
        byteArrayOutputStream = new ByteArrayOutputStream();

        videoCompressThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    synchronized (videoYuvMutex) {
                        // 防止永久wait
                        if (isInterrupted()) {
                            return;
                        }
                        if (videoYuvByteQueue[0] == videoYuvByteQueue[1] || videoYuvByteQueue[1] == null) {
                            try {
                                videoYuvMutex.wait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                        videoYuvByteQueue[0] = videoYuvByteQueue[1];
                    }
                    // compressing
                    YuvImage image = new YuvImage(videoYuvByteQueue[0], ImageFormat.NV21, cameraPreviewWidth, cameraPreviewHeight, null);
                    image.compressToJpeg(new Rect(0, 0, cameraPreviewWidth, cameraPreviewHeight), 80, byteArrayOutputStream);
                    videoJpegFrame = byteArrayOutputStream.toByteArray();
                    // sending
                    networkSingleton.SendVideo(videoJpegFrame);
                    byteArrayOutputStream.reset();
//                    testing
//                    MyApplication.setFrame(videoJpegFrame);
//                    MyApplication.setOrientation(90);
//                    inetCameraOrientationAngle = 90;
                }
            }
        };
        videoCompressThread.start();
    }

    private int setCameraDisplayOrientation(int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
        return result;
    }

    private void initViewInetFrameThread() {
        viewInetFrameThread = new Thread() {
            @Override
            public void run() {
//                测试用
//                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                byte[] oldframe=null, newframe=null;
                Matrix matrix = new Matrix();
                Canvas canvas;
                int inetCameraOrientation;
                while(!isInterrupted()) {
                    synchronized (videoSurfaceMutex) {
                        if (isInetSurfaceViewChanged) {
                            try {
                                videoSurfaceMutex.wait();
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                    newframe = MyApplication.getFrame();
                    if (newframe!=null && oldframe!=newframe) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(newframe, 0, newframe.length);
                        oldframe = newframe;
                        try {
                            canvas = surfaceHolder.lockCanvas();
//                Log.i("EEE", String.format("%d x %d", canvas.getWidth(), canvas.getHeight()));
//                            canvas.drawBitmap(bitmap, new Rect(0, 0, cameraPreviewWidth, cameraPreviewHeight), new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
                            matrix.reset();
                            inetCameraOrientation = MyApplication.getOrientation();
                            switch (inetCameraOrientation) {
                                case 270:
                                    matrix.postRotate(inetCameraOrientation);
                                    matrix.postTranslate(0, bitmap.getWidth());
                                    matrix.postScale(((float)canvas.getWidth())/bitmap.getHeight(), ((float)canvas.getHeight())/bitmap.getWidth());
                                    break;
                                case 180:
                                    matrix.postRotate(inetCameraOrientation, bitmap.getWidth()/2, bitmap.getHeight()/2);
                                    matrix.postScale(((float)canvas.getWidth())/bitmap.getWidth(), ((float)canvas.getHeight())/bitmap.getHeight());
                                    break;
                                case 90:
                                    matrix.postRotate(inetCameraOrientation);
                                    matrix.postTranslate(bitmap.getHeight(), 0);
                                    matrix.postScale(((float)canvas.getWidth())/bitmap.getHeight(), ((float)canvas.getHeight())/bitmap.getWidth());
                                    break;
                                case 0:
                                    matrix.postScale(((float)canvas.getWidth())/bitmap.getWidth(), ((float)canvas.getHeight())/bitmap.getHeight());
                                    break;
                            }
                            canvas.drawBitmap(bitmap, matrix, null);
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        } catch (Exception e) {
                            //
                        }
                    }
//                    try {
//                        sleep(20); // FPS: 50，但实际远达不到这个速度
//                    } catch (InterruptedException e) {
//                        break;
//                    }
                }
            }
        };
        viewInetFrameThread.start();
    }

    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.
            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }
            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }
            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.setPreviewCallback(previewCallback);
                mCamera.startPreview();
            } catch (Exception e){
                Log.d("CameraPreviewError", "Error starting camera preview: " + e.getMessage());
            }
        }
    }

    private void initSurfaceView() {
        int preview_width = 800;
        int preview_height = 1100;
        FrameLayout.LayoutParams tp_local = new FrameLayout.LayoutParams(preview_width, preview_height);//定义显示组件参数
        FrameLayout.LayoutParams tp_inet = new FrameLayout.LayoutParams(preview_width, preview_height, Gravity.RIGHT);//定义显示组件参数
        tp_local.rightMargin = 0;
        tp_local.topMargin = 0;
        FrameLayout frameLayout1 = new FrameLayout(this);
        FrameLayout frameLayout2 = new FrameLayout(this);

        ((FrameLayout) findViewById(R.id.preview_frame5)).addView(frameLayout1, tp_inet);
        ((FrameLayout) findViewById(R.id.preview_frame5)).addView(frameLayout2, tp_local);

        inetCameraPreview = new SurfaceView(this);
        cameraPreview = new CameraPreview(this, camera);
        frameLayout1.addView(inetCameraPreview);
        frameLayout2.addView(cameraPreview);

        surfaceHolder = inetCameraPreview.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                synchronized (videoSurfaceMutex) {
                    videoSurfaceMutex.notify();
                    isInetSurfaceViewChanged = false;
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                synchronized (videoSurfaceMutex) {
                    isInetSurfaceViewChanged = true;
                }
            }
        });

        ((FrameLayout) findViewById(R.id.preview_frame5)).bringChildToFront(frameLayout2);
    }

    private void sendMessage(String direction) {
        if (direction==null || networkSingleton==null)
            return;
        switch (direction) {
            case "LEFT":
                networkSingleton.SendControlMessage("L");
                break;
            case "RIGHT":
                networkSingleton.SendControlMessage("R");
                break;
            case "UP":
                networkSingleton.SendControlMessage("U");
                break;
            case "DOWN":
                networkSingleton.SendControlMessage("D");
                break;
            case "STOP":
                networkSingleton.SendControlMessage("S");
                break;
            case "LEV":
                networkSingleton.SendControlMessage("Z");
                break;
            case "DEX":
                networkSingleton.SendControlMessage("Y");
                break;
        }
    }

    /*
定时器
*/
    final Handler handler = new Handler(){
        public void handleMessage(Message msg) {
            if (msg.what==1) {
                if (timerAlive==1) run_car();
            }
            super.handleMessage(msg);
        }
    };

    TimerTask task = new TimerTask() {
        public void run() {
            Message message = new Message();
            message.what = 1;
            handler.sendMessage(message);
        }
    };

    /*
双击停止 & 启动路径
*/
    static int Rpointer=0;
    Timer timer;
    int timerAlive=0;

    private GestureDetector gestureDetector = new GestureDetector(null, new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            textView_INFO.setText("onDoubleStop");
            sendMessage("STOP");
            routePointX.clear();
            routePointY.clear();
            routelength=0;
            if (timerAlive==1) {
                timerAlive = 0;
            }
            return super.onDoubleTap(e);
        }
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // String info = String.format("\n\ne1: %s\n\ne2: %s\n\nvelocityX: %f\nvelocityY: %f", e1.toString(), e2.toString(), velocityX, velocityY);
            int minDistance = 100;
            int minVelocity = 750;
            textView_INFO.setText("LET'S GO!");
            Rpointer=0;
            if (timerAlive==0) {
                timerAlive=1;
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    });

    //控制
    static int posx=0;
    static int posy=0;
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        posx=(int)ev.getX()+100;
        posy=(int)ev.getY();
        if (Math.abs(posx-663)<80 && Math.abs(posy-1010)<80) {
            if (timerAlive==1) {
                timer.cancel(); //退出计时器
                timerAlive=0;
            }
            RandomActivity.this.finish();
            Intent intent = new Intent();
            Toast.makeText(getApplicationContext(), R.string.action_Control, Toast.LENGTH_SHORT).show();
            intent.setClass(this, ControlActivity.class);
            this.startActivity(intent);
        }
        gestureDetector.onTouchEvent(ev);
        routePointX.add((Integer)posx);
        routePointY.add((Integer)posy);
        ++routelength;
        return super.dispatchTouchEvent(ev);
    }

    public void run_car() {
        int posx=routePointX.get(Rpointer+1);
        int posy=routePointY.get(Rpointer+1);
        int startpointx=routePointX.get(Rpointer);
        int startpointy=routePointY.get(Rpointer);
        while (posx==startpointx && posy==startpointy) {
            ++Rpointer;
            if (Rpointer+1>=routelength) {
                textView_INFO.setText("STOP");
                sendMessage("STOP");
                if (timerAlive==1) {
                    timerAlive=0;
                }
                routePointX.clear();
                routePointY.clear();
                routelength=0;
                return;
            }
            posx=routePointX.get(Rpointer+1);
            posy=routePointY.get(Rpointer+1);
            startpointx=routePointX.get(Rpointer);
            startpointy=routePointY.get(Rpointer);
        }
        double tann=Math.atan2(posy-startpointy,posx-startpointx);
        if (tann<=2.7489 && tann>1.9635) {
            textView_INFO.setText("DOWN LEFT");
            sendMessage("LEFT");
        } else if (tann<=1.9635 && tann>1.1781) {
            textView_INFO.setText("DOWN");
            sendMessage("DOWN");
        } else if (tann<=1.1781 && tann>0.3927) {
            textView_INFO.setText("DOWN RIGHT");
            sendMessage("RIGHT");
        } else if (tann<=0.3927 && tann>-0.3927) {
            textView_INFO.setText("RIGHT");
            sendMessage("RIGHT");
        } else if (tann<=-0.3927 && tann>-1.1781) {
            textView_INFO.setText("UP RIGHT");
            sendMessage("RIGHT");
        } else if (tann<=-1.1781 && tann>-1.9635) {
            textView_INFO.setText("UP");
            sendMessage("UP");
        } else if (tann<=-1.9635 && tann>-2.7489) {
            textView_INFO.setText("UP LEFT");
            sendMessage("LEFT");
        } else {
            textView_INFO.setText("LEFT");
            sendMessage("LEFT");
        }
        ImageButton smallbutt=(ImageButton)findViewById(R.id.button_touch5);
        {
            smallbutt.setLeft(startpointx-100-60);
            smallbutt.setTop(startpointy-110-60);
            smallbutt.setRight(startpointx-100+60);
            smallbutt.setBottom(startpointy-110+60);
        }
        ++Rpointer;
        if (Rpointer+1>=routelength) {
            textView_INFO.setText("STOP");
            sendMessage("STOP");
            if (timerAlive==1) {
                timerAlive=0;
            }
            routePointX.clear();
            routePointY.clear();
            routelength=0;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        camera.setPreviewCallback(null);
        camera.stopPreview();
        if (camera!=null) {
            camera.release();
        }
        camera = null;

        synchronized (videoYuvMutex) {
            videoCompressThread.interrupt();
        }
        viewInetFrameThread.interrupt();

        networkSingleton.SendStopVideoMessage();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        this.finish();
    }
}
