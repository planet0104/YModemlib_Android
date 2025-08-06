package com.bw.ym.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.core.content.ContextCompat;

import com.bw.yml.YModem;
import com.bw.yml.YModemListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BLE OTA升级管理器
 * 提供完整的BLE设备固件升级功能
 * 
 * 基本使用方式（使用默认配置）：
 * BleOtaManager.StartOTA("firmware.bin", "DeviceName", new OTACallback() {
 *     @Override
 *     public void onProgress(int currentSent, int total) {
 *         // 更新进度
 *     }
 *     
 *     @Override
 *     public void onStatusUpdate(String status) {
 *         // 状态更新
 *     }
 *     
 *     @Override
 *     public void onSuccess() {
 *         // 升级成功
 *     }
 *     
 *     @Override
 *     public void onFailed(int errorCode, String errorMessage) {
 *         // 升级失败
 *     }
 * });
 * 
 * 自定义BLE配置使用方式：
 * BleConfig config = new BleConfig(
 *     "serviceUuid", 
 *     "txCharacteristicUuid", 
 *     "rxCharacteristicUuid", 
 *     "otaCommand"
 * );
 * BleOtaManager.StartOTA("firmware.bin", "DeviceName", config, callback);
 */
public class BleOtaManager {
    
    private static final String TAG = "BleOtaManager";
    
    /**
     * BLE设备配置类
     */
    public static class BleConfig {
        private final String serviceUuid;
        private final String txCharacteristicUuid;
        private final String rxCharacteristicUuid;
        private final String otaCommand;
        
        public BleConfig(String serviceUuid, String txCharacteristicUuid, String rxCharacteristicUuid, String otaCommand) {
            this.serviceUuid = serviceUuid;
            this.txCharacteristicUuid = txCharacteristicUuid;
            this.rxCharacteristicUuid = rxCharacteristicUuid;
            this.otaCommand = otaCommand;
        }
        
        public String getServiceUuid() { return serviceUuid; }
        public String getTxCharacteristicUuid() { return txCharacteristicUuid; }
        public String getRxCharacteristicUuid() { return rxCharacteristicUuid; }
        public String getOtaCommand() { return otaCommand; }
        
        /**
         * 创建默认配置
         */
        public static BleConfig getDefaultConfig() {
            return new BleConfig(
                "0000ffe0-0000-1000-8000-00805f9b34fb",  // SERVICE_UUID
                "0000ffe1-0000-1000-8000-00805f9b34fb",  // TX_CHARACTERISTIC_UUID
                "0000ffe2-0000-1000-8000-00805f9b34fb",  // RX_CHARACTERISTIC_UUID
                "5A0007230510000000A5"                   // OTA_COMMAND
            );
        }
        
        /**
         * 创建自定义配置（使用默认OTA指令）
         */
        public static BleConfig createConfig(String serviceUuid, String txCharacteristicUuid, String rxCharacteristicUuid) {
            return new BleConfig(serviceUuid, txCharacteristicUuid, rxCharacteristicUuid, "5A0007230510000000A5");
        }
        
        /**
         * 根据设备名称创建预设配置
         * 这里可以根据不同的设备名称返回对应的UUID配置
         */
        public static BleConfig createConfigForDevice(String deviceName) {
            // 这里可以根据设备名称返回不同的配置
            // 例如：根据设备名称的前缀或特定字符串匹配
            
            // 默认返回通用配置
            return getDefaultConfig();
            
            // 示例：不同设备的配置
            /*
            if (deviceName.startsWith("Device_A")) {
                return new BleConfig(
                    "service_uuid_for_device_a",
                    "tx_char_uuid_for_device_a", 
                    "rx_char_uuid_for_device_a",
                    "ota_command_for_device_a"
                );
            } else if (deviceName.startsWith("Device_B")) {
                return new BleConfig(
                    "service_uuid_for_device_b",
                    "tx_char_uuid_for_device_b", 
                    "rx_char_uuid_for_device_b",
                    "ota_command_for_device_b"
                );
            }
            */
        }
    }
    
    // OTA升级相关
    private static final long SCAN_PERIOD = 10000; // 10秒扫描时间
    private static final int MAX_PACKET_SIZE = 20; // BLE MTU限制
    
    // 错误编号定义
    public static final class ErrorCode {
        // 系统环境错误 (1xx)
        public static final int CONTEXT_NOT_SET = 101;              // Context未设置
        public static final int BLUETOOTH_NOT_SUPPORTED = 102;      // 设备不支持蓝牙
        public static final int BLE_NOT_SUPPORTED = 103;            // 设备不支持BLE
        public static final int BLUETOOTH_DISABLED = 104;           // 蓝牙未开启
        public static final int API_VERSION_TOO_LOW = 105;          // API版本过低
        
