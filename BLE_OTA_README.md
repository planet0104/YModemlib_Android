# BLE OTA 升级工具使用说明

## 概述

BleOtaManager 是一个完整的 BLE（蓝牙低功耗）OTA（空中升级）管理工具，提供设备搜索、连接、固件传输等全套功能。支持 YModem 协议进行固件传输，具备完善的错误处理和状态回调机制。

## 主要特性

- ✅ **一键升级**: 简单的 API 调用完成整个 OTA 流程
- ✅ **自动化流程**: 自动搜索设备、建立连接、传输固件
- ✅ **错误编号系统**: 完善的错误分类和编号，便于问题定位
- ✅ **权限管理**: 智能的蓝牙权限检测和处理
- ✅ **多版本适配**: 支持 Android 5.0+ 到 Android 12+
- ✅ **YModem 协议**: 可靠的固件传输协议
- ✅ **后台处理**: 非阻塞的后台线程处理
- ✅ **实时反馈**: 进度更新和状态回调

## 快速开始

### 1. 基本使用（默认配置）

```java
// 1. 设置应用上下文（必须在 StartOTA 之前调用）
BleOtaManager.setContext(this);

// 2. 开始 OTA 升级（使用默认UUID配置）
BleOtaManager.StartOTA("firmware.bin", "DeviceName", new BleOtaManager.OTACallback() {
    @Override
    public void onProgress(int currentSent, int total) {
        // 更新进度显示
        int progress = (int) ((float) currentSent / total * 100);
        progressBar.setProgress(progress);
    }
    
    @Override
    public void onStatusUpdate(String status) {
        // 更新状态显示
        statusTextView.setText(status);
    }
    
    @Override
    public void onSuccess() {
        // 升级成功
        Toast.makeText(this, "固件升级成功！", Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onFailed(int errorCode, String errorMessage) {
        // 升级失败 - 根据错误编号处理
        String userMessage = handleErrorCode(errorCode, errorMessage);
        Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show();
    }
});

// 3. 停止 OTA 升级（可选）
BleOtaManager.StopOTA();
```

### 2. 高级使用（自定义UUID配置）

```java
// 创建自定义BLE配置
BleOtaManager.BleConfig customConfig = new BleOtaManager.BleConfig(
    "0000ffe0-0000-1000-8000-00805f9b34fb",  // 服务UUID
    "0000ffe1-0000-1000-8000-00805f9b34fb",  // TX特征UUID (发送数据)
    "0000ffe2-0000-1000-8000-00805f9b34fb",  // RX特征UUID (接收数据)
    "5A0007230510000000A5"                   // OTA升级指令
);

// 使用自定义配置开始升级
BleOtaManager.StartOTA("firmware.bin", "DeviceName", customConfig, otaCallback);
```

### 3. 便利方法

```java
// 根据设备名称自动选择配置（可在BleConfig.createConfigForDevice中自定义逻辑）
BleOtaManager.StartOTA("firmware.bin", "DeviceName", true, otaCallback);

// 使用便利方法创建配置（使用默认OTA指令）
BleOtaManager.BleConfig config = BleOtaManager.BleConfig.createConfig(
    "serviceUuid", "txCharUuid", "rxCharUuid"
);
```

### 4. 权限配置

在 `AndroidManifest.xml` 中添加所需权限：

