#!/usr/bin/env bash
# Build all 13 FlexiBook version nodes.
#
#   Pass L (Gradle 8.14.3, JDK21): 1.20.1 Forge/Fabric     — 2 nodes
#   Pass A (Gradle 9.6.1, JDK21): 1.21.x                   — 5 nodes
#   Pass B (Gradle 9.6.1, JDK25): 26.x                     — 6 nodes
#
# Pass B needs the NeoForm 26.3-1 HolderSet patch; this script only checks for it
# and asks you to run scripts/patch-neoform-263.sh (it never edits your cache).
#
# Pass L uses settings.legacy.gradle.kts / build.legacy.gradle through the
# legacy-gradle8 wrapper: ForgeGradle 6 cannot run on Gradle 9 (MCPRuntime
# touches closed project services), and Gradle 8.14.5 broke its cold artifact
# generation, so the legacy build is pinned to 8.14.3.
#
# Gradle picks its daemon JVM from JAVA_HOME (one JVM per invocation), so the
# passes must be separate ./gradlew calls. Override the JDKs with JDK21/JDK25.
set -euo pipefail

cd "$(dirname "$0")/.."

JDK21="${JDK21:-/usr/lib/jvm/java-21-openjdk}"
JDK25="${JDK25:-/usr/lib/jvm/java-25-openjdk}"

LEGACY_NODES=(1.20.1 1.20.1-fabric)
PASS_A_NODES=(1.21.1 1.21.1-fabric 1.21.4 1.21.4-fabric 1.21.11-fabric)
PASS_B_NODES=(26.1.2 26.1.2-fabric 26.2 26.2-fabric 26.3 26.3-fabric)

for home in "$JDK21" "$JDK25"; do
    if [[ ! -x "$home/bin/java" ]]; then
        echo "error: no java at $home/bin/java (set JDK21 / JDK25)" >&2
        exit 2
    fi
done

# NeoForm 26.3-1 的 HolderSet 补丁是 Pass B 的前置条件。这里只检查、不修改用户
# 的 Gradle 缓存；未打补丁时给出明确指引并非零退出。
preflight_neoform_263() {
    local rc=0
    echo ""
    echo "========== NeoForm 26.3-1 preflight =========="
    bash scripts/patch-neoform-263.sh --check || rc=$?
    case "$rc" in
        0) ;;
        2)
            echo "note: neoform-26.3-1.zip is not in the Gradle cache yet."
            echo "      The first Pass B build will download it and is expected to fail in"
            echo "      :26.3:createMinecraftArtifacts. Then run"
            echo "        bash scripts/patch-neoform-263.sh"
            echo "      and rerun this script."
            ;;
        3)
            echo "" >&2
            echo "ERROR: NeoForm 26.3-1 in the Gradle cache lacks the HolderSet patch." >&2
            echo "       NeoForge 26.3 builds will fail in :26.3:createMinecraftArtifacts." >&2
            echo "" >&2
            echo "       Run this first (it backs up the cached zip as neoform-26.3-1.zip.orig):" >&2
            echo "" >&2
            echo "         bash scripts/patch-neoform-263.sh" >&2
            echo "" >&2
            exit 1
            ;;
        *)
            echo "error: NeoForm 26.3-1 preflight failed (exit $rc)" >&2
            exit 1
            ;;
    esac
}

build_node() {
    local home="$1" node="$2"
    echo ""
    echo "========== :$node:build (JAVA_HOME=$home) =========="
    if ! JAVA_HOME="$home" ./gradlew ":$node:build" --console=plain --stacktrace; then
        echo "" >&2
        echo "FAILED: :$node:build (JAVA_HOME=$home)" >&2
        exit 1
    fi
}

build_legacy_node() {
    local home="$1" node="$2"
    echo ""
    echo "========== :$node:build [legacy-gradle8] (JAVA_HOME=$home) =========="
    if ! JAVA_HOME="$home" legacy-gradle8/gradlew \
            -c settings.legacy.gradle.kts \
            -Pstonecutter.active=1.20.1-fabric \
            ":$node:build" --console=plain --stacktrace; then
        echo "" >&2
        echo "FAILED: :$node:build [legacy-gradle8] (JAVA_HOME=$home)" >&2
        exit 1
    fi
}

run_pass() {
    local home="$1"; shift
    local node
    for node in "$@"; do
        build_node "$home" "$node"
    done
}

preflight_neoform_263

for node in "${LEGACY_NODES[@]}"; do
    build_legacy_node "$JDK21" "$node"
done
run_pass "$JDK21" "${PASS_A_NODES[@]}"
run_pass "$JDK25" "${PASS_B_NODES[@]}"

echo ""
echo "========== built jars =========="
missing=0
for node in "${LEGACY_NODES[@]}" "${PASS_A_NODES[@]}" "${PASS_B_NODES[@]}"; do
    jar=$(find "versions/$node/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' 2>/dev/null | head -1 || true)
    if [[ -n "$jar" ]]; then
        echo "$node -> $jar"
    else
        echo "$node -> MISSING" >&2
        missing=1
    fi
done
exit "$missing"
