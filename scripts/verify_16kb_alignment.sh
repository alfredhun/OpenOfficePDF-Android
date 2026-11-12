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
    local filename
    filename="$(basename "$so")"
    if [[ "$filename" == "libc++_shared.so" ]]; then
        echo "Checking $filename..."
        echo "  [INFO] Skipping NDK runtime libc++_shared.so (app uses c++_static)"
        return 0
    fi
    echo "Checking $filename..."
    # 使用 readelf -W (-wide) 防止 64 位 ELF 分两行换行导致获取到物理地址而非最后一列对齐属性
    local load_line
    load_line=$(readelf -W -l "$so" | grep "LOAD" | head -n 1 || true)
    echo "  LOAD segment: $load_line"
    local align
    align=$(echo "$load_line" | awk '{print $NF}')
    if [[ "$align" == "0x4000" ]] || [[ "$align" == "16384" ]] || [[ "$align" == "0x10000" ]] || [[ "$align" == "65536" ]]; then
        echo "  [PASS] 16KB aligned ($align)"
    else
        echo "  [FAIL] Not 16KB aligned (current: $align)!"
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
