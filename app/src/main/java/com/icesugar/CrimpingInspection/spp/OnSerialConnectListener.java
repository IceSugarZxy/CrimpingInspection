package com.icesugar.CrimpingInspection.spp;

import android.bluetooth.BluetoothDevice;

/**
 * 蓝牙串口连接监听器
 */
public interface OnSerialConnectListener {

    /**
     * 正在连接
     */
    void onConnecting();

    /**
     * 连接成功
     */
    void onConnectSuccess();

    /**
     * 连接失败
     * @param msg 失败原因
     */
    void onConnectFailure(String msg);

    /**
     * 断开连接成功
     */
    void onDisconnected();

    /**
     * 收到数据
     * @param data 原始字节数据
     */
    void onDataReceived(byte[] data);

    /**
     * 发生错误
     * @param error 错误信息
     */
    void onError(String error);
}
