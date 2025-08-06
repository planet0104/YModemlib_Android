# BLE UUID 配置升级指南

## 修改概述

将 BleOtaManager 中固定的 UUID 配置改为可动态配置的参数，使其能够适配不同设备的 UUID，提高了代码的灵活性和复用性。

## 主要改动

### 1. 新增 BleConfig 配置类

```java
public static class BleConfig {
    private final String serviceUuid;
    private final String txCharacteristicUuid;
    private final String rxCharacteristicUuid;
    private final String otaCommand;
    
    // 构造函数和便利方法
    public static BleConfig getDefaultConfig();
    public static BleConfig createConfig(String serviceUuid, String txCharUuid, String rxCharUuid);
    public static BleConfig createConfigForDevice(String deviceName);
}
```

### 2. 扩展 StartOTA 方法

新增了多个重载方法：

```java
// 使用默认配置
public static void StartOTA(String otaFilePath, String bleDeviceName, OTACallback callback);

// 根据设备名称自动选择配置
public static void StartOTA(String otaFilePath, String bleDeviceName, boolean autoConfig, OTACallback callback);

// 使用自定义配置
public static void StartOTA(String otaFilePath, String bleDeviceName, BleConfig bleConfig, OTACallback callback);
```

### 3. 移除硬编码常量

将原来的硬编码 UUID 常量：
```java
// 旧版本
private static final String SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
private static final String CHARACTERISTIC_UUID_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";
private static final String CHARACTERISTIC_UUID_RX = "0000ffe2-0000-1000-8000-00805f9b34fb";
private static final String OTA_COMMAND = "5A0007230510000000A5";
```

改为动态配置：
```java
// 新版本
private BleConfig bleConfig;  // 实例变量，运行时设置
```

## 使用方式对比

### 旧版本使用方式
```java
// 只能使用固定的UUID配置
BleOtaManager.StartOTA("firmware.bin", "DeviceName", callback);
```

### 新版本使用方式

#### 1. 向后兼容（使用默认配置）
```java
// 与旧版本完全兼容
BleOtaManager.StartOTA("firmware.bin", "DeviceName", callback);
```

#### 2. 自定义配置
```java
// 创建自定义配置
BleOtaManager.BleConfig config = new BleOtaManager.BleConfig(
    "custom-service-uuid",
    "custom-tx-char-uuid", 
    "custom-rx-char-uuid",
    "custom-ota-command"
);

// 使用自定义配置
BleOtaManager.StartOTA("firmware.bin", "DeviceName", config, callback);
```

#### 3. 根据设备名称自动配置
```java
// 在 createConfigForDevice 中实现设备特定的逻辑
BleOtaManager.StartOTA("firmware.bin", "DeviceName", true, callback);
```

## 扩展性增强

### 多设备支持

可以在 `BleConfig.createConfigForDevice()` 方法中添加设备特定的配置：

```java
public static BleConfig createConfigForDevice(String deviceName) {
    if (deviceName.startsWith("DeviceA_")) {
        return new BleConfig(
            "uuid-for-device-a-service",
            "uuid-for-device-a-tx", 
            "uuid-for-device-a-rx",
            "ota-command-for-device-a"
        );
    } else if (deviceName.startsWith("DeviceB_")) {
        return new BleConfig(
            "uuid-for-device-b-service",
            "uuid-for-device-b-tx", 
            "uuid-for-device-b-rx",
            "ota-command-for-device-b"
        );
    }
    return getDefaultConfig();
}
```

### 配置文件支持

未来可以扩展为从配置文件或网络加载设备配置：

```java
// 示例：从JSON配置加载
public static BleConfig loadConfigFromJson(String deviceName) {
    // 从assets/ble_configs.json或网络加载配置
    // 根据设备名称返回对应的UUID配置
}
```

## 向后兼容性

- ✅ **完全兼容**：现有代码无需修改，继续使用默认配置
- ✅ **渐进升级**：可以逐步迁移到自定义配置
- ✅ **API稳定**：原有的 StartOTA 方法继续可用

## 优势总结

1. **灵活性**：支持不同设备的UUID配置
2. **可扩展性**：易于添加新设备支持
3. **向后兼容**：不影响现有代码
4. **代码复用**：同一套代码适配多种设备
5. **配置集中**：所有UUID配置都在BleConfig类中管理

## 使用建议

1. **新项目**：直接使用自定义配置方式，便于后续扩展
2. **现有项目**：如果设备UUID固定，无需修改；如需支持多设备，可逐步迁移
3. **多设备项目**：在 `createConfigForDevice` 方法中实现设备识别逻辑
4. **配置管理**：建议创建设备配置常量类，统一管理不同设备的UUID

---

**升级完成日期**: 2025年8月6日  
**兼容性**: 向后兼容，无破坏性改动  
**测试状态**: 编译通过，功能验证完成
