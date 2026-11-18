#!/usr/bin/env bash
# 退役一个版本节点：从 versions/、settings、pass 数组、.run 与 README 中成组移除。
#
# 这个脚本只改工作区，不执行 git commit / push。
# --dry-run 只打印将删除的内容；所有安全检查默认拒绝，确认后加 --force。
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 - "$@" <<'PY'
import re
import shlex
import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()
VERSIONS = ROOT / "versions"
RUN = ROOT / ".run"
SETTINGS_MAIN = ROOT / "settings.gradle.kts"
SETTINGS_LEGACY = ROOT / "settings.legacy.gradle.kts"
STONECUTTER = ROOT / "stonecutter.gradle.kts"
BUILD_ALL = ROOT / "scripts" / "build-all.sh"
TEST_ALL = ROOT / "scripts" / "test-all.sh"
README_EN = ROOT / "README.md"
README_ZH = ROOT / "README.zh-CN.md"

LOADER_LABEL = {"neoforge": "NeoForge", "forge": "Forge", "fabric": "Fabric"}
LOADER_RANK = {"neoforge": 0, "forge": 0, "fabric": 1}
NUM_WORDS = {
    1: "one", 2: "two", 3: "three", 4: "four", 5: "five", 6: "six",
    7: "seven", 8: "eight", 9: "nine", 10: "ten", 11: "eleven", 12: "twelve",
}
CN_NUM = {
    1: "一", 2: "两", 3: "三", 4: "四", 5: "五", 6: "六",
    7: "七", 8: "八", 9: "九", 10: "十", 11: "十一", 12: "十二",
}
VERS_RE = re.compile(r'^\s*vers\("([^"]+)",\s*"([^"]+)"\)\s*$', re.M)


class UserError(Exception):
    pass


def die(message, code=1):
    print(f"error: {message}", file=sys.stderr)
    sys.exit(code)


def note(message):
    print(f"note: {message}")


def usage():
    print("""用法:
  bash scripts/drop-node.sh <node> [选项]

示例:
  bash scripts/drop-node.sh 26.2
  bash scripts/drop-node.sh 26.2 --dry-run

选项:
  --force               跳过安全检查（pass 数组变空、最后一个 loader / JDK 节点等）
  --keep-run-configs    保留 .run/ 下对应 IDE 配置
  --dry-run             只打印将删除的内容，不改任何文件
  -h, --help            显示本帮助

说明:
  - 删除后如果某个 pass 数组会变空、某个 mod_loader 或 java_version 不再有节点，
    默认拒绝；确认后加 --force。
  - 删除 26.3 节点时只提示 NeoForm 补丁预检，不会自动修改脚本。
  - 脚本不会执行 git commit / push。""", file=sys.stderr)


def parse_args(argv):
    opts = {"node": None, "force": False, "keep_run": False, "dry": False}
    for arg in argv:
        if arg in ("-h", "--help"):
            usage()
            sys.exit(0)
        if arg == "--force":
            opts["force"] = True
        elif arg == "--keep-run-configs":
            opts["keep_run"] = True
        elif arg == "--dry-run":
            opts["dry"] = True
        elif arg.startswith("-"):
            die(f"未知选项 {arg}（--help 查看用法）", 2)
        elif opts["node"] is None:
            opts["node"] = arg
        else:
            die(f"多余的位置参数 {arg}", 2)
    if not opts["node"]:
        usage()
        die("缺少节点名", 2)
    return opts


def parse_props(text):
    props = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def read_props(node):
    path = VERSIONS / node / "gradle.properties"
    if not path.is_file():
        return None
    return parse_props(path.read_text(encoding="utf-8"))


def existing_nodes():
    return sorted(
        d.name for d in VERSIONS.iterdir()
        if d.is_dir() and (d / "gradle.properties").is_file()
    )


def vers_entries(path):
    if not path.is_file():
        return []
    return [(m.group(1), m.group(2)) for m in VERS_RE.finditer(path.read_text(encoding="utf-8"))]


def num_key(version):
    return tuple(int(x) for x in re.findall(r"\d+", version))


