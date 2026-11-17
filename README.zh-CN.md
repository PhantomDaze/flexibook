# FlexiBook

**Minecraft 1.20.1 → 26.3 的自适应排版成书模组，支持 Forge / NeoForge / Fabric。**

使用 DataComponent（1.20.1 走传统 NBT）+ 客户端布局引擎 + `AdaptiveBookScreen`，在保留“书”交互的前提下支持：

- HTML 子集标签（`[h1]` / `[p]` / `[b]` / `[color]` / `[img]` / `[link]` …）
- 打开时按当前语言解析翻译键并重新测量分页
- 内容过长时自动缩小字号，必要时双栏
- 安全链接：仅注册过的 command id，或确认后的 http(s) URL
- 书内搜索高亮
- **内置默认字体** `flexibook:default`（unihex Unifont；省略 `font` 时不落到 `minecraft:default`）
- `AdaptiveBookBuilder` / `FlexiBookAPI` 供其他模组调用
- 数据驱动主题（`assets/<ns>/flexibook/themes/<id>.json` 注册）

| 文档 | 说明 |
|------|------|
| **[docs/API.zh-CN.md](./docs/API.zh-CN.md)** | 其他模组详细调用（字体 §12、数据书 §14）— **中文** |
| [docs/API.md](./docs/API.md) | Full API — **English**（默认） |
| [editor/README.zh-CN.md](./editor/README.zh-CN.md) | 配套实时编辑器（Electron；与游戏同一 unihex ZIP） |
| [docs/EDITOR_PACK_GUIDE.zh-CN.md](./docs/EDITOR_PACK_GUIDE.zh-CN.md) | 编辑器导入导出与游戏内拿书（详细） |
| [docs/README.md](./docs/README.md) | 设计 / 计划 / todo 索引 |
| [README.md](./README.md) | **English** (default) |

## 分支

`unified` 是**唯一的开发分支**：一份共享 `src/` + `versions/` 下的 **13 个版本节点**，由 Stonecutter 预处理。

旧的按 JDK 划分的分支（`main`、`1.20.1`、`java21`、`java25`）仅作为历史快照保留，不要在其上开发。

## 版本节点

所有节点编译同一份共享源码树。`mod_version = 1.0.1`。

| 节点 | Minecraft | 加载器 | JDK toolchain | 构建 |
|------|-----------|--------|---------------|------|
| `1.20.1` | 1.20.1 | Forge 47.4.9 | 17 | legacy（Gradle 8.14.3） |
| `1.20.1-fabric` | 1.20.1 | Fabric 0.19.3 | 17 | legacy（Gradle 8.14.3） |
| `1.21.1` | 1.21.1 | NeoForge 21.1.244 | 21 | main（Gradle 9.6.1） |
| `1.21.1-fabric` | 1.21.1 | Fabric 0.19.3 | 21 | main |
| `1.21.4` | 1.21.4 | NeoForge 21.4.157 | 21 | main |
| `1.21.4-fabric` | 1.21.4 | Fabric 0.19.3 | 21 | main |
| `1.21.11-fabric` | 1.21.11 | Fabric 0.19.3 | 21 | main |
| `26.1.2` | 26.1.2 | NeoForge 26.1.2.93 | 25 | main |
| `26.1.2-fabric` | 26.1.2 | Fabric 0.19.3 | 25 | main |
| `26.2` | 26.2 | NeoForge 26.2.0.41-beta | 25 | main |
| `26.2-fabric` | 26.2 | Fabric 0.19.3 | 25 | main |
| `26.3` | 26.3 | NeoForge 26.3.0.110 | 25 | main |
| `26.3-fabric` | 26.3 | Fabric 0.19.3 | 25 | main |

### Plan B：两套 Gradle 构建

13 个节点被拆到两套 Gradle 构建，因为加载器要求的 Gradle 版本互不兼容：

- **主构建** — Gradle **9.6.1** + ModDevGradle **2.0.143** + Loom **1.17.17**，11 个节点（`1.21.x`、`26.x`）。
- **legacy 构建** — Gradle **8.14.3** + ForgeGradle **6.0.54** + Loom **1.13.6**，2 个节点（`1.20.1`、`1.20.1-fabric`），通过 `legacy-gradle8/gradlew` + `-c settings.legacy.gradle.kts` 驱动。

