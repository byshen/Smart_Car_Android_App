package com.example.am.kc3c.master.activity;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.am.kc3c.master.R;
import com.example.am.kc3c.master.menu.ActivityMenuGroup;
import com.example.am.kc3c.master.util.NetworkSingleton;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.speech.util.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class ControlActivity extends ActivityMenuGroup {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        for (int id: new int[] {
                R.id.main_1,
                R.id.main_2,
                R.id.main_3,
                R.id.main_4,
                R.id.main_5,
                R.id.main_6,
                R.id.main_7,
                R.id.main_8,
                R.id.main_9}) {
            ((ImageButton)findViewById(id)).setOnClickListener(onDirectionClickListener);
        }
    }

    private  View.OnClickListener onDirectionClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent();
            switch (v.getId()) {
                case R.id.main_1:
                    Toast.makeText(getApplicationContext(), R.string.action_button, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, ButtonActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_2:
                    Toast.makeText(getApplicationContext(), R.string.action_gravity, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, GravityActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_3:
                    Toast.makeText(getApplicationContext(), R.string.action_speech, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, SpeechActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_4:
                    Toast.makeText(getApplicationContext(), R.string.action_gesture, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, GestureActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_5:
                    Toast.makeText(getApplicationContext(), R.string.action_route, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, RouteActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_6:
                    Toast.makeText(getApplicationContext(), R.string.action_vector, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, VectorActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_7:
                    Toast.makeText(getApplicationContext(), R.string.action_follow, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, FollowActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_8:
                    Toast.makeText(getApplicationContext(), R.string.action_Control, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, MainActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
                case R.id.main_9:
                    Toast.makeText(getApplicationContext(), R.string.action_Control, Toast.LENGTH_SHORT).show();
                    intent.setClass(ControlActivity.this, MainActivity.class);
                    ControlActivity.this.startActivity(intent);
                    break;
            }
        }
    };
}
