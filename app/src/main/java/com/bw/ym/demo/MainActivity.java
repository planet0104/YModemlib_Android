package com.bw.ym.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bw.ym.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 简化版MainActivity
 * 使用BleOtaManager实现一键OTA升级
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    // 权限请求码
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    
    // 需要申请的蓝牙权限
    private String[] bluetoothPermissions;
    
    // UI 组件
    private TextView tvStatus;
    private EditText etDeviceName;
    private EditText etFirmwareName;
    private ProgressBar progressBar;
    private TextView tvProgressText;
    private Button btnStartOta;
    private Button btnStopOta;
    
    // OTA升级状态
    private boolean isOtaRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_simple);
        
        // 初始化 UI 组件
        initViews();
        
        // 初始化权限数组
        initBluetoothPermissions();
        
        // 设置BleOtaManager的Context
        BleOtaManager.setContext(this);
        
        // 检查并请求权限
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions();
        } else {
            updateStatus("蓝牙权限已获取，可以开始OTA升级");
        }
    }
    
    /**
     * 初始化UI组件
     */
    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        etDeviceName = findViewById(R.id.et_device_name);
        etFirmwareName = findViewById(R.id.et_firmware_name);
        progressBar = findViewById(R.id.progress_bar);
        tvProgressText = findViewById(R.id.tv_progress_text);
        btnStartOta = findViewById(R.id.btn_start_ota);
        btnStopOta = findViewById(R.id.btn_stop_ota);
        
        // 设置默认值
        etDeviceName.setText("PN00000013");
        etFirmwareName.setText("OTA_APP_0728.bin");
        
        updateUI();
    }

    /**
     * 初始化蓝牙权限数组
     */
    private void initBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本需要的权限
            bluetoothPermissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            // Android 12 以下版本需要的权限
            bluetoothPermissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    /**
     * 检查蓝牙权限是否已获取
     */
    private boolean checkBluetoothPermissions() {
        for (String permission : bluetoothPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 请求蓝牙权限
     */
    private void requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(this, bluetoothPermissions, REQUEST_BLUETOOTH_PERMISSIONS);
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "蓝牙权限已获取", Toast.LENGTH_SHORT).show();
                updateStatus("蓝牙权限已获取，可以开始OTA升级");
            } else {
                Toast.makeText(this, "蓝牙权限被拒绝，应用无法正常使用蓝牙功能", Toast.LENGTH_LONG).show();
                updateStatus("蓝牙权限被拒绝，无法使用OTA功能");
            }
        }
    }

    /**
     * 开始OTA升级
     */
    public void onStartOtaClick(View view) {
        if (isOtaRunning) {
            Toast.makeText(this, "OTA升级正在进行中", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String deviceName = etDeviceName.getText().toString().trim();
        String firmwareName = etFirmwareName.getText().toString().trim();
        
        if (deviceName.isEmpty()) {
            Toast.makeText(this, "请输入蓝牙设备名称", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (firmwareName.isEmpty()) {
            Toast.makeText(this, "请输入固件文件名", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!checkBluetoothPermissions()) {
            Toast.makeText(this, "请先获取蓝牙权限", Toast.LENGTH_SHORT).show();
            requestBluetoothPermissions();
            return;
        }
        
        // 开始OTA升级
        isOtaRunning = true;
        resetProgress();
        updateUI();
        updateStatus("正在开始OTA升级...");

        firmwareName = copyOtaFileFromAssets(firmwareName);
        
        // 基本用法：使用默认配置

        BleOtaManager.BleConfig customConfig = new BleOtaManager.BleConfig(
                "0000ffe0-0000-1000-8000-00805f9b34fb",           // 服务UUID
                "0000ffe1-0000-1000-8000-00805f9b34fb",        // TX特征UUID (发送数据)
                "0000ffe2-0000-1000-8000-00805f9b34fb",        // RX特征UUID (接收数据)
                "5A0007230510000000A5"            // OTA升级指令
        );

        BleOtaManager.StartOTA(firmwareName, deviceName, customConfig, new BleOtaManager.OTACallback() {
            @Override
            public void onProgress(int currentSent, int total) {
                // 更新进度显示
                updateProgress(currentSent, total);
            }
            
            @Override
            public void onStatusUpdate(String status) {
                // 更新状态显示
                updateStatus(status);
            }
            
            @Override
            public void onSuccess() {
                // 升级成功
                isOtaRunning = false;
                updateUI();
                updateStatus("固件升级成功！");
                Toast.makeText(MainActivity.this, "固件升级成功！", Toast.LENGTH_LONG).show();
                android.util.Log.i(TAG, "OTA升级成功完成");
            }
            
            @Override
            public void onFailed(int errorCode, String errorMessage) {
                // 升级失败 - 新增错误编号处理
                isOtaRunning = false;
                updateUI();
                
                // 根据错误编号进行不同的处理
                String userFriendlyMessage = getErrorMessage(errorCode, errorMessage);
                updateStatus("固件升级失败 [" + errorCode + "]：" + userFriendlyMessage);
                Toast.makeText(MainActivity.this, userFriendlyMessage, Toast.LENGTH_LONG).show();
                android.util.Log.e(TAG, "OTA升级失败 [" + errorCode + "]: " + errorMessage);
            }
        });
        
        Toast.makeText(this, "OTA升级已启动，正在搜索设备...", Toast.LENGTH_SHORT).show();
        android.util.Log.i(TAG, "启动OTA升级 - 固件: " + firmwareName + ", 设备: " + deviceName);
    }
    
    /**
     * 停止OTA升级
     */
    public void onStopOtaClick(View view) {
        if (!isOtaRunning) {
            Toast.makeText(this, "没有正在进行的OTA升级", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 停止升级
        BleOtaManager.StopOTA();
        
        // 更新状态
        isOtaRunning = false;
        updateUI();
        updateStatus("OTA升级已手动停止");
        resetProgress();
        
        Toast.makeText(this, "OTA升级已停止", Toast.LENGTH_SHORT).show();
        android.util.Log.i(TAG, "手动停止OTA升级");
    }

    /**
     * 更新UI状态
     */
    private void updateUI() {
        if (btnStartOta != null && btnStopOta != null) {
            btnStartOta.setEnabled(!isOtaRunning);
            btnStopOta.setEnabled(isOtaRunning);
            
            if (isOtaRunning) {
                btnStartOta.setText("升级进行中...");
            } else {
                btnStartOta.setText("开始OTA升级");
            }
        }
    }
    
    /**
     * 更新状态显示
     */
    private void updateStatus(String status) {
        if (tvStatus != null) {
            tvStatus.setText("状态：" + status);
        }
        android.util.Log.d(TAG, "状态更新: " + status);
    }

    /**
     * 更新升级进度显示
     */
    private void updateProgress(int currentSent, int total) {
        if (progressBar != null && tvProgressText != null) {
            int progressPercent = (int) ((float) currentSent / total * 100);
            progressBar.setProgress(progressPercent);
            tvProgressText.setText(progressPercent + "% (" + currentSent + "/" + total + " 字节)");
        }
    }

    /**
     * 重置进度显示
     */
    private void resetProgress() {
        if (progressBar != null && tvProgressText != null) {
            progressBar.setProgress(0);
            tvProgressText.setText("0%");
        }
    }

    /**
     * 根据错误编号获取用户友好的错误信息
     */
    private String getErrorMessage(int errorCode, String originalMessage) {
        switch (errorCode) {
            // 系统环境错误 (1xx)
            case BleOtaManager.ErrorCode.CONTEXT_NOT_SET:
                return "应用初始化错误，请重启应用";
            case BleOtaManager.ErrorCode.BLUETOOTH_NOT_SUPPORTED:
                return "设备不支持蓝牙功能";
            case BleOtaManager.ErrorCode.BLE_NOT_SUPPORTED:
                return "设备不支持BLE功能";
            case BleOtaManager.ErrorCode.BLUETOOTH_DISABLED:
                return "请先开启蓝牙";
            case BleOtaManager.ErrorCode.API_VERSION_TOO_LOW:
                return "系统版本过低，需要Android 5.0+";
                
            // 权限相关错误 (2xx)
            case BleOtaManager.ErrorCode.PERMISSION_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_SCAN_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_CONNECT_DENIED:
            case BleOtaManager.ErrorCode.PERMISSION_RUNTIME_REVOKED:
                return "请在设置中授予蓝牙权限";
                
            // 设备连接错误 (3xx)
            case BleOtaManager.ErrorCode.DEVICE_SCAN_FAILED:
                return "蓝牙扫描失败，请重试";
            case BleOtaManager.ErrorCode.DEVICE_NOT_FOUND:
            case BleOtaManager.ErrorCode.DEVICE_SCAN_TIMEOUT:
                return "未找到设备，请检查设备名称和连接状态";
            case BleOtaManager.ErrorCode.DEVICE_CONNECT_FAILED:
                return "连接设备失败，请重试";
            case BleOtaManager.ErrorCode.DEVICE_DISCONNECTED:
                return "设备连接中断，请重新连接";
            case BleOtaManager.ErrorCode.GATT_SERVICE_DISCOVERY_FAILED:
            case BleOtaManager.ErrorCode.GATT_SERVICE_NOT_FOUND:
            case BleOtaManager.ErrorCode.GATT_CHARACTERISTIC_NOT_FOUND:
                return "设备不兼容，请检查设备型号";
                
            // OTA升级错误 (4xx)
            case BleOtaManager.ErrorCode.OTA_COMMAND_SEND_FAILED:
            case BleOtaManager.ErrorCode.OTA_MODE_ENTER_FAILED:
                return "设备进入升级模式失败，请重试";
            case BleOtaManager.ErrorCode.FIRMWARE_FILE_NOT_FOUND:
                return "固件文件不存在";
            case BleOtaManager.ErrorCode.FIRMWARE_COPY_FAILED:
                return "固件文件准备失败";
            case BleOtaManager.ErrorCode.YMODEM_INIT_FAILED:
            case BleOtaManager.ErrorCode.YMODEM_TRANSFER_FAILED:
                return "固件传输失败，请重试";
                
            // 数据传输错误 (5xx)
            case BleOtaManager.ErrorCode.BLE_WRITE_FAILED:
            case BleOtaManager.ErrorCode.BLE_WRITE_QUEUE_FAILED:
            case BleOtaManager.ErrorCode.DATA_TRANSMISSION_FAILED:
            case BleOtaManager.ErrorCode.GATT_OPERATION_REJECTED:
                return "数据传输失败，请重试";
                
            // 其他错误 (9xx)
            case BleOtaManager.ErrorCode.UNKNOWN_ERROR:
                return "未知错误：" + originalMessage;
            case BleOtaManager.ErrorCode.OPERATION_CANCELLED:
                return "操作已取消";
            case BleOtaManager.ErrorCode.TIMEOUT:
                return "操作超时，请重试";
                
            default:
                return "升级失败：" + originalMessage;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 应用退出时停止OTA升级
        if (isOtaRunning) {
            BleOtaManager.StopOTA();
            android.util.Log.i(TAG, "应用退出，自动停止OTA升级");
        }
    }


    /**
     * 从assets文件夹复制OTA升级文件到应用临时目录
     */
    private String copyOtaFileFromAssets(String firmwareAssetName) {
        String targetFileName = "ota_firmware.bin";

        try {
            // 获取应用的临时文件目录
            File tempDir = new File(getCacheDir(), "ota_temp");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // 目标文件路径
            File targetFile = new File(tempDir, targetFileName);

            // 如果文件已存在，先删除
            if (targetFile.exists()) {
                targetFile.delete();
            }

            // 从assets复制文件
            InputStream inputStream = getAssets().open(firmwareAssetName);
            FileOutputStream outputStream = new FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            inputStream.close();
            outputStream.close();

            updateStatus("OTA文件复制成功，大小：" + totalBytes + " 字节");

            return targetFile.getAbsolutePath();

        } catch (IOException e) {
            android.util.Log.e(TAG, "复制OTA文件失败", e);
            updateStatus("复制OTA文件失败：" + e.getMessage());
            return null;
        } catch (Exception e) {
            android.util.Log.e(TAG, "复制OTA文件异常", e);
            updateStatus("复制OTA文件异常：" + e.getMessage());
            return null;
        }
    }
}
