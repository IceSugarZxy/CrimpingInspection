# 更新日志

## v2.1 (2026-03-25)
- **数据校验增强**：严格验证数据包格式 `+xxxx+xxxxxxxxx`
- **数据模式优化**：十六进制直接转ASCII显示，不做分包处理
- **SPP接收优化**：使用 `yield()` 替代 `sleep(10)`，提升大数据量接收性能
- **波形绘制优化**：批量处理数据点，每10个包更新一次图表

## v2.0 (2026-03-25)
- **蓝牙通讯升级**：同时支持 BLE 和 SPP（串口）两种模式
- **双协议支持**：
  - BLE: Nordic UART Service (NUS)
  - SPP: 标准串口协议，波特率115200

---

# CrimpingInspection

高压输电线压接质量检测系统 Android 上位机应用。

## 功能特性

- **蓝牙连接**：支持 BLE 低功耗蓝牙和 SPP 串口两种通讯模式
- **数据模式**：实时显示原始接收数据（十六进制转ASCII）
- **波形模式**：实时绘制涡流和磁导率波形曲线
- **特征点分析**：自动检测铝管端点、钢管端点、钢芯间隙等参数
- **数据存储**：CSV格式导出到 `/storage/emulated/0/CrimpingSpection/`

## 数据包格式

```
+xxxx+xxxxxxxxx
│    │    │
│    │    └── eddy (9位整数)
│    └───── permeability_B (5位，可能带符号)
└────────── permeability_A (5位，可能带符号)
```

## 技术栈

- **语言**：Java
- **最低SDK**：Android 7.0 (API 24)
- **目标SDK**：Android 13 (API 33)
- **图表库**：MPAndroidChart v3.1.0
- **蓝牙**：Android Bluetooth API (BLE + SPP)

## 项目结构

```
app/src/main/java/com/icesugar/CrimpingInspection/
├── MainActivity.java          # 主界面
├── DataProcessor.java         # 数据解析处理器
├── DynamicLineChartManager.java  # 图表管理
├── ble/                       # BLE蓝牙模块
│   ├── BLEManager.java
│   └── BLEBroadcastReceiver.java
├── spp/                       # SPP串口模块
│   └── BluetoothSerialManager.java
└── util/                      # 工具类
    ├── TypeConversion.java
    └── ClsUtils.java
```

## 权限

- `BLUETOOTH` / `BLUETOOTH_ADMIN` - 基础蓝牙
- `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` - Android 12+
- `ACCESS_FINE_LOCATION` - 扫描所需
- `MANAGE_EXTERNAL_STORAGE` - 数据存储

## GitHub

https://github.com/IceSugarZxy/CrimpingInspection
