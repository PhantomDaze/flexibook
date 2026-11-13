#!/usr/bin/env bash
# Build all 11 FlexiBook version nodes in two JVM passes.
#
#   Pass A (JDK21 daemon): 1.20.1 (Forge 47), 1.21.x — 7 nodes
#   Pass B (JDK25 daemon): 26.x                     — 4 nodes
#
# Gradle picks its daemon JVM from JAVA_HOME (one JVM per invocation), so the
# two passes must be separate ./gradlew calls. Override the JDKs with the
# JDK21 / JDK25 environment variables if they live elsewhere.
set -euo pipefail

cd "$(dirname "$0")/.."

JDK21="${JDK21:-/usr/lib/jvm/java-21-openjdk}"
JDK25="${JDK25:-/usr/lib/jvm/java-25-openjdk}"

PASS_A_NODES=(1.20.1 1.20.1-fabric 1.21.1 1.21.1-fabric 1.21.4 1.21.4-fabric 1.21.11-fabric)
PASS_B_NODES=(26.1.2 26.1.2-fabric 26.2 26.2-fabric)

for home in "$JDK21" "$JDK25"; do
    if [[ ! -x "$home/bin/java" ]]; then
        echo "error: no java at $home/bin/java (set JDK21 / JDK25)" >&2
        exit 2
    fi
done

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

run_pass() {
    local home="$1"; shift
    local node
    for node in "$@"; do
        build_node "$home" "$node"
    done
}

run_pass "$JDK21" "${PASS_A_NODES[@]}"
run_pass "$JDK25" "${PASS_B_NODES[@]}"

echo ""
echo "========== built jars =========="
missing=0
for node in "${PASS_A_NODES[@]}" "${PASS_B_NODES[@]}"; do
    jar=$(find "versions/$node/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' 2>/dev/null | head -1 || true)
    if [[ -n "$jar" ]]; then
        echo "$node -> $jar"
    else
        echo "$node -> MISSING" >&2
        missing=1
    fi
done
exit "$missing"
