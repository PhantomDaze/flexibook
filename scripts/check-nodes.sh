#!/usr/bin/env bash
# 节点集合 / 文档计数漂移检测（纯只读）。
#
# 节点注册点分散在多处，漏改任何一处都是静默故障。本脚本解析并交叉比对：
#   1. settings.gradle.kts / settings.legacy.gradle.kts 的 vers("id","mc")
#   2. stonecutter.gradle.kts 的 legacyNodes / passANodes / passBNodes
#   3. scripts/build-all.sh 与 scripts/test-all.sh 的 LEGACY/PASS_A/PASS_B_NODES
#   4. versions/*/ 实际目录
#   5. .run/*.run.xml（每节点的 IDE 配置、必需任务、legacy externalProjectPath）
#   6. README / settings / stonecutter / 脚本头 / workflows 里的节点与用例计数
#
# 只读：本脚本不写入、不修改任何文件或 Gradle 缓存。
#
# 用法：
#   bash scripts/check-nodes.sh            # 完整报告
#   bash scripts/check-nodes.sh --quiet    # 只输出问题
#   bash scripts/check-nodes.sh --help
#
# 退出码：
#   0  一致
#   1  发现漂移
#   2  解析失败 / 环境错误 / 参数错误
set -euo pipefail

cd "$(dirname "$0")/.."

QUIET=0
for arg in "$@"; do
    case "$arg" in
        --quiet|-q) QUIET=1 ;;
        -h|--help)
            sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "error: unknown argument: $arg (try --help)" >&2
            exit 2
            ;;
    esac
done

if ! command -v python3 >/dev/null 2>&1; then
    echo "error: python3 is required" >&2
    exit 2
fi

python3 - "$QUIET" <<'PYEOF'
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

QUIET = sys.argv[1] == '1'


def fail(message):
    """解析/环境失败：退出码 2，区别于“发现漂移”的 1。"""
    print(f'error: {message}', file=sys.stderr)
    raise SystemExit(2)


def read(path):
    try:
        with open(path, encoding='utf-8') as fh:
            return fh.read()
    except OSError as exc:
        fail(f'cannot read {path}: {exc}')


def parse_settings(path):
    text = read(path)
    pairs = re.findall(r'vers\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', text)
    if not pairs:
        fail(f'no vers("id","mc") entries in {path}')
    return [node for node, _mc in pairs], {node: mc for node, mc in pairs}


def parse_kotlin_list(text, name, path):
    match = re.search(rf'val\s+{name}\s*=\s*listOf\((.*?)\)', text, re.S)
    if not match:
        fail(f'{path}: cannot find val {name} = listOf(...)')
    values = re.findall(r'"([^"]+)"', match.group(1))
    if not values:
        fail(f'{path}: {name} is empty')
    return values


def parse_bash_array(text, name, path):
    match = re.search(rf'{name}=\((.*?)\)', text, re.S)
    if not match:
        fail(f'{path}: cannot find {name}=(...)')
    values = re.findall(r'([0-9][0-9A-Za-z._-]*)', match.group(1))
    if not values:
        fail(f'{path}: {name} is empty')
    return values


def parse_properties(path):
    props = {}
    for line in read(path).splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        props[key.strip()] = value.strip()
    return props


