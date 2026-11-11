# OpenOfficePDF Android 第三方 Native 依赖集成与 16KB 对齐规范

本项目遵循 **全链路离线优先** 与 **Android 15+ 16KB ELF 页面对齐** 强制硬件基线标准。

## 1. 核心 Native 依赖库清单与许可证矩阵

| 模块名称 | 选型技术 | 许可证类型 | 用途与集成路径 | 16KB 对齐强制标志 |
| --- | --- | --- | --- | --- |
| **PDF 渲染与对象操作** | Google PDFium (Chrome PDF) | Apache-2.0 | `native/third_party/pdfium/` | `-Wl,-z,max-page-size=16384` |
| **OCR 推理引擎** | ONNX Runtime Mobile | MIT | `native/third_party/onnxruntime/` | `-Wl,-z,max-page-size=16384` |
| **图像几何矫正** | OpenCV (Core & Imgproc) | Apache-2.0 | `native/third_party/opencv/` | `-Wl,-z,max-page-size=16384` |
| **Office 跨进程排版** | Collabora / LibreOfficeKit | MPL-2.0 / LGPL-3.0 | 独立进程 `:office_engine` | 进程物理隔离，动态共享内存通信 |

---

## 2. 目录拓扑结构契约

```
native/third_party/
├── pdfium/
│   ├── include/
│   │   ├── fpdfview.h          # 核心加载、页面计算与瓦片渲染头文件
│   │   └── fpdf_edit.h         # 对象级文本/矢量操作头文件
│   └── lib/arm64-v8a/
│       └── libpdfium.so        # 16KB 对齐预编译二进制 (或 CMake 源码编译产物)
├── onnxruntime/
│   ├── include/
│   │   └── onnxruntime_c_api.h # ONNX Runtime C 运行时 API
│   └── lib/arm64-v8a/
│       └── libonnxruntime.so
└── opencv/
    ├── include/
    │   └── opencv2_core.hpp    # 几何矫正、透视变换核心矩阵结构
    └── lib/arm64-v8a/
        └── libopencv_java4.so
```

---

## 3. 16KB 页面大小二进制验证指南

在所有 prebuilt `.so` 置入 `lib/arm64-v8a/` 之后，必须运行项目根目录下的审计脚本：

### Linux / macOS
```bash
bash scripts/verify_16kb_alignment.sh native/third_party/pdfium/lib/arm64-v8a/libpdfium.so
```

### Windows PowerShell
```powershell
.\scripts\verify_16kb_alignment.ps1 native/third_party/pdfium/lib/arm64-v8a/libpdfium.so
```

**断言判定规则**：
- 执行 `readelf -l <so>`
- LOAD 段对齐值 `Align` 必须为 `0x4000` (16384 字节) 或更大。
- 若出现 `0x1000` (4KB)，构建流程必须立即报错拦截，禁止发布进入安装包。