def node_sort_key(name, props):
    return (
        num_key(props.get("minecraft_version", "")),
        LOADER_RANK.get(props.get("mod_loader", ""), 9),
        name,
    )


def pass_for_props(props, legacy):
    if legacy:
        return "L"
    try:
        java = int(props.get("java_version", "0"))
    except ValueError:
        return None
    if java == 21:
        return "A"
    if java >= 25:
        return "B"
    return None


def remove_vers_lines(text, nodes):
    pattern = re.compile(r'^\s*vers\("([^"]+)",\s*"[^"]+"\)\s*$')
    kept = []
    for line in text.splitlines(keepends=True):
        match = pattern.match(line)
        if match and match.group(1) in nodes:
            continue
        kept.append(line)
    return "".join(kept)


def edit_kotlin_list(text, variable, new_nodes, props_map, remove=False):
    pattern = re.compile(rf"(val {variable} = listOf\()(.*?)(\))", re.S)
    match = pattern.search(text)
    if not match:
        raise UserError(f"stonecutter.gradle.kts 找不到 {variable} 列表")
    body = match.group(2)
    existing = re.findall(r'"([^"]+)"', body)
    if remove:
        merged = [n for n in existing if n not in new_nodes]
        merged = sorted(set(merged), key=lambda n: node_sort_key(n, props_map.get(n, {})))
    else:
        merged = sorted(set(existing) | set(new_nodes),
                        key=lambda n: node_sort_key(n, props_map.get(n, {})))
    if "\n" in body:
        indent_match = re.search(r'\n([ \t]+)"', body)
        indent = indent_match.group(1) if indent_match else "    "
        new_body = "\n" + indent + ", ".join(f'"{n}"' for n in merged) + "\n"
    else:
        new_body = ", ".join(f'"{n}"' for n in merged)
    return text[:match.start(2)] + new_body + text[match.end(2):]


def edit_shell_array(text, variable, new_nodes, props_map, remove=False):
    pattern = re.compile(rf"^({re.escape(variable)}=)\(([^)]*)\)$", re.M)
    match = pattern.search(text)
    if not match:
        raise UserError(f"{variable} 数组未找到")
    existing = match.group(2).split()
    if remove:
        merged = [n for n in existing if n not in new_nodes]
        merged = sorted(set(merged), key=lambda n: node_sort_key(n, props_map.get(n, {})))
    else:
        merged = sorted(set(existing) | set(new_nodes),
                        key=lambda n: node_sort_key(n, props_map.get(n, {})))
    return text[:match.start(2)] + " ".join(merged) + text[match.end(2):]


def edit_readme_table(text, nodes):
    lines = text.splitlines(keepends=True)
    row_pattern = re.compile(r"^\| `([^`]+)` \|")
    return "".join(
        line for line in lines
        if not (row_pattern.match(line) and row_pattern.match(line).group(1) in nodes)
    )


