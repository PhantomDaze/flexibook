#!/usr/bin/env bash
# Run the local equivalent of the old GitHub CI: unit tests on all 13 nodes,
# then print a per-node JUnit summary. Optionally run the client smoke.
#
#   Pass L (Gradle 8.14.3, JDK21): 1.20.1 Forge/Fabric     — 2 nodes
#   Pass A (Gradle 9.6.1, JDK21): 1.21.x                   — 5 nodes
#   Pass B (Gradle 9.6.1, JDK25): 26.x                     — 6 nodes
#
# Pass B needs the NeoForm 26.2-2 / 26.3-1 HolderSet patch; this script only checks
# for it and asks you to run scripts/patch-neoform.sh (it never edits your cache).
#
# Usage:
#   bash scripts/test-all.sh                  # unit tests only
#   bash scripts/test-all.sh --smoke          # unit tests + client smoke (needs a display)
#   bash scripts/test-all.sh --smoke-only     # client smoke only
#   bash scripts/test-all.sh --help
#
# Environment overrides: JDK21, JDK25, and DISPLAY / xvfb-run for smoke.
set -euo pipefail

cd "$(dirname "$0")/.."

JDK21="${JDK21:-/usr/lib/jvm/java-21-openjdk}"
JDK25="${JDK25:-/usr/lib/jvm/java-25-openjdk}"

LEGACY_NODES=(1.20.1 1.20.1-fabric)
PASS_A_NODES=(1.21.1 1.21.1-fabric 1.21.4 1.21.4-fabric 1.21.11-fabric)
PASS_B_NODES=(26.1.2 26.1.2-fabric 26.2 26.2-fabric 26.3 26.3-fabric)
ALL_NODES=("${LEGACY_NODES[@]}" "${PASS_A_NODES[@]}" "${PASS_B_NODES[@]}")

RUN_TESTS=1
RUN_SMOKE=0
for arg in "$@"; do
    case "$arg" in
        --smoke)      RUN_SMOKE=1 ;;
        --smoke-only) RUN_TESTS=0; RUN_SMOKE=1 ;;
        --help|-h)
            sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "error: unknown argument: $arg (try --help)" >&2
            exit 2
            ;;
    esac
done

for home in "$JDK21" "$JDK25"; do
    if [[ ! -x "$home/bin/java" ]]; then
        echo "error: no java at $home/bin/java (set JDK21 / JDK25)" >&2
        exit 2
    fi
done

# NeoForm 26.2-2 / 26.3-1 的 HolderSet 补丁是 Pass B（26.x 测试 / smoke）的前置条件。
# 只检查、不修改用户的 Gradle 缓存；未打补丁时给出明确指引并非零退出。
preflight_neoform() {
    local rc=0
    echo ""
    echo "========== NeoForm 26.2-2 / 26.3-1 preflight =========="
    bash scripts/patch-neoform.sh --check || rc=$?
    case "$rc" in
        0) ;;
        2)
            echo "note: one or more NeoForm 26.2-2 / 26.3-1 zips are not in the Gradle cache yet."
            echo "      The first Pass B build will download them and is expected to fail in"
            echo "      :26.2:createMinecraftArtifacts or :26.3:createMinecraftArtifacts. Then run"
            echo "        bash scripts/patch-neoform.sh"
            echo "      and rerun this script."
            ;;
        3)
            echo "" >&2
            echo "ERROR: a NeoForm 26.2-2 / 26.3-1 zip in the Gradle cache lacks the HolderSet patch." >&2
            echo "       NeoForge 26.2/26.3 tests and smoke will fail in createMinecraftArtifacts." >&2
            echo "" >&2
            echo "       Run this first (each modified zip is backed up as <zip>.orig):" >&2
            echo "" >&2
            echo "         bash scripts/patch-neoform.sh" >&2
            echo "" >&2
            exit 1
            ;;
        *)
            echo "error: NeoForm 26.x preflight failed (exit $rc)" >&2
            exit 1
            ;;
    esac
}

# ---------------------------------------------------------------- unit tests

test_node() {
    local home="$1" node="$2"
    echo ""
    echo "========== :$node:test (JAVA_HOME=$home) =========="
    if ! JAVA_HOME="$home" ./gradlew ":$node:test" --console=plain --stacktrace; then
        echo "" >&2
        echo "FAILED: :$node:test (JAVA_HOME=$home)" >&2
        return 1
    fi
}

test_legacy_node() {
    local home="$1" node="$2"
    echo ""
    echo "========== :$node:test [legacy-gradle8] (JAVA_HOME=$home) =========="
    if ! JAVA_HOME="$home" legacy-gradle8/gradlew \
            -c settings.legacy.gradle.kts \
            -Pstonecutter.active=1.20.1-fabric \
            ":$node:test" --console=plain --stacktrace; then
        echo "" >&2
        echo "FAILED: :$node:test [legacy-gradle8] (JAVA_HOME=$home)" >&2
        return 1
    fi
}