```xml
<!-- Android 12 以下版本权限 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Android 12+ 版本权限 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- 蓝牙功能声明 -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### 5. 固件文件准备

将固件文件放在 `app/src/main/assets/` 目录下：

```
app/src/main/assets/
├── firmware_v1.0.bin
├── firmware_v2.0.bin
└── your_firmware.bin
```
## 错误处理系统

### 错误编号分类

BleOtaManager 提供了完善的错误编号系统，便于上层应用进行精确的错误处理：

#### 系统环境错误 (1xx)
- `101` - CONTEXT_NOT_SET: Context未设置
- `102` - BLUETOOTH_NOT_SUPPORTED: 设备不支持蓝牙
- `103` - BLE_NOT_SUPPORTED: 设备不支持BLE
- `104` - BLUETOOTH_DISABLED: 蓝牙未开启
- `105` - API_VERSION_TOO_LOW: API版本过低

#### 权限相关错误 (2xx)
- `201` - PERMISSION_DENIED: 蓝牙权限被拒绝
- `202` - PERMISSION_SCAN_DENIED: 扫描权限被拒绝
- `203` - PERMISSION_CONNECT_DENIED: 连接权限被拒绝
- `204` - PERMISSION_RUNTIME_REVOKED: 运行时权限被撤销

#### 设备连接错误 (3xx)
- `301` - DEVICE_SCAN_FAILED: 设备扫描失败
- `302` - DEVICE_NOT_FOUND: 未找到目标设备
- `303` - DEVICE_SCAN_TIMEOUT: 设备扫描超时
- `304` - DEVICE_CONNECT_FAILED: 设备连接失败
- `305` - DEVICE_DISCONNECTED: 设备连接断开
- `306` - GATT_SERVICE_DISCOVERY_FAILED: GATT服务发现失败
- `307` - GATT_SERVICE_NOT_FOUND: 未找到所需GATT服务
- `308` - GATT_CHARACTERISTIC_NOT_FOUND: 未找到所需特征

#### OTA升级错误 (4xx)
- `401` - OTA_COMMAND_SEND_FAILED: OTA指令发送失败
- `402` - OTA_MODE_ENTER_FAILED: OTA模式进入失败
- `403` - FIRMWARE_FILE_NOT_FOUND: 固件文件未找到
- `404` - FIRMWARE_COPY_FAILED: 固件文件复制失败
- `405` - YMODEM_INIT_FAILED: YModem初始化失败
- `406` - YMODEM_TRANSFER_FAILED: YModem传输失败

#### 数据传输错误 (5xx)
- `501` - BLE_WRITE_FAILED: BLE写入失败
- `502` - BLE_WRITE_QUEUE_FAILED: BLE写入队列失败
- `503` - DATA_TRANSMISSION_FAILED: 数据传输失败
- `504` - GATT_OPERATION_REJECTED: GATT操作被拒绝

#### 其他错误 (9xx)
- `901` - UNKNOWN_ERROR: 未知错误
- `902` - OPERATION_CANCELLED: 操作被取消
- `903` - TIMEOUT: 操作超时

### 错误处理示例

```java
private String handleErrorCode(int errorCode, String originalMessage) {
    switch (errorCode) {
        // 系统环境错误
        case BleOtaManager.ErrorCode.BLUETOOTH_DISABLED:
            // 引导用户开启蓝牙
            return "请先开启蓝牙";
            
        // 权限相关错误
        case BleOtaManager.ErrorCode.PERMISSION_DENIED:
        case BleOtaManager.ErrorCode.PERMISSION_SCAN_DENIED:
        case BleOtaManager.ErrorCode.PERMISSION_CONNECT_DENIED:
            // 引导用户授予权限
            return "请在设置中授予蓝牙权限";
            
        // 设备连接错误
        case BleOtaManager.ErrorCode.DEVICE_SCAN_TIMEOUT:
        case BleOtaManager.ErrorCode.DEVICE_NOT_FOUND:
            // 提示检查设备状态
            return "未找到设备，请检查设备名称和连接状态";
            
        // OTA升级错误
        case BleOtaManager.ErrorCode.FIRMWARE_FILE_NOT_FOUND:
        case BleOtaManager.ErrorCode.FIRMWARE_COPY_FAILED:
            // 提示检查固件文件
            return "固件文件不存在或损坏";
            
        default:
            return "升级失败：" + originalMessage;
    }
}
```

## API说明

### 静态方法

#### `setContext(Context context)`
设置应用上下文，必须在使用其他功能前调用。

#### `StartOTA(String binName, String bleDeviceName, OTACallback callback)`
开始OTA升级流程（使用默认配置）。

**参数：**
- `binName`: 固件文件名（在assets目录中）
- `bleDeviceName`: 目标BLE设备名称
- `callback`: 升级回调接口

#### `StartOTA(String binName, String bleDeviceName, boolean autoConfig, OTACallback callback)`
开始OTA升级流程（可选择根据设备名称自动配置）。

**参数：**
- `binName`: 固件文件名（在assets目录中）
- `bleDeviceName`: 目标BLE设备名称
- `autoConfig`: 是否根据设备名称自动选择配置
- `callback`: 升级回调接口

#### `StartOTA(String binName, String bleDeviceName, BleConfig bleConfig, OTACallback callback)`
开始OTA升级流程（使用自定义配置）。

**参数：**
- `binName`: 固件文件名（在assets目录中）
- `bleDeviceName`: 目标BLE设备名称
- `bleConfig`: BLE设备配置对象
- `callback`: 升级回调接口

#### `StopOTA()`
停止当前的OTA升级流程。

### BLE配置类

#### `BleConfig`

用于配置BLE设备的UUID和OTA指令：

```java
public static class BleConfig {
    // 构造函数
    public BleConfig(String serviceUuid, String txCharacteristicUuid, 
                     String rxCharacteristicUuid, String otaCommand);
    