        // 权限相关错误 (2xx)
        public static final int PERMISSION_DENIED = 201;            // 蓝牙权限被拒绝
        public static final int PERMISSION_SCAN_DENIED = 202;       // 扫描权限被拒绝
        public static final int PERMISSION_CONNECT_DENIED = 203;    // 连接权限被拒绝
        public static final int PERMISSION_RUNTIME_REVOKED = 204;   // 运行时权限被撤销
        
        // 设备连接错误 (3xx)
        public static final int DEVICE_SCAN_FAILED = 301;           // 设备扫描失败
        public static final int DEVICE_NOT_FOUND = 302;             // 未找到目标设备
        public static final int DEVICE_SCAN_TIMEOUT = 303;          // 设备扫描超时
        public static final int DEVICE_CONNECT_FAILED = 304;        // 设备连接失败
        public static final int DEVICE_DISCONNECTED = 305;          // 设备连接断开
        public static final int GATT_SERVICE_DISCOVERY_FAILED = 306; // GATT服务发现失败
        public static final int GATT_SERVICE_NOT_FOUND = 307;       // 未找到所需GATT服务
        public static final int GATT_CHARACTERISTIC_NOT_FOUND = 308; // 未找到所需特征
        
        // OTA升级错误 (4xx)
        public static final int OTA_COMMAND_SEND_FAILED = 401;      // OTA指令发送失败
        public static final int OTA_MODE_ENTER_FAILED = 402;        // OTA模式进入失败
        public static final int FIRMWARE_FILE_NOT_FOUND = 403;      // 固件文件未找到
        public static final int FIRMWARE_COPY_FAILED = 404;         // 固件文件复制失败
        public static final int YMODEM_INIT_FAILED = 405;           // YModem初始化失败
        public static final int YMODEM_TRANSFER_FAILED = 406;       // YModem传输失败
        
        // 数据传输错误 (5xx)
        public static final int BLE_WRITE_FAILED = 501;             // BLE写入失败
        public static final int BLE_WRITE_QUEUE_FAILED = 502;       // BLE写入队列失败
        public static final int DATA_TRANSMISSION_FAILED = 503;     // 数据传输失败
        public static final int GATT_OPERATION_REJECTED = 504;      // GATT操作被拒绝
        
        // 其他错误 (9xx)
        public static final int UNKNOWN_ERROR = 901;                // 未知错误
        public static final int OPERATION_CANCELLED = 902;          // 操作被取消
        public static final int TIMEOUT = 903;                      // 操作超时
    }
    
    // 静态实例变量
    private static BleOtaManager instance;
    private static final Object lock = new Object();
    
    // 成员变量
    private Context context;
    private OTACallback otaCallback;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Handler mainHandler;
    
    // BLE配置
    private BleConfig bleConfig;
    
    // 蓝牙相关
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothDevice targetDevice;
    
    // 状态变量
    private boolean isScanning = false;
    private boolean isOtaModeEntered = false;
    private String targetDeviceName;
    private String firmwareFileName;
    
    // YModem相关
    private YModem yModem;
    
    // BLE写入队列管理
    private ConcurrentLinkedQueue<byte[]> writeQueue = new ConcurrentLinkedQueue<>();
    private AtomicBoolean isWriting = new AtomicBoolean(false);
    
    /**
     * OTA升级回调接口
     */
    public interface OTACallback {
        /**
         * 进度更新
         * @param currentSent 已发送字节数
         * @param total 总字节数
         */
        void onProgress(int currentSent, int total);
        
        /**
         * 状态更新
         * @param status 状态信息
         */
        void onStatusUpdate(String status);
        
        /**
         * 升级成功
         */
        void onSuccess();
        
        /**
         * 升级失败
         * @param errorCode 错误编号
         * @param errorMessage 错误信息
         */
        void onFailed(int errorCode, String errorMessage);
    }
    
