package com.example.am.kc3c.master.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;
import android.widget.Toast;

import com.example.am.kc3c.master.R;
import com.example.am.kc3c.master.menu.ActivityMenuGroup;
import com.example.am.kc3c.master.util.MyApplication;
import com.example.am.kc3c.master.util.NetworkSingleton;


public class MainActivity extends ActivityMenuGroup {

    private NetworkSingleton networkSingleton;
    private BroadcastReceiver broadcastReceiver;
    static boolean firsttimerun=true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NetworkSingleton.ACTION_BLUETOOTH_OPENED);
        intentFilter.addAction(NetworkSingleton.ACTION_BLUETOOTH_CAR_CONNECTED);
        intentFilter.addAction(NetworkSingleton.ACTION_BLUETOOTH_ERROR);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_ENABLE);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_CLOSED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_LISTENER_THREAD_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_CONTROL_SENDER_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_VIDEO_SENDER_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_CONTROL_SENDER_THREAD_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_VIDEO_SENDER_THREAD_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_CONTROL_LISTNER_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_VIDEO_LISTNER_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_UDP_LISTNER_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_WIFI_UDP_SENDER_CREATED);
        intentFilter.addAction(NetworkSingleton.ACTION_ONRECEIVE_CONTROL_MSG);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String s = null;
                switch (intent.getAction()) {
                    case NetworkSingleton.ACTION_BLUETOOTH_OPENED:
                        s = "Bluetooth has opened";
                        break;
                    case NetworkSingleton.ACTION_BLUETOOTH_CAR_CONNECTED:
                        s = "Program has connected to car.";
                        break;
                    case NetworkSingleton.ACTION_BLUETOOTH_ERROR:
                        s = "Error occurred while opening bluetooth or conncecting to car.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_ENABLE:
                        s = "Wifi is open.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_CLOSED:
                        s = "Wifi is not open.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_LISTENER_THREAD_CREATED:
                        s = "Wifi's socket listener/server has been created."
                            + "\n"
                            + networkSingleton.getLocalTCPServerInfo();
                        break;
                    case NetworkSingleton.ACTION_WIFI_CONTROL_SENDER_CREATED:
                        s = "Control message sender/socket has been created.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_VIDEO_SENDER_CREATED:
                        s = "Video sender/socket has been created.";
                        ((TextView)findViewById(R.id.textView_ip)).setText(networkSingleton.getInetIP());
                        ((TextView)findViewById(R.id.textView_port)).setText(networkSingleton.getInetPort());
                        break;
                    case NetworkSingleton.ACTION_WIFI_CONTROL_SENDER_THREAD_CREATED:
                        s = "Control sender thread has been created.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_VIDEO_SENDER_THREAD_CREATED:
                        s = "Video sender thread has been created.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_CONTROL_LISTNER_CREATED:
                        s = "Control message listner/socket has been created.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_VIDEO_LISTNER_CREATED:
                        s = "Video listner/socket has been created.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_UDP_LISTNER_CREATED:
                        s = "UDP multicast listner/server has been created."
                            + "\n"
                            + networkSingleton.getLocalUDPServerInfo();
                        break;
                    case NetworkSingleton.ACTION_WIFI_UDP_SENDER_CREATED:
                        s = "UDP multicast sender/socket has been created.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_READ_CONTROL_THREAD_CLOSED:
                        s = "Control listner/socket has been closed.";
                        break;
                    case NetworkSingleton.ACTION_WIFI_READ_VIDEO_THREAD_CLOSED:
                        s = "Video listner/socket has been closed.";
                        break;
                    case NetworkSingleton.ACTION_ONRECEIVE_CONTROL_MSG:
                        ((TextView)findViewById(R.id.textView_ctrlmsg)).setText(
                            String.format("Receive msg <%s>.", intent.getStringExtra("Extra_Msg")));
                        break;
                }
                if (s!=null) {
                    ((TextView)findViewById(R.id.textView_log)).setText(
                            ((TextView)findViewById(R.id.textView_log)).getText()
                                    + s
                                    + "\n");
                }
            }
        };
        // 为了确保收到广播，在network初始化前就注册broadcastReceive
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
        networkSingleton = NetworkSingleton.getInstance();

        if (firsttimerun) {
            firsttimerun=false;
            Intent intent = new Intent();
            Toast.makeText(getApplicationContext(), R.string.action_Control, Toast.LENGTH_SHORT).show();
            intent.setClass(this, ControlActivity.class);
            this.startActivity(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyApplication.setSavedValue("log", String.valueOf(((TextView) findViewById(R.id.textView_log)).getText()));
        MyApplication.setSavedValue("ip", String.valueOf(((TextView) findViewById(R.id.textView_ip)).getText()));
        MyApplication.setSavedValue("port", String.valueOf(((TextView) findViewById(R.id.textView_port)).getText()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((TextView)findViewById(R.id.textView_log)).setText(MyApplication.getSavedValue("log"));
        ((TextView)findViewById(R.id.textView_ip)).setText(MyApplication.getSavedValue("ip"));
        ((TextView)findViewById(R.id.textView_port)).setText(MyApplication.getSavedValue("port"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }
}
