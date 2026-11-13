# FlexiBook

**Minecraft 1.20.1 → 26.2 的自适应排版成书模组，支持 Forge / NeoForge / Fabric。**

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

`unified` 是**唯一的开发分支**：一份共享 `src/` + `versions/` 下的 **11 个版本节点**，由 Stonecutter 预处理。

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

### Plan B：两套 Gradle 构建

11 个节点被拆到两套 Gradle 构建，因为加载器要求的 Gradle 版本互不兼容：

- **主构建** — Gradle **9.6.1** + ModDevGradle **2.0.143** + Loom **1.17.17**，9 个节点（`1.21.x`、`26.x`）。
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
./gradlew smokeAllClientsPassB   # 26.x 四节点，JDK25

# 兼容别名（旧 IDE 配置仍可用）
./gradlew autoSmokeClients        # 26.x 四节点
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

## CI

| 工作流 | 阻塞 | 内容 |
|--------|------|------|
| [`.github/workflows/build.yml`](./.github/workflows/build.yml) | **是**（push / PR） | `build-main`（JDK21+25，Gradle 9.6.1，9 节点）与 `build-legacy`（JDK21 daemon + JDK17 toolchain，Gradle 8.14.3，2 节点）；上传 11 个 jar |
| [`.github/workflows/client-smoke.yml`](./.github/workflows/client-smoke.yml) | 否（`continue-on-error: true`） | 通过 `xvfb-run` + Mesa llvmpipe 跑 Pass A（1.21.x）；手动触发时可加 Pass B |

**客户端 smoke 工作流尚未在 GitHub Actions 上验证。** 托管 runner 无 GPU，Minecraft 走软件 OpenGL 很可能超时或失败。该 workflow 带 `continue-on-error: true`，并提供注释掉的 `runs-on: [self-hosted, gpu]` 示例。Pass L 被有意排除（legacy 构建需要额外的 ForgeGradle 网络/缓存，耗时也更长）。

CI 备注：

- ForgeGradle 需要 `piston-meta.mojang.com`；NeoForge MDG/NFRT 每次 `createMinecraftArtifacts` 都会下载 `version_manifest_v2.json`。托管 CI 有网，正常。
- 本地离线复跑需暖缓存并刷新 ForgeGradle 的 1 小时 etag TTL：
  `find ~/.gradle/caches/forge_gradle -type f -exec touch {} +`
- MDG 的 manifest 下载没有离线开关；断网时只能复用已生成的 artifacts。

## smoke 状态与已知限制

- **11 个节点全部通过本机客户端 smoke**（Pass L、Pass A、Pass B）。
- 此前的 1.20.1 Forge `WAIT_TITLE` 超时根因是 Forge 的 mod 加载警告：缺少 `pack.mcmeta` 时 Forge 会显示 `LoadingErrorScreen`（“failed to load a valid ResourcePackInfo”）而不是 `TitleScreen`。legacy 构建现在为 1.20.1 节点生成 `pack.mcmeta`；`AutoSmokeClient` 也会打印实际的 `screen`/`overlay` 类名（超时时还会反射打印 screen 字段）便于后续排查。
- 客户端 smoke CI 工作流是实验性的，未在 GitHub Actions 上验证（见上）。

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
