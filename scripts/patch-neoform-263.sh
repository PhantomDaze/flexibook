#!/usr/bin/env bash
# 给 Gradle 缓存中的 NeoForm 26.3-1 制品追加 HolderSet 补丁。
#
# 背景：26.3 唯一的 NeoForm 配置是 26.3-1（NeoForge 26.3.0.101–110 全部依赖它）。
# 它反编译出的 HolderSet.Named 匿名子类把 contents() 写成 protected，而 javac
# 依据官方 class 文件把父类方法视为 public，于是 recompile 报“降低访问权限”。
# 这是 NeoForm 上游缺陷，不是本模组的问题。修复方式是向 zip 内追加一条
# patches/net/minecraft/core/HolderSet.java.patch（protected → public）。
#
# 何时可以删除本脚本：NeoForm 发布修复版（如 26.3-2）或 NeoForge 更换 baseline，
# 且确认新配置已修正该问题之后。
#
# 用法：
#   bash scripts/patch-neoform-263.sh            # 打补丁（修改前自动备份 zip）
#   bash scripts/patch-neoform-263.sh --check    # 只检查，不写任何文件
#   bash scripts/patch-neoform-263.sh --help
#
# 环境变量：
#   GRADLE_USER_HOME  默认 ~/.gradle
#   NEOFORM_ZIP       直接指定 neoform-26.3-1.zip 路径（跳过缓存搜索）
#
# 退出码：
#   0  已打补丁（或本次刚打好并通过校验）
#   1  zip 存在但已有条目内容不符 / 完整性校验失败 / 写入失败
#   2  找不到 neoform-26.3-1.zip（通常说明还没跑过构建、NeoForm 尚未下载）
#   3  --check 模式下存在未打补丁的 zip（环境或脚本自身错误亦为 3）
set -euo pipefail

readonly NEOFORM_VERSION='26.3-1'
readonly ENTRY='patches/net/minecraft/core/HolderSet.java.patch'
# 内嵌补丁的 sha256，用于防止脚本被误改后写入错误内容。
readonly PATCH_SHA256='2355947309674e46c4bbcddb24131346f0ec95d84b8feb3d2f4cf6d783e6a07e'

usage() {
    cat <<'EOF'
Patch the cached NeoForm 26.3-1 artifact for NeoForge 26.3 builds.

Usage:
  bash scripts/patch-neoform-263.sh            apply (backs up the zip first)
  bash scripts/patch-neoform-263.sh --check    report only; never writes

Environment:
  GRADLE_USER_HOME   default: ~/.gradle
  NEOFORM_ZIP        explicit path to neoform-26.3-1.zip

Exit codes:
  0 already patched / patched successfully
  1 patch differs, integrity failed, or write failed
  2 neoform-26.3-1.zip not found (run a build once so Gradle downloads it)
  3 --check found an unpatched zip, or environment/script error
EOF
}

MODE='apply'
case "${1:-}" in
    '') ;;
    --check) MODE='check' ;;
    -h|--help) usage; exit 0 ;;
    *)
        echo "error: unknown argument: $1 (try --help)" >&2
        exit 3
        ;;
esac

for cmd in python3 unzip sha256sum; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "error: $cmd is required" >&2; exit 3; }
done

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
CACHE_ROOT="$GRADLE_USER_HOME/caches"