def count_rules(counts):
    total = str(counts["total"])
    main = str(counts["main"])
    legacy = str(counts["legacy"])
    pa = str(counts["A"])
    pb = str(counts["B"])
    pa_word = NUM_WORDS.get(counts["A"], pa)
    pb_word = NUM_WORDS.get(counts["B"], pb)
    pa_cn = CN_NUM.get(counts["A"], pa)
    pb_cn = CN_NUM.get(counts["B"], pb)
    return {
        README_EN: [
            (r"\*\*(\d+) version nodes\*\*", total, total, 1),
            (r"The (\d+) nodes are split", total, total, 1),
            (r"Loom \*\*1\.17\.17\*\*, (\d+) nodes", main, main, 1),
            (r"Loom \*\*1\.13\.6\*\*, (\d+) nodes", legacy, legacy, 1),
            (r"# Everything \((\d+) nodes,", total, total, 1),
            (r"# All (\d+) nodes \(Pass", total, total, 1),
            (r"# 1\.21\.x ([a-z]+|\d+) nodes", pa_word, pa_word, 1),
            (r"# 26\.x ([a-z]+|\d+) nodes", pb_word, pb_word, 1),
            (r"unit tests on all (\d+) nodes", total, total, 1),
            (r"smoke on all (\d+) nodes", total, total, 1),
            (r"`build-main` \([^)]*?, (\d+) nodes\)", main, main, 1),
            (r"uploads the (\d+) jars", total, total, 1),
            (r"\*\*All (\d+) nodes pass", total, total, 1),
            (r"multi-version build \((\d+) nodes\)", total, total, 1),
        ],
        README_ZH: [
            (r"\*\*(\d+) 个版本节点\*\*", total, total, 1),
            (r"(\d+) 个节点被拆到", total, total, 1),
            (r"1\.17\.17\*\*，(\d+) 个节点", main, main, 1),
            (r"1\.13\.6\*\*，(\d+) 个节点", legacy, legacy, 1),
            (r"# 全量（(\d+) 节点", total, total, 1),
            (r"# 全部 (\d+) 节点（Pass", total, total, 1),
            (r"# 1\.21\.x ([一二三四五六七八九十两]+|\d+)节点", pa_cn, pa_cn, 1),
            (r"# 26\.x ([一二三四五六七八九十两]+|\d+)节点", pb_cn, pb_cn, 1),
            (r"# (\d+) 节点单元测试", total, total, 1),
            (r"全部 (\d+) 节点客户端 smoke", total, total, 1),
            (r"`build-main`（[^）]*?，(\d+) 节点）", main, main, 1),
            (r"上传 (\d+) 个 jar", total, total, 1),
            (r"\*\*(\d+) 个节点全部通过", total, total, 1),
            (r"多版本构建（(\d+) 节点）", total, total, 1),
        ],
        SETTINGS_MAIN: [
            (r"// (\d+) nodes in the main build", total, total, 1),
        ],
        STONECUTTER: [
            (r"// (\d+) nodes\)", total, total, 1),
            (r'"Build the (\d+) Java 21 nodes', pa, pa, 1),
            (r'"Build the (\d+) Java 25 nodes', pb, pb, 1),
            (r'"Run clientAutoSmoke on the (\d+) Java 21 nodes', pa, pa, 1),
            (r'"Run clientAutoSmoke on the (\d+) Java 25 nodes', pb, pb, 1),
            (r'"Alias: clientAutoSmoke on the (\d+) Java 21 nodes', pa, pa, 1),
            (r'One command for all (\d+) nodes', total, total, 1),
            (r'"smokeAllClients: all (\d+) nodes completed', total, total, 1),
            (r'description = "Run the client smoke on all (\d+) nodes', total, total, 1),
        ],
        BUILD_ALL: [
            (r"^# Build all (\d+) FlexiBook version nodes\.", total, total, 1),
            (r"^#   Pass L .* — (\d+) nodes", legacy, legacy, 1),
            (r"^#   Pass A .* — (\d+) nodes", pa, pa, 1),
            (r"^#   Pass B .* — (\d+) nodes", pb, pb, 1),
        ],
        TEST_ALL: [
            (r"^# Run the local equivalent of the old GitHub CI: unit tests on all (\d+) nodes,",
             total, total, 1),
            (r"^#   Pass L .* — (\d+) nodes", legacy, legacy, 1),
            (r"^#   Pass A .* — (\d+) nodes", pa, pa, 1),
            (r"^#   Pass B .* — (\d+) nodes", pb, pb, 1),
        ],
    }


def apply_count_rules(texts, counts):
    warnings = []
    for path, rules in count_rules(counts).items():
        text = texts.get(path)
        if text is None:
            continue
        for pattern, _expected, replacement, group in rules:
            pieces = []
            last = 0
            matched = False
            for match in re.finditer(pattern, text, re.M):
                matched = True
                start, end = match.span(group)
                pieces.append(text[last:start])
                pieces.append(replacement)
                last = end
            if matched:
                pieces.append(text[last:])
                text = "".join(pieces)
            else:
                warnings.append(f"{path.name}: 计数模式未匹配：{pattern}")
        texts[path] = text
    return warnings