    // 静态方法
    public static BleConfig getDefaultConfig();              // 获取默认配置
    public static BleConfig createConfig(String serviceUuid, 
                                       String txCharUuid, 
                                       String rxCharUuid);   // 创建配置（使用默认OTA指令）
    public static BleConfig createConfigForDevice(String deviceName); // 根据设备名称创建配置
}
```

### 回调接口

#### `OTACallback`

```java
public interface OTACallback {
    void onProgress(int currentSent, int total);     // 进度更新
    void onStatusUpdate(String status);              // 状态更新
    void onSuccess();                                // 升级成功
    void onFailed(int errorCode, String errorMessage); // 升级失败（新增错误编号）
}
```

### 错误编号常量

可以通过 `BleOtaManager.ErrorCode` 类访问所有错误编号常量：

```java
// 使用示例
if (errorCode == BleOtaManager.ErrorCode.BLUETOOTH_DISABLED) {
    // 处理蓝牙未开启的情况
}
```

## 高级用法

### 1. 自定义 UUID 配置

现在你可以为不同的设备配置不同的UUID，而不需要修改源代码：

```java
// 方法1：直接创建配置
BleOtaManager.BleConfig config = new BleOtaManager.BleConfig(
    "0000ffe0-0000-1000-8000-00805f9b34fb",  // 服务UUID
    "0000ffe1-0000-1000-8000-00805f9b34fb",  // TX特征UUID
    "0000ffe2-0000-1000-8000-00805f9b34fb",  // RX特征UUID
    "5A0007230510000000A5"                   // OTA指令
);

// 方法2：使用便利方法（使用默认OTA指令）
BleOtaManager.BleConfig config = BleOtaManager.BleConfig.createConfig(
    "serviceUuid", "txCharUuid", "rxCharUuid"
);

