package com.example.am.kc3c.master.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


/**
 * Created by am on 2015/4/16.
 * 双机Socket通信，小车蓝牙通信接口
 *
 * 单例类，唯一的广播源
 */
public class NetworkSingleton {

    // 网络相关接口
    private ServerSocket LocalServerSocket = null;
    private MulticastSocket multicastSocket = null;
    private Socket sendControlSocket = null;
    private Socket sendVideoSocket = null;
    // 临时变量，供Timer使用
    private Socket readClient = null;
    private Socket sendClient = null;

    // WIFI接口
    private int n_localIP;
    private String str_localIP;
    WifiManager wifiManager;
    WifiManager.MulticastLock multicastLock;

    // 读写线程
    private Thread readDispathThread = null;
    private Thread UDPListnerThread = null;
    private Thread UDPSenderThread = null;
    private Thread initBluetoothThread = null;
    private Thread SendControlThreadQueue = null;
    private Thread SendVideoThreadQueue = null;
    private ReadThread controlThread = null;
    private ReadThread videoThread = null;
    // 守护线程
    private Thread UDPDaemonThread;

    // 互斥锁
    private final Object ControlMutex = new Object();
    private final Object VideoMutex = new Object();
    private final Object UDPDaemonMutex = new Object();
    private byte[][] ControlMessageQueue = new byte[2][];
    private byte[][] VideoQueue = new byte[2][];

    // 广播
    private LocalBroadcastManager localBroadcastManager;
    public static final String ACTION_BLUETOOTH_OPENED = "com.example.am.kc3c.ACTION_BLUETOOTH_OPENED";
    public static final String ACTION_BLUETOOTH_CAR_CONNECTED = "com.example.am.kc3c.ACTION_BLUETOOTH_CAR_CONNECTED";
    public static final String ACTION_BLUETOOTH_ERROR = "com.example.am.kc3c.ACTION_BLUETOOTH_ERROR";
    public static final String ACTION_WIFI_ENABLE = "com.example.am.kc3c.ACTION_WIFI_ENABLE";
    public static final String ACTION_WIFI_CLOSED = "com.example.am.kc3c.ACTION_WIFI_CLOSED";
    public static final String ACTION_WIFI_LISTENER_THREAD_CREATED = "com.example.am.kc3c.ACTION_WIFI_LISTENER_THREAD_CREATED";
    public static final String ACTION_WIFI_CONTROL_SENDER_CREATED = "com.example.am.kc3c.ACTION_WIFI_CONTROL_SENDER_CREATED";
    public static final String ACTION_WIFI_VIDEO_SENDER_CREATED = "com.example.am.kc3c.ACTION_WIFI_VIDEO_SENDER_CREATED";
    public static final String ACTION_WIFI_CONTROL_SENDER_THREAD_CREATED = "com.example.am.kc3c.ACTION_WIFI_CONTROL_SENDER_THREAD_CREATED";
    public static final String ACTION_WIFI_VIDEO_SENDER_THREAD_CREATED = "com.example.am.kc3c.ACTION_WIFI_VIDEO_SENDER_THREAD_CREATED";
    public static final String ACTION_WIFI_CONTROL_LISTNER_CREATED = "com.example.am.kc3c.ACTION_WIFI_CONTROL_LISTNER_CREATED";
    public static final String ACTION_WIFI_VIDEO_LISTNER_CREATED = "com.example.am.kc3c.ACTION_WIFI_VIDEO_LISTNER_CREATED";
    public static final String ACTION_WIFI_UDP_LISTNER_CREATED = "com.example.am.kc3c.ACTION_WIFI_UDP_LISTNER_CREATED";
    public static final String ACTION_WIFI_UDP_SENDER_CREATED = "com.example.am.kc3c.ACTION_WIFI_UDP_SENDER_CREATED";
    public static final String ACTION_WIFI_READ_CONTROL_THREAD_CLOSED = "com.example.am.kc3c.ACTION_WIFI_READ_CONTROL_THREAD_CLOSED";
    public static final String ACTION_WIFI_READ_VIDEO_THREAD_CLOSED = "com.example.am.kc3c.ACTION_WIFI_READ_VIDEO_THREAD_CLOSED";
    public static final String ACTION_ONRECEIVE_CONTROL_MSG = "com.example.am.kc3c.ACTION_ONRECEIVE_CONTROL_MSG";
    public static final String ACTION_ONRECEIVE_VIDEO_BEGIN = "com.example.am.kc3c.ACTION_ONRECEIVE_VIDEO_BEGIN";
    public static final String ACTION_ONRECEIVE_VIDEO_STOP = "com.example.am.kc3c.ACTION_ONRECEIVE_VIDEO_STOP";

