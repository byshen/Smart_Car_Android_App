package com.example.am.kc3c.master.util;

import android.app.Application;
import android.support.annotation.NonNull;

import com.example.am.kc3c.master.R;
import com.iflytek.cloud.SpeechUtility;

import java.util.HashMap;

/**
 * Created by am on 2015/5/11.
 * 提供全局变量，及Application引用
 */

public class MyApplication extends Application {

    private static MyApplication instance;
    private static HashMap<String, String> map;
    private static int Orientation = 90;
    private static byte[] Frame = null;

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        instance = this;
        map = new HashMap<String, String>();
        SpeechUtility.createUtility(this, String.format("appid=%s", getString(R.string.app_id)));
    }

    public static MyApplication getInstance() {
        // TODO Auto-generated method stub
        return instance;
    }

    public static void setSavedValue(@NonNull String key, @NonNull String value) {
        map.put(key, value);
    }

    public static String getSavedValue(@NonNull String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        return "";
    }

    public static void setOrientation(int orientation) {
        Orientation = orientation;
    }

    public static int getOrientation() {
        return Orientation;
    }

    public static void setFrame(byte[] frame) {
        Frame = frame;
    }

    public static byte[] getFrame() {
        return Frame;
    }

}