根因：ForgeGradle 6 的 `MCPRuntime.ExecuteFunction` 在 Gradle 9.6.1 / 8.14.5 上以 `project services has been closed` 失败——只有 8.14.3 还能完成冷的 artifact 生成；反过来 Loom 1.17.17 声明 plugin `api-version` 9.5.0，无法在 Gradle 8 上加载。因此同一份源码树配两个 wrapper、两个 settings 文件。

## 环境要求

- **JDK 21**：所有 `1.20.1` / `1.21.x` 任务的 Gradle daemon。
- **JDK 25**：所有 `26.x` 任务的 Gradle daemon。
- **JDK 17**：`1.20.1` 节点的 toolchain（已配置 `org.gradle.java.installations.auto-download=true`，缺省会自动下载）。
- Gradle wrapper 已入库：`./gradlew` = 9.6.1，`legacy-gradle8/gradlew` = 8.14.3。
- Stonecutter 0.6.2。

### JDK 选择

项目**没有硬编码 `org.gradle.java.home`**。每次调用时 Gradle daemon 的 JVM 由 `JAVA_HOME` 决定，因此要按 pass 选对：

| 任务 | `JAVA_HOME` | 节点 toolchain |
|------|-------------|----------------|
| `1.20.1*`（legacy 构建） | JDK 21 | 17 |
| `1.21.x`（主构建 Pass A） | JDK 21 | 21 |
| `26.x`（主构建 Pass B） | JDK 25 | 25 |

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk   # 或 java-25-openjdk / java-17-openjdk
```

### NeoForm 26.3-1 补丁（仅 26.3 NeoForge 节点）

**症状**：`:26.3:build` 在 `createMinecraftArtifacts` 重编译反编译源码时失败：

```text
error: contents() in <anonymous net.minecraft.core.HolderSet$1> cannot override contents() in net.minecraft.core.HolderSet.Named
  attempting to assign weaker access privileges; was public
...
Node action for recompile failed
```

**原因**：NeoForm 配置 `26.3-1`——26.3 唯一的 NeoForm 配置，所有 NeoForge `26.3.0.x` 版本都依赖它——把 `emptyNamed()` 匿名子类里的 `HolderSet.Named#contents()` 生成为 `protected`，而 javac 在 classpath 上看到的官方 class 文件声明为 `public`。换 NeoForge 版本无效。**这是 NeoForm 上游问题，不是本模组的缺陷。**

**处理**：把缺失的 NeoForm 补丁注入缓存制品：

```bash
bash scripts/patch-neoform-263.sh          # 打补丁；先把 zip 备份为 neoform-26.3-1.zip.orig
bash scripts/patch-neoform-263.sh --check  # 只检查，不写任何文件
```

脚本是幂等的（按补丁字节内容判断），只在首次修改前备份（已存在的 `.orig` 不会被覆盖），并用 `unzip -t` 校验归档。可用 `GRADLE_USER_HOME` / `NEOFORM_ZIP` 覆盖定位。

**全新机器**上还没有这个 zip：先跑一次构建（会下载 NeoForm，然后在上面同样的位置失败），再执行本脚本，然后重新构建。`scripts/build-all.sh` 与 `scripts/test-all.sh` 会在 Pass B 之前运行 `--check`，未打补丁时停止并给出上述命令；它们**不会**自行修改你的 Gradle 缓存。（`26.3-fabric` 不使用 NeoForm，但 Pass B 同时包含两个 26.3 节点，所以按整趟检查。）

**影响面**：只影响在全新 Gradle 缓存上从源码构建 26.3 NeoForge 节点的人。已发布的 jar 只构建一次，不受影响；其他节点使用不同的 NeoForm 配置，也不受影响。

**何时移除**：NeoForm 发布修复版配置（如 `26.3-2`）或 NeoForge 为 26.3 更换 baseline 后，在干净缓存上不跑本脚本也能 `:26.3:build` 成功时，即可删除 `scripts/patch-neoform-263.sh` 及本节。

