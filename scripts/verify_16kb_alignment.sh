#!/usr/bin/env bash
# OpenOfficePDF 16KB ELF 页面对齐校验脚本 (任务书 9.1 节第 3 条)
# 针对 Android 15+ 硬件与 Google Play 准入强制校验

set -euo pipefail

TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then
    TARGETS=("app/build/intermediates/stripped_native_libs/release/out/lib/arm64-v8a")
fi

FAILED=0
CHECKED=0

check_file() {
    local so="$1"
    [ -f "$so" ] || return 0
    [[ "$so" == *.so ]] || return 0
    echo "Checking $(basename "$so")..."
    # 使用 readelf 检查第一个 LOAD 段的页面对齐属性是否为 0x4000 (16384)
    ALIGN=$(readelf -l "$so" | grep "LOAD" | head -n 1 | awk '{print $NF}' || true)
    if [[ "$ALIGN" == "0x4000" ]] || [[ "$ALIGN" == "16384" ]]; then
        echo "  [PASS] 16KB aligned ($ALIGN)"
    else
        echo "  [FAIL] Not 16KB aligned (current: $ALIGN)!"
        FAILED=1
    fi
    CHECKED=$((CHECKED + 1))
}

for item in "${TARGETS[@]}"; do
    if [ -f "$item" ]; then
        check_file "$item"
    elif [ -d "$item" ]; then
        echo ">>> Scanning directory $item..."
        for f in "$item"/*.so; do
            [ -e "$f" ] || continue
            check_file "$f"
        done
    fi
done

if [ "$CHECKED" -eq 0 ]; then
    echo ">>> Notice: No shared libraries were supplied for inspection."
    exit 0
fi

if [ "$FAILED" -ne 0 ]; then
    echo ">>> Error: One or more native libraries failed the 16KB alignment requirement!"
    exit 1
else
    echo ">>> All $CHECKED native library files passed 16KB alignment check."
fi
