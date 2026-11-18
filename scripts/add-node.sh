#!/usr/bin/env bash
# 新增一个 Minecraft 版本节点（或一组 loader 节点），并把它接进所有注册点。
#
# 这个脚本只改工作区，不执行 git commit / push。
# 所有改动先在内存中生成并校验，确认计数一致后才落盘；--dry-run 完全不写文件。
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 - "$@" <<'PY'
import json
import os
import re
import shlex
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
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

MANIFEST_URLS = [
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
]
NEO_METADATA = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
FAPI_METADATA = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
FORGE_METADATA = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
PARCHMENT = "https://maven.parchmentmc.org/org/parchmentmc/data/parchment-{mc}/maven-metadata.xml"

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
  bash scripts/add-node.sh <mc-version> [选项]

示例:
  bash scripts/add-node.sh 26.4
  bash scripts/add-node.sh 26.4 --loaders fabric
  bash scripts/add-node.sh 1.21.5 --legacy

选项:
  --loaders <a,b>           要创建的 loader（默认主构建 neoforge,fabric；--legacy 时 forge,fabric）
  --java <N>                显式指定 java_version，覆盖自动推断
  --neo-version <v>         NeoForge 版本，跳过 Maven 查询
  --fabric-api-version <v>  Fabric API 版本，跳过 Maven 查询
  --loader-version <v>      Fabric Loader 版本；legacy Forge 节点下作为 Forge 版本别名
  --forge-version <v>       Forge 版本（仅 --legacy）
  --legacy                  注册到 settings.legacy.gradle.kts（Java 17 / Gradle 8.14.3）
  --allow-non-release       允许 snapshot / pre-release / rc（默认拒绝）
  --dry-run                 只打印将要做的改动，不写任何文件
  --force                   允许覆盖内容不同的既有节点 / 生成文件
  -h, --help                显示本帮助