## 构建

```bash
# 主构建单节点（1.21.x 用 JDK21，26.x 用 JDK25）
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :1.21.4:build --console=plain
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew :26.1.2:build --console=plain

# 主构建整趟（build 已包含 test）
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew buildPassA --console=plain
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew buildPassB --console=plain

# legacy 1.20.1 节点：Gradle 8.14.3 wrapper + legacy settings 文件
JAVA_HOME=/usr/lib/jvm/java-21-openjdk legacy-gradle8/gradlew \
    -c settings.legacy.gradle.kts \
    -Pstonecutter.active=1.20.1-fabric \
    :1.20.1:build --console=plain --stacktrace

# 全量（11 节点，三趟 JVM）；可覆盖 JDK21=... / JDK25=...
bash scripts/build-all.sh
```

`./gradlew chiseledBuild` 仍然存在，但它会在**同一个** daemon 里访问所有节点；需要 26.x 与 1.21.x 用不同 JDK 时，请用 pass 任务或 `scripts/build-all.sh`。

无需 `Set active`：`build.gradle` 会按需把共享 `src/` 预处理到 `versions/<node>/build/chiseledSrc`。

### 产物

```text
versions/<node>/build/libs/flexibook-1.0.1+<mc>.jar          # 可分发
versions/<node>/build/libs/flexibook-1.0.1+<mc>-sources.jar  # 源码
```

各节点加载器元数据正确（Forge 为 `META-INF/mods.toml`，NeoForge 为 `META-INF/neoforge.mods.toml`，Fabric 为 `fabric.mod.json`）。

## 客户端 smoke

smoke 会启动真实客户端：启用 smoke 资源包 → 创建创造世界 → 打开一本书并保持 N 秒。每次运行以 `[FlexiBook AutoSmoke] PASS` 或 `[FlexiBook AutoSmoke] FAIL: …` 结束；即使游戏进程以 0 退出，Gradle 任务也会因 FAIL 标记而失败。

```bash
# 全部 11 节点（Pass L → A → B，每趟使用各自的 JAVA_HOME）
./gradlew smokeAllClients

# 单趟
./gradlew smokeAllClientsPassL   # 1.20.1 Forge/Fabric，legacy-gradle8，JDK21
./gradlew smokeAllClientsPassA   # 1.21.x 五节点，JDK21
./gradlew smokeAllClientsPassB   # 26.x 六节点，JDK25

# 兼容别名（旧 IDE 配置仍可用）
./gradlew autoSmokeClients        # 原 26.1.2/26.2 四节点
./gradlew autoSmokeAllClients     # 1.21.x 五节点
./gradlew smokeTestAllClients     # 1.20.1 两节点
```

默认读 10 秒、超时 300 秒，可覆盖；旧属性名 `flexibook.smokeTest.*` 仍然接受：

```bash
./gradlew smokeAllClientsPassA \
    -Pflexibook.autoSmoke.readSeconds=10 \
    -Pflexibook.autoSmoke.timeoutSeconds=300
```

### 夹具（`dev/smoke`）

```text
dev/smoke/
├── book.txt                 # 被测书
└── resourcepacks/
    └── note_pack/           # 这里的任意文件夹或 .zip 会复制到 run/resourcepacks/
```

- `book.txt`：**第一个非空、非 `#` 行**生效。`demo` 表示内置 `ExampleBooks.demoGuide()`；其它值必须是已注册的 id，例如 `note:note11`。
- `dev/smoke/resourcepacks/` 下的每个文件夹/`.zip` 都会被复制到节点的 `run/resourcepacks/`，并由 `AutoSmokeClient` 强制启用。
- `syncSmokeResourcePacks` / `syncSmokeBookId` 负责生成每节点副本（`versions/<node>/run/`）；所有 `runClient` / `runClientAutoSmoke` 都依赖它们。

### `run/options.txt` 与无障碍引导

每次客户端启动前，`prepareSmokeOptions` 会确保节点的 `run/options.txt` 含 `onboardAccessibility:false`。