run_unit_tests() {
    # 先把旧的 XML 清掉，避免把上一轮残留用例统计进来。
    for node in "${ALL_NODES[@]}"; do
        rm -rf "versions/$node/build/test-results/test"
    done

    # 所有节点都跑完再汇总（不因单节点失败提前退出，便于一次看全）。
    local failed=0
    for node in "${LEGACY_NODES[@]}"; do
        test_legacy_node "$JDK21" "$node" || failed=1
    done
    for node in "${PASS_A_NODES[@]}"; do
        test_node "$JDK21" "$node" || failed=1
    done
    for node in "${PASS_B_NODES[@]}"; do
        test_node "$JDK25" "$node" || failed=1
    done

    report_totals || failed=1
    if (( failed != 0 )); then
        echo "" >&2
        echo "UNIT TESTS FAILED (see :node:test output above)" >&2
        return 1
    fi
}

# 解析 Gradle 生成的 JUnit XML，按节点汇总 tests/failures/errors/skipped。
report_totals() {
    python3 - "${ALL_NODES[@]}" <<'PYEOF'
import glob
import sys
import xml.etree.ElementTree as ET

expected = sys.argv[1:]
pattern = 'versions/*/build/test-results/test/*.xml'
files = sorted(glob.glob(pattern))

per_node = {node: [0, 0, 0, 0] for node in expected}
parse_errors = []
for path in files:
    node = path.split('/')[1]
    if node not in per_node:
        continue
    try:
        root = ET.parse(path).getroot()
    except Exception as exc:
        parse_errors.append(f'{path}: {exc}')
        continue
    values = [int(root.get(k, 0) or 0) for k in ('tests', 'failures', 'errors', 'skipped')]
    for i, v in enumerate(values):
        per_node[node][i] += v

header = f"{'node':24} {'tests':>7} {'failures':>9} {'errors':>7} {'skipped':>8}"
print("")
print("========== test totals ==========")
print(header)
print('-' * len(header))
total = [0, 0, 0, 0]
missing = 0
for node in expected:
    c = per_node[node]
    if c[0] == 0 and node not in {p.split('/')[1] for p in files}:
        print(f'{node:24} {"MISSING":>7}')
        missing += 1
        continue
    print(f'{node:24} {c[0]:7} {c[1]:9} {c[2]:7} {c[3]:8}')
    for i, v in enumerate(c):
        total[i] += v
print('-' * len(header))
summary = f'TOTAL ({len(expected)} nodes)'
print(f'{summary:24} {total[0]:7} {total[1]:9} {total[2]:7} {total[3]:8}')

for msg in parse_errors:
    print(f'warning: failed to parse JUnit XML {msg}', file=sys.stderr)

# 有节点缺失或任何失败/错误都返回非零。
if missing or total[1] or total[2]:
    sys.exit(1)
sys.exit(0)
PYEOF
}

# -------------------------------------------------------------------- smoke

run_smoke() {
    if [[ -z "${DISPLAY:-}" ]] && ! command -v xvfb-run >/dev/null 2>&1; then
        echo "" >&2
        echo "error: client smoke needs a display." >&2
        echo "  - run inside a desktop session (DISPLAY is set), or" >&2
        echo "  - install xvfb and let this script use 'xvfb-run -a'." >&2
        echo "  Or run manually: ./gradlew smokeAllClientsPassA" >&2
        return 2
    fi

    if [[ -z "${DISPLAY:-}" ]]; then
        echo ""
        echo "========== client smoke (xvfb-run, no DISPLAY) =========="
        echo "note: software rendering is slow; expect several minutes."
    else
        echo ""
        echo "========== client smoke (DISPLAY=$DISPLAY) =========="
    fi

    # smokeAllClients 内部按 pass 切 JDK21/JDK25；显式传入环境变量保证一致。
    if [[ -z "${DISPLAY:-}" ]]; then
        JDK21="$JDK21" JDK25="$JDK25" LIBGL_ALWAYS_SOFTWARE=1 \
            xvfb-run -a --server-args="-screen 0 1280x720x24" \
            ./gradlew smokeAllClients --console=plain
    else
        JDK21="$JDK21" JDK25="$JDK25" ./gradlew smokeAllClients --console=plain
    fi
}

# --------------------------------------------------------------------- main

preflight_neoform

status=0
if (( RUN_TESTS )); then
    run_unit_tests || status=1
fi
if (( RUN_SMOKE )); then
    run_smoke || status=1
fi
exit "$status"