    // 0x3C3C = 15420
    private static int LocalServerPort = 15420;
    private static int InetPort = 15420;
    private static int UdpServerPort = 15420;

    // 蓝牙
    private BluetoothSocket bluetoothSocket = null;
    private static String CarName = "HC-06";
    private static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static class SingletonHolder {
        private static NetworkSingleton instance = new NetworkSingleton();
    }

    private NetworkSingleton() {
        localBroadcastManager = LocalBroadcastManager.getInstance(MyApplication.getInstance().getApplicationContext());
//        initBluetooth();
        initWifi();
        initListner();
        initUDPListner();
        initUDPSender();
        intiSendQueueThread();
        initDaemonThread();
    }

    public void generalSendBroadcast(@NonNull String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        localBroadcastManager.sendBroadcast(intent);
    }

    public void generalSendBroadcast(@NonNull String action, @NonNull String extra) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.putExtra("Extra_Msg", extra);
        localBroadcastManager.sendBroadcast(intent);
    }

    private class ReadThread extends Thread {

        private Socket readSocket;
        private int type;

        // 受控端专用
        private void sendControlToCar() throws Exception {
            // 控制小车的代码
            InputStream inputStream = readSocket.getInputStream();
            byte[] buffer = new byte[1024];
            int len;
            while (!this.isInterrupted()) {
                len = inputStream.read(buffer, 0, buffer.length);
                if (len<=0) {
                    return;
                }
                generalSendBroadcast(ACTION_ONRECEIVE_CONTROL_MSG, new String(buffer, len-1, 1));
                if (bluetoothSocket==null || !bluetoothSocket.isConnected()) {
                    continue;
                }
                // 若输入流在短时间内有大量控制信息，则以最后一条为准
                bluetoothSocket.getOutputStream().write(buffer[len - 1]);
                bluetoothSocket.getOutputStream().flush();
            }
        }

        private void receiveVideo() throws Exception {
            // 视频传输的代码
            while (!this.isInterrupted()) {
                InputStream inputStream = readSocket.getInputStream();
                byte[] header = new byte[8];
                int len=0;
                while (len<8) {
                    try {
                        len += inputStream.read(header, len, 8-len);
                    } catch (IOException e) {
                        Log.e("ReceiveVideoThread", e.getMessage());
                        throw new Exception("Unexpected Error during video transferration");
                    }
                }
                String headerString = new String(header);
                if(headerString.charAt(0)=='1' && headerString.charAt(1)=='1'){
                    generalSendBroadcast(ACTION_ONRECEIVE_VIDEO_STOP);
                } else if(headerString.charAt(0)=='1' && headerString.charAt(1)=='0') {
                    MyApplication.setOrientation(Integer.parseInt(headerString.substring(4)));
                    generalSendBroadcast(ACTION_ONRECEIVE_VIDEO_BEGIN);
                } else if(headerString.charAt(0)=='0' && headerString.charAt(1)=='0') {
                    int ImageLen = Integer.parseInt(headerString.substring(2));
                    byte[] buffer = new byte[ImageLen];
                    len = 0;
                    while (len<ImageLen) {
                        try {
                            len += inputStream.read(buffer, len, ImageLen-len);
                        } catch (IOException e) {
                            Log.e("ReceiveVideoThread", e.getMessage());
                            throw new Exception("Unexpected Error during video transferration");
                        }
                    }
                    MyApplication.setFrame(buffer);
                }
            }
        }

        @Override
        public void run() {
            try {
                switch (type) {
                    case 1: sendControlToCar(); break;
                    case 2: receiveVideo(); break;
                    default: break;
                }
            } catch (Exception e) {
                Log.e("ReadThread", e.getMessage());
            } finally {
                try {
                    readSocket.close();
                } catch (Exception e) {
                    Log.e("ReadThread", e.getMessage());
                }
                switch (type) {
                    case 1: generalSendBroadcast(ACTION_WIFI_READ_CONTROL_THREAD_CLOSED); break;
                    case 2: generalSendBroadcast(ACTION_WIFI_READ_VIDEO_THREAD_CLOSED); break;
                    default: break;
                }
            }
        }

        public ReadThread(Socket s, int t) {
            readSocket = s;
            type = t;
        }
    }

    private void initBluetooth() {
        initBluetoothThread = new Thread() {
            @Override
            public void run() {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter==null) {
                    generalSendBroadcast(ACTION_BLUETOOTH_ERROR);
                    return;
                }
                try {
                    if (!bluetoothAdapter.isEnabled()) {
                        bluetoothAdapter.enable();
                    }
                    generalSendBroadcast(ACTION_BLUETOOTH_OPENED);
                } catch (Exception e) {
                    Log.e("Bluetooth", e.getMessage());
                    generalSendBroadcast(ACTION_BLUETOOTH_ERROR);
                    return;
                }
                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }

                BluetoothDevice bluetoothDevice;
                for (Object obj: bluetoothAdapter.getBondedDevices().toArray()) {
                    bluetoothDevice = (BluetoothDevice)obj;
                    if (bluetoothDevice.getName().equals(CarName)) {
                        try {
                            bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                            bluetoothSocket.connect();
                            generalSendBroadcast(ACTION_BLUETOOTH_CAR_CONNECTED);
                        } catch (IOException e) {
                            Log.e("Bluetooth", e.getMessage());
                            generalSendBroadcast(ACTION_BLUETOOTH_ERROR);
                        }
                        break;
                    }
                }

            }
        };
        initBluetoothThread.start();
    }

    private String IP_Int2String(int n_IP) {
        int[] IPs = new int[4];
        for (int i=0; i<4; ++i) {
            IPs[i] = (n_IP >> (i*8)) & 0xFF;
        }
        return String.format("%d.%d.%d.%d",
                IPs[0], IPs[1], IPs[2], IPs[3]);
    }

    private void initWifi() {
        wifiManager = (WifiManager)MyApplication.getInstance().getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            generalSendBroadcast(ACTION_WIFI_CLOSED);
            return;
        }
        n_localIP = wifiManager.getConnectionInfo().getIpAddress();
        str_localIP = IP_Int2String(n_localIP);

        multicastLock = wifiManager.createMulticastLock("KC3C");
        multicastLock.setReferenceCounted(false);
        generalSendBroadcast(ACTION_WIFI_ENABLE);
    }

    private void initListner() {
        if (!wifiManager.isWifiEnabled())
            return;
        if (LocalServerSocket!=null && !LocalServerSocket.isClosed())
            return;
        try {
            LocalServerSocket = new ServerSocket(LocalServerPort);
        } catch(Exception e) {
            Log.e("ServerSocket", e.getMessage());
            try {
                LocalServerSocket.close();
            } catch (Exception e2) {
                Log.e("ServerSocket", e2.getMessage());
            } finally {
                LocalServerSocket = null;
            }
            return;
        }
        readDispathThread = new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[1024];
                while(!this.isInterrupted()) {
                    try {
                        readClient = LocalServerSocket.accept();
                        if (controlThread!=null && !controlThread.isAlive()) {
                            controlThread = null;
                        }
                        if (videoThread!=null && !videoThread.isAlive()) {
                            videoThread = null;
                        }
                        if (controlThread!=null && videoThread!=null) {
                            readClient.close();
                            continue;
                        }
                        try {
                            // 5秒读时
                            Timer timer = new Timer(true);
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        readClient.close();
                                    } catch (Exception e) {
                                        Log.e("readDispathThreadTimer", e.getMessage());
                                    }
                                }
                            }, 5000);

                            int len = readClient.getInputStream().read(buffer);
                            timer.cancel();
                            String s = new String(buffer, 0, len);

                            if (s.equals("CONTROL") && controlThread==null) {
                                readClient.getOutputStream().write("GET".getBytes());
                                readClient.getOutputStream().flush();
                                controlThread = new ReadThread(readClient, 1);
                                controlThread.start();
                                generalSendBroadcast(ACTION_WIFI_CONTROL_LISTNER_CREATED);
                            } else if(s.equals("VIDEO") && videoThread==null) {
                                readClient.getOutputStream().write("GET".getBytes());
                                readClient.getOutputStream().flush();
                                videoThread = new ReadThread(readClient, 2);
                                videoThread.start();
                                generalSendBroadcast(ACTION_WIFI_VIDEO_LISTNER_CREATED);
                            } else {
                               readClient.close();
                            }
                        } catch (Exception e) {
                            Log.e("readDispathThread", e.getMessage());
                            if (!readClient.isClosed()) {
                                readClient.close();
                            }
                        }

                    } catch (Exception e) {
                        Log.e("ServerSocketThread", e.getMessage());
                        // 分发线程永不停止
                        if (LocalServerSocket==null || LocalServerSocket.isClosed()) {
                            return;
                        }
                    }
                }
            }
        };
        readDispathThread.start();
        generalSendBroadcast(ACTION_WIFI_LISTENER_THREAD_CREATED);
    }

    private void initUDPListner() {
        if (!wifiManager.isWifiEnabled())
            return;
        try {
            multicastSocket = new MulticastSocket(UdpServerPort);
            // 不回读自己发的组播，无用
            multicastSocket.setLoopbackMode(true);
            multicastSocket.joinGroup(InetAddress.getByName("239.8.7.6"));

            UDPListnerThread = new Thread() {
                @Override
                public void run() {
                    byte[] buf = new byte[1024];
                    DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
                    String s;

                    while (!this.isInterrupted()/* && (sendControlSocket==null || sendVideoSocket==null)*/) {
                        if (sendControlSocket!=null && sendControlSocket.isClosed()) {
                            sendControlSocket = null;
                        }
                        if (sendVideoSocket!=null && sendVideoSocket.isClosed()) {
                            sendVideoSocket = null;
                        }
                        if (sendControlSocket!=null && sendVideoSocket!=null) {
                            synchronized (UDPDaemonMutex) {
                                try {
                                    UDPDaemonMutex.wait();
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                        }
                        multicastLock.acquire();
                        try {
                            multicastSocket.receive(datagramPacket);
                            s = new String(buf, 0, datagramPacket.getLength());
                            if (!s.equals("UDP_INIT") || datagramPacket.getAddress().getHostAddress().equals(str_localIP))
                                continue;

                            if (sendControlSocket==null) {
                                sendControlSocket = initTCPviaUDP("CONTROL", datagramPacket.getAddress());
                                if (sendControlSocket!=null) {
                                    generalSendBroadcast(ACTION_WIFI_CONTROL_SENDER_CREATED);
                                }
                            }
                            if (sendVideoSocket==null) {
                                sendVideoSocket = initTCPviaUDP("VIDEO", datagramPacket.getAddress());
                                if (sendVideoSocket!=null) {
                                    generalSendBroadcast(ACTION_WIFI_VIDEO_SENDER_CREATED);
                                }
                            }
                        } catch (IOException e) {
                            Log.e("UDPListner", e.getMessage());
                            if (multicastSocket==null || multicastSocket.isClosed()) {
                                break;
                            }
                        }
                        multicastLock.release();
                    }
                    try {
                        if (multicastSocket!=null && !multicastSocket.isClosed()) {
                            multicastSocket.close();
                        }
                    } catch (Exception e) {
                        Log.e("UDPListner", e.getMessage());
                    } finally {
                        multicastSocket = null;
                        multicastLock.release();
                    }
                }
            };
            UDPListnerThread.start();
            generalSendBroadcast(ACTION_WIFI_UDP_LISTNER_CREATED);
        } catch (IOException e) {
            try {
                multicastSocket.close();
            } catch (Exception e1) {
                Log.e("UDPListner", e1.getMessage());
            } finally {
                multicastSocket = null;
                multicastLock.release();
            }
            Log.e("UDPListner", e.getMessage());
        }
    }

    private Socket initTCPviaUDP(String data, InetAddress inetAddress) {
        try {
            sendClient = new Socket(inetAddress, InetPort);

            sendClient.getOutputStream().write(data.getBytes());
            sendClient.getOutputStream().flush();

            byte[] buf = new byte[1024];
            int len;

            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        sendClient.close();
                    } catch (Exception e) {
                        Log.e("UDPListner", "<sendVideoSocket> " + e.getMessage());
                    }
                }
            }, 5000);

            len = sendClient.getInputStream().read(buf, 0, buf.length);
            timer.cancel();
            String tmp = new String(buf, 0, len);
            if (!tmp.equals("GET")) {
                throw new Exception("Do not get correct response.");
            }
        } catch (Exception e) {
            try {
                sendClient.close();
            } catch ( Exception e1) {
                Log.e("UDPListner", "<sendSocket> Fail to close sendClient.");
            } finally {
                sendClient = null;
            }
            Log.i("UDPListner", "<sendSocket>" + e.getMessage() + data);
        }
        return sendClient;
    }

    private void initUDPSender() {
        if (!wifiManager.isWifiEnabled())
            return;

        UDPSenderThread = new Thread() {
            @Override
            public void run() {
                MulticastSocket multicastSender;
                DatagramPacket datagramPacket;
                try {
                    multicastSender = new MulticastSocket();
                    multicastSender.joinGroup(InetAddress.getByName("239.8.7.6"));
                    datagramPacket = new DatagramPacket("UDP_INIT".getBytes(),
                            0,
                            "UDP_INIT".getBytes().length,
                            InetAddress.getByName("239.8.7.6"),
                            UdpServerPort);
                } catch (IOException e) {
                    Log.e("UDPSender", e.getMessage());
                    return;
                }
                while (!isInterrupted()) {
                    if (controlThread!=null&&controlThread.isAlive() && videoThread!=null&&videoThread.isAlive()) {
                        synchronized (UDPDaemonMutex) {
                            try {
                                UDPDaemonMutex.wait();
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                    try {
                        multicastSender.send(datagramPacket);
                        sleep(1000);
                    } catch (IOException|InterruptedException e) {
//                        Log.e("UDPSender", e.getMessage());
                        if (multicastSender.isClosed() || this.isInterrupted()) {
                            try {
                                multicastSender.close();
                            } catch (Exception e1) {
                                Log.e("UDPSender", e1.getMessage());
                            }
                            break;
                        }
                    }
                }
            }
        };
        UDPSenderThread.start();
        generalSendBroadcast(ACTION_WIFI_UDP_SENDER_CREATED);
    }

    private void intiSendQueueThread() {
        ControlMessageQueue[0] = ControlMessageQueue[1] = null;
        VideoQueue[0] = VideoQueue[1] = null;
        SendControlThreadQueue = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    synchronized (ControlMutex) {
                        if (ControlMessageQueue[0] == ControlMessageQueue[1] || ControlMessageQueue[1] == null) {
                            try {
                                ControlMutex.wait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                        ControlMessageQueue[0] = ControlMessageQueue[1];
                    }
                    if (sendControlSocket==null || sendControlSocket.isClosed())
                        continue;
                    try {
                        sendControlSocket.getOutputStream().write(ControlMessageQueue[0]);
                        sendControlSocket.getOutputStream().flush();
                    } catch (Exception e) {
                        Log.e("SendControl", e.getMessage());
                        try {
                            sendControlSocket.close();
                        } catch (IOException e1) {
                            //
                        } finally {
                            sendControlSocket = null;
                        }
                    }
                }
            }
        };
        SendVideoThreadQueue = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    synchronized (VideoMutex) {
                        if (VideoQueue[0] == VideoQueue[1] || VideoQueue[1] == null) {
                            try {
                                VideoMutex.wait();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                        VideoQueue[0] = VideoQueue[1];
                    }
                    // generating header
                    // header的组成，共8位byte
                    // 00XXXXXX: 表示传输的数据是图像，共XXXXXX个byte
                    // 1000XXXX: 打开图像传输功能，后4位表示图像的旋转角度
                    // 11000000: 关闭图像传输功能
                    if (sendVideoSocket==null || sendVideoSocket.isClosed())
                        continue;
                    if (VideoQueue[0].length!=8) {
                        try {
                            sendVideoSocket.getOutputStream().write(IntToString("00", VideoQueue[0].length, 8, '0').getBytes());
                        } catch (IOException e) {
                            Log.e("SendVideo", e.getMessage());
                        }
                    }
                    // sending
                    try {
                        sendVideoSocket.getOutputStream().write(VideoQueue[0]);
                        sendVideoSocket.getOutputStream().flush();
                    } catch (Exception e) {
                        Log.e("SendVideo", e.getMessage());
                        try {
                            sendVideoSocket.close();
                        } catch (IOException e1) {
                            //
                        } finally {
                            sendVideoSocket = null;
                        }
                    }
                }
            }
        };
        SendControlThreadQueue.start();
        SendVideoThreadQueue.start();
        generalSendBroadcast(ACTION_WIFI_CONTROL_SENDER_THREAD_CREATED);
        generalSendBroadcast(ACTION_WIFI_VIDEO_SENDER_THREAD_CREATED);
    }

    private void initDaemonThread() {
       UDPDaemonThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    if (!(controlThread != null && controlThread.isAlive()
                            && videoThread != null && videoThread.isAlive()
                            && sendControlSocket!=null && !sendControlSocket.isClosed()
                            && sendVideoSocket!=null && !sendVideoSocket.isClosed())) {
                        synchronized (UDPDaemonMutex) {
                            if (wifiManager.isWifiEnabled()) {
                                if (n_localIP != wifiManager.getConnectionInfo().getIpAddress()) {
                                    n_localIP = wifiManager.getConnectionInfo().getIpAddress();
                                    str_localIP = IP_Int2String(n_localIP);
                                }
                                UDPDaemonMutex.notifyAll();
                            }
                        }
                    }
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        UDPDaemonThread.start();
    }

    public void SendControlMessage(@NonNull String cmd) {
        synchronized (ControlMutex) {
            ControlMessageQueue[1] = cmd.getBytes();
            ControlMutex.notify();
        }
    }

    public void SendVideo(@NonNull byte[] videoFrameData) {
        synchronized (VideoMutex) {
            VideoQueue[1] = videoFrameData;
            VideoMutex.notify();
        }
    }

    private String IntToString(String prefix, int data, int length, char fill) {
        String s = String.valueOf(data);
        String f = prefix;
        for (int i=length-prefix.length()-s.length(); i>0; --i) {
            f += fill;
        }
        f += s;
        return f;
    }

    public void SendBeginVideoMessage(int angle) {
        if (angle>=10000 || angle<0) {
            angle = 0;
        }
        SendVideo(IntToString("1000", angle, 8, '0').getBytes());
    }

    public void SendStopVideoMessage() {
        SendVideo("11000000".getBytes());
    }

    public String getInetIP() {
        if (sendVideoSocket==null || sendVideoSocket.isClosed()) {
            return "0.0.0.0";
        }
        return sendVideoSocket.getInetAddress().getHostAddress();
    }

    public String getInetPort() {
        if (sendVideoSocket==null || sendVideoSocket.isClosed()) {
            return "00000";
        }
        return String.valueOf(InetPort);
    }

    public String getLocalTCPServerInfo() {
        if (str_localIP ==null || readDispathThread==null || !readDispathThread.isAlive()) {
            return "0.0.0.0:00000";
        }
        return str_localIP + ":" + String.valueOf(LocalServerPort);
    }

    public String getLocalUDPServerInfo() {
        if (str_localIP ==null || UDPListnerThread==null || !UDPListnerThread.isAlive()) {
            return "0.0.0.0:00000";
        }
        return str_localIP + ":" + String.valueOf(UdpServerPort);
    }

    public void cleanUp() {
        // 清理函数
        releaseSocket();
        releaseThread();

        multicastLock.release();

        // 0x3C3C = 15420
        LocalServerPort = 15420;
        InetPort = 15420;
        UdpServerPort = 15420;

        ControlMessageQueue[0] = ControlMessageQueue[1] = null;
        VideoQueue[0] = VideoQueue[1] = null;
    }

    private void releaseSocket() {
        // 网络相关接口
        if (LocalServerSocket != null) {
            try {
                if (!LocalServerSocket.isClosed()) {
                    LocalServerSocket.close();
                }
            } catch (IOException e) {
                Log.e("CleanUp", "<LocalServerSocket> "+e.getMessage());
            } finally {
                LocalServerSocket = null;
            }
        }
        if (multicastSocket != null) {
            multicastSocket.close();
            multicastSocket = null;
        }
        if (sendControlSocket != null) {
            try {
                if (!sendControlSocket.isClosed()) {
                    sendControlSocket.close();
                }
            } catch (IOException e) {
                Log.e("CleanUp", "<sendControlSocket> "+e.getMessage());
            } finally {
                sendControlSocket = null;
            }
        }
        if (sendVideoSocket != null) {
            try {
                if (!sendVideoSocket.isClosed()) {
                    sendVideoSocket.close();
                }
            } catch (IOException e) {
                Log.e("CleanUp", "<sendVideoSocket> "+e.getMessage());
            } finally {
                sendVideoSocket = null;
            }
        }
        if (bluetoothSocket != null) {
            try {
                if (!bluetoothSocket.isConnected()) {
                    bluetoothSocket.close();
                }
            } catch (IOException e) {
                Log.e("CleanUp", "<bluetoothSocket> "+e.getMessage());
            } finally {
                bluetoothSocket = null;
            }
        }
    }

    private void releaseThread() {
        // 守护线程
        if (UDPDaemonThread!=null) {
            UDPDaemonThread.interrupt();
            UDPDaemonThread = null;
        }
        // 读写线程
        if (readDispathThread!=null) {
            readDispathThread.interrupt();
            readDispathThread = null;
        }
        if (controlThread!=null) {
            controlThread.interrupt();
            controlThread = null;
        }
        if (videoThread!=null) {
            videoThread.interrupt();
            videoThread = null;
        }
        if (UDPListnerThread !=null) {
            UDPListnerThread.interrupt();
            UDPListnerThread = null;
        }
        if (UDPSenderThread !=null) {
            UDPSenderThread.interrupt();
            UDPSenderThread = null;
        }
        if (initBluetoothThread !=null) {
            initBluetoothThread.interrupt();
            initBluetoothThread = null;
        }
        if (SendControlThreadQueue !=null) {
            SendControlThreadQueue.interrupt();
            SendControlThreadQueue = null;
        }
        if (SendVideoThreadQueue !=null) {
            SendVideoThreadQueue.interrupt();
            SendVideoThreadQueue = null;
        }
    }

    public static NetworkSingleton getInstance() {
        return SingletonHolder.instance;
    }

}