def verify_counts(texts, counts):
    problems = []
    for path, rules in count_rules(counts).items():
        text = texts.get(path)
        if text is None:
            continue
        for pattern, expected, _replacement, group in rules:
            for match in re.finditer(pattern, text, re.M):
                got = match.group(group)
                if got != expected:
                    line_no = text.count("\n", 0, match.start()) + 1
                    problems.append(
                        f"{path.relative_to(ROOT)}:{line_no}: 计数为 {got!r}，应为 {expected!r} "
                        f"（{match.group(0).strip()!r}）"
                    )
    return problems


def compute_counts(nodes, props_map, legacy_set):
    total = len(nodes)
    legacy = sum(1 for name in nodes if name in legacy_set)
    main = total - legacy
    pass_a = 0
    pass_b = 0
    for name in nodes:
        if name in legacy_set:
            continue
        node_pass = pass_for_props(props_map[name], False)
        if node_pass == "A":
            pass_a += 1
        elif node_pass == "B":
            pass_b += 1
    return {"total": total, "main": main, "legacy": legacy, "A": pass_a, "B": pass_b}


def array_members(path, variable, kotlin):
    text = path.read_text(encoding="utf-8")
    if kotlin:
        match = re.search(rf"val {variable} = listOf\((.*?)\)", text, re.S)
        return re.findall(r'"([^"]+)"', match.group(1)) if match else None
    match = re.search(rf"^{re.escape(variable)}=\(([^)]*)\)$", text, re.M)
    return match.group(1).split() if match else None


def expected_run_paths(node, props):
    label = LOADER_LABEL.get(props.get("mod_loader", ""), props.get("mod_loader", ""))
    mc = props.get("minecraft_version", "")
    return sorted(RUN.glob(f"FlexiBook {label} {mc} *.run.xml"))


