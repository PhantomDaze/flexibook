# FlexiBook

NeoForge **1.21.1** 自适应排版成书模组。

用自定义 DataComponent + 客户端布局引擎 + `AdaptiveBookScreen`，在保留“书”交互的前提下支持：

- HTML 子集标签（`[h1]` / `[p]` / `[b]` / `[color]` / `[img]` / `[link]` …）
- 打开时按当前语言解析翻译键并重新测量分页
- 内容过长时自动缩小字号，必要时双栏
- 安全链接：仅注册过的 command id，或确认后的 http(s) URL
- 书内搜索高亮
- `AdaptiveBookBuilder` / `FlexiBookAPI` 供其他模组调用

- **其他模组详细调用文档**：[docs/API.md](./docs/API.md)
- 设计方案：[Minecraft_FlexiBook_HTML_Subset_Scheme.md](./Minecraft_FlexiBook_HTML_Subset_Scheme.md)
- 文档索引：[docs/README.md](./docs/README.md)

## 要求

- **JDK 21** 运行 Gradle（JDK 25/26 会因 Gradle/Groovy 字节码版本报错）
- Minecraft 1.21.1
- NeoForge 21.1.x（见 `gradle.properties` 中 `neo_version`）

## 构建

```bash
# 若默认 java 不是 21：
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
# 或在 gradle.properties 设置 org.gradle.java.home=...

./gradlew build
# jar: build/libs/flexibook-1.0.0.jar

./gradlew runClient
```

## 游戏内

创造栏 **FlexiBook** 分类：

- 空白 `flexi_book`
- **FlexiBook Field Guide** 示例书（中英 `lang` 齐全）

右键打开；左右方向键或按钮翻页；底部可搜索。

## 其他模组接入

完整说明（依赖、Builder 全表、标签属性、链接安全、组件读写、i18n、FAQ）见 **[docs/API.md](./docs/API.md)**。

```java
ItemStack book = FlexiBookAPI.builder("my_guide")
    .titleKey("mymod.book.title")
    .defaultFont("mymod:fancy") // 可选：整书默认字体
    .h1("mymod.book.ch1")
    .p("mymod.book.p1")
    .font("mymod.book.quote", ResourceLocation.withDefaultNamespace("alt")) // 单段其它字体
    .bullet("mymod.book.b1")
    .image(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/icon.png"), 48, 48)
    .link("mymod.book.click", FlexiBookAPI.commandAction("mymod:open_map"))
    .divider()
    .fromMarkup("[p]mymod.book.extra[/p]") // 仅当 elements 为空时整本存 raw
    .buildItem();

// 安全动作（不要注册任意 shell）
FlexiBookAPI.registerCommandAction("mymod:open_map", ctx -> ctx.message("mymod.map.opened"));
```

内容也可以只存 raw 标签字符串：

```java
AdaptiveBookContent content = AdaptiveBookContent.ofMarkup(
    new TranslatableText("mymod.book.title"),
    """
    [h1]mymod.book.ch1[/h1]
    [p]mymod.book.p1[/p]
    [bullet]mymod.book.b1[/bullet]
    """
);
ItemStack stack = FlexiBookAPI.createBook(content);
```

DataComponent id：`flexibook:adaptive_book_content`。

## 标签一览

| 标签 | 说明 |
|------|------|
| `[h1]` `[h2]` | 标题 |
| `[p]` | 段落（可嵌套行内样式） |
| `[b]` `[i]` `[u]` | 粗体 / 斜体 / 下划线 |
| `[color=#RRGGBB]` | 颜色 |
| `[font font="ns:id"]` / `[font=ns:id]` | 行内字体（可与书级 `defaultFont` 混用） |
| `[br]` | 换行 |
| `[divider]` | 分隔线 |
| `[img src="..." width="48" height="48" /]` | 图片（资源路径） |
| `[link cmd="modid:action"]` / `[link url="https://..."]` | 安全链接 |
| `[bullet]` | 列表项 |
| `[div class="..."]` | 容器 |

转义：`\[` `\]`。

## v1 范围与非目标

**已做**：DataComponent、双形态存储、TagParser、自适应 layout + 缓存、单页 Screen、Builder、示例书双语、搜索、主题纹理入口。

**未做 / 简化**：

- 讲台完整兼容（仅 `FlexiBookAPI.LecternCompat` TODO）
- 不劫持原版 `written_book`
- 书内编辑、表格、自动目录、翻书 3D 动画

## 许可证

[GNU GPL v3](./LICENSE) © PhantomDaze