    /**
     * 私有构造函数
     */
    private BleOtaManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 获取单例实例
     */
    private static BleOtaManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new BleOtaManager();
                }
            }
        }
        return instance;
    }

    /**
     * 开始OTA升级（使用默认配置）
     * @param otaFilePath 固件文件完整的路径名
     * @param bleDeviceName BLE设备名称
     * @param callback 升级回调
     */
    public static void StartOTA(String otaFilePath, String bleDeviceName, OTACallback callback) {
        StartOTA(otaFilePath, bleDeviceName, BleConfig.getDefaultConfig(), callback);
    }
    
    /**
     * 开始OTA升级（根据设备名称自动选择配置）
     * @param otaFilePath 固件文件完整的路径名
     * @param bleDeviceName BLE设备名称
     * @param autoConfig 是否根据设备名称自动选择配置
     * @param callback 升级回调
     */
    public static void StartOTA(String otaFilePath, String bleDeviceName, boolean autoConfig, OTACallback callback) {
        BleConfig config = autoConfig ? BleConfig.createConfigForDevice(bleDeviceName) : BleConfig.getDefaultConfig();
        StartOTA(otaFilePath, bleDeviceName, config, callback);
    }
    
    /**
     * 开始OTA升级（使用自定义配置）
     * @param otaFilePath 固件文件完整的路径名
     * @param bleDeviceName BLE设备名称
     * @param bleConfig BLE设备配置
     * @param callback 升级回调
     */
    public static void StartOTA(String otaFilePath, String bleDeviceName, BleConfig bleConfig, OTACallback callback) {
        BleOtaManager manager = getInstance();
        manager.startOtaInternal(otaFilePath, bleDeviceName, bleConfig, callback);
    }
    
    /**
     * 停止OTA升级
     */
    public static void StopOTA() {
        BleOtaManager manager = getInstance();
        manager.stopOtaInternal();
    }
    
    /**
     * 内部开始OTA升级方法
     */
    private void startOtaInternal(String binName, String bleDeviceName, BleConfig bleConfig, OTACallback callback) {
        this.firmwareFileName = binName;
        this.targetDeviceName = bleDeviceName;
        this.bleConfig = bleConfig;
        this.otaCallback = callback;
        
        // 停止之前的升级
        stopOtaInternal();
        
        // 启动后台线程
        startBackgroundThread();
        
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                startOtaProcess();
            }
        });
    }
    
    /**
     * 内部停止OTA升级方法
     * 断开所有蓝牙连接并清理所有相关资源
     */
    private void stopOtaInternal() {
        android.util.Log.d(TAG, "开始停止OTA升级流程...");
        
        // 停止YModem传输
        if (yModem != null) {
            android.util.Log.d(TAG, "停止YModem传输");
            yModem.stop();
            yModem = null;
        }
        
        // 断开BLE连接
        disconnectDevice();
        
        // 停止BLE扫描
        stopLeScan();
        
        // 停止后台线程
        stopBackgroundThread();
        
        // 清理状态
        resetState();
        
        android.util.Log.d(TAG, "OTA升级流程已完全停止，所有资源已清理");
        updateStatus("OTA升级已停止");
    }
    
    /**
     * 启动后台线程
     */
    private void startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("BleOtaThread");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }
    
    /**
     * 停止后台线程
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                android.util.Log.e(TAG, "停止后台线程失败", e);
            }
        }
    }
    
    /**
     * 重置状态
     */
    private void resetState() {
        isScanning = false;
        isOtaModeEntered = false;
        targetDevice = null;
        writeCharacteristic = null;
        notifyCharacteristic = null;
        writeQueue.clear();
        isWriting.set(false);
    }
    
    /**
     * 开始OTA升级流程
     */
    private void startOtaProcess() {
        updateStatus("开始OTA升级流程...");
        
        // 获取应用上下文
        // 注意：这里需要在调用StartOTA之前设置context
        if (context == null) {
            failWithReason(ErrorCode.CONTEXT_NOT_SET, "Context未设置，请先调用setContext方法");
            return;
        }
        
        // 检查蓝牙权限
        String permissionError = checkBluetoothPermissionsDetailed();
        if (permissionError != null) {
            failWithReason(ErrorCode.PERMISSION_DENIED, "蓝牙权限检查失败：" + permissionError);
            return;
        }
        
        // 初始化蓝牙
        if (!initBluetooth()) {
            // initBluetooth方法内部已经调用了具体的failWithReason
            return;
        }
        
        // 开始搜索设备
        searchBtDevice();
    }
    
    /**
     * 设置上下文（必须在StartOTA之前调用）
     */
    public static void setContext(Context context) {
        getInstance().context = context.getApplicationContext();
    }
    
    /**
     * 检查蓝牙权限
     */
    private boolean checkBluetoothPermissions() {
        String[] bluetoothPermissions = getRequiredBluetoothPermissions();
        
        for (String permission : bluetoothPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 详细检查蓝牙权限，返回具体的错误信息
     * @return null表示权限检查通过，非null表示具体的权限错误信息
     */
    private String checkBluetoothPermissionsDetailed() {
        String[] bluetoothPermissions = getRequiredBluetoothPermissions();
        StringBuilder missingPermissions = new StringBuilder();
        
        for (String permission : bluetoothPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != 
                PackageManager.PERMISSION_GRANTED) {
                if (missingPermissions.length() > 0) {
                    missingPermissions.append(", ");
                }
                missingPermissions.append(getPermissionDisplayName(permission));
            }
        }
        
        if (missingPermissions.length() > 0) {
            return "缺少以下权限: " + missingPermissions.toString() + 
                   "。请在应用设置中授予这些权限后重试。";
        }
        
        return null; // 权限检查通过
    }
    
    /**
     * 获取所需的蓝牙权限列表
     */
    private String[] getRequiredBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要的权限
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            // Android 12 以下版本需要的权限
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }
    
    /**
     * 获取权限的显示名称
     */
    private String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.BLUETOOTH_SCAN:
                return "蓝牙扫描权限";
            case Manifest.permission.BLUETOOTH_CONNECT:
                return "蓝牙连接权限";
            case Manifest.permission.BLUETOOTH_ADVERTISE:
                return "蓝牙广播权限";
            case Manifest.permission.BLUETOOTH:
                return "蓝牙权限";
            case Manifest.permission.BLUETOOTH_ADMIN:
                return "蓝牙管理权限";
            case Manifest.permission.ACCESS_FINE_LOCATION:
                return "精确位置权限";
            case Manifest.permission.ACCESS_COARSE_LOCATION:
                return "大致位置权限";
            default:
                return permission;
        }
    }
    
    /**
     * 初始化蓝牙
     */
    private boolean initBluetooth() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                updateStatus("蓝牙管理器获取失败：设备可能不支持蓝牙");
                failWithReason(ErrorCode.BLUETOOTH_NOT_SUPPORTED, "蓝牙管理器获取失败：设备可能不支持蓝牙");
                return false;
            }
            
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null) {
                updateStatus("蓝牙适配器获取失败：设备不支持蓝牙功能");
                failWithReason(ErrorCode.BLUETOOTH_NOT_SUPPORTED, "蓝牙适配器获取失败：设备不支持蓝牙功能");
                return false;
            }
            
            if (!bluetoothAdapter.isEnabled()) {
                updateStatus("蓝牙未开启：请在系统设置中开启蓝牙后重试");
                failWithReason(ErrorCode.BLUETOOTH_DISABLED, "蓝牙未开启：请在系统设置中开启蓝牙后重试");
                return false;
            }
            
            // 检查BLE扫描API是否可用（API 21+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothLeScanner == null) {
                    updateStatus("BLE扫描器获取失败：设备可能不支持BLE功能");
                    failWithReason(ErrorCode.BLE_NOT_SUPPORTED, "BLE扫描器获取失败：设备可能不支持BLE功能");
                    return false;
                }
                updateStatus("蓝牙初始化成功");
                return true;
            } else {
                // API 21以下版本不支持BLE扫描
                updateStatus("设备不支持BLE扫描：需要Android 5.0或更高版本");
                failWithReason(ErrorCode.API_VERSION_TOO_LOW, "设备不支持BLE扫描：需要Android 5.0或更高版本");
                return false;
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "蓝牙初始化异常", e);
            updateStatus("蓝牙初始化异常：" + e.getMessage());
            failWithReason(ErrorCode.BLUETOOTH_NOT_SUPPORTED, "蓝牙初始化异常：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 搜索BLE设备
     */
    @SuppressLint("MissingPermission")
    private void searchBtDevice() {
        if (isScanning) {
            updateStatus("正在搜索BLE设备中...");
            return;
        }
        
        // 检查API版本
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            failWithReason(ErrorCode.API_VERSION_TOO_LOW, "设备不支持BLE扫描（需要Android 5.0+）");
            return;
        }
        
        // 再次检查蓝牙权限（运行时可能被撤销）
        String permissionError = checkBluetoothPermissionsDetailed();
        if (permissionError != null) {
            failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "搜索设备时权限检查失败：" + permissionError);
            return;
        }
        
        updateStatus("开始搜索BLE设备：" + targetDeviceName);
        
        // 设置扫描超时
        backgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    stopLeScan();
                    if (targetDevice == null) {
                        failWithReason(ErrorCode.DEVICE_SCAN_TIMEOUT, "搜索超时：未找到目标设备 '" + targetDeviceName + "'。请确认设备名称正确且设备处于可连接状态。");
                    }
                }
            }
        }, SCAN_PERIOD);
        
        try {
            isScanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
            updateStatus("BLE设备扫描已启动，正在搜索 '" + targetDeviceName + "'...");
        } catch (SecurityException e) {
            isScanning = false;
            failWithReason(ErrorCode.PERMISSION_SCAN_DENIED, "启动BLE扫描失败：权限不足 - " + e.getMessage());
        } catch (Exception e) {
            isScanning = false;
            failWithReason(ErrorCode.DEVICE_SCAN_FAILED, "启动BLE扫描失败：" + e.getMessage());
        }
    }
    
    /**
     * BLE设备扫描回调
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null) {
                updateStatus("发现BLE设备：" + device.getName());
                
                if (targetDeviceName.equals(device.getName())) {
                    updateStatus("找到目标BLE设备：" + device.getName());
                    targetDevice = device;
                    
                    // 停止扫描
                    stopLeScan();
                    
                    // 尝试连接
                    connectToDevice(device);
                }
            }
        }
        
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                onScanResult(1, result);
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            isScanning = false;
            failWithReason(ErrorCode.DEVICE_SCAN_FAILED, "BLE扫描失败，错误码：" + errorCode);
        }
    };
    
    /**
     * 停止BLE扫描
     */
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopLeScan() {
        if (isScanning && bluetoothLeScanner != null && checkBluetoothPermissions()) {
            isScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
            updateStatus("BLE设备扫描已停止");
        }
    }
    
    /**
     * 连接到指定的BLE设备
     */
    @SuppressLint("MissingPermission")
    private void connectToDevice(final BluetoothDevice device) {
        // 检查权限
        String permissionError = checkBluetoothPermissionsDetailed();
        if (permissionError != null) {
            failWithReason(ErrorCode.PERMISSION_CONNECT_DENIED, "连接设备时权限检查失败：" + permissionError);
            return;
        }
        
        updateStatus("正在连接BLE设备：" + device.getName());
        
        try {
            // 连接到GATT服务器
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            if (bluetoothGatt == null) {
                failWithReason(ErrorCode.DEVICE_CONNECT_FAILED, "创建GATT连接失败：设备可能不支持GATT服务");
            }
        } catch (SecurityException e) {
            failWithReason(ErrorCode.PERMISSION_CONNECT_DENIED, "连接BLE设备失败：权限不足 - " + e.getMessage());
        } catch (Exception e) {
            failWithReason(ErrorCode.DEVICE_CONNECT_FAILED, "连接BLE设备失败：" + e.getMessage());
        }
    }
    
    /**
     * GATT回调处理连接、服务发现、特征读写等
     */
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateStatus("BLE设备连接成功，正在发现服务...");
                
                // 检查权限后开始发现服务
                String permissionError = checkBluetoothPermissionsDetailed();
                if (permissionError != null) {
                    failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "服务发现时权限检查失败：" + permissionError);
                    return;
                }
                
                try {
                    boolean discoverResult = gatt.discoverServices();
                    if (!discoverResult) {
                        failWithReason(ErrorCode.GATT_SERVICE_DISCOVERY_FAILED, "启动服务发现失败：GATT操作被拒绝");
                    }
                } catch (SecurityException e) {
                    failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "服务发现失败：权限不足 - " + e.getMessage());
                } catch (Exception e) {
                    failWithReason(ErrorCode.GATT_SERVICE_DISCOVERY_FAILED, "服务发现失败：" + e.getMessage());
                }
                
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateStatus("BLE设备连接已断开");
                
                // 重置OTA状态
                isOtaModeEntered = false;
                writeCharacteristic = null;
                notifyCharacteristic = null;
                
                failWithReason(ErrorCode.DEVICE_DISCONNECTED, "BLE设备连接断开");
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateStatus("BLE服务发现成功，正在配置特征...");
                
                // 查找所需的服务和特征
                BluetoothGattService service = gatt.getService(UUID.fromString(bleConfig.getServiceUuid()));
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(UUID.fromString(bleConfig.getTxCharacteristicUuid()));
                    notifyCharacteristic = service.getCharacteristic(UUID.fromString(bleConfig.getRxCharacteristicUuid()));
                    
                    if (writeCharacteristic != null && notifyCharacteristic != null) {
                        // 启用通知
                        final boolean notificationSet = gatt.setCharacteristicNotification(notifyCharacteristic, true);
                        
                        // 写入描述符以启用通知
                        BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                        
                        updateStatus("BLE特征配置成功，通知已启用：" + notificationSet);
                        
                        // 配置完成，发送OTA指令
                        backgroundHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                sendOtaCommand();
                            }
                        }, 1000); // 延迟1秒确保配置完成
                        
                    } else {
                        failWithReason(ErrorCode.GATT_CHARACTERISTIC_NOT_FOUND, "未找到所需的BLE特征");
                    }
                } else {
                    failWithReason(ErrorCode.GATT_SERVICE_NOT_FOUND, "未找到所需的BLE服务");
                }
            } else {
                failWithReason(ErrorCode.GATT_SERVICE_DISCOVERY_FAILED, "BLE服务发现失败");
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            
            // 接收数据
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final String hexData = bytesToHex(data);
                
                android.util.Log.w(TAG, "BLE收到消息,长度" + data.length + "->" + hexData);
                updateStatus("收到消息,长度" + data.length + "->" + hexData);
                
                // 传递给YModem处理
                if (yModem != null) {
                    android.util.Log.d(TAG, "BLE数据传递给YModem处理：" + hexData);
                    yModem.onReceiveData(data);
                } else {
                    android.util.Log.w(TAG, "YModem为null，数据暂时忽略：" + hexData);
                }
            } else {
                android.util.Log.w(TAG, "BLE收到空数据");
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final byte[] data = characteristic.getValue();
                final int dataLength = data != null ? data.length : 0;
                
                android.util.Log.d(TAG, "BLE写入成功：" + dataLength + "字节");
                
                // 减少UI更新频率，只在重要写入时更新状态
                if (dataLength <= 20) {
                    updateStatus("写入成功：" + dataLength + "字节");
                }
            } else {
                final byte[] data = characteristic.getValue();
                final int dataLength = data != null ? data.length : 0;
                
                android.util.Log.e(TAG, "BLE写入失败，状态码：" + status + "，数据：" + dataLength + "字节");
                updateStatus("数据发送失败，状态码：" + status + "，长度：" + dataLength);
            }
            
            // 重置写入标志并继续处理队列
            isWriting.set(false);
            processWriteQueue();
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateStatus("BLE通知描述符写入成功，设备已完全就绪");
            } else {
                updateStatus("BLE通知描述符写入失败，状态码：" + status);
            }
        }
    };
    
    /**
     * 断开BLE连接并清理相关资源
     */
    @SuppressLint("MissingPermission")
    private void disconnectDevice() {
        android.util.Log.d(TAG, "开始断开BLE设备连接...");
        
        if (bluetoothGatt != null) {
            if (checkBluetoothPermissions()) {
                android.util.Log.d(TAG, "正在关闭GATT连接...");
                bluetoothGatt.close();
                android.util.Log.d(TAG, "GATT连接已关闭");
            } else {
                android.util.Log.w(TAG, "权限不足，无法正常关闭GATT连接");
            }
            bluetoothGatt = null;
        } else {
            android.util.Log.d(TAG, "GATT连接为空，无需断开");
        }
        
        // 停止扫描
        stopLeScan();
        
        // 重置相关状态
        isOtaModeEntered = false;
        writeCharacteristic = null;
        notifyCharacteristic = null;
        
        // 清空写入队列
        writeQueue.clear();
        isWriting.set(false);
        
        android.util.Log.d(TAG, "BLE设备连接断开完成，所有相关资源已清理");
    }
    
    /**
     * 发送OTA升级模式指令
     */
    @SuppressLint("MissingPermission")
    private void sendOtaCommand() {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            failWithReason(ErrorCode.OTA_COMMAND_SEND_FAILED, "BLE连接未建立，无法发送OTA指令");
            return;
        }
        
        // 检查权限
        String permissionError = checkBluetoothPermissionsDetailed();
        if (permissionError != null) {
            failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "发送OTA指令时权限检查失败：" + permissionError);
            return;
        }
        
        android.util.Log.d(TAG, "开始发送OTA升级模式指令：" + bleConfig.getOtaCommand());
        updateStatus("正在发送OTA升级模式指令...");
        
        try {
            // 将16进制字符串转换为字节数组
            byte[] otaCommandBytes = hexStringToByteArray(bleConfig.getOtaCommand());
            
            android.util.Log.d(TAG, "OTA指令字节数组长度：" + otaCommandBytes.length + "，内容：" + bytesToHex(otaCommandBytes));
            
            // 设置特征值并写入
            writeCharacteristic.setValue(otaCommandBytes);
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
            
            if (success) {
                android.util.Log.d(TAG, "OTA指令写入蓝牙成功，等待设备响应...");
                updateStatus("OTA升级模式指令已发送：" + bleConfig.getOtaCommand() + "，等待设备响应...");
                
                // 延迟设置OTA模式标志，给设备一些响应时间
                backgroundHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isOtaModeEntered = true;
                        android.util.Log.d(TAG, "设置OTA模式已进入标志");
                        updateStatus("设备应已进入OTA模式，开始传输固件...");
                        
                        // 开始YModem传输
                        startYmodem();
                    }
                }, 2000); // 2秒延迟
                
            } else {
                failWithReason(ErrorCode.GATT_OPERATION_REJECTED, "发送OTA指令失败：GATT写入操作被拒绝");
            }
            
        } catch (SecurityException e) {
            failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "发送OTA指令失败：权限不足 - " + e.getMessage());
        } catch (Exception e) {
            android.util.Log.e(TAG, "发送OTA指令异常：" + e.getMessage(), e);
            failWithReason(ErrorCode.OTA_COMMAND_SEND_FAILED, "发送OTA指令失败：" + e.getMessage());
        }
    }
    
    /**
     * 开始YModem传输
     */
    private void startYmodem() {
        android.util.Log.d(TAG, "准备开始YModem传输...");
        
        if (!isOtaModeEntered) {
            failWithReason(ErrorCode.OTA_MODE_ENTER_FAILED, "设备未进入OTA升级模式");
            return;
        }
        
        if (yModem != null) {
            android.util.Log.d(TAG, "停止之前的YModem实例");
            yModem.stop();
            yModem = null;
        }
        if (firmwareFileName == null) {
            failWithReason(ErrorCode.FIRMWARE_COPY_FAILED, "OTA文件准备失败，无法开始传输");
            return;
        }
        
        // 从完整路径中提取文件名
        File otaFile = new File(firmwareFileName);
        String fileName = otaFile.getName();
        
        android.util.Log.d(TAG, "OTA文件信息 - 文件名：" + fileName + "，完整路径：" + firmwareFileName);
        updateStatus("准备传输文件：" + fileName);
        
        try {
            yModem = new YModem.Builder()
                    .with(context)
                    .filePath(firmwareFileName)
                    .fileName(fileName)
                    .checkMd5("")
                    .sendSize(128)
                    .callback(new YModemListener() {
                        @Override
                        public void onDataReady(byte[] data) {
                            final int dataLength = data.length;
                            
                            android.util.Log.d(TAG, "YModem要求发送数据：" + dataLength + "字节");
                            updateStatus("YModem要求发送数据：" + dataLength + "字节");
                            
                            // 通过BLE发送数据
                            sendDataViaBle(data);
                        }
                        
                        @Override
                        public void onProgress(int currentSent, int total) {
                            // 修复进度计算：确保不超过100%
                            // YModem协议会发送额外的包头、校验等数据，导致currentSent可能超过total
                            final int adjustedCurrentSent = Math.min(currentSent, total);
                            final int finalTotal = total;
                            
                            android.util.Log.d(TAG, "YModem传输进度：" + adjustedCurrentSent + "/" + finalTotal + 
                                " (原始：" + currentSent + "/" + total + ")");
                            
                            // 回调进度更新
                            if (otaCallback != null) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        otaCallback.onProgress(adjustedCurrentSent, finalTotal);
                                    }
                                });
                            }
                        }
                        
                        @Override
                        public void onSuccess() {
                            android.util.Log.i(TAG, "YModem传输成功");
                            updateStatus("YModem 传输成功，固件升级完成");
                            
                            // 传输完成后重置OTA状态
                            isOtaModeEntered = false;
                            
                            // OTA升级成功后先断开蓝牙连接并清理资源，再通知回调
                            android.util.Log.i(TAG, "OTA升级成功，开始断开蓝牙连接并清理资源");
                            updateStatus("升级成功，正在断开连接...");
                            
                            // 先执行断开和清理
                            stopOtaInternal();
                            updateStatus("蓝牙连接已断开，升级流程完成");
                            
                            // 断开完成后再通知成功回调
                            if (otaCallback != null) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        otaCallback.onSuccess();
                                    }
                                });
                            }
                        }
                        
                        @Override
                        public void onFailed(String reason) {
                            android.util.Log.e(TAG, "YModem传输失败：" + reason);
                            failWithReason(ErrorCode.YMODEM_TRANSFER_FAILED, "YModem 传输失败：" + reason);
                        }
                    }).build();
            
            android.util.Log.d(TAG, "YModem实例创建完成，开始启动传输");
            yModem.start(null);
            
            android.util.Log.i(TAG, "YModem固件传输已启动，文件：" + fileName);
            updateStatus("YModem 固件传输已启动，文件：" + fileName);
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "YModem初始化失败", e);
            failWithReason(ErrorCode.YMODEM_INIT_FAILED, "YModem初始化失败：" + e.getMessage());
        }
    }
    
    /**
     * 通过BLE发送数据（分包发送以适应BLE MTU限制）
     */
    private void sendDataViaBle(byte[] data) {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            android.util.Log.e(TAG, "BLE连接未建立，无法发送数据");
            updateStatus("BLE连接未建立，无法发送数据");
            return;
        }
        
        // 添加调试日志
        android.util.Log.d(TAG, "准备发送BLE数据：" + data.length + "字节");
        
        if (data.length <= MAX_PACKET_SIZE) {
            // 数据小于等于最大包大小，直接发送
            writeQueue.offer(data);
            processWriteQueue();
        } else {
            // 数据太大，需要分包发送
            android.util.Log.d(TAG, "数据需要分包，原始大小：" + data.length + "字节，分包大小：" + MAX_PACKET_SIZE + "字节");
            
            for (int i = 0; i < data.length; i += MAX_PACKET_SIZE) {
                int end = Math.min(i + MAX_PACKET_SIZE, data.length);
                byte[] packet = new byte[end - i];
                System.arraycopy(data, i, packet, 0, end - i);
                
                android.util.Log.d(TAG, "分包 " + (i/MAX_PACKET_SIZE + 1) + "：" + packet.length + "字节");
                
                writeQueue.offer(packet);
            }
            processWriteQueue();
        }
    }
    
    /**
     * 处理BLE写入队列
     */
    @SuppressLint("MissingPermission")
    private void processWriteQueue() {
        if (isWriting.get() || writeQueue.isEmpty()) {
            return;
        }
        
        if (bluetoothGatt == null || writeCharacteristic == null) {
            android.util.Log.e(TAG, "BLE连接丢失，清空写入队列");
            writeQueue.clear();
            isWriting.set(false);
            return;
        }
        
        // 检查权限
        String permissionError = checkBluetoothPermissionsDetailed();
        if (permissionError != null) {
            android.util.Log.e(TAG, "写入数据时权限检查失败：" + permissionError);
            writeQueue.clear();
            isWriting.set(false);
            failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "数据传输失败：" + permissionError);
            return;
        }
        
        if (isWriting.compareAndSet(false, true)) {
            byte[] data = writeQueue.poll();
            if (data != null) {
                android.util.Log.d(TAG, "从队列发送BLE数据：" + data.length + "字节");
                
                // 分包发送的数据需要小间隔确保接收方能够处理
                if (data.length <= 20) {
                    try {
                        // 小包直接发送
                        writeCharacteristic.setValue(data);
                        boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                        
                        if (!success) {
                            android.util.Log.e(TAG, "BLE写入失败：" + data.length + "字节");
                            updateStatus("写入失败：" + data.length + "字节");
                            isWriting.set(false);
                            // 可以选择重试或继续处理队列
                            processWriteQueue();
                        }
                    } catch (SecurityException e) {
                        android.util.Log.e(TAG, "BLE写入权限错误：" + e.getMessage());
                        isWriting.set(false);
                        failWithReason(ErrorCode.PERMISSION_RUNTIME_REVOKED, "数据写入失败：权限不足 - " + e.getMessage());
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "BLE写入异常：" + e.getMessage());
                        isWriting.set(false);
                        failWithReason(ErrorCode.BLE_WRITE_FAILED, "数据写入失败：" + e.getMessage());
                    }
                } else {
                    // 大包不应该出现在这里，记录错误
                    android.util.Log.e(TAG, "队列中出现大包数据，跳过：" + data.length + "字节");
                    isWriting.set(false);
                    processWriteQueue();
                }
                // 成功的情况下，在onCharacteristicWrite回调中会重置isWriting标志
            } else {
                isWriting.set(false);
            }
        }
    }
    
    /**
     * 将16进制字符串转换为字节数组
     */
    private byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
    
    /**
     * 字节数组转16进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        String hex;
        for (int i = 0; i < bytes.length; i++) {
            hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            result.append(hex.toUpperCase()).append(" ");
        }
        return result.toString();
    }
    
    /**
     * 更新状态
     */
    private void updateStatus(final String status) {
        android.util.Log.d(TAG, status);
        
        if (otaCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    otaCallback.onStatusUpdate(status);
                }
            });
        }
    }
    
    /**
     * 升级失败处理
     * 自动断开蓝牙连接并清理所有资源
     */
    private void failWithReason(final int errorCode, final String errorMessage) {
        android.util.Log.e(TAG, "OTA升级失败 [" + errorCode + "]: " + errorMessage);
        
        // 先自动停止升级并断开所有蓝牙连接
        android.util.Log.i(TAG, "OTA升级失败，开始断开蓝牙连接并清理资源");
        stopOtaInternal();
        
        // 断开完成后再通知失败回调
        if (otaCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    otaCallback.onFailed(errorCode, errorMessage);
                }
            });
        }
    }
}
