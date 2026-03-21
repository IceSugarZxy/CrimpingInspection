package com.icesugar.CrimpingInspection.ble;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.icesugar.CrimpingInspection.MainActivity;

/**
 * 蓝牙广播接收器
 */
public class BLEBroadcastReceiver extends BroadcastReceiver {
    private final Handler mHandler;

    public BLEBroadcastReceiver(Handler handler) {
        this.mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_STARTED)) { // 开启搜索
            Message message = new Message();
            message.what = MainActivity.START_DISCOVERY; // 使用 MainActivity 中的常量
            mHandler.sendMessage(message);

        } else if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) { // 完成搜索
            Message message = new Message();
            message.what = MainActivity.STOP_DISCOVERY;
            mHandler.sendMessage(message);

        } else if (TextUtils.equals(action, BluetoothAdapter.ACTION_STATE_CHANGED)) { // 系统蓝牙状态监听
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_OFF) {
                Message message = new Message();
                message.what = MainActivity.BT_CLOSED;
                mHandler.sendMessage(message);
            } else if (state == BluetoothAdapter.STATE_ON) {
                Message message = new Message();
                message.what = MainActivity.BT_OPENED;
                mHandler.sendMessage(message);
            }
        }
    }


}