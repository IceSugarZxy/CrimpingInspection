package com.icesugar.CrimpingInspection;

import static java.lang.Math.abs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.icesugar.CrimpingInspection.ble.BLEBroadcastReceiver;
import com.icesugar.CrimpingInspection.ble.BLEManager;
import com.icesugar.CrimpingInspection.ble.OnBleConnectListener;
import com.icesugar.CrimpingInspection.ble.OnDeviceSearchListener;
import com.icesugar.CrimpingInspection.permission.PermissionListener;
import com.icesugar.CrimpingInspection.permission.PermissionRequest;

/**
 * CrimpingInspection开发
 */

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivityDebug";

    //bt_patch(mtu).bin
    public static final String SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";  //蓝牙通讯服务
    public static final String READ_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";  //读特征
    public static final String WRITE_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";  //写特征

    //动态申请权限
    private final String[] requestPermissionArray = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
    };
    // 声明一个集合，在后面的代码中用来存储用户拒绝授权的权限
    private List<String> deniedPermissionList = new ArrayList<>();

    public static final int CONNECT_SUCCESS = 0x01;
    public static final int CONNECT_FAILURE = 0x02;
    public static final int DISCONNECT_SUCCESS = 0x03;
    public static final int SEND_SUCCESS = 0x04;
    public static final int SEND_FAILURE = 0x05;
    public static final int RECEIVE_SUCCESS = 0x06;
    public static final int RECEIVE_FAILURE = 0x07;
    public static final int START_DISCOVERY = 0x08;
    public static final int STOP_DISCOVERY = 0x09;
    public static final int DISCOVERY_DEVICE = 0x0A;
    public static final int DISCOVERY_OUT_TIME = 0x0B;
    public static final int SELECT_DEVICE = 0x0C;
    public static final int BT_OPENED = 0x0D;
    public static final int BT_CLOSED = 0x0E;

    private TextView tvCurConState;
    private TextView tvName;
    private TextView tvAddress;
    private TextView tvTotal;
    private TextView tvResult;

    private Button btSearch;
    private Button btConnect;
    private Button btDisconnect;
    private Button btReset;
    private Button btSave;
    private Button btAll;
    private Button btAnalysis;
    private Button btTest;
    private EditText evSteelcore;
    private LinearLayout llDeviceList;
    private ListView lvDevices;
    private LVDevicesAdapter lvDevicesAdapter;
    private Context mContext;
    private BLEManager bleManager;
    private BLEBroadcastReceiver bleBroadcastReceiver;
    private BluetoothDevice curBluetoothDevice;  //当前连接的设备
    //当前设备连接状态
    private boolean curConnState = false;

    /**
     * 绘图部分
     */
    private int total = 0;
    private LineChart mChart1;
    private LineChart mChart2;
    private DynamicLineChartManager dynamicLineChartManager1;
    private DynamicLineChartManager dynamicLineChartManager2;
    private float eddy = 0;
    private List<Float> eddy_list = new ArrayList<>();
    private List<Float> perm_list = new ArrayList<>();

    private final String mStrPath = Environment.getExternalStorageDirectory().getPath();
    private String putout = "";
    private float chart1_max = 5e7f;
    private float chart1_min = 0;
    private float chart2_max = 200f;
    private float chart2_min = -300f;
    
    // 内存监控定时器
    private Timer memoryMonitorTimer;
    private TimerTask memoryMonitorTask;
    private Description eddyDescription, permDescription;
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @SuppressLint({"SetTextI18n", "DefaultLocale"})
        @Override
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case START_DISCOVERY:
                    Log.d(TAG, "开始搜索设备...");
                    break;

                case STOP_DISCOVERY:
                    Log.d(TAG, "停止搜索设备...");
                    break;

                case DISCOVERY_DEVICE:  //扫描到设备
                    BLEDevice bleDevice = (BLEDevice) msg.obj;
                    lvDevicesAdapter.addDevice(bleDevice);

                    break;

                case SELECT_DEVICE:
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) msg.obj;
                    tvName.setText(bluetoothDevice.getName());
                    tvAddress.setText(bluetoothDevice.getAddress());
                    curBluetoothDevice = bluetoothDevice; // 更新当前设备
                    llDeviceList.setVisibility(View.GONE);

                    break;

                case CONNECT_FAILURE: //连接失败
                    Log.d(TAG, "连接失败");
                    tvCurConState.setText("连接失败");
                    curConnState = false;
                    break;

                case CONNECT_SUCCESS:  //连接成功
                    Log.d(TAG, "连接成功");
                    tvCurConState.setText("连接成功");
                    tvCurConState.setTextColor(Color.BLUE);
                    curConnState = true;
                    llDeviceList.setVisibility(View.GONE);
                    break;

                case DISCONNECT_SUCCESS:
                    Log.d(TAG, "断开成功");
                    tvCurConState.setText("断开成功");
                    tvCurConState.setTextColor(Color.RED);
                    curConnState = false;

                    break;

                case RECEIVE_FAILURE: //接收失败
                    String receiveError = (String) msg.obj;
                    break;

                case RECEIVE_SUCCESS:  // 接收成功
                    byte[] recBufSuc = (byte[]) msg.obj;
                    processReceivedData(recBufSuc); // 调用封装后的方法
                    break;

                case BT_CLOSED:
                    Log.d(TAG, "系统蓝牙已关闭");
                    break;

                case BT_OPENED:
                    Log.d(TAG, "系统蓝牙已打开");
                    break;
            }

        }
    };

    private void processReceivedData(byte[] recBufSuc) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 在这里处理数据并获取结果
                DataProcessor.ProcessingResult result = DataProcessor.processData(recBufSuc);
                // 使用 Handler 将数据传回主线程
                runOnUiThread(() -> {
                    try {
                        // 在这里获取返回的变量
                        eddy = result.eddy;
                        eddy_list.add(eddy);
                        perm_list = result.Perm_list;
                        putout += result.add_putout;
                        // 调用绘图和保存的方法
                        Draw_chart1(eddy_list);
                        Draw_chart2(perm_list);
                        total += 1;
                        tvTotal.setText(String.valueOf(total));
                        
                        // 清理列表
                        eddy_list.clear();
                    } catch (Exception e) {
                        e.printStackTrace();  // 打印异常信息
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();  // 打印异常信息
            }
        });
    }

    private void Draw_chart1(List<Float> data) {
        runOnUiThread(() -> {
            // 计算data的最大值和最小值
            float dataMax = Float.NEGATIVE_INFINITY;
            float dataMin = Float.POSITIVE_INFINITY;
            
            for (Float value : data) {
                if (value > dataMax) {
                    dataMax = value;
                }
                if (value < dataMin) {
                    dataMin = value;
                }
            }
            
            // 设置Y轴范围
            dynamicLineChartManager1.setYAxis(dataMax + 500000, dataMin - 500000);
            dynamicLineChartManager1.addEntry(data);
        });
    }

    private void Draw_chart2(List<Float> data) {
        chart2_max = Math.max(chart2_max, Math.max(data.get(0), data.get(1)));
        chart2_min = Math.min(chart2_min, Math.min(data.get(0), data.get(1)));
        runOnUiThread(() -> {
            dynamicLineChartManager2.setYAxis(1.2f * chart2_max, Math.min(0, 1.2f*(-100+chart2_min)));
            dynamicLineChartManager2.addEntry(data);
        });
    }

    @Override
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = MainActivity.this;

        //动态申请权限（Android 6.0）

        //初始化视图
        initView();
        //初始化数据
        initData();
        //注册广播
        initBLEBroadcastReceiver();
        //初始化权限
        initPermissions();
        //初始化LineChart
        initLineChart();
        //初始化监听
        iniListener();
        
        // 启动内存监控定时器
        startMemoryMonitorTimer();
    }

    /**
     * 初始化视图
     */
    private void initView() {
        btSearch = findViewById(R.id.bt_search);
        tvCurConState = findViewById(R.id.tv_cur_con_state);
        btConnect = findViewById(R.id.bt_connect);
        btDisconnect = findViewById(R.id.bt_disconnect);
        btReset = findViewById(R.id.bt_reset);
        btSave = findViewById(R.id.bt_save);
        btAll = findViewById(R.id.bt_all);
        btAnalysis = findViewById(R.id.bt_analysis);
        btTest = findViewById(R.id.bt_test);
        evSteelcore = findViewById(R.id.ev_steelcore);
        tvName = findViewById(R.id.tv_name);
        tvAddress = findViewById(R.id.tv_address);
        tvTotal = findViewById(R.id.tv_total);
        tvResult = findViewById(R.id.tv_result);
        llDeviceList = findViewById(R.id.ll_device_list);
        lvDevices = findViewById(R.id.lv_devices);
    }

    /**
     * 初始化监听
     */
    private void iniListener() {
        btSearch.setOnClickListener(this);
        btConnect.setOnClickListener(this);
        btDisconnect.setOnClickListener(this);
        btAll.setOnClickListener(this);
        btAnalysis.setOnClickListener(this);
        btTest.setOnClickListener(this);
        btReset.setOnClickListener(this);
        btSave.setOnClickListener(this);

        // 为每个按钮设置触摸监听器
        setButtonTouchListener(btSearch);
        setButtonTouchListener(btConnect);
        setButtonTouchListener(btDisconnect);
        setButtonTouchListener(btAll);
        setButtonTouchListener(btAnalysis);
        setButtonTouchListener(btTest);
        setButtonTouchListener(btReset);
        setButtonTouchListener(btSave);

        // 设置设备列表的点击监听
        lvDevices.setOnItemClickListener((adapterView, view, i, l) -> {
            BLEDevice bleDevice = (BLEDevice) lvDevicesAdapter.getItem(i);
            BluetoothDevice bluetoothDevice = bleDevice.getBluetoothDevice();
            if (bleManager != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bleManager.stopDiscoveryDevice();
            }
            Message message = new Message();
            message.what = SELECT_DEVICE;
            message.obj = bluetoothDevice;
            mHandler.sendMessage(message);
        });

        lvDevices.setOnItemClickListener((adapterView, view, i, l) -> {
            BLEDevice bleDevice = (BLEDevice) lvDevicesAdapter.getItem(i);
            BluetoothDevice bluetoothDevice = bleDevice.getBluetoothDevice();
            if(bleManager != null){
                bleManager.stopDiscoveryDevice();
            }
            Message message = new Message();
            message.what = SELECT_DEVICE;
            message.obj = bluetoothDevice;
            mHandler.sendMessage(message);
        });
    }

    // 设置按钮的触摸监听器的辅助方法
    @SuppressLint("ClickableViewAccessibility")
    private void setButtonTouchListener(final Button button) {
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.setBackgroundResource(R.drawable.bt_bg_pressed); // 按下时背景
                button.setTextColor(ContextCompat.getColor(v.getContext(), R.color.black)); // 按下时文字颜色
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setBackgroundResource(R.drawable.bt_bg); // 松开时恢复背景
                button.setTextColor(ContextCompat.getColor(v.getContext(), R.color.white)); // 松开时恢复文字颜色
            }
            return false; // 继续处理点击事件
        });
    }
    /**
     * 初始化数据
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private void initData() {
        //列表适配器
        lvDevicesAdapter = new LVDevicesAdapter(MainActivity.this);
        lvDevices.setAdapter(lvDevicesAdapter);

        //初始化ble管理器
        bleManager = new BLEManager();
        if(!bleManager.initBle(mContext)) {
            Log.d(TAG, "该设备不支持低功耗蓝牙");
            Toast.makeText(mContext, "该设备不支持低功耗蓝牙", Toast.LENGTH_SHORT).show();
        }else{
            if(!bleManager.isEnable()){
                //去打开蓝牙
                bleManager.openBluetooth(mContext,false);
            }
        }
    }

    /**
     * 注册广播
     */
    private void initBLEBroadcastReceiver() {
        // 注册广播接收
        bleBroadcastReceiver = new BLEBroadcastReceiver(mHandler); // 传入 mHandler
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); // 开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); // 扫描结束
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); // 手机蓝牙状态监听
        registerReceiver(bleBroadcastReceiver, intentFilter);
    }

    /**
     * 初始化权限
     */
    private void initPermissions() {
        //Android 6.0以上动态申请权限
        final PermissionRequest permissionRequest = new PermissionRequest();
        permissionRequest.requestRuntimePermission(MainActivity.this, requestPermissionArray, new PermissionListener() {
            @Override
            public void onGranted() {
                Log.d(TAG,"所有权限已被授予");
            }
            //用户勾选“不再提醒”拒绝权限后，关闭程序再打开程序只进入该方法！
            @Override
            public void onDenied(List<String> deniedPermissions) {
                deniedPermissionList = deniedPermissions;
                for (String deniedPermission : deniedPermissionList) {
                    Log.e(TAG,"被拒绝权限：" + deniedPermission);
                }
            }
        });
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }
    }

    /**
     * 初始化LineChart
     */
    protected void initLineChart() {
        //绘图部分
        mChart1 = findViewById(R.id.dynamic_chart1);
        mChart2 = findViewById(R.id.dynamic_chart2);
        // 设置 Y 轴的格式化器
        YAxis yAxis = mChart1.getAxisLeft();
        yAxis.setValueFormatter(new ValueFormatter() {
            @SuppressLint("DefaultLocale")
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                if (value >= 1_000_000) {
                    return String.format("%.1fM", value / 1_000_000); // 百万
                } else if (value >= 1_000) {
                    return String.format("%.1fK", value / 1_000); // 千
                } else {
                    return String.valueOf(value); // 小于千的数值直接显示
                }
            }
        });
        //增加MarkerView
        CustomMarkerView markerView = new CustomMarkerView(mContext, R.layout.custom_marker_view);
        mChart1.setMarker(markerView);
        mChart1.setHighlightPerDragEnabled(false); // 禁用拖动高亮
        mChart1.setHighlightPerTapEnabled(false); // 禁用点击高亮
        mChart2.setMarker(markerView);
        mChart2.setHighlightPerDragEnabled(false);
        mChart2.setHighlightPerTapEnabled(false);
        dynamicLineChartManager1 = new DynamicLineChartManager(mChart1, Arrays.asList("涡流"), Arrays.asList(Color.RED));
        dynamicLineChartManager2 = new DynamicLineChartManager(mChart2, Arrays.asList("幅值", "相位"), Arrays.asList(Color.MAGENTA, Color.BLUE));
        dynamicLineChartManager1.setYAxis(chart1_max, chart1_min);
        dynamicLineChartManager2.setYAxis(chart2_max, chart2_min);
        eddyDescription = mChart1.getDescription();
        permDescription = mChart2.getDescription();
        setDescriptionPosition(eddyDescription, mChart1);
        setDescriptionPosition(permDescription, mChart2);
    }

    private void setDescriptionPosition(Description description, LineChart chart) {
        // 获取图表的宽度和高度
        float chartWidth = chart.getWidth();
        // 设置描述位置到右上角
        float x = 1500;
        float y = 100;
        description.setPosition(x, y);
        description.setText("测试结果");
        description.setTextSize(10);
        chart.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //注销广播接收
        unregisterReceiver(bleBroadcastReceiver);
        // 停止内存监控定时器
        stopMemoryMonitorTimer();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    @RequiresPermission(allOf = {"android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"})
    public void onClick(View view) {
        if (view.getId() == R.id.bt_search) {  // 搜索蓝牙
            if (btSearch.getText().toString().equals("搜索")) {
                llDeviceList.setVisibility(View.VISIBLE);
                searchBtDevice();
                btSearch.setText("返回");
            } else {
                llDeviceList.setVisibility(View.GONE);
                btSearch.setText("搜索");
            }
        } else if (view.getId() == R.id.bt_connect) { // 连接蓝牙
            if (!curConnState) {
                if (bleManager != null) {
                    bleManager.connectBleDevice(mContext, curBluetoothDevice, 15000, SERVICE_UUID, READ_UUID, WRITE_UUID, onBleConnectListener);
                }
            } else {
                Toast.makeText(this, "当前设备已连接", Toast.LENGTH_SHORT).show();
            }
        } else if (view.getId() == R.id.bt_disconnect) { // 断开连接
            if (curConnState) {
                if (bleManager != null) {
                    bleManager.disConnectDevice();
                }
            } else {
                Toast.makeText(this, "当前设备未连接", Toast.LENGTH_SHORT).show();
            }
        } else if (view.getId() == R.id.bt_all) {
            // 对chart1进行y轴范围自适应
            if (dynamicLineChartManager1 != null && mChart1.getData() != null) {
                LineDataSet eddyDataSet = (LineDataSet) mChart1.getData().getDataSetByIndex(0);
                if (eddyDataSet != null && eddyDataSet.getEntryCount() > 0) {
                    float maxValue = Float.NEGATIVE_INFINITY;
                    float minValue = Float.POSITIVE_INFINITY;
                    for (int i = 0; i < eddyDataSet.getEntryCount(); i++) {
                        float value = eddyDataSet.getEntryForIndex(i).getY();
                        if (value > maxValue) {
                            maxValue = value;
                        }
                        if (value < minValue) {
                            minValue = value;
                        }
                    }
                    dynamicLineChartManager1.setYAxis(maxValue + 500000, minValue - 500000);
                }
            }

            // 对chart2进行y轴范围自适应
            if (dynamicLineChartManager2 != null && mChart2.getData() != null) {
                float maxValue = Float.NEGATIVE_INFINITY;
                float minValue = Float.POSITIVE_INFINITY;
                
                // 遍历chart2中的所有数据集（幅值和相位）
                for (int dataSetIndex = 0; dataSetIndex < mChart2.getData().getDataSetCount(); dataSetIndex++) {
                    LineDataSet dataSet = (LineDataSet) mChart2.getData().getDataSetByIndex(dataSetIndex);
                    if (dataSet != null && dataSet.getEntryCount() > 0) {
                        for (int i = 0; i < dataSet.getEntryCount(); i++) {
                            float value = dataSet.getEntryForIndex(i).getY();
                            if (value > maxValue) {
                                maxValue = value;
                            }
                            if (value < minValue) {
                                minValue = value;
                            }
                        }
                    }
                }
                
                // 应用y轴范围自适应
                dynamicLineChartManager2.setYAxis(maxValue + 100, minValue - 100);
            }

            dynamicLineChartManager1.fitScreen();
            dynamicLineChartManager2.fitScreen();
        } else if (view.getId() == R.id.bt_analysis) {
            FeaturePointDraw();
        } else if (view.getId() == R.id.bt_test) {
            // 调取历史数据并绘制功能
            loadAndDrawHistoricalData();

        } else if (view.getId() == R.id.bt_reset) {
            mChart1.highlightValue(null);
            mChart2.highlightValue(null);
            dynamicLineChartManager1.fitScreen();
            dynamicLineChartManager2.fitScreen();
            chart1_max = 12e6f;
            chart1_min = 0;
            chart2_max = 1500f;
            chart2_min = -300f;
            dynamicLineChartManager1 = new DynamicLineChartManager(mChart1, Arrays.asList("涡流"), Arrays.asList(Color.RED));
            dynamicLineChartManager2 = new DynamicLineChartManager(mChart2, Arrays.asList("幅值", "相位"), Arrays.asList(Color.MAGENTA, Color.BLUE));
            dynamicLineChartManager1.setYAxis(chart1_max, chart1_min);
            dynamicLineChartManager2.setYAxis(chart2_max, chart2_min);
            total = 0;
            tvTotal.setText("0");
            putout = "";
            eddy_list.clear();
            perm_list.clear();
            eddyDescription.setText("测试结果");
            permDescription.setText("测试结果");
            tvResult.setText("");
        } else if (view.getId() == R.id.bt_save) {
            createFile(putout);
        }
    }

    /**
     * 绘制特征点
     */
    @SuppressLint("SetTextI18n")
    private void FeaturePointDraw() {
        // PERM特征点定位
        LineDataSet permDataAmplitudeSet = (LineDataSet) mChart2.getData().getDataSetByIndex(0);
        LineDataSet permDataPhaseSet = (LineDataSet) mChart2.getData().getDataSetByIndex(1);

        // 防崩溃
        if (permDataAmplitudeSet == null || permDataPhaseSet == null) {
            Log.e("FeaturePointDraw", "LineDataSet for PERM is null");
            return;
        }

        // 添加调试信息
        Log.d("FeaturePointDraw", "开始PERM特征点分析");

        // 进行Y轴范围调整
        float chart1MaxValue = Float.NEGATIVE_INFINITY;
        float chart1MinValue = Float.POSITIVE_INFINITY;
        for (int i = 0; i < mChart1.getData().getDataSetCount(); i++) {
            LineDataSet dataSet = (LineDataSet) mChart1.getData().getDataSetByIndex(i);
            for (int j = 0; j < dataSet.getEntryCount(); j++) {
                float value = dataSet.getEntryForIndex(j).getY();
                if (value > chart1MaxValue) chart1MaxValue = value;
                if (value < chart1MinValue) chart1MinValue = value;
            }
        }

        float chart2MaxValue = Float.NEGATIVE_INFINITY;
        float chart2MinValue = Float.POSITIVE_INFINITY;
        for (int i = 0; i < mChart2.getData().getDataSetCount(); i++) {
            LineDataSet dataSet = (LineDataSet) mChart2.getData().getDataSetByIndex(i);
            for (int j = 0; j < dataSet.getEntryCount(); j++) {
                float value = dataSet.getEntryForIndex(j).getY();
                if (value > chart2MaxValue) chart2MaxValue = value;
                if (value < chart2MinValue) chart2MinValue = value;
            }
        }

        // 设置Y轴范围
        dynamicLineChartManager1.setYAxis(chart1MaxValue + 500000, chart1MinValue - 500000);
        dynamicLineChartManager2.setYAxis(chart2MaxValue + 100, chart2MinValue - 100);

        // 定义PERM特征点变量
        int permFirstPoint = -1;    // 钢管左端
        int permSecondPoint = -1;   // 钢管右端
        int permThirdPoint;    // 中心位
        int permFourthPoint = -1;     // 钢芯左端
        int permFifthPoint = -1;     // 钢芯右端

        // 钢管左端（前半段的最大值）
        float permAmplitudeFirstValue = Float.NEGATIVE_INFINITY;
        // 钢管右端（后半段的最大值）
        float permAmplitudeSecondValue = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < permDataAmplitudeSet.getEntryCount() / 2; i++) {
            float currentValue = permDataAmplitudeSet.getEntryForIndex(i).getY();
            if (currentValue > permAmplitudeFirstValue) {
                permAmplitudeFirstValue = currentValue;
                permFirstPoint = i;
            }
        }

        for (int i = permDataAmplitudeSet.getEntryCount() / 2; i < permDataAmplitudeSet.getEntryCount(); i++) {
            float currentValue = permDataAmplitudeSet.getEntryForIndex(i).getY();
            if (currentValue > permAmplitudeSecondValue) {
                permAmplitudeSecondValue = currentValue;
                permSecondPoint = i;
            }
        }

        // 钢管中心
        permThirdPoint = ( permFirstPoint + permSecondPoint ) / 2;

        // 查找permPhase与水平线的相交点
        List<Integer> intersectionPoints = findIntersectionPointsWithPermPhase(0f);
        // 按X坐标排序
        intersectionPoints.sort(Comparator.naturalOrder());

        // 钢芯左端：permPhaseMiniIndex左侧第一个交点
        for (int i = 0; i < intersectionPoints.size(); i++) {
            int point = intersectionPoints.get(i);
            if (point > permThirdPoint - 50) {
                permFourthPoint = intersectionPoints.get(i - 1);
                break;
            }
        }

        // 钢芯右端：permPhaseMiniIndex右侧第一个极大值
        int permFifthPointPre = findNthExtremePoint(
                permDataPhaseSet, permThirdPoint - 100, permSecondPoint, 1, 50, true, false
        );
        for (int i = 0; i < intersectionPoints.size(); i++) {
            int point = intersectionPoints.get(i);
            if (point > permFifthPointPre) {
                permFifthPoint = intersectionPoints.get(i);
                break;
            }
        }

        // EDDY特征点定位
        LineDataSet eddyDataSet = (LineDataSet) mChart1.getData().getDataSetByIndex(0);

        // 定义EDDY特征点变量
        int eddyFirstPoint = -1;   // 正序第一个斜率半衰点
        int eddySecondPoint = -1;  // 倒序第一个斜率半衰点

        int startIndex = -1;
        int endIndex = -1;

        // 使用整合后的函数寻找特征点
        // 正序寻找eddyFirstPoint
        startIndex = permFirstPoint - 300;
        endIndex = permFirstPoint;
        eddyFirstPoint = findSlopeConditionPoint
                (eddyDataSet, startIndex, endIndex, false, 100, 0.1f);
        
        // 检查是否找到有效的eddyFirstPoint
        if (eddyFirstPoint == -1) {
            Log.e("FeaturePointDraw", "未找到有效的eddyFirstPoint");
            return;
        }
        
        // 倒序寻找eddySecondPoint - 寻找和eddyFirstPoint相同Y值的点
        float eddyFirstPointY = eddyDataSet.getEntryForIndex(eddyFirstPoint).getY();
        startIndex = permSecondPoint;
        endIndex = ( permSecondPoint + eddyFirstPoint ) / 2;
        
        // 从右向左倒序查找相同Y值的点
        for (int i = startIndex; i >= endIndex; i--) {
            float currentValue = eddyDataSet.getEntryForIndex(i).getY();
            if (Math.abs(currentValue - eddyFirstPointY) < 10000.0f) { // 允许10000的误差
                eddySecondPoint = i;
                break; // 找到第一个匹配的点就停止
            }
        }
        
        // 如果没有找到相同Y值的点，则使用原来的斜率条件查找作为备选
        if (eddySecondPoint == -1) {
            eddySecondPoint = findSlopeConditionPoint
                    (eddyDataSet, startIndex, endIndex, true, 50, 0.5f);
        }

        // 偏置参数
        float factor = 36.3f;

        float al_offset = 1.56f;
        float steel_offset = -1.77f;
        float steel_core_offset = -0f;

        // 铝管显示
        List<Highlight> eddyHighlights = new ArrayList<>();

        eddyHighlights.add(new Highlight(eddyFirstPoint, 0, 0));
        eddyHighlights.add(new Highlight(eddySecondPoint, 0, 0));

        mChart1.highlightValues(eddyHighlights.toArray(new Highlight[0]));

        float eddyFirst = eddyFirstPoint;
        float eddySecond = eddySecondPoint;

        String eddyFirstStr = String.format(Locale.US, "%.2f", eddyFirst);
        String eddySecondStr = String.format(Locale.US, "%.2f", eddySecond);
        String al_length_str = String.format(Locale.US, "%.2f", (eddySecond - eddyFirst) / factor + al_offset);

        eddyDescription.setText(
                "铝管压接左侧:" + eddyFirstStr + " " +
                "铝管压接右侧:" + eddySecondStr + " " +
                "总长度:" + al_length_str + "cm"
        );
        mChart1.invalidate();

        // 钢管显示
        List<Highlight> permHighlights = new ArrayList<>();

        permHighlights.add(new Highlight(permFirstPoint, 0, 0));
        permHighlights.add(new Highlight(permSecondPoint, 0, 0));
        permHighlights.add(new Highlight(permThirdPoint, 0, 0));
        permHighlights.add(new Highlight(permFourthPoint,1, 0));
        permHighlights.add(new Highlight(permFifthPoint, 1, 0));

        mChart2.highlightValues(permHighlights.toArray(new Highlight[0]));

        float permFirst = permFirstPoint;
        float permSecond = permSecondPoint;
        float permThird = permThirdPoint;
        float permFourth = permFourthPoint;
        float permFifth = permFifthPoint;

        String permFirst_str = String.format(Locale.US, "%.2f", permFirst);
        String permSecond_str = String.format(Locale.US, "%.2f", permSecond);
        String permThird_str = String.format(Locale.US, "%.2f", permThird);
        String permFourth_str = String.format(Locale.US, "%.2f", permFourth);
        String permFifth_str = String.format(Locale.US, "%.2f", permFifth);

        float steel_length = (permSecond - permFirst) / factor + steel_offset;
        float steel_core_length = (permFifth - permFourth) / factor + steel_core_offset;
        float drift = ((permFirst - eddyFirst) - (permSecond - eddySecond)) / factor;

        String steel_length_str = String.format(Locale.US, "%.2f", steel_length);
        String steel_core_length_str = String.format(Locale.US, "%.2f", steel_core_length);
        String drift_str = String.format(Locale.US, "%.2f", Math.abs(drift));

        permDescription.setText(
                "钢管左端:" + permFirst_str + " " +
                "钢管右端:" + permSecond_str + " " +
                "钢管中心:" + permThird_str + " " +
                "钢芯左端:" + permFourth_str + " " +
                "钢芯右端:" + permFifth_str
        );
        mChart2.invalidate();

        // 右上文字显示
        tvResult.setText("铝管长度:"+al_length_str+"cm\n");
        tvResult.append("钢管长度:"+steel_length_str+"cm\n");
        if (drift > 0) {
            tvResult.append("钢管左偏:"+drift_str+"cm\n");
        } else if (drift == 0) {
            tvResult.append("钢管无偏移\n");
        } else {
            tvResult.append("钢管右偏:"+drift_str+"cm\n");
        }
        tvResult.append("钢芯间隙参数:"+steel_core_length_str+"\n");

        // 添加完成日志
        Log.d("FeaturePointDraw", "特征点分析完成");
    }

    /**
     * 检验窗口斜率并找到满足条件的点
     * 窗口大小50，两侧取点，找到第一个i，其斜率小于i-10斜率的1/2
     * @param dataSet 数据集
     * @param startIndex 起始索引
     * @param endIndex 结束索引
     * @param isReverse 是否反向搜索（从后往前）
     * @return 满足条件的第一个点的索引，如果没有找到返回-1
     */
    private int findSlopeConditionPoint
    (LineDataSet dataSet, int startIndex, int endIndex, boolean isReverse, int windowSize, float factor) {
        // 确保索引范围有效
        if (startIndex < windowSize || endIndex > dataSet.getEntryCount() - windowSize) {
            Log.e("SlopeCheck", "索引范围无效");
            return -1;
        }
        
        if (isReverse) {
            // 反向搜索：从后往前
            for (int i = endIndex - windowSize - 10; i >= startIndex + windowSize + 10; i--) {

                if (i < windowSize || i >= dataSet.getEntryCount() - windowSize) {
                    continue;
                }

                // 计算当前点的窗口斜率
                int leftIndex = Math.max(i - windowSize, 0);
                int rightIndex = Math.min(i + windowSize, dataSet.getEntryCount() - 1);
                float leftValue = dataSet.getEntryForIndex(leftIndex).getY();
                float rightValue = dataSet.getEntryForIndex(rightIndex).getY();
                float currentSlope = Math.abs((rightValue - leftValue) / (rightIndex - leftIndex));

                // 计算右边第10个点的窗口斜率
                int prevIndex = i + 10;
                if (prevIndex < windowSize || prevIndex >= dataSet.getEntryCount() - windowSize) {
                    continue;
                }

                int prevLeftIndex = Math.max(prevIndex - windowSize, 0);
                int prevRightIndex = Math.min(prevIndex + windowSize, dataSet.getEntryCount() - 1);
                float prevLeftValue = dataSet.getEntryForIndex(prevLeftIndex).getY();
                float prevRightValue = dataSet.getEntryForIndex(prevRightIndex).getY();
                float prevSlope = Math.abs((prevRightValue - prevLeftValue) / (prevRightIndex - prevLeftIndex));

                if (factor > 1) {
                    // 检查当前斜率是否大于右边第10个点斜率的factor倍
                    if (currentSlope > prevSlope * factor) {
                        return i;
                    }
                } else {
                    // 检查当前斜率是否小于右边第10个点斜率的factor倍
                    if (currentSlope < prevSlope * factor) {
                        return i;
                    }
                }

            }
        } else {
            // 正向搜索：从前往后
            for (int i = startIndex + windowSize + 10; i <= endIndex - windowSize - 10; i++) {

                if (i < windowSize || i >= dataSet.getEntryCount() - windowSize) {
                    continue;
                }

                // 计算当前点的窗口斜率
                int leftIndex = Math.max(i - windowSize, 0);
                int rightIndex = Math.min(i + windowSize, dataSet.getEntryCount() - 1);
                float leftValue = dataSet.getEntryForIndex(leftIndex).getY();
                float rightValue = dataSet.getEntryForIndex(rightIndex).getY();
                float currentSlope = (rightValue - leftValue) / (rightIndex - leftIndex);

                // 计算左边第10个点的窗口斜率
                int prevIndex = i - 10;
                if (prevIndex < windowSize || prevIndex >= dataSet.getEntryCount() - windowSize) {
                    continue;
                }

                int prevLeftIndex = Math.max(prevIndex - windowSize, 0);
                int prevRightIndex = Math.min(prevIndex + windowSize, dataSet.getEntryCount() - 1);
                float prevLeftValue = dataSet.getEntryForIndex(prevLeftIndex).getY();
                float prevRightValue = dataSet.getEntryForIndex(prevRightIndex).getY();
                float prevSlope = (prevRightValue - prevLeftValue) / (prevRightIndex - prevLeftIndex);

                if (factor > 0) {
                    // 检查当前斜率是否大于左边第10个点斜率的factor倍
                    if (currentSlope > prevSlope * factor) {
                        return i;
                    }
                } else {
                    // 检查当前斜率是否小于左边第10个点斜率的factor倍
                    if (currentSlope < prevSlope * factor) {
                        return i;
                    }
                }
            }
        }
        
        Log.d("SlopeCheck", "未找到满足条件的点");
        return -1;
    }

    /**
     * 寻找数据集中指定区间内的第n个极值点
     * @param dataSet 数据集
     * @param startIndex 起始索引
     * @param endIndex 结束索引
     * @param nthExtreme 要寻找的第n个极值点（n从1开始）
     * @param windowSize 窗口大小，用于判断极值
     * @param isMaxPoint true表示寻找极大值，false表示寻找极小值
     * @param isReverse true表示倒序寻找（从endIndex到startIndex），false表示正序寻找（从startIndex到endIndex）
     * @return 第n个极值点的索引，如果未找到则返回-1
     */
    private int findNthExtremePoint(LineDataSet dataSet, int startIndex, int endIndex, int nthExtreme, int windowSize, boolean isMaxPoint, boolean isReverse) {
        // 检查输入参数的有效性
        if (dataSet == null || dataSet.getEntryCount() == 0) {
            Log.e("ExtremePointSearch", "数据集无效");
            return -1;
        }
        
        int dataSize = dataSet.getEntryCount();
        
        // 修正索引边界，确保在有效范围内
        startIndex = Math.max(0, startIndex);
        endIndex = Math.min(dataSize - 1, endIndex);
        
        // 检查索引范围是否有效
        if (startIndex > endIndex || nthExtreme <= 0 || windowSize <= 0) {
            Log.e("ExtremePointSearch", "参数无效: startIndex=" + startIndex + ", endIndex=" + endIndex + ", nthExtreme=" + nthExtreme);
            return -1;
        }
        
        // 检查窗口大小是否过大
        if (windowSize * 2 + 1 > dataSize) {
            Log.e("ExtremePointSearch", "窗口大小过大");
            return -1;
        }

        int extremePointCount = 0;
        // 记录已识别的极值点索引，用于避免平台区域重复识别
        List<Integer> foundExtremePoints = new ArrayList<>();

        if (isReverse) {
            // 倒序搜索：从endIndex到startIndex
            for (int i = endIndex; i >= startIndex; i--) {
                // 检查当前点是否可以作为窗口中心
                if (i < windowSize || i >= dataSize - windowSize) {
                    continue; // 跳过边界点，无法形成完整窗口
                }
                
                float currentValue = dataSet.getEntryForIndex(i).getY();
                boolean isExtremePoint = true;

                // 检查当前点是否是窗口内的极值点
                for (int j = i - windowSize; j <= i + windowSize; j++) {
                    if (j != i && j >= 0 && j < dataSize) { // 确保索引有效
                        float compareValue = dataSet.getEntryForIndex(j).getY();
                        // 当isMaxPoint为true时，检查是否有更大的值；为false时，检查是否有更小的值
                        if ((isMaxPoint && compareValue > currentValue) || (!isMaxPoint && compareValue < currentValue)) {
                            isExtremePoint = false;
                            break;
                        }
                    }
                }

                if (isExtremePoint) {
                    // 检查是否与已识别的极值点过于接近（避免平台区域的重复识别）
                    boolean isDuplicateInPlatform = false;

                    // 检查当前点是否在已识别极值点的平台区域内
                    for (int prevExtreme : foundExtremePoints) {
                        // 如果当前点与已识别极值点的值相同，且在窗口大小范围内，则认为是同一平台
                        if (Math.abs(i - prevExtreme) <= windowSize * 2 &&
                                Math.abs(dataSet.getEntryForIndex(prevExtreme).getY() - currentValue) < 1e-6f) {
                            isDuplicateInPlatform = true;
                            break;
                        }
                    }

                    // 如果不是平台区域的重复点，则计数
                    if (!isDuplicateInPlatform) {
                        foundExtremePoints.add(i);
                        extremePointCount++;
                        if (extremePointCount == nthExtreme) {
                            return i;
                        }
                    }
                }
            }
        } else {
            // 正序搜索：从startIndex到endIndex
            for (int i = startIndex; i <= endIndex; i++) {
                // 检查当前点是否可以作为窗口中心
                if (i < windowSize || i >= dataSize - windowSize) {
                    continue; // 跳过边界点，无法形成完整窗口
                }
                
                float currentValue = dataSet.getEntryForIndex(i).getY();
                boolean isExtremePoint = true;

                // 检查当前点是否是窗口内的极值点
                for (int j = i - windowSize; j <= i + windowSize; j++) {
                    if (j != i && j >= 0 && j < dataSize) { // 确保索引有效
                        float compareValue = dataSet.getEntryForIndex(j).getY();
                        // 当isMaxPoint为true时，检查是否有更大的值；为false时，检查是否有更小的值
                        if ((isMaxPoint && compareValue > currentValue) || (!isMaxPoint && compareValue < currentValue)) {
                            isExtremePoint = false;
                            break;
                        }
                    }
                }

                if (isExtremePoint) {
                    extremePointCount++;
                    if (extremePointCount == nthExtreme) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 查找permPhase数据与水平线的相交点
     * @param yValue 水平线的Y轴值
     * @return 相交点的索引列表
     */
    private List<Integer> findIntersectionPointsWithPermPhase(float yValue) {
        List<Integer> intersectionPoints = new ArrayList<>();
        
        try {
            // 获取chart2的相位数据集（索引1）
            if (mChart2.getData() != null && mChart2.getData().getDataSetCount() > 1) {
                LineDataSet permPhaseDataSet = (LineDataSet) mChart2.getData().getDataSetByIndex(1);
                
                // 遍历相位数据，查找与水平线相交的点
                for (int i = 0; i < permPhaseDataSet.getEntryCount() - 1; i++) {
                    Entry currentEntry = permPhaseDataSet.getEntryForIndex(i);
                    Entry nextEntry = permPhaseDataSet.getEntryForIndex(i + 1);
                    
                    float currentY = currentEntry.getY();
                    float nextY = nextEntry.getY();
                    
                    // 检查当前点和下一个点是否跨越水平线 
                     if ((currentY <= yValue && nextY >= yValue) || 
                         (currentY >= yValue && nextY <= yValue)) { 
                         // 计算精确的相交点索引（线性插值） 
                         float t = (yValue - currentY) / (nextY - currentY); 
                         float intersectionX = currentEntry.getX() + t * (nextEntry.getX() - currentEntry.getX()); 
                         int roundedX = Math.round(intersectionX);
                         
                         // 检查25个数据范围内是否已有点
                         boolean hasPointInRange = false;
                         for (Integer point : intersectionPoints) {
                             if (Math.abs(point - roundedX) <= 25) {
                                 hasPointInRange = true;
                                 break;
                             }
                         }
                         
                         // 检查是否为跳变点（前后数据差值大于80）
                         boolean isJumpPoint = false;
                         if (i > 0 && i < permPhaseDataSet.getEntryCount() - 2) {
                             Entry prevEntry = permPhaseDataSet.getEntryForIndex(i-1);
                             Entry nextNextEntry = permPhaseDataSet.getEntryForIndex(i+1);
                             if (Math.abs(prevEntry.getY() - currentEntry.getY()) > 80 || 
                                 Math.abs(currentEntry.getY() - nextNextEntry.getY()) > 80) {
                                 isJumpPoint = true;
                             }
                         }
                         
                         if (!hasPointInRange && !isJumpPoint) {
                             intersectionPoints.add(roundedX); 
                         }
                     }
                }
            }
        } catch (Exception e) {
            Log.e("Intersection", "查找相交点失败: " + e.getMessage());
        }
        
        Log.d("Intersection", "总共找到 " + intersectionPoints.size() + " 个相交点");
        return intersectionPoints;
    }

    /**
     * 在chart2上添加水平线
     * @param yValue 水平线的Y轴值
     */
    private void addHorizontalLineToChart2(float yValue) {
        try {
            // 获取chart2的数据集数量
            int dataSetCount = mChart2.getData().getDataSetCount();
            
            // 获取现有数据集的长度（使用第一个数据集作为参考）
            int dataLength = 0;
            if (dataSetCount > 0) {
                LineDataSet firstDataSet = (LineDataSet) mChart2.getData().getDataSetByIndex(0);
                dataLength = firstDataSet.getEntryCount();
            }
            
            // 检查是否已经存在水平线数据集（索引为2）
            if (dataSetCount > 2) {
                // 如果已经存在水平线数据集，则更新数据
                LineDataSet horizontalLineSet = (LineDataSet) mChart2.getData().getDataSetByIndex(2);
                horizontalLineSet.clear();
                
                // 添加水平线的数据点（长度与现有数据集相同）
                for (int i = 0; i < dataLength; i++) {
                    horizontalLineSet.addEntry(new Entry(i, yValue));
                }
            } else {
                // 创建新的水平线数据集
                LineDataSet horizontalLineSet = new LineDataSet(null, "水平线");
                horizontalLineSet.setColor(Color.RED); // 设置线条颜色为红色
                horizontalLineSet.setLineWidth(2f); // 设置线条宽度
                horizontalLineSet.setDrawCircles(false); // 不绘制数据点
                horizontalLineSet.setDrawValues(false); // 不显示数值
                horizontalLineSet.setDrawFilled(false); // 不填充
                
                // 添加水平线的数据点（长度与现有数据集相同）
                for (int i = 0; i < dataLength; i++) {
                    horizontalLineSet.addEntry(new Entry(i, yValue));
                }
                
                // 将水平线数据集添加到chart2
                mChart2.getData().addDataSet(horizontalLineSet);
            }
            
            // 刷新图表
            mChart2.invalidate();
            Log.d("HorizontalLine", "在chart2上添加了值为" + yValue + "的水平线，长度：" + dataLength);
            
        } catch (Exception e) {
            Log.e("HorizontalLine", "添加水平线失败: " + e.getMessage());
        }
    }

    /**
     * 创建CSV文件并保存到CrimpingSpection文件夹
     * 修改文件保存路径，确保文件保存在指定文件夹下
     */
    private void createFile(String data) {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 格式化为字符串，精确到秒
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String formattedTime = now.format(formatter);

        // 创建CrimpingSpection文件夹路径
        String folderPath = mStrPath + "/CrimpingSpection";
        File folder = new File(folderPath);

        // 确保文件夹存在，如果不存在则创建
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                Log.i("文件夹创建", "CrimpingSpection文件夹创建成功");
            } else {
                Log.e("文件夹创建", "CrimpingSpection文件夹创建失败");
                Toast.makeText(mContext, "文件夹创建失败", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String msg = "_" + evSteelcore.getText().toString();
        // 设置文件保存路径
        String savePath = folderPath + "/" + formattedTime + msg + ".csv";
        //传入路径 + 文件名
        File mFile = new File(savePath);
        try {
            // 创建文件
            if (mFile.createNewFile()) {
                Log.i("文件创建", "文件创建成功");
            } else {
                Log.w("文件创建", "文件已存在，无法创建新文件");
            }

            // 检查文件是否可写
            if (mFile.canWrite()) {
                // 使用 try-with-resources 自动关闭输出流
                try (OutputStream ff = Files.newOutputStream(mFile.toPath())) {
                    byte[] szBuf = data.getBytes();
                    ff.write(szBuf);
                    Log.i("文件写入", "数据写入成功");
                    Toast.makeText(mContext, "文件保存成功", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Log.e("文件写入", "写入数据时发生错误", e);
                    Toast.makeText(mContext, "文件写入失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("文件写入", "文件不可写");
                Toast.makeText(mContext, "文件不可写", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e("文件操作", "操作文件时发生错误", e);
            Toast.makeText(mContext, "文件生成失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 相位解缠绕算法（基于突变点检测）
     * 1. 检测所有相位突变点（超过90°变化）
     * 2. 将突变点两两配对
     * 3. 调整配对点之间的相位值
     * @param phaseData 原始相位数据
     * @return 解缠绕后的连续相位数据
     */
    private List<Float> unwrapPhaseEnhanced(List<Float> phaseData) {
        if (phaseData == null || phaseData.size() == 0) {
            return new ArrayList<>();
        }

        // 1. 检测突变点
        List<Integer> jumpPoints = new ArrayList<>();
        for (int i = 1; i < phaseData.size(); i++) {
            float diff = Math.abs(phaseData.get(i) - phaseData.get(i-1));
            if (diff > 90f) {
                jumpPoints.add(i);
            }
        }

        // 2. 复制原始数据
        List<Float> unwrappedData = new ArrayList<>(phaseData);

        // 3. 处理突变点对
        for (int i = 0; i < jumpPoints.size(); i += 2) {
            if (i+1 >= jumpPoints.size()) break;
            
            int start = jumpPoints.get(i);
            int end = jumpPoints.get(i+1);
            
            // 计算需要调整的相位偏移量
            float offset = 180f * Math.signum(unwrappedData.get(start) - unwrappedData.get(start-1));
            
            // 4. 调整区间内的相位值
            for (int j = start; j < end; j++) {
                unwrappedData.set(j, unwrappedData.get(j) - offset);
            }
        }

        return simpleMovingAverageFilter(unwrappedData, 3);
    }

    /**
     * 简单移动平均滤波
     * @param data 原始数据
     * @param windowSize 窗口大小
     * @return 滤波后的数据
     */
    private List<Float> simpleMovingAverageFilter(List<Float> data, int windowSize) {
        if (data == null || data.size() == 0 || windowSize <= 0) {
            return data;
        }
        
        List<Float> filteredData = new ArrayList<>();
        
        for (int i = 0; i < data.size(); i++) {
            float sum = 0;
            int count = 0;
            
            for (int j = Math.max(0, i - windowSize); j <= Math.min(data.size() - 1, i + windowSize); j++) {
                sum += data.get(j);
                count++;
            }
            
            filteredData.add(sum / count);
        }
        
        return filteredData;
    }

    /**
     * 调取历史数据并绘制功能
     * 从CrimpingSpection文件夹读取CSV文件数据并绘制到图表中
     */
    private void loadAndDrawHistoricalData() {
        // 创建文件选择对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择历史数据文件");
        
        // 获取CrimpingSpection文件夹中的所有CSV文件
        File folder = new File(mStrPath + "/CrimpingSpection");
        File[] csvFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        
        if (csvFiles == null || csvFiles.length == 0) {
            Toast.makeText(this, "未找到历史数据文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 按修改时间排序，最新的文件在前
        Arrays.sort(csvFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        String[] fileNames = new String[csvFiles.length];
        for (int i = 0; i < csvFiles.length; i++) {
            fileNames[i] = csvFiles[i].getName();
        }
        
        builder.setItems(fileNames, (dialog, which) -> {
            File selectedFile = csvFiles[which];
            processHistoricalData(selectedFile);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 处理历史数据文件并绘制图表
     * @param dataFile 选中的历史数据文件
     */
    private void processHistoricalData(File dataFile) {
        try {
            // 读取CSV文件内容
            String content = new String(Files.readAllBytes(dataFile.toPath()));
            String[] lines = content.split("\n");
            
            // 清空当前图表数据
            eddy_list.clear();
            perm_list.clear();
            tvResult.setText("");
            // 清空 chart1 和 chart2 的高亮
            if (mChart1 != null) {
                mChart1.highlightValues(null);
            }
            if (mChart2 != null) {
                mChart2.highlightValues(null);
            }
            
            // 创建临时列表存储所有数据点
            List<Float> eddyDataPoints = new ArrayList<>();
            List<Float> permAmplitudePoints = new ArrayList<>();
            List<Float> permPhasePoints = new ArrayList<>();
            
            // 解析CSV数据
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || i == 0) continue; // 跳过空行和标题行
                
                String[] values = line.split(",");
                if (values.length >= 3) {
                    try {
                        float eddyValue = Float.parseFloat(values[0]);
                        float permValue = Float.parseFloat(values[1]);
                        float permPhase = Float.parseFloat(values[2]);
                        
                        // 添加到数据点列表
                        eddyDataPoints.add(eddyValue);
                        permAmplitudePoints.add(permValue);
                        permPhasePoints.add(permPhase);
                        
                    } catch (NumberFormatException e) {
                        Log.e("历史数据解析", "数据格式错误: " + line);
                    }
                }
            }
            
            // 批量绘制图表数据
            if (!eddyDataPoints.isEmpty() && !permAmplitudePoints.isEmpty()) {
                // 清空现有图表数据
                runOnUiThread(() -> {
                    // 重新初始化图表管理器
                    dynamicLineChartManager1 = new DynamicLineChartManager(mChart1, Arrays.asList("涡流"), Arrays.asList(Color.RED));
                    dynamicLineChartManager2 = new DynamicLineChartManager(mChart2, Arrays.asList("幅值", "相位"), Arrays.asList(Color.MAGENTA, Color.BLUE));

                    // 重置图表范围
                    chart1_max = 5e7f;
                    chart1_min = 4e7f;
                    chart2_max = 200f;
                    chart2_min = -300f;
                    
                    // 批量添加涡流数据（包含计算后的斜率）
                    for (int i = 0; i < eddyDataPoints.size(); i++) {
                        List<Float> eddyPoint = new ArrayList<>();
                        eddyPoint.add(eddyDataPoints.get(i));
                        dynamicLineChartManager1.addEntry(eddyPoint);
                    }
                    
                    // 批量添加磁导率数据（包含计算后的斜率）
                    for (int i = 0; i < permAmplitudePoints.size(); i++) {
                        List<Float> permPoint = new ArrayList<>();
                        permPoint.add(permAmplitudePoints.get(i));
                        permPoint.add(permPhasePoints.get(i));
                        dynamicLineChartManager2.addEntry(permPoint);
                    }
                    
                    // 设置Y轴范围
                    if (eddyDataPoints.size() > 0) {
                        float eddyMax = Collections.max(eddyDataPoints);
                        float eddyMin = Collections.min(eddyDataPoints);
                        runOnUiThread(() -> {
                            dynamicLineChartManager1.setYAxis(eddyMax + 1000000, eddyMin - 1000000);
                        });
                    }
                    
                    if (permAmplitudePoints.size() > 0) {
                        float permMax = Collections.max(permAmplitudePoints);
                        float permMin = Collections.min(permAmplitudePoints);
                        runOnUiThread(() -> {
                            dynamicLineChartManager2.setYAxis(1.2f * permMax, Math.min(0, 1.2f * (-300 + permMin)));
                        });
                    }

                    // 更新数据量显示
                    total = eddyDataPoints.size();
                    tvTotal.setText(String.valueOf(total));

                    // 适配屏幕显示
                    dynamicLineChartManager1.fitScreen();
                    dynamicLineChartManager2.fitScreen();
                });
                
                // 自动分析特征点
                // FeaturePointDraw();

                tvTotal.append("    当前文件：" + dataFile.getName());

                Toast.makeText(this, "历史数据加载完成: " + dataFile.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "历史数据为空", Toast.LENGTH_SHORT).show();
            }
            
        } catch (IOException e) {
            Log.e("历史数据读取", "读取文件失败", e);
            Toast.makeText(this, "读取历史数据失败", Toast.LENGTH_SHORT).show();
        }
    }

    //////////////////////////////////  搜索设备  /////////////////////////////////////////////////
    @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
    private void searchBtDevice() {
        if(bleManager == null){
            Log.d(TAG, "searchBtDevice()-->bleManager == null");
            return;
        }

        if (bleManager.isDiscovery()) { //当前正在搜索设备...

            bleManager.stopDiscoveryDevice();
        }

        if(lvDevicesAdapter != null){
            lvDevicesAdapter.clear();  //清空列表
        }

        //开始搜索
        bleManager.startDiscoveryDevice(onDeviceSearchListener,15000);
    }

    //扫描结果回调
    private final OnDeviceSearchListener onDeviceSearchListener = new OnDeviceSearchListener() {

        @Override
        public void onDeviceFound(BLEDevice bleDevice) {
            Message message = new Message();
            message.what = DISCOVERY_DEVICE;
            message.obj = bleDevice;
            mHandler.sendMessage(message);
        }

        @Override
        public void onDiscoveryOutTime() {
            Message message = new Message();
            message.what = DISCOVERY_OUT_TIME;
            mHandler.sendMessage(message);
        }
    };

    //连接回调
    private final OnBleConnectListener onBleConnectListener = new OnBleConnectListener() {
        @Override
        public void onConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            //因为服务发现成功之后，才能通讯，所以在成功发现服务的地方表示连接成功
        }

        @Override
        public void onConnectFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, String exception, int status) {
            Message message = new Message();
            message.what = CONNECT_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onDisConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onDisConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            Message message = new Message();
            message.what = DISCONNECT_SUCCESS;
            message.obj = status;
            mHandler.sendMessage(message);
        }

        @Override
        public void onServiceDiscoverySucceed(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            //因为服务发现成功之后，才能通讯，所以在成功发现服务的地方表示连接成功
            Message message = new Message();
            message.what = CONNECT_SUCCESS;
            mHandler.sendMessage(message);
        }

        @Override
        public void onServiceDiscoveryFailed(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, String failMsg) {
            Message message = new Message();
            message.what = CONNECT_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReceiveMessage(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, BluetoothGattCharacteristic characteristic, byte[] msg) {
            Message message = new Message();
            message.what = RECEIVE_SUCCESS;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReceiveError(String errorMsg) {
            Message message = new Message();
            message.what = RECEIVE_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onWriteSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, byte[] msg) {
            Message message = new Message();
            message.what = SEND_SUCCESS;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onWriteFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, byte[] msg, String errorMsg) {
            Message message = new Message();
            message.what = SEND_FAILURE;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReadRssi(BluetoothGatt bluetoothGatt, int Rssi, int status) {

        }

        @Override
        public void onMTUSetSuccess(String successMTU, int newMtu) {

        }

        @Override
        public void onMTUSetFailure(String failMTU) {

        }
    };

    /**
     * 启动内存监控定时器
     */
    private void startMemoryMonitorTimer() {
        if (memoryMonitorTimer != null) {
            memoryMonitorTimer.cancel();
        }
        
        memoryMonitorTimer = new Timer();
        memoryMonitorTask = new TimerTask() {
            @Override
            public void run() {
                // 在UI线程中执行内存监控
                runOnUiThread(() -> {
                    monitorMemoryUsage();
                });
            }
        };
        
        // 每秒执行一次内存监控
        memoryMonitorTimer.schedule(memoryMonitorTask, 0, 1000);
    }
    
    /**
     * 停止内存监控定时器
     */
    private void stopMemoryMonitorTimer() {
        if (memoryMonitorTimer != null) {
            memoryMonitorTimer.cancel();
            memoryMonitorTimer = null;
        }
        if (memoryMonitorTask != null) {
            memoryMonitorTask.cancel();
            memoryMonitorTask = null;
        }
    }

    /**
     * 监控内存使用情况
     */
    private void monitorMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsage = (double) usedMemory / maxMemory;
        
        // 将内存使用情况转换为MB显示
        long usedMemoryMB = usedMemory / (1024 * 1024);
        long maxMemoryMB = maxMemory / (1024 * 1024);
        
        // 在日志中输出内存使用情况
        Log.d("MemoryMonitor", String.format("内存使用: %.1f%% (%dMB/%dMB)", 
            memoryUsage * 100, usedMemoryMB, maxMemoryMB));
        
        // 如果内存使用超过50%，发出警告
        if (memoryUsage > 0.5) {
            Log.w("MemoryMonitor", "内存使用过高，建议清理数据或重启应用");
            
            // 在UI线程中显示警告（可选）
            runOnUiThread(() -> {
                Toast.makeText(this, 
                    String.format("内存使用过高: %.1f%%", memoryUsage * 100), 
                    Toast.LENGTH_SHORT).show();
            });
        }
        
        // // 如果内存使用超过90%，强制清理图表数据
        // if (memoryUsage > 0.9) {
        //     Log.e("MemoryMonitor", "内存使用严重过高，强制清理数据");
            
        //     runOnUiThread(() -> {
        //         if (dynamicLineChartManager1 != null) {
        //             // 清理图表1的数据
        //             dynamicLineChartManager1.clearAllData();
        //         }
        //         if (dynamicLineChartManager2 != null) {
        //             // 清理图表2的数据
        //             dynamicLineChartManager2.clearAllData();
        //         }
                
        //         Toast.makeText(this, "内存不足，已清理图表数据", Toast.LENGTH_LONG).show();
        //     });
        // }
    }
}
