# 保护 JNI 原生入口与 C++ 桥接
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保护 Room 实体与 DAO
-keep class com.openofficepdf.core.database.entity.** { *; }
-keep interface com.openofficepdf.core.database.dao.** { *; }

# 保护 AIDL 接口
-keep class com.openofficepdf.core.ipc.IOfficeProcessBridge** { *; }
-keep class com.openofficepdf.core.ipc.IOcrProcessBridge** { *; }

# 保护领域模型 (Normalized Page Space 核心数据契约)
-keep class com.openofficepdf.core.model.** { *; }