# 补丁正文；这里必须与验证时实际写入的内容逐字节一致。
PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT
cat > "$PATCH_FILE" <<'PATCH_EOF'
--- a/net/minecraft/core/HolderSet.java
+++ b/net/minecraft/core/HolderSet.java
@@ -39,7 +_,7 @@
     static <T> HolderSet.Named<T> emptyNamed(HolderOwner<T> owner, TagKey<T> key) {
         return new HolderSet.Named<T>(owner, key) {
             @Override
-            protected List<Holder<T>> contents() {
+            public List<Holder<T>> contents() {
                 throw new UnsupportedOperationException("Tag " + this.key() + " can't be dereferenced during construction");
             }
         };
PATCH_EOF

actual_sha="$(sha256sum "$PATCH_FILE" | awk '{print $1}')"
if [[ "$actual_sha" != "$PATCH_SHA256" ]]; then
    echo "error: embedded patch is corrupted (sha256=$actual_sha, expected=$PATCH_SHA256)" >&2
    exit 3
fi

# unzip -t 完整性校验。
# 早期的手工修法（Python zipfile 追加）重写过中央目录，会让 Info-ZIP 对既有条目
# 报 “GPF bit 11 (UTF-8) 不一致” 的警告，但数据本身完好。这里只容忍这一类警告；
# 出现 CRC、压缩数据损坏等真实错误仍然判失败。用本脚本打补丁不会产生该警告。
zip_integrity() {
    local z="$1" log rc=0
    log="$(unzip -t "$z" 2>&1)" || rc=$?
    if (( rc == 0 )); then
        echo "  [integrity ok] $z"
        return 0
    fi
    if grep -q 'mismatch between local and central GPF bit 11' <<<"$log" \
        && ! grep -Eqi 'bad CRC|cannot find|invalid compressed data|unexpected end of|bad zipfile|error:' <<<"$log"; then
        echo "  [integrity ok, legacy utf8-flag warnings ignored] $z"
        return 0
    fi
    echo "error: zip integrity check failed (unzip -t): $z" >&2
    grep -Ei 'bad CRC|cannot find|invalid compressed data|unexpected end of|bad zipfile|error:' <<<"$log" | tail -10 >&2 || true
    return 1
}


# 定位缓存中的 neoform zip；允许同一版本存在多份（不同 hash 目录）。
ZIPS=()
if [[ -n "${NEOFORM_ZIP:-}" ]]; then
    if [[ ! -f "$NEOFORM_ZIP" ]]; then
        echo "error: NEOFORM_ZIP does not point to a file: $NEOFORM_ZIP" >&2
        exit 2
    fi
    ZIPS=("$NEOFORM_ZIP")
elif [[ -d "$CACHE_ROOT" ]]; then
    while IFS= read -r -d '' z; do
        ZIPS+=("$z")
    done < <(find "$CACHE_ROOT" -type f \
        -path "*/net.neoforged/neoform/${NEOFORM_VERSION}/*/neoform-${NEOFORM_VERSION}.zip" \
        -print0 2>/dev/null | sort -z)
fi

if (( ${#ZIPS[@]} == 0 )); then
    echo "error: no neoform-${NEOFORM_VERSION}.zip found under $CACHE_ROOT" >&2
    echo "" >&2
    echo "  Gradle has not downloaded NeoForm 26.3-1 yet. Run a build once first:" >&2
    echo "    JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew :26.3:build --console=plain" >&2
    echo "  That first build is expected to fail in :26.3:createMinecraftArtifacts." >&2
    echo "  Then rerun this script and rebuild." >&2
    exit 2
fi

echo "NeoForm ${NEOFORM_VERSION} HolderSet patch — mode: $MODE"
echo "  GRADLE_USER_HOME: $GRADLE_USER_HOME"

# 完整性检查先于任何写入；坏 zip 不碰。
for z in "${ZIPS[@]}"; do
    zip_integrity "$z" || exit 1
done

# 核心逻辑用 python3 操作 zipfile：读取/校验用 zipfile，追加采用字节级方式，
# 保证已有条目及其头部元数据逐字节不变。
set +e
python3 - "$MODE" "$PATCH_FILE" "$ENTRY" "${ZIPS[@]}" <<'PY'
import struct
import sys
import zipfile
import zlib
import hashlib
import os
import shutil

mode, patch_path, entry, *zips = sys.argv[1:]
patch = open(patch_path, 'rb').read()


def read_entry(z):
    """返回 'patched' / 'differs' / 'missing'；坏 zip 直接报错退出。"""
    try:
        with zipfile.ZipFile(z) as zf:
            if entry not in zf.namelist():
                return 'missing'
            return 'patched' if zf.read(entry) == patch else 'differs'
    except (zipfile.BadZipFile, OSError) as exc:
        print(f'error: cannot read {z}: {exc}', file=sys.stderr)
        raise SystemExit(1)


def verify_integrity(zip_path):
    """仅做 CRC 完整性校验（不要求补丁条目存在）。"""
    with zipfile.ZipFile(zip_path) as zf:
        bad = zf.testzip()
        if bad is not None:
            raise RuntimeError(f'corrupted entry: {bad}')


def verify_patched(zip_path):
    """校验完整性，并确认补丁条目存在且内容一致。"""
    verify_integrity(zip_path)
    with zipfile.ZipFile(zip_path) as zf:
        if entry not in zf.namelist():
            raise RuntimeError('patch entry missing after write')
        if zf.read(entry) != patch:
            raise RuntimeError('patch entry content mismatch after write')


def append_entry_bytes(zip_path, rel_name, content):
    """字节级追加条目，保持已有局部记录与中央目录记录原样不变。

    不用 zipfile 追加模式：Python 3.14 重写中央目录时会丢掉已有条目的
    UTF-8 标志位（虽数据无损，但会改变原有条目头部），此处规避。
    """
    data = open(zip_path, 'rb').read()
    eocd = data.rfind(b'PK\x05\x06')
    if eocd < 0 or eocd + 22 > len(data):
        raise RuntimeError('no valid EOCD record')
    (disk, cd_disk, n_disk, n_total,
     cd_size, cd_off, clen) = struct.unpack('<HHHHIIH', data[eocd + 4:eocd + 22])
    if n_total == 0xFFFF or cd_off == 0xFFFFFFFF or cd_size == 0xFFFFFFFF:
        raise RuntimeError('ZIP64 archives are not supported')
    if cd_off + cd_size != eocd:
        raise RuntimeError('central directory is not contiguous')
    comment = data[eocd + 22:eocd + 22 + clen]

    name_b = rel_name.encode('utf-8')
    crc = zlib.crc32(content) & 0xFFFFFFFF
    co = zlib.compressobj(9, zlib.DEFLATED, -15)
    comp = co.compress(content) + co.flush()

    dos_time, dos_date = 0, 0x0021  # 1980-02-01 00:00，输出可复现
    flags = 0x0800  # bit 11: UTF-8 文件名
    method = 8      # deflate
    local_off = cd_off

    local = struct.pack(
        '<IHHHHHIIIHH', 0x04034B50, 20, flags, method, dos_time, dos_date,
        crc, len(comp), len(content), len(name_b), 0,
    ) + name_b + comp

    version_made = struct.unpack('<H', data[cd_off + 4:cd_off + 6])[0]
    central = struct.pack(
        '<IHHHHHHIIIHHHHHII', 0x02014B50, version_made, 20, flags, method,
        dos_time, dos_date, crc, len(comp), len(content),
        len(name_b), 0, 0, 0, 0, (0o644 << 16), local_off,
    ) + name_b

    old_local = data[:cd_off]
    old_cd = data[cd_off:eocd]
    new_cd = old_cd + central
    new_eocd = struct.pack(
        '<IHHHHIIH', 0x06054B50, disk, cd_disk, n_disk + 1, n_total + 1,
        len(new_cd), cd_off + len(local), len(comment),
    ) + comment

    with open(zip_path, 'wb') as f:
        f.write(old_local)
        f.write(local)
        f.write(new_cd)
        f.write(new_eocd)


if mode == 'check':
    rc = 0
    for z in zips:
        state = read_entry(z)
        if state == 'patched':
            print(f'  [patched]     {z}')
        elif state == 'differs':
            print(f'  [not patched] {z}  (entry exists but content differs)')
            rc = 3
        else:
            print(f'  [not patched] {z}  ({entry} is absent)')
            rc = 3
    print('result: already patched' if rc == 0 else 'result: NOT patched')
    raise SystemExit(rc)

rc = 0
for z in zips:
    state = read_entry(z)
    if state == 'patched':
        print(f'  [already patched, skipping] {z}')
        continue
    if state == 'differs':
        print(f'error: {z} already contains a different {entry}; refusing to overwrite it', file=sys.stderr)
        rc = 1
        continue

    try:
        verify_integrity(z)
    except (RuntimeError, zipfile.BadZipFile, OSError) as exc:
        print(f'error: zip integrity check failed before patching {z}: {exc}', file=sys.stderr)
        rc = 1
        continue

    backup = z + '.orig'
    if os.path.exists(backup):
        print(f'  [backup kept] {backup}')
    else:
        shutil.copy2(z, backup)
        print(f'  [backup created] {backup}')

    try:
        append_entry_bytes(z, entry, patch)
        verify_patched(z)
    except (RuntimeError, zipfile.BadZipFile, OSError) as exc:
        print(f'error: failed to patch {z}: {exc}', file=sys.stderr)
        rc = 1
        continue
    print(f'  [patched] {z}')

raise SystemExit(rc)
PY
py_rc=$?
set -e

if (( py_rc != 0 )); then
    exit "$py_rc"
fi

# 修改后再次用 unzip -t 校验归档完整性。
if [[ "$MODE" == 'apply' ]]; then
    for z in "${ZIPS[@]}"; do
        zip_integrity "$z" || exit 1
    done
fi

if [[ "$MODE" == 'check' ]]; then
    echo "result: OK — NeoForm ${NEOFORM_VERSION} already patched"
else
    echo "result: OK — patch applied and verified"
fi
