package com.icesugar.CrimpingInspection.spp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 蓝牙串口通讯管理器 (SPP - Serial Port Profile)
 *
 * 功能：
 * 1. 扫描设备
 * 2. 连接设备
 * 3. 断开连接
 * 4. 发送数据
 * 5. 接收数据
 */
public class BluetoothSerialManager {
    private static final String TAG = "BluetoothSerialManager";

    // 串口服务UUID (SPP标准UUID)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // 连接超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 10000;

    private Context mContext;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice;

    private InputStream inputStream;
    private OutputStream outputStream;

    private OnSerialConnectListener listener;
    private Handler mainHandler;

    // 连接状态
    private boolean isConnecting = false;
    private boolean isConnected = false;

    // 读线程
    private ExecutorService readExecutor;
    private volatile boolean isReading = false;

    public BluetoothSerialManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 初始化蓝牙
     * @param context 上下文
     * @return 是否初始化成功
     */
    public boolean initBluetooth(Context context) {
        mContext = context;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "该设备不支持蓝牙");
            return false;
        }
        Log.d(TAG, "蓝牙初始化成功");
        return true;
    }

    /**
     * 获取蓝牙适配器
     */
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * 检查蓝牙是否启用
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * 获取连接状态
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * 获取当前连接的设备
     */
    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    /**
     * 开始扫描设备
     */
    public void startDiscovery() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "startDiscovery: bluetoothAdapter == null");
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
        Log.d(TAG, "开始扫描蓝牙设备");
    }

    /**
     * 停止扫描
     */
    public void stopDiscovery() {
        if (bluetoothAdapter == null) {
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        Log.d(TAG, "停止扫描");
    }

    /**
     * 获取已配对的设备
     */
    public java.util.Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null) {
            return null;
        }
        return bluetoothAdapter.getBondedDevices();
    }

    /**
     * 连接蓝牙设备
     * @param device 目标设备
     */
    public void connect(BluetoothDevice device) {
        if (isConnecting || isConnected) {
            Log.w(TAG, "正在连接中或已连接");
            return;
        }

        if (device == null) {
            Log.e(TAG, "connect: device == null");
            notifyConnectFailure("设备为空");
            return;
        }

        isConnecting = true;
        notifyConnecting();

        new Thread(() -> {
            try {
                // 如果已存在连接，先断开
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }

                Log.d(TAG, "开始连接: " + device.getName() + " - " + device.getAddress());

                // 创建串口Socket
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);

                // 取消Discovery，否则会影响连接
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                // 连接（带超时）
                boolean connected = false;
                long startTime = System.currentTimeMillis();
                while (!connected && (System.currentTimeMillis() - startTime) < CONNECT_TIMEOUT) {
                    try {
                        bluetoothSocket.connect();
                        connected = true;
                    } catch (IOException e) {
                        // 连接被拒绝，继续尝试
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }

                if (!connected) {
                    Log.e(TAG, "连接超时");
                    closeSocket();
                    notifyConnectFailure("连接超时");
                    isConnecting = false;
                    return;
                }

                // 连接成功
                connectedDevice = device;
                isConnected = true;
                isConnecting = false;

                // 获取输入输出流
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();

                Log.w(TAG, "连接成功: " + device.getName());
                notifyConnectSuccess();

                // 开始读取数据
                startReading();

            } catch (IOException e) {
                Log.e(TAG, "连接异常: " + e.getMessage());
                closeSocket();
                notifyConnectFailure("连接异常: " + e.getMessage());
                isConnecting = false;
                isConnected = false;
            }
        }).start();
    }

    /**
     * 反射方式连接（备用方法，用于某些设备）
     */
    public void connectByReflection(BluetoothDevice device) {
        if (isConnecting || isConnected) {
            return;
        }

        if (device == null) {
            notifyConnectFailure("设备为空");
            return;
        }

        isConnecting = true;
        notifyConnecting();

        new Thread(() -> {
            try {
                Log.d(TAG, "使用反射方法连接: " + device.getName());

                // 反射获取createRfcommSocket方法
                Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);

                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                bluetoothSocket.connect();

                connectedDevice = device;
                isConnected = true;
                isConnecting = false;

                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();

                notifyConnectSuccess();
                startReading();

            } catch (Exception e) {
                Log.e(TAG, "反射连接失败: " + e.getMessage());
                closeSocket();
                notifyConnectFailure("连接失败: " + e.getMessage());
                isConnecting = false;
                isConnected = false;
            }
        }).start();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        isReading = false;
        closeSocket();
        connectedDevice = null;
        isConnected = false;
        isConnecting = false;
        notifyDisconnected();
        Log.d(TAG, "已断开连接");
    }

    /**
     * 发送数据
     * @param data 字符串数据
     * @return 是否发送成功
     */
    public boolean sendData(String data) {
        if (!isConnected || outputStream == null) {
            Log.e(TAG, "sendData: 未连接");
            return false;
        }
        try {
            outputStream.write(data.getBytes());
            outputStream.flush();
            Log.d(TAG, "发送数据: " + data);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "发送数据失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送字节数据
     * @param bytes 字节数组
     * @return 是否发送成功
     */
    public boolean sendBytes(byte[] bytes) {
        if (!isConnected || outputStream == null) {
            Log.e(TAG, "sendBytes: 未连接");
            return false;
        }
        try {
            outputStream.write(bytes);
            outputStream.flush();
            Log.d(TAG, "发送字节: " + bytes.length + " bytes");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "发送字节失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 开始读取数据
     */
    private void startReading() {
        if (readExecutor != null) {
            readExecutor.shutdownNow();
        }
        isReading = true;
        readExecutor = Executors.newSingleThreadExecutor();
        readExecutor.execute(() -> {
            byte[] buffer = new byte[1024];
            while (isReading && isConnected) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        // 循环读取所有可用数据，避免丢包
                        do {
                            int bytesRead = inputStream.read(buffer);
                            if (bytesRead > 0) {
                                byte[] data = new byte[bytesRead];
                                System.arraycopy(buffer, 0, data, 0, bytesRead);
                                Log.w(TAG, "收到数据: " + bytesRead + " bytes");
                                notifyDataReceived(data);
                            }
                        } while (inputStream.available() > 0);
                    } else {
                        Thread.yield();  // 无数据时让出CPU
                    }
                } catch (IOException e) {
                    Log.e(TAG, "读取数据异常: " + e.getMessage());
                    break;
                }
            }
        });
    }

    /**
     * 关闭Socket
     */
    private void closeSocket() {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
            // ignore
        }
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
            // ignore
        }
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 设置监听器
     */
    public void setOnSerialConnectListener(OnSerialConnectListener listener) {
        this.listener = listener;
    }

    private void notifyConnecting() {
        if (listener != null) {
            mainHandler.post(listener::onConnecting);
        }
    }

    private void notifyConnectSuccess() {
        if (listener != null) {
            mainHandler.post(listener::onConnectSuccess);
        }
    }

    private void notifyConnectFailure(String msg) {
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectFailure(msg));
        }
    }

    private void notifyDisconnected() {
        if (listener != null) {
            mainHandler.post(listener::onDisconnected);
        }
    }

    private void notifyDataReceived(byte[] data) {
        if (listener != null) {
            mainHandler.post(() -> listener.onDataReceived(data));
        }
    }

    /**
     * 销毁，释放资源
     */
    public void destroy() {
        disconnect();
        if (readExecutor != null) {
            readExecutor.shutdownNow();
            readExecutor = null;
        }
    }
}
