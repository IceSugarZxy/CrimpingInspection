package com.icesugar.CrimpingInspection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索到的设备列表适配器
 */
public class LVDevicesAdapter extends BaseAdapter {

    private Context context;
    private List<BLEDevice> list;

    public LVDevicesAdapter(Context context) {
        this.context = context;
        list = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return list == null ?  0 : list.size();
    }

    @Override
    public Object getItem(int i) {
        if(list == null){
            return null;
        }
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @SuppressLint("SetTextI18n")
    @Override
    @androidx.annotation.RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    public View getView(int i, View view, ViewGroup viewGroup) {
        DeviceViewHolder viewHolder;
        if(view == null){
            view = LayoutInflater.from(context).inflate(R.layout.layout_lv_devices_item,null);
            viewHolder = new DeviceViewHolder();
            viewHolder.tvDeviceName = view.findViewById(R.id.tv_device_name);
            viewHolder.tvDeviceAddress = view.findViewById(R.id.tv_device_address);
            viewHolder.tvDeviceRSSI = view.findViewById(R.id.tv_device_rssi);
            view.setTag(viewHolder);
        }else{
            viewHolder = (DeviceViewHolder) view.getTag();
        }

        if(list.get(i).getBluetoothDevice().getName() == null){
            viewHolder.tvDeviceName.setText("NULL");
        }else{
            viewHolder.tvDeviceName.setText(list.get(i).getBluetoothDevice().getName());
        }

        viewHolder.tvDeviceAddress.setText(list.get(i).getBluetoothDevice().getAddress());
        viewHolder.tvDeviceRSSI.setText("RSSI：" + list.get(i).getRSSI());

        return view;
    }

    /**
     * 初始化所有设备列表
     * @param bluetoothDevices
     */
    public void addAllDevice(List<BLEDevice> bluetoothDevices){
        if(list != null){
            list.clear();
            list.addAll(bluetoothDevices);
            notifyDataSetChanged();
        }
    }

    /**
     * 添加列表子项
     * @param bleDevice
     */
    @androidx.annotation.RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    public void addDevice(BLEDevice bleDevice){
        if(list == null){
            return;
        }
        if (bleDevice.getBluetoothDevice().getName() == null){
            return;
        }
        // 遍历列表，检查是否存在同名设备
        for (int i = 0; i < list.size(); i++) {
            String existingDeviceName = list.get(i).getBluetoothDevice().getName();
            // 判断名称是否相等
            if (existingDeviceName != null && existingDeviceName.equals(bleDevice.getBluetoothDevice().getName())) {
                // 替换旧设备
                list.set(i, bleDevice);
                notifyDataSetChanged(); // 刷新
                return; // 退出方法
            }
        }

        // 如果没有找到同名设备，则添加新设备
        list.add(bleDevice);
        notifyDataSetChanged(); // 刷新
    }

    /**
     * 清空列表
     */
    public void clear(){
        if(list != null){
            list.clear();
        }
        notifyDataSetChanged(); //刷新
    }

    class DeviceViewHolder {

        TextView tvDeviceName;
        TextView tvDeviceAddress;
        TextView tvDeviceRSSI;
    }

}
