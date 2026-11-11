#!/usr/bin/env bash
# OpenOfficePDF 16KB ELF 页面对齐校验脚本 (任务书 9.1 节第 3 条)
# 针对 Android 15+ 硬件与 Google Play 准入强制校验

set -euo pipefail

SO_DIR="${1:-app/build/intermediates/stripped_native_libs/release/out/lib/arm64-v8a}"

echo ">>> Scanning native shared libraries in $SO_DIR for 16KB page alignment..."

if [ ! -d "$SO_DIR" ]; then
    echo "Directory not found: $SO_DIR. Build native libraries first."
    exit 0
fi

FAILED=0
for so in "$SO_DIR"/*.so; do
    [ -e "$so" ] || continue
    echo "Checking $(basename "$so")..."
    # 使用 readelf 或 llvm-objdump 检查 LOAD 段对齐属性是否为 0x4000 (16384)
    ALIGN=$(readelf -l "$so" | grep "LOAD" | head -n 1 | awk '{print $NF}' || true)
    if [[ "$ALIGN" == "0x4000" ]] || [[ "$ALIGN" == "16384" ]]; then
        echo "  [PASS] 16KB aligned ($ALIGN)"
    else
        echo "  [FAIL] Not 16KB aligned (current: $ALIGN)!"
        FAILED=1
    fi
done

if [ "$FAILED" -ne 0 ]; then
    echo ">>> Error: One or more native libraries failed the 16KB alignment requirement!"
    exit 1
else
    echo ">>> All native libraries passed 16KB alignment check."
fi
