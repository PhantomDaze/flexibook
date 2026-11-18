#!/usr/bin/env bash
# 兼容旧名（26.3 专用入口）：等价于
#   bash scripts/patch-neoform.sh --config 26.3-1
#
# 新脚本 scripts/patch-neoform.sh 同时支持 NeoForm 26.2-2 与 26.3-1，
# 并可用 --config <id> 选择配置；请优先使用新脚本。
set -euo pipefail

exec bash "$(cd "$(dirname "$0")" && pwd)/patch-neoform.sh" --config 26.3-1 "$@"
