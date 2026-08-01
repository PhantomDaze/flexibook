# FlexiBook

**Minecraft 26.2 / 26.1.2 / 1.21.4 / 1.21.1（NeoForge）**、**1.20.1（Forge）** 与 **Fabric 1.20.1 / 1.21.1 / 1.21.4 / 1.21.11** 自适应排版成书模组。

> 官方 NeoForge **没有** 1.20.1 线（从 1.20.2 起）。1.20.1 产物对应 **LexForge 47.x**。

用自定义 DataComponent（26.x / 1.21.x）/ 物品 NBT（1.20.1）+ 客户端布局引擎 + `AdaptiveBookScreen`，在保留“书”交互的前提下支持：

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

## 要求

| 目标 | 加载器 | JDK（toolchain） | 说明 |
|------|--------|------------------|------|
| **26.2** | NeoForge 26.2.x（beta） | **25** | DataComponent；`setScreenAndShow` |
| **26.1.2** | NeoForge 26.1.2.x | **25** | DataComponent；`Identifier` + extract GUI |
| **1.21.4** | NeoForge 21.4.x | **21** | DataComponent 存书 |
| **1.21.1** | NeoForge 21.1.x | **21** | DataComponent 存书 |
| **1.20.1** | Forge 47.4.x | **17** | NBT 键 `flexibook:content` |
| **1.21.11-fabric** | Fabric Loader + API | **21** | DataComponent；`Identifier` + 混合 GUI |
| **1.21.4-fabric** | Fabric Loader + API | **21** | DataComponent 存书 |
| **1.21.1-fabric** | Fabric Loader + API | **21** | DataComponent 存书 |
| **1.20.1-fabric** | Fabric Loader + API | **17** | NBT 键 `flexibook:content` |

- **Gradle 8.14.x**（wrapper）+ **Fabric Loom 1.13.x**。Stonecutter 多版本；默认 active 为 **1.21.1**（可切换）。
- Gradle 进程需 **JDK 21**（`org.gradle.java.home` 已固定；**26.x** 编译仍用 toolchain JDK 25）。系统默认 JDK 26 会弄坏 Stonecutter 的 Kotlin DSL。

## Git 分支（按 JDK toolchain）

| 分支 | 内容 |
|------|------|
| **`main`** | JDK 25 主线（MC **26.1.2 / 26.2** NeoForge）；默认 active **26.1.2** |
| **`java17`** | 仅 MC **1.20.1** Forge + Fabric（toolchain **17**） |
| **`java21`** | MC **1.21.1 / 1.21.4** NeoForge + Fabric，以及 **1.21.11** Fabric（toolchain **21**） |
| **`java25`** | 仅 MC **26.1.2 / 26.2** NeoForge（toolchain **25**） |

开发时 checkout 与目标 JDK 对应的分支；精简分支的 `versions/` 与 Stonecutter 节点仅含该 JDK 线。

## 构建

```bash
# Gradle 守护进程用 JDK 21（见 gradle.properties 的 org.gradle.java.home）。
# 各版本节点仍用各自 toolchain 编译（17 / 21 / 25）。
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# --- 无需切换 active ---
# 任意 :version:compileJava / :version:runClient / :version:build
# 都会先跑 setupChiseledBuild，把根 src/ 预处理到
# versions/<v>/build/chiseledSrc（仅针对该版本）。

# 本分支全部版本（jar + 测试）：
./gradlew chiseledBuild
# versions/26.1.2/build/libs/flexibook-1.0.0+26.1.2.jar
# versions/26.2/build/libs/flexibook-1.0.0+26.2.jar

# 单版本（与 stonecutter.active 无关）：
./gradlew :26.1.2:build
./gradlew :26.2:build
./gradlew :26.1.2:runClient
./gradlew :26.2:runClient

# 可选：只改 IDE/根 src/ 的注释状态（构建不需要）
./gradlew "Set active project to 26.1.2"
./gradlew "Set active project to 26.2"
./gradlew "Reset active project"   # 提交前恢复 VCS 默认
```

`main` 分支节点：`26.1.2`、`26.2`（NeoForge，JDK 25 toolchain）。
其它 JDK 线在 `java17` / `java21` 分支，同样始终走 chiseled 源码。

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

**已做**：DataComponent、双形态存储（elements 或 rawMarkup）、TagParser、自适应 layout + 缓存、单页 Screen、Builder、示例书双语、搜索、可注册数据驱动主题（默认 + contain 示例 + JSON 重载）、内置书字体 `flexibook:default`（unihex，与编辑器同一 ZIP）、独立编辑器 Unihex 预览、资源包导出/导入、`/flexibook give|list`。

**未做 / 简化**：

- 讲台完整兼容（仅 `FlexiBookAPI.LecternCompat` 桩）
- 不劫持原版 `written_book`
- 书内编辑、表格、自动目录、翻书 3D 动画

## 许可证

[GNU GPL v3](./LICENSE) © PhantomDaze