def main():
    problems = []  # (category, message)
    checks = 0

    def problem(category, message):
        problems.append((category, message))

    # ---------------------------------------------------------------- 节点集合
    main_nodes, main_mc = parse_settings('settings.gradle.kts')
    legacy_nodes, legacy_mc = parse_settings('settings.legacy.gradle.kts')

    stone = read('stonecutter.gradle.kts')
    stone_legacy = parse_kotlin_list(stone, 'legacyNodes', 'stonecutter.gradle.kts')
    stone_pa = parse_kotlin_list(stone, 'passANodes', 'stonecutter.gradle.kts')
    stone_pb = parse_kotlin_list(stone, 'passBNodes', 'stonecutter.gradle.kts')

    # 兼容别名 autoSmokeClients 的节点列表（用于 README 里的“原四节点”描述）。
    alias_nodes = []
    alias_match = re.search(r'"autoSmokeClients".*?nodes\s*=\s*listOf\((.*?)\)', stone, re.S)
    if alias_match:
        alias_nodes = re.findall(r'"([^"]+)"', alias_match.group(1))

    build_all = read('scripts/build-all.sh')
    ba_legacy = parse_bash_array(build_all, 'LEGACY_NODES', 'scripts/build-all.sh')
    ba_pa = parse_bash_array(build_all, 'PASS_A_NODES', 'scripts/build-all.sh')
    ba_pb = parse_bash_array(build_all, 'PASS_B_NODES', 'scripts/build-all.sh')

    test_all = read('scripts/test-all.sh')
    ta_legacy = parse_bash_array(test_all, 'LEGACY_NODES', 'scripts/test-all.sh')
    ta_pa = parse_bash_array(test_all, 'PASS_A_NODES', 'scripts/test-all.sh')
    ta_pb = parse_bash_array(test_all, 'PASS_B_NODES', 'scripts/test-all.sh')

    dirs = sorted(
        os.path.basename(p.rstrip('/'))
        for p in glob.glob('versions/*/')
        if os.path.isdir(p)
    )
    if not dirs:
        fail('no directories under versions/')

    # 注册集合 = 主 settings ∪ legacy settings。
    registered = list(dict.fromkeys(main_nodes + legacy_nodes))
    registered_set = set(registered)
    dirs_set = set(dirs)

    for node in sorted(registered_set - dirs_set):
        problem('registered-without-dir',
                f'{node}: registered in settings*.gradle.kts but versions/{node}/ is missing')
    for node in sorted(dirs_set - registered_set):
        problem('dir-without-registration',
                f'{node}: versions/{node}/ exists but is not registered in settings*.gradle.kts')
    for node in sorted(set(main_nodes) & set(legacy_nodes)):
        problem('double-registration',
                f'{node}: registered in both settings.gradle.kts and settings.legacy.gradle.kts')

    def check_unique(values, source):
        seen = Counter(values)
        for node, count in sorted(seen.items()):
            if count > 1:
                problem('duplicate-in-array', f'{source}: {node} appears {count} times')

    for values, source in [
        (stone_legacy, 'stonecutter.gradle.kts legacyNodes'),
        (stone_pa, 'stonecutter.gradle.kts passANodes'),
        (stone_pb, 'stonecutter.gradle.kts passBNodes'),
        (ba_legacy, 'scripts/build-all.sh LEGACY_NODES'),
        (ba_pa, 'scripts/build-all.sh PASS_A_NODES'),
        (ba_pb, 'scripts/build-all.sh PASS_B_NODES'),
        (ta_legacy, 'scripts/test-all.sh LEGACY_NODES'),
        (ta_pa, 'scripts/test-all.sh PASS_A_NODES'),
        (ta_pb, 'scripts/test-all.sh PASS_B_NODES'),
    ]:
        check_unique(values, source)
        for node in sorted(set(values) - dirs_set):
            problem('unknown-node-in-array', f'{source}: {node} has no versions/{node}/ directory')

    # 每个节点应有的 pass（legacy 优先；主构建按 gradle.properties 的 java_version）。
    props = {}
    node_loader = {}
    node_java = {}
    for node in dirs:
        path = f'versions/{node}/gradle.properties'
        if not os.path.isfile(path):
            problem('missing-properties', f'{node}: {path} is missing')
            continue
        p = parse_properties(path)
        props[node] = p
        node_loader[node] = p.get('mod_loader')
        node_java[node] = p.get('java_version')

    legacy_set = set(legacy_nodes)
    main_set = set(main_nodes)
    pass_a_set = {n for n in main_set if node_java.get(n) == '21'}
    pass_b_set = {n for n in main_set if node_java.get(n) == '25'}
    unknown_java = {n for n in main_set if node_java.get(n) not in {'21', '25'}}
    for node in sorted(unknown_java):
        problem('unknown-java-version',
                f'{node}: java_version={node_java.get(node)!r} (expected 21 or 25)')

    def expected_pass(node):
        if node in legacy_set:
            return 'legacy'
        if node in pass_a_set:
            return 'passA'
        if node in pass_b_set:
            return 'passB'
        return None

    # 每处数组都必须包含期望节点，且不能放错 pass。
    array_specs = [
        ('stonecutter.gradle.kts', 'legacyNodes', stone_legacy),
        ('stonecutter.gradle.kts', 'passANodes', stone_pa),
        ('stonecutter.gradle.kts', 'passBNodes', stone_pb),
        ('scripts/build-all.sh', 'LEGACY_NODES', ba_legacy),
        ('scripts/build-all.sh', 'PASS_A_NODES', ba_pa),
        ('scripts/build-all.sh', 'PASS_B_NODES', ba_pb),
        ('scripts/test-all.sh', 'LEGACY_NODES', ta_legacy),
        ('scripts/test-all.sh', 'PASS_A_NODES', ta_pa),
        ('scripts/test-all.sh', 'PASS_B_NODES', ta_pb),
    ]
    want_array_name = {
        'legacy': {'legacyNodes', 'LEGACY_NODES'},
        'passA': {'passANodes', 'PASS_A_NODES'},
        'passB': {'passBNodes', 'PASS_B_NODES'},
    }
    for node in dirs:
        want = expected_pass(node)
        if want is None:
            continue
        # 只要求节点出现在“它所属 pass”的数组里；出现在别的 pass 由下一段单独报告。
        for path, name, values in array_specs:
            if name in want_array_name[want] and node not in values:
                problem('missing-from-array',
                        f'{node}: missing from {path} {name} (expected {want})')
        all_pass_arrays = {
            'legacy': stone_legacy + ba_legacy + ta_legacy,
            'passA': stone_pa + ba_pa + ta_pa,
            'passB': stone_pb + ba_pb + ta_pb,
        }
        for other, values in all_pass_arrays.items():
            if node in values and other != want:
                problem('wrong-pass-array',
                        f'{node}: found in {other} arrays but belongs to {want}')

    # ------------------------------------------------------------- .run 配置
    action_map = {
        'runClient': 'client',
        'runServer': 'server',
        'runData': 'data',
        'runClientData': 'data',
        'runServerData': 'data',
        'runClientAutoSmoke': 'clientAutoSmoke',
    }
    run_nodes = {}
    run_files = sorted(glob.glob('.run/*.run.xml'))
    for path in run_files:
        text = read(path)
        ext_match = re.search(r'externalProjectPath"\s+value="([^"]*)"', text)
        ext = ext_match.group(1) if ext_match else ''
        for task in re.findall(r'<option\s+value=":([^"]+)"', text):
            parts = task.split(':')
            if len(parts) < 2:
                continue
            node, action = parts[0], action_map.get(parts[1])
            if action is None:
                continue
            info = run_nodes.setdefault(node, {'actions': set(), 'files': {}, 'ext': set()})
            info['actions'].add(action)
            info['files'].setdefault(action, os.path.basename(path))
            info['ext'].add(ext)

    for node in sorted(set(run_nodes) - dirs_set):
        problem('run-config-unknown-node',
                f'{node}: .run config references a node with no versions/{node}/ directory')

    for node in dirs:
        loader = node_loader.get(node)
        legacy = node in legacy_set
        if legacy:
            required = {'client', 'server'}
            if loader == 'forge':
                required.add('data')
        elif loader == 'neoforge':
            required = {'client', 'server', 'data', 'clientAutoSmoke'}
        elif loader == 'fabric':
            required = {'client', 'server', 'clientAutoSmoke'}
        else:
            continue
        info = run_nodes.get(node, {'actions': set(), 'files': {}, 'ext': set()})
        for action in sorted(required - info['actions']):
            problem('missing-run-config',
                    f'{node}: missing {action} (.run/FlexiBook ... {action}.run.xml)')
        if legacy:
            bad = sorted(ext for ext in info['ext'] if not ext.endswith('legacy-gradle8'))
            if bad:
                problem('legacy-run-path',
                        f'{node}: .run externalProjectPath must point to legacy-gradle8, got {bad!r}')
        else:
            bad = sorted(ext for ext in info['ext'] if 'legacy-gradle8' in ext)
            if bad:
                problem('run-path',
                        f'{node}: main-build .run config points into legacy-gradle8')

    # --------------------------------------------------------------- 文档计数
    # 每节点用例数：优先读取已有的 JUnit XML；没有构建结果时回退到 61。
    node_tests = {}
    for path in glob.glob('versions/*/build/test-results/test/*.xml'):
        node = path.split(os.sep)[1] if os.sep in path else path.split('/')[1]
        try:
            root = ET.parse(path).getroot()
            node_tests[node] = node_tests.get(node, 0) + int(root.get('tests', 0) or 0)
        except (ET.ParseError, OSError):
            continue
    if node_tests:
        common = Counter(node_tests.values()).most_common(1)[0][0]
        if len(set(node_tests.values())) > 1:
            problem('test-count-inconsistent',
                    f'per-node test counts differ: {dict(sorted(node_tests.items()))}; using {common}')
        per_node_tests = common
        total_tests = sum(node_tests.get(n, per_node_tests) for n in dirs)
    else:
        per_node_tests = 61
        total_tests = per_node_tests * len(dirs)

    def tests_for(nodes):
        return sum(node_tests.get(n, per_node_tests) for n in nodes)

    all_set = registered_set | dirs_set
    node_expect = {
        'total': len(all_set),
        'main': len(main_set),
        'legacy': len(legacy_set),
        'passA': len(pass_a_set),
        'passB': len(pass_b_set),
        'alias': len(alias_nodes),
    }
    test_expect = {
        'total': total_tests,
        'main': tests_for(main_set),
        'legacy': tests_for(legacy_set),
        'passA': tests_for(pass_a_set),
        'passB': tests_for(pass_b_set),
        'perNode': per_node_tests,
    }
    label_text = {
        'total': 'total',
        'main': 'main build',
        'legacy': 'legacy build',
        'passA': 'Pass A',
        'passB': 'Pass B',
        'alias': 'autoSmokeClients alias',
    }

    # 关键词 → 上下文。weak=True 的关键词（如裸 all）在文件默认上下文可用时让位。
    keywords = [
        # buildPassA/B 属于主构建整体，必须排在 pass a/b 之前（用 (?<!build) 防止误配）。
        (r'(?i)buildpass[ab]', 'main', True),
        (r'(?i)(?<!build)pass\s*a\b|\b1\.21\.x\b', 'passA', True),
        (r'(?i)(?<!build)pass\s*b\b|\b26\.x\b', 'passB', True),
        (r'(?i)(?<!build)pass\s*l\b', 'legacy', True),
        (r'(?i)build-main|main\s+build|main\s+settings|主构建', 'main', True),
        (r'(?i)build-legacy|legacy\s+build|legacy\s+settings|legacy\s*构建|legacy-gradle8', 'legacy', True),
        (r'(?i)\blegacy\b', 'legacy', True),
        (r'(?i)\btest-all\b|unit tests on all|smokeallclients|split across', 'total', True),
        (r'全部|全量|合计|总计|节点单元测试|多版本构建', 'total', True),
        (r'(?i)multi-version build', 'total', True),
        (r'(?i)\beverything\b', 'total', True),
        (r'(?i)\ball\b', 'total', False),
        (r'(?i)java\s*21', 'passA', True),
        (r'(?i)java\s*25', 'passB', True),
        (r'(?i)autoSmokeAllClients', 'passA', True),
        (r'(?i)smokeTestAllClients', 'legacy', True),
        (r'(?i)autoSmokeClients|原\s*26\.1\.2/26\.2', 'alias', True),
    ]
    keyword_compiled = [(re.compile(rx), ctx, strong) for rx, ctx, strong in keywords]

    def classify(window, start, end, offset, default):
        """选择离计数最近的上下文关键词；没有关键词时用文件默认上下文。"""
        found = []
        for rx, ctx, strong in keyword_compiled:
            for m in rx.finditer(window):
                found.append((m.start(), m.end(), ctx, strong))
        before = [k for k in found if k[1] <= start]
        if before:
            _, _, ctx, strong = max(before, key=lambda k: k[1])
            if not strong and default is not None:
                return default
            return ctx
        after = [k for k in found if k[1] > end and k[0] >= start]
        if after:
            _, _, ctx, strong = min(after, key=lambda k: k[0])
            if not strong and default is not None:
                return default
            return ctx
        return default

    # (?<!Java\s+) 之类变长后顾在 Python re 中不支持，这里只排除常见的
    # “Java 17/21/25 nodes”写法（固定一个空格），避免把工具链版本当节点数。
    node_re = re.compile(r'(?<![\d.\-])(?<!Java )(\d+)\s*(?:个(?:版本)?节点|节点|nodes?\b)')
    cn_node_re = re.compile(r'(?<![\d.\-])([一二三四五六七八九十两]+)\s*(?:个)?节点')
    test_re = re.compile(r'(?<![\d.\-])(\d+)\s+tests?\b')
    jar_re = re.compile(r'(?<![\d.\-])(\d+)\s*(?:jars?\b|个\s*jar\b)')

    cn_digits = {'一': 1, '二': 2, '两': 2, '三': 3, '四': 4, '五': 5,
                 '六': 6, '七': 7, '八': 8, '九': 9, '十': 10}

    def cn_to_int(text):
        if text == '十':
            return 10
        if '十' in text:
            tens, ones = text.split('十', 1)
            return (cn_digits.get(tens, 1) if tens else 1) * 10 + (cn_digits.get(ones, 0) if ones else 0)
        return cn_digits[text]

    doc_files = [
        ('README.md', 'total'),
        ('README.zh-CN.md', 'total'),
        ('dev/smoke/README.md', 'total'),
        ('settings.gradle.kts', 'main'),
        ('settings.legacy.gradle.kts', 'legacy'),
        ('stonecutter.gradle.kts', None),
        ('build.gradle', 'main'),
        ('scripts/build-all.sh', None),
        ('scripts/test-all.sh', None),
    ]
    doc_files += [(p, 'main') for p in sorted(glob.glob('.github/workflows/*.yml'))]

    doc_checks = 0
    for path, default in doc_files:
        if not os.path.isfile(path):
            fail(f'cannot read {path}: file is missing')
        lines = read(path).splitlines()
        # 只对 Kotlin 配置看上一行：它们会把一句话折行（如 “main settings (...,
        # 13 nodes)”），且不会出现 README 那种跨句关键词串味。
        use_prev = path in {'settings.gradle.kts', 'stonecutter.gradle.kts'}
        for lineno, line in enumerate(lines, 1):
            prev = lines[lineno - 2] if (use_prev and lineno >= 2) else ''
            offset = len(prev) + 1
            window = prev + ' ' + line

            for match in node_re.finditer(line):
                value = int(match.group(1))
                ctx = classify(window, offset + match.start(), offset + match.end(), offset, default)
                if ctx is None:
                    continue
                doc_checks += 1
                want = node_expect[ctx]
                if value != want:
                    problem('stale-doc-count',
                            f'{path}:{lineno}: says {value} node(s) ({label_text[ctx]}); expected {want}')

            for match in cn_node_re.finditer(line):
                value = cn_to_int(match.group(1))
                ctx = classify(window, offset + match.start(), offset + match.end(), offset, default)
                if ctx is None:
                    continue
                doc_checks += 1
                want = node_expect[ctx]
                if value != want:
                    problem('stale-doc-count',
                            f'{path}:{lineno}: says {value} node(s) ({label_text[ctx]}); expected {want}')

            for match in test_re.finditer(line):
                value = int(match.group(1))
                ctx = classify(window, offset + match.start(), offset + match.end(), offset, default)
                if ctx is None:
                    continue
                doc_checks += 1
                want = test_expect[ctx]
                if value != want:
                    problem('stale-test-count',
                            f'{path}:{lineno}: says {value} tests ({label_text[ctx]}); expected {want}')

            for match in jar_re.finditer(line):
                value = int(match.group(1))
                doc_checks += 1
                if value != node_expect['total']:
                    problem('stale-jar-count',
                            f'{path}:{lineno}: says {value} jars; expected {node_expect["total"]}')

    # .run 配置名里的 “all clients (N)” 也必须等于节点总数。
    for path in run_files:
        name_match = re.search(r'<configuration[^>]*\bname="([^"]*)"', read(path))
        if not name_match:
            continue
        for match in re.finditer(r'all clients \((\d+)\)', name_match.group(1)):
            doc_checks += 1
            value = int(match.group(1))
            if value != node_expect['total']:
                problem('stale-doc-count',
                        f'{os.path.basename(path)}: config name says {value} nodes; expected {node_expect["total"]}')

    # ------------------------------------------------------------------ 输出
    if not QUIET:
        print('Node-set drift check')
        print('====================')
        print('sources:')
        print(f'  settings.gradle.kts          {len(main_nodes)} nodes')
        print(f'  settings.legacy.gradle.kts    {len(legacy_nodes)} nodes')
        print(f'  versions/* (directories)      {len(dirs)} nodes')
        print(f'  stonecutter.gradle.kts        L={len(stone_legacy)} A={len(stone_pa)} B={len(stone_pb)}')
        print(f'  scripts/build-all.sh          L={len(ba_legacy)} A={len(ba_pa)} B={len(ba_pb)}')
        print(f'  scripts/test-all.sh           L={len(ta_legacy)} A={len(ta_pa)} B={len(ta_pb)}')
        print(f'  .run/*.run.xml                {len(run_files)} files, {len(run_nodes)} nodes referenced')
        print(f'  documented counts checked     {doc_checks}')
        print(f'  per-node tests                {per_node_tests} (total {total_tests})')
        print('')

    if problems:
        print(f'problems ({len(problems)}):')
        for category, message in problems:
            print(f'  [{category}] {message}')
        if not QUIET:
            print('')
            print('result: DRIFT DETECTED')
        raise SystemExit(1)

    if not QUIET:
        print('result: OK — node sets and documented counts agree')
    raise SystemExit(0)


try:
    main()
except SystemExit:
    raise
except Exception as exc:  # 解析器自身异常也按“解析失败”处理
    print(f'error: internal parse failure: {exc}', file=sys.stderr)
    raise SystemExit(2)
PYEOF