// 方法3：根据设备名称自动选择（需要在createConfigForDevice方法中自定义逻辑）
BleOtaManager.BleConfig config = BleOtaManager.BleConfig.createConfigForDevice("DeviceName");
```

### 2. 多设备适配

你可以在 `BleConfig.createConfigForDevice()` 方法中添加逻辑来支持不同的设备：

```java
// 在BleOtaManager.java的createConfigForDevice方法中添加：
public static BleConfig createConfigForDevice(String deviceName) {
    if (deviceName.startsWith("Device_A")) {
        return new BleConfig(
            "uuid_for_device_a_service",
            "uuid_for_device_a_tx", 
            "uuid_for_device_a_rx",
            "ota_command_for_device_a"
        );
    } else if (deviceName.startsWith("Device_B")) {
        return new BleConfig(
            "uuid_for_device_b_service",
            "uuid_for_device_b_tx", 
            "uuid_for_device_b_rx",
            "ota_command_for_device_b"
        );
    }
    return getDefaultConfig(); // 默认配置
}
```

### 3. 自定义 OTA 指令

修改 OTA 升级模式指令（现在通过配置对象）：

```java
// 创建带有自定义OTA指令的配置
BleOtaManager.BleConfig config = new BleOtaManager.BleConfig(
    "serviceUuid", "txCharUuid", "rxCharUuid", 
    "YOUR_CUSTOM_OTA_COMMAND_HEX"  // 自定义OTA指令
);
```

### 4. 自定义扫描超时

修改设备扫描超时时间：

```java
private static final long SCAN_PERIOD = 10000; // 10秒扫描时间
```

### 5. 状态监听示例

```java
@Override
public void onStatusUpdate(String status) {
    Log.d("OTA", "状态更新: " + status);
    
    // 根据状态执行不同操作
    if (status.contains("搜索")) {
        showSearchingAnimation();
    } else if (status.contains("连接")) {
        showConnectingAnimation();
    } else if (status.contains("传输")) {
        showTransferringAnimation();
    }
}
```

### 6. 权限请求处理

```java
public void requestBluetoothPermissions() {
    String[] permissions;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        };
    } else {
        permissions = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
    }
    
    ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
}
```

## 完整示例

### MainActivity 示例

```java
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private ProgressBar progressBar;
    private TextView statusTextView;
    private TextView progressTextView;
    private Button startButton;
    private Button stopButton;
    private boolean isOtaRunning = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        
        // 设置上下文
        BleOtaManager.setContext(this);
    }
    
    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.tvStatus);
        progressTextView = findViewById(R.id.tvProgress);
        startButton = findViewById(R.id.btnStart);
        stopButton = findViewById(R.id.btnStop);
        
        startButton.setOnClickListener(v -> startOtaUpgrade());
        stopButton.setOnClickListener(v -> stopOtaUpgrade());
    }
    
    private void startOtaUpgrade() {
        if (isOtaRunning) return;
        
        String firmwareName = "firmware.bin";
        String deviceName = "YourDeviceName";
        
        isOtaRunning = true;
        updateUI();
        resetProgress();
        
        BleOtaManager.StartOTA(firmwareName, deviceName, new BleOtaManager.OTACallback() {
            @Override
            public void onProgress(int currentSent, int total) {
                updateProgress(currentSent, total);
            }
            
            @Override
            public void onStatusUpdate(String status) {
                updateStatus(status);
            }
            
            @Override
            public void onSuccess() {
                isOtaRunning = false;
                updateUI();
                updateStatus("固件升级成功！");
                Toast.makeText(MainActivity.this, "固件升级成功！", Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onFailed(int errorCode, String errorMessage) {
                isOtaRunning = false;
                updateUI();
                
                String userMessage = getErrorMessage(errorCode, errorMessage);
                updateStatus("升级失败 [" + errorCode + "]：" + userMessage);
                Toast.makeText(MainActivity.this, userMessage, Toast.LENGTH_LONG).show();
                
                // 根据错误类型执行特定操作
                handleSpecificError(errorCode);
            }
        });
    }
    
    private void stopOtaUpgrade() {
        if (!isOtaRunning) return;
        
        BleOtaManager.StopOTA();
        isOtaRunning = false;
        updateUI();
        updateStatus("OTA升级已停止");
        resetProgress();
    }
    
    private void updateUI() {
        startButton.setEnabled(!isOtaRunning);
        stopButton.setEnabled(isOtaRunning);
        startButton.setText(isOtaRunning ? "升级进行中..." : "开始OTA升级");
    }
    
    private void updateStatus(String status) {
        statusTextView.setText("状态：" + status);
        Log.d(TAG, "状态更新: " + status);
    }
    
    private void updateProgress(int currentSent, int total) {
        int progressPercent = (int) ((float) currentSent / total * 100);
        progressBar.setProgress(progressPercent);
        progressTextView.setText(progressPercent + "% (" + currentSent + "/" + total + " 字节)");
    }
    
    private void resetProgress() {
        progressBar.setProgress(0);
        progressTextView.setText("0%");
    }
    
    private String getErrorMessage(int errorCode, String originalMessage) {
        switch (errorCode) {
            // 系统环境错误
            case BleOtaManager.ErrorCode.CONTEXT_NOT_SET:
                return "系统初始化失败";
            case BleOtaManager.ErrorCode.BLUETOOTH_NOT_SUPPORTED:
                return "设备不支持蓝牙功能";
            case BleOtaManager.ErrorCode.BLE_NOT_SUPPORTED:
                return "设备不支持蓝牙低功耗功能";
            case BleOtaManager.ErrorCode.BLUETOOTH_DISABLED:
                return "请先开启蓝牙";
            case BleOtaManager.ErrorCode.API_VERSION_TOO_LOW:
                return "系统版本过低，不支持该功能";
                
            // 权限相关错误
            case BleOtaManager.ErrorCode.PERMISSION_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_SCAN_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_CONNECT_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_RUNTIME_REVOKED:
                return "请在设置中授予蓝牙相关权限";
                
            // 设备连接错误
            case BleOtaManager.ErrorCode.DEVICE_SCAN_FAILED:
                return "设备扫描失败，请重试";
            case BleOtaManager.ErrorCode.DEVICE_NOT_FOUND:
            case BleOtaManager.ErrorCode.DEVICE_SCAN_TIMEOUT:
                return "未找到设备，请检查设备名称和状态";
            case BleOtaManager.ErrorCode.DEVICE_CONNECT_FAILED:
                return "设备连接失败，请重试";
            case BleOtaManager.ErrorCode.DEVICE_DISCONNECTED:
                return "设备连接断开";
            case BleOtaManager.ErrorCode.GATT_SERVICE_DISCOVERY_FAILED:
            case BleOtaManager.ErrorCode.GATT_SERVICE_NOT_FOUND:
            case BleOtaManager.ErrorCode.GATT_CHARACTERISTIC_NOT_FOUND:
                return "设备服务不兼容";
                
            // OTA升级错误
            case BleOtaManager.ErrorCode.OTA_COMMAND_SEND_FAILED:
            case BleOtaManager.ErrorCode.OTA_MODE_ENTER_FAILED:
                return "设备进入升级模式失败";
            case BleOtaManager.ErrorCode.FIRMWARE_FILE_NOT_FOUND:
            case BleOtaManager.ErrorCode.FIRMWARE_COPY_FAILED:
                return "固件文件不存在或损坏";
            case BleOtaManager.ErrorCode.YMODEM_INIT_FAILED:
            case BleOtaManager.ErrorCode.YMODEM_TRANSFER_FAILED:
                return "固件传输失败";
                
            // 数据传输错误
            case BleOtaManager.ErrorCode.BLE_WRITE_FAILED:
            case BleOtaManager.ErrorCode.BLE_WRITE_QUEUE_FAILED:
            case BleOtaManager.ErrorCode.DATA_TRANSMISSION_FAILED:
            case BleOtaManager.ErrorCode.GATT_OPERATION_REJECTED:
                return "数据传输失败";
                
            // 其他错误
            case BleOtaManager.ErrorCode.OPERATION_CANCELLED:
                return "操作已取消";
            case BleOtaManager.ErrorCode.TIMEOUT:
                return "操作超时";
            case BleOtaManager.ErrorCode.UNKNOWN_ERROR:
            default:
                return "升级失败：" + originalMessage;
        }
    }
    
    private void handleSpecificError(int errorCode) {
        switch (errorCode) {
            case BleOtaManager.ErrorCode.BLUETOOTH_DISABLED:
                // 引导用户开启蓝牙
                showBluetoothEnableDialog();
                break;
            case BleOtaManager.ErrorCode.PERMISSION_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_SCAN_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_CONNECT_DENIED:
                // 引导用户授予权限
                showPermissionDialog();
                break;
            default:
                // 其他错误的通用处理
                break;
        }
    }
    
    private void showBluetoothEnableDialog() {
        // 实现蓝牙开启引导对话框
    }
    
    private void showPermissionDialog() {
        // 实现权限申请引导对话框
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isOtaRunning) {
            BleOtaManager.StopOTA();
        }
    }
}
```

## 常见问题

### Q1: 升级过程中出现权限错误怎么办？
**A:** 检查是否已授予所有必要的蓝牙权限，特别是 Android 12+ 版本的新权限。

### Q2: 找不到设备怎么办？
**A:** 
- 确认设备名称拼写正确
- 确认设备处于可连接状态
- 检查设备是否在蓝牙范围内
- 尝试重启蓝牙

### Q3: 固件传输失败怎么办？
**A:**
- 检查固件文件是否存在于 assets 目录
- 确认固件文件格式正确
- 检查设备是否支持该固件版本

### Q4: 如何自定义设备的 UUID？
**A:** 修改 `BleOtaManager.java` 中的 UUID 常量，使其匹配你的设备。

### Q5: 如何调试 OTA 升级过程？
**A:** 
- 查看 Logcat 输出，标签为 "BleOtaManager"
- 使用状态回调监听升级过程
- 根据错误编号快速定位问题

## 版本兼容性

- **最低支持版本**: Android 5.0 (API 21)
- **推荐版本**: Android 6.0+ (API 23) 以获得更好的权限管理体验
- **测试版本**: Android 5.0 - Android 13

## 技术架构

### 核心组件
- **BleOtaManager**: 主管理类，提供静态 API
- **YModem**: 固件传输协议实现
- **后台线程**: 非阻塞的蓝牙操作处理
- **权限管理**: 智能的权限检测和处理

### 工作流程
1. 权限检查 → 2. 蓝牙初始化 → 3. 设备扫描 → 4. 设备连接 
5. 服务发现 → 6. 特征配置 → 7. 发送OTA指令 → 8. YModem传输 → 9. 完成升级

## 注意事项

1. **Context 设置**: 必须在调用 `StartOTA` 之前调用 `setContext()`
2. **权限申请**: 确保在运行时已获得所有必要权限
3. **固件文件**: 固件文件必须放在 `assets` 目录下
4. **设备状态**: 确保目标设备处于可连接状态
5. **线程安全**: 所有回调都在主线程中执行，可直接更新UI
6. **资源释放**: 在 Activity 销毁时调用 `StopOTA()` 释放资源

## 开源协议

本项目遵循 MIT 开源协议，可自由使用和修改。

---

**开发团队**: YModem BLE OTA Team  
**最后更新**: 2025年8月6日  
**版本**: v2.0.0