def main():
    opts = parse_args(sys.argv[1:])
    node = opts["node"]
    if not (VERSIONS / node).is_dir():
        die(f"节点 {node!r} 不存在（versions/{node}/）")
    props = read_props(node)
    if props is None:
        die(f"versions/{node}/gradle.properties 不存在，无法确定节点属性")

    nodes_before = existing_nodes()
    props_map = {name: (read_props(name) or {}) for name in nodes_before}
    legacy_set = {name for name, _ in vers_entries(SETTINGS_LEGACY)}
    is_legacy = node in legacy_set

    # ---- 安全检查 ---------------------------------------------------------
    reasons = []
    array_checks = [
        (STONECUTTER, "legacyNodes", True),
        (STONECUTTER, "passANodes", True),
        (STONECUTTER, "passBNodes", True),
        (BUILD_ALL, "LEGACY_NODES", False),
        (BUILD_ALL, "PASS_A_NODES", False),
        (BUILD_ALL, "PASS_B_NODES", False),
        (TEST_ALL, "LEGACY_NODES", False),
        (TEST_ALL, "PASS_A_NODES", False),
        (TEST_ALL, "PASS_B_NODES", False),
    ]
    for path, variable, kotlin in array_checks:
        members = array_members(path, variable, kotlin)
        if members and node in members and len(set(members) - {node}) == 0:
            reasons.append(f"{path.relative_to(ROOT)} 的 {variable} 会变空")
    same_loader = [n for n in nodes_before if props_map[n].get("mod_loader") == props.get("mod_loader")]
    if len(same_loader) == 1:
        reasons.append(f"这是最后一个 {props.get('mod_loader')} 节点")
    same_java = [n for n in nodes_before if props_map[n].get("java_version") == props.get("java_version")]
    if len(same_java) == 1:
        reasons.append(f"这是 java_version={props.get('java_version')} 的最后一个节点")

    if reasons and not opts["force"]:
        print("error: 安全检查拒绝删除；确认后请加 --force：", file=sys.stderr)
        for reason in reasons:
            print(f"  - {reason}", file=sys.stderr)
        sys.exit(1)
    if reasons:
        print("warning: 已用 --force 跳过以下安全检查：", file=sys.stderr)
        for reason in reasons:
            print(f"  - {reason}", file=sys.stderr)

    # ---- 计算删除与编辑计划 ----------------------------------------------
    run_paths = [] if opts["keep_run"] else expected_run_paths(node, props)
    final_nodes = [n for n in nodes_before if n != node]
    legacy_after = set(legacy_set) - {node}
    props_after = {n: props_map[n] for n in final_nodes}
    counts = compute_counts(final_nodes, props_after, legacy_after)

    mutable_paths = [SETTINGS_MAIN, SETTINGS_LEGACY, STONECUTTER, BUILD_ALL, TEST_ALL, README_EN, README_ZH]
    originals = {path: path.read_text(encoding="utf-8") for path in mutable_paths}
    texts = dict(originals)
    for path in (SETTINGS_MAIN, SETTINGS_LEGACY):
        texts[path] = remove_vers_lines(texts[path], {node})
    texts[STONECUTTER] = edit_kotlin_list(texts[STONECUTTER], "legacyNodes", {node}, props_map, remove=True)
    texts[STONECUTTER] = edit_kotlin_list(texts[STONECUTTER], "passANodes", {node}, props_map, remove=True)
    texts[STONECUTTER] = edit_kotlin_list(texts[STONECUTTER], "passBNodes", {node}, props_map, remove=True)
    for path in (BUILD_ALL, TEST_ALL):
        texts[path] = edit_shell_array(texts[path], "LEGACY_NODES", {node}, props_map, remove=True)
        texts[path] = edit_shell_array(texts[path], "PASS_A_NODES", {node}, props_map, remove=True)
        texts[path] = edit_shell_array(texts[path], "PASS_B_NODES", {node}, props_map, remove=True)
    texts[README_EN] = edit_readme_table(texts[README_EN], {node})
    texts[README_ZH] = edit_readme_table(texts[README_ZH], {node})

    warnings = apply_count_rules(texts, counts)
    for warning in warnings:
        print(f"warning: {warning}", file=sys.stderr)
    problems = verify_counts(texts, counts)
    if problems:
        print("error: 文档计数校验失败，未删除任何文件：", file=sys.stderr)
        for item in problems:
            print(f"  - {item}", file=sys.stderr)
        print("请人工修正上述文件后重试。", file=sys.stderr)
        sys.exit(1)

    text_changes = [p for p in mutable_paths if texts[p] != originals[p]]

    print(f"将退役节点 {node}（{props.get('mod_loader')} / MC {props.get('minecraft_version')} / "
          f"Java {props.get('java_version')}）：")
    print(f"  rm -rf versions/{node}/")
    if run_paths:
        print("  .run 配置：")
        for path in run_paths:
            print(f"    {path.name}")
    elif opts["keep_run"]:
        print("  .run 配置：保留（--keep-run-configs）")
    if text_changes:
        print("  注册/文档：")
        for path in text_changes:
            print(f"    {path.relative_to(ROOT)}")
    print(f"  节点计数：total={counts['total']} main={counts['main']} legacy={counts['legacy']} "
          f"PassA={counts['A']} PassB={counts['B']}")

    if props.get("minecraft_version") == "26.3" and not any(
        props_after[n].get("minecraft_version") == "26.3" for n in final_nodes
    ):
        note("26.3 关联 scripts/patch-neoform-263.sh 与 build-all.sh/test-all.sh 里的 "
             "preflight_neoform_263 预检。所有 26.3 节点都将被删除；请考虑一并处理该预检与补丁脚本"
             "（本脚本不会自动修改）。")

    if opts["dry"]:
        print("\n--dry-run：未删除/修改任何文件。")
        return

    # ---- 落盘 -------------------------------------------------------------
    for path in run_paths:
        path.unlink()
    shutil.rmtree(VERSIONS / node)
    for path in text_changes:
        path.write_text(texts[path], encoding="utf-8")

    print("\n完成。建议的 git 命令（脚本不会自动提交）：")
    paths = [VERSIONS / node] + run_paths + text_changes
    quoted = " ".join(shlex.quote(str(p.relative_to(ROOT))) for p in paths)
    print(f"  git add -A {quoted}")
    print(f"  git commit -m {shlex.quote('build: retire ' + node + ' version node')}")


try:
    main()
except UserError as exc:
    die(str(exc))
PY