原因：1.20.2+ 的干净 `run/` 首次启动会先显示无障碍引导屏而不是 `TitleScreen`，smoke 在 `WAIT_TITLE` 等标题屏会一直等下去。实测 1.21.4：加该行后 29 秒 PASS，不加则超时。

## 本地测试

GitHub 自动 CI 已**停用**：push / PR 不再启动任何 workflow。日常测试在本地跑：

```bash
bash scripts/test-all.sh            # 11 节点单元测试 + JUnit 汇总
bash scripts/test-all.sh --smoke    # 先跑单元测试，再跑全部 11 节点客户端 smoke
bash scripts/test-all.sh --smoke-only
```

`test-all.sh` 等价于原 CI 做的事：

- 按三趟 JVM 对每个节点跑 `:node:test`——legacy 1.20.1（Gradle 8.14.3，JDK21）、Pass A 1.21.x（Gradle 9.6.1，JDK21）、Pass B 26.x（Gradle 9.6.1，JDK25）。
- 解析 `versions/*/build/test-results/test/*.xml`，逐节点打印 `tests / failures / errors / skipped` 并给出总计；有失败、错误或节点缺失则返回非零。
- `--smoke` 需要显示环境：桌面会话（已设 `DISPLAY`）或已安装 `xvfb-run`（脚本会自动改用 `xvfb-run -a`）。不会静默跳过。
- JDK 可用 `JDK21=... JDK25=...` 覆盖。

## GitHub workflow（仅手动）

两个 workflow 保留用于偶尔在服务端跑一次，**只能**通过 `workflow_dispatch` 启动（Actions → *Run workflow*）：

| 工作流 | 触发 | 内容 |
|--------|------|------|
| [`.github/workflows/build.yml`](./.github/workflows/build.yml) | 手动 | `build-main`（JDK21+25，Gradle 9.6.1，9 节点）与 `build-legacy`（JDK21 daemon + JDK17 toolchain，Gradle 8.14.3，2 节点）；上传 11 个 jar |
| [`.github/workflows/client-smoke.yml`](./.github/workflows/client-smoke.yml) | 手动（参数 `passes`、`read_seconds`） | 通过 `xvfb-run` + Mesa llvmpipe 跑 Pass A（1.21.x），可选 Pass B（26.x） |

备注：

- ForgeGradle 需要 `piston-meta.mojang.com`；NeoForge MDG/NFRT 每次 `createMinecraftArtifacts` 都会下载 `version_manifest_v2.json`。请保证有网（本地跑 `test-all.sh` 同理）。
- 本地离线复跑需暖缓存并刷新 ForgeGradle 的 1 小时 etag TTL：
  `find ~/.gradle/caches/forge_gradle -type f -exec touch {} +`
- MDG 的 manifest 下载没有离线开关；断网时只能复用已生成的 artifacts。

## smoke 状态与已知限制

- **13 个节点全部通过本机客户端 smoke**（Pass L、Pass A、Pass B）。
- 此前的 1.20.1 Forge `WAIT_TITLE` 超时根因是 Forge 的 mod 加载警告：缺少 `pack.mcmeta` 时 Forge 会显示 `LoadingErrorScreen`（“failed to load a valid ResourcePackInfo”）而不是 `TitleScreen`。legacy 构建现在为 1.20.1 节点生成 `pack.mcmeta`；`AutoSmokeClient` 也会打印实际的 `screen`/`overlay` 类名（超时时还会反射打印 screen 字段）便于后续排查。

## 游戏内

创造栏 **FlexiBook** 分类：

- 空白 `flexi_book`
- **FlexiBook Field Guide** 示例书（中英 `lang` 齐全，展示自适应、i18n、富文本、图片、链接、搜索）

右键打开；左右方向键或按钮翻页；底部可搜索。

资源包数据书（启用资源包后 **F3+T**）：

```text
/flexibook list
/flexibook give <namespace:bookId>
# 例：/flexibook give note:note11
```

需要权限等级 ≥ 2。创造栏只有空白书 + 内置 demo；自定义包用命令发放。

## 其他模组接入