说明:
  - 节点命名沿用现有规则：neoforge/forge 节点为 <mc>，fabric 节点为 <mc>-fabric。
  - pass 归属：--legacy → Pass L；java 21 → Pass A；java >= 25 → Pass B；其他值直接报错。
  - 脚本不会执行 git commit / push。""", file=sys.stderr)


def fetch_text(urls, what):
    urls = urls if isinstance(urls, list) else [urls]
    headers = {"User-Agent": "flexibook-add-node/1.0"}
    last_error = None
    for url in urls:
        for attempt in range(1, 4):
            try:
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req, timeout=45) as response:
                    data = response.read()
                if data:
                    return data.decode("utf-8", "replace")
            except Exception as exc:  # 网络问题：重试
                last_error = exc
                time.sleep(attempt)
    raise UserError(f"无法获取 {what}: {last_error}")


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
    if not VERSIONS.is_dir():
        return []
    return sorted(
        d.name for d in VERSIONS.iterdir()
        if d.is_dir() and (d / "gradle.properties").is_file()
    )


def vers_entries(path):
    """返回 [(node, mc), ...]，按文件出现顺序。"""
    if not path.is_file():
        return []
    return [(m.group(1), m.group(2)) for m in VERS_RE.finditer(path.read_text(encoding="utf-8"))]


def num_key(version):
    return tuple(int(x) for x in re.findall(r"\d+", version))


def pad_key(key, length):
    return key + (0,) * (length - len(key))


def version_distance(left, right):
    a, b = num_key(left), num_key(right)
    size = max(len(a), len(b))
    return sum(abs(x - y) for x, y in zip(pad_key(a, size), pad_key(b, size)))


def node_sort_key(name, props):
    return (
        num_key(props.get("minecraft_version", "")),
        LOADER_RANK.get(props.get("mod_loader", ""), 9),
        name,
    )


def is_legacy_node(name, legacy_set):
    return name in legacy_set


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


def bump_patch(version):
    parts = version.split(".")
    try:
        parts[-1] = str(int(parts[-1]) + 1)
    except ValueError:
        return version + ".1"
    return ".".join(parts)


def derive_range(template_props, target_mc):
    old_mc = template_props["minecraft_version"]
    old = template_props["minecraft_version_range"]
    if old.startswith("["):
        match = re.fullmatch(r"\[([^,\]]+)(?:,([^)\]]+))?([)\]])", old)
        if match and match.group(2):
            return f"[{target_mc},{bump_patch(target_mc)})"
        return f"[{target_mc}]"
    if old.startswith("~"):
        if old == "~" + old_mc:
            return "~" + target_mc
        if old.endswith("-"):
            return "~" + ".".join(target_mc.split(".")[:2]) + "-"
        if old_mc in old:
            return old.replace(old_mc, target_mc)
        return "~" + target_mc
    return old


def set_prop(text, key, value):
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.M)
    if not pattern.search(text):
        raise UserError(f"模板缺少属性 {key}；请先人工确认节点属性键集合")
    return pattern.sub(f"{key}={value}", text, count=1)


def xml_versions(text):
    return re.findall(r"<version>([^<]+)</version>", text)


def is_prerelease(version):
    return bool(re.search(r"-(beta|alpha|pre|rc|snapshot)", version, re.I))


def version_choice_key(version):
    base = re.split(r"[-+]", version)[0]
    return (num_key(base), 0 if is_prerelease(version) else 1)


def pick_highest(versions):
    return max(versions, key=version_choice_key)


def fabric_loader_version():
    values = []
    for name in existing_nodes():
        props = read_props(name) or {}
        if props.get("mod_loader") == "fabric" and props.get("loader_version"):
            values.append((props["loader_version"], name))
    if not values:
        return None, None
    counts = Counter(v for v, _ in values)
    best = sorted(counts, key=lambda v: (counts[v], version_choice_key(v)), reverse=True)[0]
    sources = sorted({name for v, name in values if v == best})
    return best, sources


def lookup_neo(mc):
    prefix = mc[2:] if mc.startswith("1.") else mc
    versions = xml_versions(fetch_text(NEO_METADATA, "NeoForge maven-metadata.xml"))
    matches = [v for v in versions if v.startswith(prefix + ".")]
    if not matches:
        return None, None
    releases = [v for v in matches if not is_prerelease(v)]
    if releases:
        return pick_highest(releases), "stable"
    return pick_highest(matches), "prerelease"


def lookup_fabric_api(mc):
    versions = xml_versions(fetch_text(FAPI_METADATA, "Fabric API maven-metadata.xml"))
    matches = [v for v in versions if v.endswith("+" + mc)]
    if not matches:
        return None
    return pick_highest(matches)


def lookup_forge(mc):
    versions = xml_versions(fetch_text(FORGE_METADATA, "Forge maven-metadata.xml"))
    matches = [v for v in versions if v.startswith(mc + "-")]
    if not matches:
        return None
    return pick_highest(v[len(mc) + 1:] for v in matches)


def lookup_parchment(mc):
    url = PARCHMENT.format(mc=mc)
    try:
        versions = xml_versions(fetch_text([url], "Parchment maven-metadata.xml"))
    except UserError:
        return None
    return pick_highest(versions) if versions else None


def parse_args(argv):
    opts = {
        "mc": None, "loaders": None, "java": None, "neo": None, "fapi": None,
        "loader": None, "forge": None, "legacy": False, "dry": False,
        "force": False, "allow_non_release": False,
    }
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg in ("-h", "--help"):
            usage()
            sys.exit(0)
        if arg.startswith("--"):
            name, eq, inline = arg.partition("=")

            def value():
                nonlocal i
                if eq:
                    return inline
                i += 1
                if i >= len(argv):
                    die(f"{name} 需要一个参数", 2)
                return argv[i]

            if name == "--loaders":
                opts["loaders"] = value()
            elif name == "--java":
                opts["java"] = value()
            elif name == "--neo-version":
                opts["neo"] = value()
            elif name == "--fabric-api-version":
                opts["fapi"] = value()
            elif name == "--loader-version":
                opts["loader"] = value()
            elif name == "--forge-version":
                opts["forge"] = value()
            elif name == "--legacy":
                if eq:
                    die("--legacy 不接受参数", 2)
                opts["legacy"] = True
            elif name == "--dry-run":
                if eq:
                    die("--dry-run 不接受参数", 2)
                opts["dry"] = True
            elif name == "--force":
                if eq:
                    die("--force 不接受参数", 2)
                opts["force"] = True
            elif name == "--allow-non-release":
                if eq:
                    die("--allow-non-release 不接受参数", 2)
                opts["allow_non_release"] = True
            else:
                die(f"未知选项 {name}（--help 查看用法）", 2)
        elif opts["mc"] is None:
            opts["mc"] = arg
        else:
            die(f"多余的位置参数 {arg}", 2)
        i += 1
    if not opts["mc"]:
        usage()
        die("缺少 Minecraft 版本号", 2)
    return opts


def parse_loaders(raw, legacy):
    if raw is None:
        return ["forge", "fabric"] if legacy else ["neoforge", "fabric"]
    loaders = []
    for item in raw.split(","):
        item = item.strip().lower()
        if not item:
            continue
        if item not in LOADER_LABEL:
            die(f"未知 loader {item!r}（可选 neoforge / fabric / forge）", 2)
        if item not in loaders:
            loaders.append(item)
    if not loaders:
        die("--loaders 不能为空", 2)
    if "forge" in loaders and "neoforge" in loaders:
        die("同一个 MC 版本不能同时生成 forge 和 neoforge 节点（节点名会冲突）")
    if legacy and "neoforge" in loaders:
        die("legacy 构建只支持 forge/fabric；neoforge 节点请走主构建")
    if not legacy and "forge" in loaders:
        die("forge 只在 legacy 构建中支持；请加 --legacy")
    return loaders


def validate_mc(mc, allow_non_release):
    raw = fetch_text(MANIFEST_URLS, "Minecraft version_manifest_v2.json")
    try:
        manifest = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise UserError(f"version_manifest_v2.json 解析失败: {exc}")
    versions = manifest.get("versions", [])
    entry = next((v for v in versions if v.get("id") == mc), None)
    if entry is None:
        candidates = [v for v in versions if v.get("id", "").startswith(mc)]
        lines = [f"Minecraft 版本 {mc!r} 不在官方清单中。"]
        if candidates:
            lines.append("最接近的候选：")
            for item in candidates[:12]:
                lines.append(f"  {item.get('id')} ({item.get('type')})")
        else:
            lines.append("没有前缀匹配的候选。")
        raise UserError("\n".join(lines))
    if entry.get("type") != "release" and not allow_non_release:
        raise UserError(
            f"{mc} 的类型是 {entry.get('type')}，不是正式版（release）。\n"
            "非正式版可能无法被 loader 支持；确实要继续请加 --allow-non-release。"
        )
    if entry.get("type") != "release":
        note(f"{mc} 是 {entry.get('type')}，已按 --allow-non-release 继续；loader 不一定支持该版本。")
    return entry


def resolve_java(mc, entry, explicit):
    if explicit is not None:
        try:
            return int(explicit)
        except ValueError:
            die(f"--java 需要整数，收到 {explicit!r}", 2)
    try:
        raw = fetch_text([entry["url"]], f"{mc} version.json")
        data = json.loads(raw)
        return int(data["javaVersion"]["majorVersion"])
    except Exception as exc:
        note(f"无法从 {mc} 的 version.json 读取 javaVersion（{exc}），改用版本号推断。")
    if mc.startswith("26."):
        guess = 25
    elif mc.startswith("1.21."):
        guess = 21
    elif mc.startswith("1.20."):
        guess = 17
    else:
        raise UserError("无法推断 java_version；请用 --java 显式指定。")
    note(f"按 MC 版本推断 java_version={guess}（推测值，可用 --java 覆盖）。")
    return guess


def choose_template(candidates, target_mc, loader):
    pool = []
    for name in candidates:
        props = read_props(name) or {}
        if props.get("mod_loader") != loader:
            continue
        pool.append((name, props))
    if not pool:
        raise UserError(
            f"没有可参照的 {loader} 节点（同 build 内）；请先人工建立第一个 {loader} 节点。"
        )
    def rank(item):
        name, props = item
        key = num_key(props.get("minecraft_version", ""))
        return (version_distance(props.get("minecraft_version", ""), target_mc),
                tuple(-x for x in key), name)
    return sorted(pool, key=rank)[0][0]


def render_props(template_name, target_mc, loader, resolved, java_version):
    source = (VERSIONS / template_name / "gradle.properties").read_text(encoding="utf-8")
    template_props = parse_props(source)
    replacements = {
        "minecraft_version": target_mc,
        "minecraft_version_range": derive_range(template_props, target_mc),
        "java_version": str(java_version),
    }
    if loader == "fabric":
        replacements["loader_version"] = resolved["loader_version"]
        replacements["fabric_api_version"] = resolved["fabric_api_version"]
    elif loader == "neoforge":
        replacements["neo_version"] = resolved["neo_version"]
    elif loader == "forge":
        replacements["forge_version"] = resolved["forge_version"]
    if "parchment_minecraft_version" in template_props:
        replacements["parchment_minecraft_version"] = target_mc
        parchment = resolved.get("parchment_mappings_version")
        if parchment:
            replacements["parchment_mappings_version"] = parchment
        else:
            note("Parchment mappings 查询失败，沿用模板值；请人工确认 "
                 f"{template_name} 的 parchment_mappings_version 是否适用于 {target_mc}。")
    result = source
    for key, value in replacements.items():
        result = set_prop(result, key, value)
    # 模板注释里可能写着旧的版本范围（例如 fabric 的 “FAPI uses ~26.2- style”）。
    old_range = template_props.get("minecraft_version_range", "")
    new_range = replacements["minecraft_version_range"]
    if old_range and new_range != old_range:
        result = "\n".join(
            line.replace(old_range, new_range) if line.lstrip().startswith("#") else line
            for line in result.split("\n")
        )
    return result


def run_kinds(template_name, loader):
    template_props = read_props(template_name) or {}
    template_mc = template_props.get("minecraft_version", "")
    label = LOADER_LABEL[loader]
    prefix = f"FlexiBook {label} {template_mc} "
    kinds = []
    for path in sorted(RUN.glob(prefix + "*.run.xml")):
        kinds.append(path.name[len(prefix):-len(".run.xml")])
    return kinds


def render_run_config(template_name, loader, target_node, target_mc, kind):
    template_props = read_props(template_name) or {}
    template_mc = template_props.get("minecraft_version", "")
    label = LOADER_LABEL[loader]
    source_path = RUN / f"FlexiBook {label} {template_mc} {kind}.run.xml"
    if not source_path.is_file():
        raise UserError(f"缺少 .run 模板 {source_path}")
    text = source_path.read_text(encoding="utf-8")
    text = text.replace(f":{template_name}:", f":{target_node}:")
    text = text.replace(
        f"FlexiBook {label} {template_mc} {kind}",
        f"FlexiBook {label} {target_mc} {kind}",
    )
    out_path = RUN / f"FlexiBook {label} {target_mc} {kind}.run.xml"
    return out_path, text


def insert_vers(text, new_entries, target_pass, props_map, fallback_indent="        "):
    """在 settings 文件的 shared { } 块中按版本顺序插入 vers(...) 行。"""
    lines = text.splitlines(keepends=True)
    pattern = re.compile(r'^(\s*)vers\("([^"]+)",\s*"([^"]+)"\)\s*$')
    entries = []
    for index, line in enumerate(lines):
        match = pattern.match(line)
        if not match:
            continue
        name = match.group(2)
        props = props_map.get(name) or {}
        entries.append({
            "line": index,
            "key": node_sort_key(name, props),
            "pass": pass_for_props(props, target_pass == "L"),
            "indent": match.group(1),
        })
    indent = entries[0]["indent"] if entries else fallback_indent
    new_sorted = sorted(new_entries, key=lambda item: node_sort_key(item[0], props_map[item[0]]))
    new_lines = [f'{indent}vers("{name}", "{mc}")\n' for name, mc in new_sorted]
    same_pass = [entry for entry in entries if entry["pass"] == target_pass]
    if same_pass:
        first_key = node_sort_key(new_sorted[0][0], props_map[new_sorted[0][0]])
        insert_at = None
        for entry in same_pass:
            if entry["key"] > first_key:
                insert_at = entry["line"]
                break
        if insert_at is None:
            insert_at = same_pass[-1]["line"] + 1
    elif entries:
        insert_at = entries[-1]["line"] + 1
    else:
        # 没有 vers 行：插到 shared { 块结尾之前。
        insert_at = len(lines)
        inside = False
        for index, line in enumerate(lines):
            if re.match(r"^\s*shared\s*\{\s*$", line):
                inside = True
                continue
            if inside and re.match(r"^\s*\}\s*$", line):
                insert_at = index
                break
    lines[insert_at:insert_at] = new_lines
    return "".join(lines)


def remove_vers_lines(text, nodes):
    pattern = re.compile(r'^\s*vers\("([^"]+)",\s*"[^"]+"\)\s*$')
    kept = []
    for line in text.splitlines(keepends=True):
        match = pattern.match(line)
        if match and match.group(1) in nodes:
            continue
        kept.append(line)
    return "".join(kept)


def edit_kotlin_list(text, variable, new_nodes, props_map):
    pattern = re.compile(rf"(val {variable} = listOf\()(.*?)(\))", re.S)
    match = pattern.search(text)
    if not match:
        raise UserError(f"stonecutter.gradle.kts 找不到 {variable} 列表")
    body = match.group(2)
    existing = re.findall(r'"([^"]+)"', body)
    merged = sorted(set(existing) | set(new_nodes), key=lambda n: node_sort_key(n, props_map.get(n, {})))
    if "\n" in body:
        indent_match = re.search(r'\n([ \t]+)"', body)
        indent = indent_match.group(1) if indent_match else "    "
        new_body = "\n" + ", ".join(f'"{n}"' for n in merged) + "\n"
        # 保留原有缩进风格：如果原 body 里有缩进，则使用它。
        if indent_match:
            new_body = "\n" + indent + ", ".join(f'"{n}"' for n in merged) + "\n"
    else:
        new_body = ", ".join(f'"{n}"' for n in merged)
    return text[:match.start(2)] + new_body + text[match.end(2):]


def edit_shell_array(text, variable, new_nodes, props_map):
    pattern = re.compile(rf"^({re.escape(variable)}=)\(([^)]*)\)$", re.M)
    match = pattern.search(text)
    if not match:
        raise UserError(f"{variable} 数组未找到")
    existing = match.group(2).split()
    merged = sorted(set(existing) | set(new_nodes), key=lambda n: node_sort_key(n, props_map.get(n, {})))
    return text[:match.start(2)] + " ".join(merged) + text[match.end(2):]


def readme_row(node, props, legacy, lang, has_main_rows):
    loader = props["mod_loader"]
    label = LOADER_LABEL[loader]
    version = props.get("neo_version") or props.get("forge_version") or props.get("loader_version", "")
    java = props.get("java_version", "")
    if lang == "zh":
        build = "legacy（Gradle 8.14.3）" if legacy else ("main（Gradle 9.6.1）" if not has_main_rows else "main")
    else:
        build = "legacy (Gradle 8.14.3)" if legacy else ("main (Gradle 9.6.1)" if not has_main_rows else "main")
    return f"| `{node}` | {props['minecraft_version']} | {label} {version} | {java} | {build} |\n"


def edit_readme_table(text, new_rows, props_map, lang):
    if not new_rows:
        return text
    lines = text.splitlines(keepends=True)
    row_pattern = re.compile(r"^\| `([^`]+)` \|")
    indices = []
    for index, line in enumerate(lines):
        match = row_pattern.match(line)
        if match and match.group(1) in props_map:
            indices.append(index)
    if not indices:
        raise UserError(f"{lang} README 找不到版本节点表")
    if indices != list(range(indices[0], indices[-1] + 1)):
        raise UserError(f"{lang} README 节点表行不连续，请人工处理")
    has_main_rows = any(not props_map[row_pattern.match(lines[i]).group(1)].get("_legacy")
                        for i in indices)
    entries = []
    existing_names = set()
    # 既有行原样保留，只按排序插入新行，避免无意改写其他节点的描述。
    for index in indices:
        name = row_pattern.match(lines[index]).group(1)
        existing_names.add(name)
        entries.append((node_sort_key(name, props_map[name]), lines[index]))
    for name in new_rows:
        if name in existing_names:
            continue
        props = props_map[name]
        entries.append((
            node_sort_key(name, props),
            readme_row(name, props, props.get("_legacy", False), lang, has_main_rows),
        ))
    entries.sort(key=lambda item: item[0])
    return "".join(lines[:indices[0]]) + "".join(line for _, line in entries) + "".join(
        lines[indices[-1] + 1:]
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


def current_state():
    legacy_entries = vers_entries(SETTINGS_LEGACY)
    legacy_set = {name for name, _ in legacy_entries}
    nodes = existing_nodes()
    props = {name: read_props(name) or {} for name in nodes}
    return nodes, props, legacy_set


def compute_counts(nodes, props, legacy_set):
    total = len(nodes)
    legacy = sum(1 for name in nodes if name in legacy_set)
    main = total - legacy
    pass_a = 0
    pass_b = 0
    for name in nodes:
        if name in legacy_set:
            continue
        node_pass = pass_for_props(props[name], False)
        if node_pass == "A":
            pass_a += 1
        elif node_pass == "B":
            pass_b += 1
    return {"total": total, "main": main, "legacy": legacy, "A": pass_a, "B": pass_b}


def suggested_git_add(paths):
    unique = []
    for path in paths:
        text = str(path.relative_to(ROOT)) if isinstance(path, Path) else str(path)
        if text not in unique:
            unique.append(text)
    return "git add " + " ".join(shlex.quote(p) for p in unique)


def main():
    opts = parse_args(sys.argv[1:])
    mc = opts["mc"]
    loaders = parse_loaders(opts["loaders"], opts["legacy"])
    entry = validate_mc(mc, opts["allow_non_release"])
    java_version = resolve_java(mc, entry, opts["java"])

    nodes_before, props_before, legacy_set = current_state()
    target_pass = pass_for_props({"java_version": str(java_version)}, opts["legacy"])
    if target_pass is None:
        raise UserError(
            f"java_version={java_version} 不匹配任何现有 pass（Pass A=21，Pass B>=25）。\n"
            "请先新增对应的 pass（stonecutter.gradle.kts / scripts/build-all.sh / scripts/test-all.sh），"
            "或对 Java 17 节点使用 --legacy。"
        )

    resolved = {}
    skipped = []
    if "neoforge" in loaders:
        version = opts["neo"]
        if version is None:
            version, kind = lookup_neo(mc)
            if version is None:
                skipped.append(f"neoforge（Maven 上没有 {mc} 对应版本）")
            elif kind == "prerelease":
                note(f"NeoForge 只有预发布版本可用，选用 {version}。")
        if version:
            resolved["neoforge"] = {"neo_version": version}

    if "fabric" in loaders:
        fapi = opts["fapi"]
        if fapi is None:
            fapi = lookup_fabric_api(mc)
            if fapi is None:
                skipped.append(f"fabric（Fabric API 没有 +{mc} 版本）")
        loader_version = opts["loader"]
        if loader_version is None:
            loader_version, sources = fabric_loader_version()
            if loader_version and sources:
                note(f"Fabric Loader 沿用仓库现有值 {loader_version}（来自 {', '.join(sources[:3])} 等）。")
        if fapi and loader_version:
            resolved["fabric"] = {
                "fabric_api_version": fapi,
                "loader_version": loader_version,
            }
        elif fapi and not loader_version:
            skipped.append("fabric（仓库没有可沿用的 loader_version，且未给 --loader-version）")

    if "forge" in loaders:
        version = opts["forge"] or opts["loader"]
        if version is None:
            version = lookup_forge(mc)
            if version is None:
                skipped.append(f"forge（Forge Maven 上没有 {mc} 版本）")
        if version:
            resolved["forge"] = {"forge_version": version}

    for item in skipped:
        note(f"跳过 {item}。")
    if not resolved:
        raise UserError("所有请求的 loader 都没有可用版本，未生成任何节点。")

    canvass = [name for name in nodes_before]
    planned = {}
    for loader in loaders:
        if loader not in resolved:
            continue
        node = mc if loader != "fabric" else f"{mc}-fabric"
        template = choose_template(canvass, mc, loader)
        rendered = render_props(template, mc, loader, resolved[loader], java_version)
        planned[node] = {
            "loader": loader,
            "template": template,
            "props_text": rendered,
            "props": parse_props(rendered),
            "legacy": opts["legacy"],
        }
        canvass.append(node)

    # 组装“未来的节点集合”，用于计数与排序。
    final_nodes = [name for name in nodes_before if name not in planned]
    final_nodes += list(planned)
    props_map = dict(props_before)
    for name in nodes_before:
        props_map[name] = dict(props_map.get(name, {}))
        props_map[name]["_legacy"] = name in legacy_set
    for name, info in planned.items():
        props_map[name] = dict(info["props"])
        props_map[name]["_legacy"] = info["legacy"]
    legacy_set_after = set(legacy_set)
    for name, info in planned.items():
        if info["legacy"]:
            legacy_set_after.add(name)
        else:
            legacy_set_after.discard(name)

    # ---- 冲突检查 ---------------------------------------------------------
    conflicts = []
    run_outputs = {}
    for name, info in planned.items():
        path = VERSIONS / name / "gradle.properties"
        if path.is_file():
            current = path.read_text(encoding="utf-8")
            if current != info["props_text"] and not opts["force"]:
                conflicts.append(f"{path.relative_to(ROOT)} 已存在且内容与将要生成的不同")
        kinds = run_kinds(info["template"], info["loader"])
        for kind in kinds:
            out_path, out_text = render_run_config(
                info["template"], info["loader"], name, mc, kind
            )
            run_outputs[out_path] = out_text
            if out_path.is_file() and out_path.read_text(encoding="utf-8") != out_text and not opts["force"]:
                conflicts.append(f"{out_path.relative_to(ROOT)} 已存在且内容与将要生成的不同")

    if conflicts and not opts["force"]:
        print("error: 目标已存在且内容不同；确认要覆盖请加 --force：", file=sys.stderr)
        for item in conflicts:
            print(f"  - {item}", file=sys.stderr)
        sys.exit(1)

    # ---- 在内存中编辑注册/文档 -------------------------------------------
    mutable_paths = [SETTINGS_MAIN, SETTINGS_LEGACY, STONECUTTER, BUILD_ALL, TEST_ALL, README_EN, README_ZH]
    originals = {path: path.read_text(encoding="utf-8") for path in mutable_paths}
    texts = dict(originals)

    planned_names = set(planned)
    # 1) 幂等：先从两个 settings 里删掉同名旧行，再按 pass 插入。
    target_settings = SETTINGS_LEGACY if opts["legacy"] else SETTINGS_MAIN
    for path in (SETTINGS_MAIN, SETTINGS_LEGACY):
        texts[path] = remove_vers_lines(texts[path], planned_names)
    entries_by_file = {}
    for name, info in planned.items():
        node_pass = pass_for_props(info["props"], info["legacy"])
        entries_by_file.setdefault((target_settings, node_pass), []).append(
            (name, info["props"]["minecraft_version"])
        )
    for (path, node_pass), entries in entries_by_file.items():
        texts[path] = insert_vers(texts[path], entries, node_pass, props_map)

    # 2) pass 数组。
    legacy_new = [n for n, i in planned.items() if i["legacy"]]
    a_new = [n for n, i in planned.items() if not i["legacy"] and pass_for_props(i["props"], False) == "A"]
    b_new = [n for n, i in planned.items() if not i["legacy"] and pass_for_props(i["props"], False) == "B"]
    texts[STONECUTTER] = edit_kotlin_list(texts[STONECUTTER], "legacyNodes", legacy_new, props_map)
    texts[STONECUTTER] = edit_kotlin_list(texts[STONECUTTER], "passANodes", a_new, props_map)
    texts[STONECUTTER] = edit_kotlin_list(texts[STONECUTTER], "passBNodes", b_new, props_map)
    for path in (BUILD_ALL, TEST_ALL):
        texts[path] = edit_shell_array(texts[path], "LEGACY_NODES", legacy_new, props_map)
        texts[path] = edit_shell_array(texts[path], "PASS_A_NODES", a_new, props_map)
        texts[path] = edit_shell_array(texts[path], "PASS_B_NODES", b_new, props_map)

    # 3) README 节点表。
    texts[README_EN] = edit_readme_table(texts[README_EN], list(planned), props_map, "en")
    texts[README_ZH] = edit_readme_table(texts[README_ZH], list(planned), props_map, "zh")

    # 4) 计数。
    counts = compute_counts(final_nodes, props_map, legacy_set_after)
    warnings = apply_count_rules(texts, counts)
    for warning in warnings:
        print(f"warning: {warning}", file=sys.stderr)
    problems = verify_counts(texts, counts)
    if problems:
        print("error: 文档计数校验失败，未写入任何文件：", file=sys.stderr)
        for item in problems:
            print(f"  - {item}", file=sys.stderr)
        print("请人工修正上述文件后重试。", file=sys.stderr)
        sys.exit(1)

    # ---- dry-run / 幂等判定 ----------------------------------------------
    text_changes = [p for p in mutable_paths if texts[p] != originals[p]]
    run_changes = [
        p for p, content in run_outputs.items()
        if not p.is_file() or p.read_text(encoding="utf-8") != content
    ]
    node_changes = [
        name for name, info in planned.items()
        if not (VERSIONS / name / "gradle.properties").is_file()
        or (VERSIONS / name / "gradle.properties").read_text(encoding="utf-8") != info["props_text"]
    ]

    already_present = not text_changes and not run_changes and not node_changes
    if already_present:
        for name in planned:
            print(f"{name}: already present, nothing to do")
        return

    print("将执行以下改动：")
    for name, info in planned.items():
        state = "create" if name in node_changes else "already present"
        print(f"  node {name} [{info['loader']}, template {info['template']}] -> {state}")
        print(f"    versions/{name}/gradle.properties")
    if run_changes:
        print("  .run 配置：")
        for path in run_changes:
            print(f"    {path.name}")
    if text_changes:
        print("  注册/文档：")
        for path in text_changes:
            print(f"    {path.relative_to(ROOT)}")
    print(f"  节点计数：total={counts['total']} main={counts['main']} legacy={counts['legacy']} "
          f"PassA={counts['A']} PassB={counts['B']}")

    if opts["dry"]:
        print("\n--dry-run：未写入任何文件。")
        return

    # ---- 落盘 -------------------------------------------------------------
    for name, info in planned.items():
        (VERSIONS / name).mkdir(parents=True, exist_ok=True)
        (VERSIONS / name / "gradle.properties").write_text(info["props_text"], encoding="utf-8")
    for path, content in run_outputs.items():
        if not path.is_file() or path.read_text(encoding="utf-8") != content:
            path.write_text(content, encoding="utf-8")
    for path in text_changes:
        path.write_text(texts[path], encoding="utf-8")

    print("\n完成。建议的 git 命令（脚本不会自动提交）：")
    print("  " + suggested_git_add(
        [VERSIONS / name for name in planned] + list(run_outputs) + text_changes
    ))
    print(f"  git commit -m {shlex.quote('build: add ' + mc + ' version nodes')}")


try:
    main()
except UserError as exc:
    die(str(exc))
PY
