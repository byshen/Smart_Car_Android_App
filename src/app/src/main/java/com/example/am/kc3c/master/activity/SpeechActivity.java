package com.example.am.kc3c.master.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.am.kc3c.master.R;
import com.example.am.kc3c.master.menu.ActivityStandard;
import com.example.am.kc3c.master.util.MyApplication;
import com.example.am.kc3c.master.util.NetworkSingleton;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.speech.util.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Timer;
import java.util.TimerTask;

public class SpeechActivity extends ActivityStandard {

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

    private RecognizerDialog mIatDialog;
    private String mLanguage = "mandarin";
    private TextView voice_iat;
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;   //startActivityForResult操作要求的标识码
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech);

        textView_INFO = (TextView)findViewById(R.id.textView_info_vedio3);
        //voice_iat = (TextView)findViewById(R.id.voice_iat);

        networkSingleton = NetworkSingleton.getInstance();
        initBrocastReceiver();
        initCamera();
        networkSingleton.SendBeginVideoMessage(cameraOrientationAngle);
        initCompressThread();
        initSurfaceView();
        initViewInetFrameThread();
        initVoiceControl();
    }

    private String[][] hint_chinese = new String[][] {
            {"前", "上"},
            {"后", "下"},
            {"左"},
            {"右"},
            {"停"},
            {"顺时针"/*, "右旋"*/}, // 与“左”、“右”冲突，无意义
            {"逆时针"/*, "左旋"*/},
    };
    private String[][] hint_english = new String[][] {
            {"up", "front", "straight", "ahead"},
            {"back", "down"},
            {"left"},
            {"right"},
            {"stop"},
            {"clockwise", "dextrorotation"},
            {"anticlockwise", "levorotation"},
    };
    private String[][] hint = new String[][] {
            {"后左", "下左","last"},
            {"后右", "下右","round"},
            {"up", "front", "straight", "ahead","前", "上"},
            {"back", "down","后", "下"},
            {"left","左","作","坐","做"},
            {"right","右","有","又"},
            {"stop","停"},
            {"clockwise", "dextrorotation","顺时针"},
            {"anticlockwise", "levorotation","逆时针"},
    };

    private void initVoiceControl() {
        mIatDialog = new RecognizerDialog(this, null);

        ((RadioGroup)findViewById(R.id.radiogroup_language)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.radio_mandarin:
                        mLanguage = "mandarin";
                        break;
                    case R.id.radio_cantonese:
                        mLanguage = "cantonese";
                        break;
                    case R.id.radio_english:
                        mLanguage = "english";
                        break;
                }
            }
        });
        mLanguage = "english";

        PackageManager pm = getPackageManager();
        ((ImageButton)findViewById(R.id.button_voice)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //voice_iat.setText("");
//                setVoiceParams();
//                mIatDialog.setListener(recognizerDialogListener);
//                mIatDialog.show();
                // TODO Auto-generated method stub
                try {
                    //通过Intent传递语音识别的模式，开启语音
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    //语言模式和自由模式的语音识别
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    //提示语音开始
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "开始语音");
                    //开始语音识别
                    startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
                } catch (Exception e) {
                    // TODO: handle exception
                    e.printStackTrace();
                    //Toast.makeText(getApplicationContext(), "找不到语音设备", 1).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        //回调获取从谷歌得到的数据
        if(requestCode==VOICE_RECOGNITION_REQUEST_CODE && resultCode==RESULT_OK){
            //取得语音的字符
            ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            String resultString="";
            for(int i=0;i<results.size();i++){
                resultString+=results.get(i);
            }
            //Toast.makeText(this, resultString, 1).show();
            resultString = results.toString().toLowerCase();
            //ppp(resultString);
            Boolean tmp_flag = Boolean.FALSE;
            int i = 0;
            for (;i < hint.length;i += 1){
                for (int j = 0;j < hint[i].length && !tmp_flag;++j){
                    if (resultString.indexOf(hint[i][j]) != -1){
                        tmp_flag = Boolean.TRUE;
                    }
                }
                if (tmp_flag) break;
            }
            ppp(resultString+"\t"+i);
            switch (i){
                case 1:
                    textView_INFO.setText("Voice: Left Back");
                    //imageView_INFO.setImageResource(R.mipmap.button_up);
                    sendMessage("BLEFT");
                    break;
                case 2:
                    textView_INFO.setText("Voice: Right Back");
                    //imageView_INFO.setImageResource(R.mipmap.button_up);
                    sendMessage("BRIGHT");
                    break;
                case 3:
                    textView_INFO.setText("Voice: Up");
                    //imageView_INFO.setImageResource(R.mipmap.button_up);
                    sendMessage("UP");
                    break;
                case 4:
                    textView_INFO.setText("Voice: Down");
                    //imageView_INFO.setImageResource(R.mipmap.button_down);
                    sendMessage("DOWN");
                    break;
                case 5:
                    textView_INFO.setText("Voice: Left");
                    //imageView_INFO.setImageResource(R.mipmap.button_left);
                    sendMessage("LEFT");
                    break;
                case 6:
                    textView_INFO.setText("Voice: Right");
                    //imageView_INFO.setImageResource(R.mipmap.button_right);
                    sendMessage("RIGHT");
                    break;
                case 7:
                    textView_INFO.setText("Voice: Stop");
                    //imageView_INFO.setImageResource(R.mipmap.button_stop);
                    sendMessage("STOP");
                case 8:
                    textView_INFO.setText("Voice: DextroRotation");
                    //imageView_INFO.setImageResource(R.mipmap.button_rotate_right);
                    sendMessage("DEX");
                    break;
                case 9:
                    textView_INFO.setText("Voice: LevoRotation");
                    //imageView_INFO.setImageResource(R.mipmap.button_rotate_left);
                    sendMessage("LEV");
                    break;
                default:
                    ppp(resultString+"\t"+i);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void ppp(String r){
        textView_INFO.setText(r);
    }

    private RecognizerDialogListener recognizerDialogListener =  new RecognizerDialogListener() {
        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            printResult(results);
            if (isLast) {
                int[] weight = new int[6];
                int k = 0;
                int tmp;
                String[][] hint;
                String language = mLanguage;
                String str_result = voice_iat.getText().toString().toLowerCase();
                if (language.equals("english")) {
                    hint = hint_english;
                } else {
                    hint = hint_chinese;
                }
                for (int i=0; i<6; ++i) {
                    weight[i] = -1;
                    for (String item: hint[i]) {
                        tmp = str_result.lastIndexOf(item);
                        if (tmp>weight[i]) {
                            weight[i] = tmp;
                        }
                    }
                    if (weight[i]>weight[k]) {
                        k = i;
                    }
                }
                if (weight[k]==-1) {
                    textView_INFO.setText("Voice: Stop");
                    //imageView_INFO.setImageResource(R.mipmap.button_stop);
                    sendMessage("STOP");
                } else {
                    switch (k) {
                        case 0:
                            textView_INFO.setText("Voice: Up");
                            //imageView_INFO.setImageResource(R.mipmap.button_up);
                            sendMessage("UP");
                            break;
                        case 1:
                            textView_INFO.setText("Voice: Down");
                            //imageView_INFO.setImageResource(R.mipmap.button_down);
                            sendMessage("DOWN");
                            break;
                        case 2:
                            textView_INFO.setText("Voice: Left");
                            //imageView_INFO.setImageResource(R.mipmap.button_left);
                            sendMessage("LEFT");
                            break;
                        case 3:
                            textView_INFO.setText("Voice: Right");
                            //imageView_INFO.setImageResource(R.mipmap.button_right);
                            sendMessage("RIGHT");
                            break;
                        case 4:
                            textView_INFO.setText("Voice: DextroRotation");
                            //imageView_INFO.setImageResource(R.mipmap.button_rotate_right);
                            sendMessage("DEX");
                            break;
                        case 5:
                            textView_INFO.setText("Voice: LevoRotation");
                            //imageView_INFO.setImageResource(R.mipmap.button_rotate_left);
                            sendMessage("LEV");
                            break;
                    }
                }
            }
        }

        @Override
        public void onError(SpeechError speechError) {

        }
    };

    private void setVoiceParams() {
        // 清空参数
        mIatDialog.setParameter(SpeechConstant.PARAMS, null);

        // 设置听写引擎
        mIatDialog.setParameter(SpeechConstant.DOMAIN, "iat");
        mIatDialog.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        // 设置返回结果格式
        mIatDialog.setParameter(SpeechConstant.RESULT_TYPE, "json");

        switch (mLanguage) {
            case "mandarin":
                mIatDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
                mIatDialog.setParameter(SpeechConstant.ACCENT, "mandarin");
                break;
            case "cantonese":
                mIatDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
                mIatDialog.setParameter(SpeechConstant.ACCENT, "cantonese");
                break;
            case "english":
                mIatDialog.setParameter(SpeechConstant.LANGUAGE, "en_us");
                break;
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIatDialog.setParameter(SpeechConstant.VAD_BOS, "4000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIatDialog.setParameter(SpeechConstant.VAD_EOS, "1000");

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIatDialog.setParameter(SpeechConstant.ASR_PTT, "1");

        // 设置听写结果是否结果动态修正，为“1”则在听写过程中动态递增地返回结果，否则只在听写结束之后返回最终结果
        // 注：该参数暂时只对在线听写有效
        mIatDialog.setParameter(SpeechConstant.ASR_DWA, "0");
    }

    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());

        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            Log.e("printResult", e.getMessage());
        }

        mIatResults.put(sn, text);

        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }

        voice_iat.setText(resultBuffer.toString());
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

    private int setCameraDisplayOrientation(int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
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

        ((FrameLayout) findViewById(R.id.preview_frame3)).addView(frameLayout1, tp_inet);
        ((FrameLayout) findViewById(R.id.preview_frame3)).addView(frameLayout2, tp_local);

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

        ((FrameLayout) findViewById(R.id.preview_frame3)).bringChildToFront(frameLayout2);
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
            case "BLEFT":
                networkSingleton.SendControlMessage("l");
                break;
            case "BRIGHT":
                networkSingleton.SendControlMessage("r");
                break;
        }
    }

    //控制
    static int posx=0;
    static int posy=0;
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        posx=(int)ev.getX();
        posy=(int)ev.getY();
        if (Math.abs(posx-663)<80 && Math.abs(posy-1010)<80) {
            SpeechActivity.this.finish();
            Intent intent = new Intent();
            Toast.makeText(getApplicationContext(), R.string.action_Control, Toast.LENGTH_SHORT).show();
            intent.setClass(this, ControlActivity.class);
            this.startActivity(intent);
        }
        // textView_INFO.setText(String.valueOf(posx) + " " + String.valueOf(posy));
        return super.dispatchTouchEvent(ev);
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