完整说明（依赖、Builder 全表、标签属性、链接安全、组件读写、i18n、主题 JSON、FAQ）见 **[docs/API.zh-CN.md](./docs/API.zh-CN.md)**（英文默认版：[docs/API.md](./docs/API.md)）。

```java
ItemStack book = FlexiBookAPI.builder("my_guide")
    .titleKey("mymod.book.title")
    // .defaultFont(...) 可省略 → 运行时 resolvedFont() = flexibook:default
    .h1("mymod.book.ch1")
    .p("mymod.book.p1")
    .font("mymod.book.quote", ResourceLocation.withDefaultNamespace("uniform")) // 单段显式覆盖
    .bullet("mymod.book.b1")
    .image(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/icon.png"), 48, 48)
    .link("mymod.book.click", FlexiBookAPI.commandAction("mymod:open_map"))
    .divider()
    .fromMarkup("[p]mymod.book.extra[/p]") // 仅当 elements 为空时整本存 raw
    .buildItem();

// 安全动作（不要注册任意 shell）
FlexiBookAPI.registerCommandAction("mymod:open_map", ctx -> ctx.message("mymod.map.opened"));
```

内容也可以只存 raw 标签字符串（不写 `font` 同样走内置默认）：

```java
AdaptiveBookContent content = AdaptiveBookContent.ofMarkup(
    new TranslatableText("mymod.book.title"),
    """
    [h1]mymod.book.ch1[/h1]
    [p]mymod.book.p1[/p]
    [bullet]mymod.book.b1[/bullet]
    """
);
// content.resolvedFont() → flexibook:default
ItemStack stack = FlexiBookAPI.createBook(content);
```

DataComponent id：`flexibook:adaptive_book_content`。

主题可通过资源包放置 JSON 文件注册（`assets/<namespace>/flexibook/themes/<path>.json`），id 即 `namespace:path`。  
字体优先级：`span/heading font` → 书级 `font` → **`flexibook:default`**（详见 [docs/API.zh-CN.md](./docs/API.zh-CN.md) §12）。

### 六段资源包布局

```
assets/<ns>/
  lang/*.json
  font/*
  textures/gui/book.png
  textures/item/flexi_book.png   # 可选；写在 assets/flexibook/... 可覆盖物品图标
  flexibook/
    books/<id>.json              # 索引：content + theme + 可选 font
    contents/<id>.json           # 正文
    themes/<id>.json
```

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"));
```

## 标签一览

| 标签 | 说明 |
|------|------|
| `[h1]` `[h2]` | 标题 |
| `[p]` | 段落（可嵌套行内样式） |
| `[b]` `[i]` `[u]` | 粗体 / 斜体 / 下划线 |
| `[color=#RRGGBB]` | 颜色 |
| `[font font="ns:id"]` / `[font=ns:id]` | 行内字体（覆盖书级 `font`；书级省略则为 `flexibook:default`） |
| `[br]` | 换行 |
| `[divider]` | 分隔线 |
| `[img src="..." width="48" height="48" /]` | 图片（资源路径） |
| `[link cmd="modid:action"]` / `[link url="https://..."]` | 安全链接 |
| `[bullet]` | 列表项 |
| `[div class="..."]` | 容器 |

转义：`\[` `\]`。

## v1 范围与非目标

**已做**：DataComponent（1.20.1 为传统 NBT）、双形态存储（elements 或 rawMarkup）、TagParser、自适应 layout + 缓存、单页 Screen、Builder、示例书双语、搜索、可注册数据驱动主题（默认 + contain 示例 + JSON 重载）、内置书字体 `flexibook:default`（unihex，与编辑器同一 ZIP）、独立编辑器 Unihex 预览、资源包导出/导入、`/flexibook give|list`、多版本构建（11 节点）、客户端 smoke harness。

**未做 / 简化**：

- 讲台完整兼容（仅 `FlexiBookAPI.LecternCompat` 桩）
- 不劫持原版 `written_book`
- 书内编辑、表格、自动目录、翻书 3D 动画

## 许可证

[GNU GPL v3](./LICENSE) © PhantomDaze
