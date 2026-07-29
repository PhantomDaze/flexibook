# FlexiBook 调用文档

面向其他模组与数据包作者的 **API / 数据 / 标签** 说明。  
实现版本：`flexibook` **1.0.0** · Minecraft **1.21.1** · NeoForge **21.1.x**。

设计背景见 [`DESIGN.md`](./DESIGN.md)。快速上手见 [`../README.md`](../README.md)。文档索引见 [`README.md`](./README.md)。

---

## 目录

1. [依赖接入](#1-依赖接入)
2. [核心概念](#2-核心概念)
3. [快速开始](#3-快速开始)
4. [FlexiBookAPI](#4-flexibookapi)
5. [AdaptiveBookBuilder](#5-adaptivebookbuilder)
6. [内容模型](#6-内容模型)
7. [标签语法（Markup）](#7-标签语法markup)
8. [链接与安全动作](#8-链接与安全动作)
9. [物品与 DataComponent](#9-物品与-datacomponent)
10. [打开界面](#10-打开界面)
11. [国际化（i18n）](#11-国际化i18n)
12. [字体（每书 / 行内）](#12-字体每书--行内)
13. [布局与主题](#13-布局与主题)
14. [数据化书籍注册](#14-数据化书籍注册)
15. [完整示例](#15-完整示例)
16. [限制与常见问题](#16-限制与常见问题)
17. [包与符号索引](#17-包与符号索引)

---

## 1. 依赖接入

### 1.1 Gradle（推荐）

将构建产物放到本地 `libs/`，或发布到自己的 Maven 后引用：

```gradle
repositories {
    // 本地 jar 示例
    flatDir { dirs 'libs' }
}

dependencies {
    // 运行时与编译都需要（物品、组件、打开书）
    implementation name: 'flexibook-1.0.0'
    // 若已安装到 mavenLocal：
    // implementation 'io.github.PhantomDaze.flexibook:flexibook:1.0.0'
}
```

`mods.toml` 中声明依赖（按需 `required` / `optional`）：

```toml
[[dependencies.mymod]]
    modId="flexibook"
    type="required"          # 或 optional
    versionRange="[1.0.0,)"
    ordering="NONE"
    side="BOTH"
```

| 字段 | 值 |
|------|-----|
| modid | `flexibook` |
| 主包 | `io.github.PhantomDaze.flexibook` |
| 公开门面 | `...api.FlexiBookAPI` / `...api.AdaptiveBookBuilder` |
| 许可证 | GPL-3.0-only（衍生作品需遵守 GPLv3） |

### 1.2 需要 import 的典型类型

```java
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.api.AdaptiveBookBuilder;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.content.TranslatableText;
import io.github.PhantomDaze.flexibook.content.LinkActionRegistry;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import io.github.PhantomDaze.flexibook.registry.ModItems;
```

客户端专用（**勿在服务端公共类静态初始化**）：

```java
import io.github.PhantomDaze.flexibook.client.ClientModEvents;
// AdaptiveBookScreen / LinkHandler / BookThemeReloadListener 仅客户端
// BookTheme / BookThemeRegistry / BookThemes 为纯数据，公共侧可 registerTheme
```

---

## 2. 核心概念

```
ItemStack(flexibook:flexi_book)
    └── DataComponent flexibook:adaptive_book_content
            └── AdaptiveBookContent
                    ├── title: TranslatableText          // 打开时再翻译
                    ├── defaultFont: Optional<RL>        // 整本书默认字体
                    ├── themeId: Optional<RL>            // 主题 id → BookThemeRegistry
                    └── payload（二选一，elements 优先）
                          ├── rawMarkup: String          // [h1]... 标签源
                          └── elements: List<BookElement>
                                └── InlineSpan.style.font / Heading.font 可覆盖
```

| 概念 | 说明 |
|------|------|
| **双形态存储** | 可存结构化 `BookElement` 列表，或 raw 标签字符串；两者都在时 **elements 胜出** |
| **翻译延迟** | 标题与正文键在 **打开书 / 布局** 时按当前客户端语言解析，不在写入时固化译文 |
| **字体分层** | 行内/标题 `font` → 书级 `defaultFont` → 内置 `flexibook:default`（unihex）；绝不静默落到 `minecraft:default` |
| **主题注册** | `BookTheme` 按 `ResourceLocation` 注册；书上存 `theme` id，缺省 `flexibook:default` |
| **安全链接** | `cmd` 只触发已注册的动作 ID；`url` 仅 http(s) 且经确认框 |
| **自定义物品** | `flexibook:flexi_book`，**不**劫持原版 `written_book` |
| **纯客户端 Screen** | 无 Menu；右键在客户端打开 `AdaptiveBookScreen` |

运行时解析路径：

```
AdaptiveBookContent.resolveElements()
  → 已有 elements ? 直接用
  → 否则 TagParser.parse(rawMarkup)
  → BookLayoutEngine（缩放 / 双栏 / 缓存）
  → AdaptiveBookScreen 单页绘制 + 翻页 + 搜索高亮
```

---

## 3. 快速开始

### 3.1 用 Builder 生成一本可右键打开的书

```java
ItemStack book = FlexiBookAPI.builder("my_guide")
    .titleKey("mymod.book.title")
    .h1("mymod.book.ch1")
    .p("mymod.book.p1")
    .bullet("mymod.book.b1")
    .image(
        ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/icon.png"),
        48, 48,
        "mymod.book.icon_tip"
    )
    .link("mymod.book.click_me", FlexiBookAPI.commandAction("mymod:open_map"))
    .divider()
    .buildItem();

player.getInventory().add(book);
```

### 3.2 注册链接动作（建议在模组公共初始化里）

```java
// 公共侧即可注册；handler 内部可只做客户端提示
FlexiBookAPI.registerCommandAction("mymod:open_map", ctx -> {
    ctx.message("mymod.map.opened"); // 翻译键 → 本地玩家聊天/actionbar 风格提示
});
```

### 3.3 只喂标签字符串

```java
AdaptiveBookContent content = AdaptiveBookContent.ofMarkup(
    new TranslatableText("mymod.book.title"),
    """
    [h1]mymod.book.ch1[/h1]
    [p]mymod.book.p1 [b]mymod.book.emph[/b][/p]
    [bullet]mymod.book.b1[/bullet]
    [link cmd="mymod:open_map"]mymod.book.click_me[/link]
    [img src="mymod:textures/gui/icon.png" width="48" height="48"/]
    """
);
ItemStack stack = FlexiBookAPI.createBook(content);
```

---

## 4. FlexiBookAPI

门面类：`io.github.PhantomDaze.flexibook.api.FlexiBookAPI`（不可实例化）。

| 方法 | 说明 |
|------|------|
| `builder(String guideId)` | 新建 `AdaptiveBookBuilder`。`guideId` v1 **仅预留**（未来 TOC/身份），不写入组件 |
| `createBook(AdaptiveBookContent content)` | 创建 `flexi_book` 物品并写入内容组件 |
| `registerCommandAction(String id, Consumer<ActionContext> action)` | 注册安全 command 链接；`id` 建议 `modid:name` |
| `commandAction(String id)` | 构造 `LinkAction.CommandId`，供 Builder / 结构化元素使用 |
| `urlAction(String url)` | 构造 `LinkAction.Url`（运行时仍校验 http(s)） |
| `registerDefaultActions()` | 注册示例书用的 `flexibook:say_hi`；FlexiBook 自身已在模组入口调用，外部一般不必再调 |
| `defaultThemeId()` / `containThemeId()` | 内置示例主题 id：`flexibook:default` / `flexibook:contain` |
| `registerTheme(id, BookTheme)` | 注册或覆盖主题（公共侧可调；纯数据） |
| `getTheme(id)` / `resolveTheme(id)` / `themeIds()` | 查询；`resolve` 未知 id 回退到 default 样例 |
| `LecternCompat` | **空接口 + TODO**：v1 不支持讲台，勿依赖 |

### 4.1 `ActionContext`

```java
public interface LinkActionRegistry.ActionContext {
    /** 向本地玩家展示一条已翻译消息（客户端有玩家时）。 */
    void message(String translationKey, Object... args);
}
```

注意：

- 注册表在 **公共侧**（`ConcurrentHashMap`），专用服务器加载安全。
- 点击发生在 **客户端**；若要在动作里打开 Screen、改客户端状态，请自行保证只在客户端执行（或在 handler 内再分支）。
- **不会**执行任意 `/command` 字符串；未注册 id 会被拦截并提示 `flexibook.link.unknown_action`。

---

## 5. AdaptiveBookBuilder

类：`io.github.PhantomDaze.flexibook.api.AdaptiveBookBuilder`。

链式调用，最终：

- `buildContent()` → `AdaptiveBookContent`
- `buildItem()` → 带组件的 `ItemStack(ModItems.FLEXI_BOOK)`

### 5.1 方法一览

| 方法 | 作用 |
|------|------|
| `titleKey(String key, String... args)` | 书名翻译键 + 可选参数 |
| `title(TranslatableText title)` | 直接设标题对象 |
| `defaultFont(ResourceLocation)` / `defaultFont(String id)` | **整本书**显式默认字体；省略则布局/绘制用 `flexibook:default` |
| `theme(ResourceLocation)` / `theme(String id)` | 书用主题 id（写入组件字段 `theme`）；省略则打开时用 `flexibook:default` |
| `h1(String key)` / `h2(String key)` | 一/二级标题（键） |
| `h1(String key, ResourceLocation font)` / `h2(..., font)` | 标题使用指定字体 |
| `p(String key)` | 段落，单 span 翻译键 |
| `p(String key, ResourceLocation font)` | 整段指定字体 |
| `font(String key, ResourceLocation font)` | 同 `p(key, font)`，语义更直观 |
| `pLiteral(String text)` / `pLiteral(text, font)` | 段落，字面量（**不**走 i18n） |
| `pRaw(String markupFragment)` | 解析一段标签并 **追加** 到 elements |
| `bullet(String key)` / `bullet(key, font)` | 列表项 |
| `image(ResourceLocation, w, h)` | 图片 |
| `image(ResourceLocation, w, h, tooltipKey)` | 图片 + 悬停翻译键 |
| `link(String textKey, LinkAction action)` | 一段可点链接段落（默认蓝+下划线） |
| `link(textKey, action, font)` | 链接 + 字体 |
| `divider()` | 分隔线 |
| `br()` | 块级换行 |
| `element(BookElement)` | 追加任意结构化元素 |
| `fromMarkup(String markup)` | 设置 **整本** raw 源串（见下） |
| `buildContent()` / `buildItem()` | 产出 |

### 5.2 `fromMarkup` 与 elements 的优先级

`buildContent()` 逻辑：

```text
if (rawMarkup != null && elements 为空)
    → AdaptiveBookContent.ofMarkup(title, rawMarkup)
else
    → AdaptiveBookContent.ofElements(title, elements)
```

含义：

- **只**调用 `fromMarkup(...)`、不调 `h1/p/...` → 存 raw，打开时再 parse。
- 调用了任意 `h1/p/pRaw/...` → 存 **结构化 elements**；此时再 `fromMarkup` **不会**覆盖已累积的 elements（raw 字段也不会写入组件）。
- `pRaw` 是「把片段 parse 后并入 elements」，与 `fromMarkup` 不同。

推荐：

- 程序化章节、可维护内容 → Builder 结构化方法。
- 外部文件/配置一整页标签 → `fromMarkup` 或 `AdaptiveBookContent.ofMarkup`。
- 结构化为主、偶发富文本 → `pRaw("[p][b]key[/b][/p]")`。

### 5.3 默认标题

未设标题时默认为 `flexibook.book.untitled`。

---

## 6. 内容模型

### 6.1 `AdaptiveBookContent`

```java
public record AdaptiveBookContent(
    TranslatableText title,
    Optional<String> rawMarkup,              // codec 字段名 "raw"
    Optional<List<BookElement>> elements,    // codec 字段名 "elements"
    Optional<ResourceLocation> defaultFont,  // codec 字段名 "font"
    Optional<ResourceLocation> themeId       // codec 字段名 "theme"
)
```

| 工厂 / 成员 | 说明 |
|-------------|------|
| `EMPTY` | 空书标题键 + 空 elements |
| `ofElements(title, list)` / `…(title, list, font)` / `…(title, list, font, theme)` | 只存结构 |
| `ofMarkup(title, markup)` / `…(title, markup, font)` / `…(title, markup, font, theme)` | 只存 raw |
| `withDefaultFont(ResourceLocation)` | 复制并设置书级字体 |
| `withThemeId(ResourceLocation)` | 复制并设置主题 id |
| `defaultFont()` | 整书**显式**默认字体（Optional）；被 span/标题 font 覆盖 |
| `resolvedFont()` | 显式 `defaultFont` 若有，否则 `FlexiBookFonts.DEFAULT`（`flexibook:default`） |
| `themeId()` | 主题注册表 id；缺省/未知 → 主题 `flexibook:default` |
| `resolveElements()` | 布局/渲染前统一得到 `List<BookElement>` |
| `isEmpty()` | 解析后无元素且 raw 空白 |
| `CODEC` / `STREAM_CODEC` | 持久化 + 网络同步 |

**序列化约定（与 codec 一致）**：

```jsonc
{
  "title": { "key": "mymod.book.title", "args": [] },
  "font": "flexibook:default",    // 可选；省略时 resolvedFont() 仍为 flexibook:default
  "theme": "flexibook:contain",   // 可选，主题 id
  // 二选一：
  "raw": "[h1]mymod.book.ch1[/h1]\n[p]mymod.book.p1[/p]",
  // 或
  "elements": [
    { "type": "heading", "data": { "level": 1, "text": { "key": "mymod.book.ch1" } } }
  ]
}
```

`BookElement` 使用 `Codec.STRING.dispatch`（`type` → `data` 字段）。手写 JSON 时请以实际 codec 为准，**优先用 Builder / Java API 写入**，避免手写 elements 树出错。

实际 JSON 结构（示例）：
```json
{ "type": "heading", "data": { "level": 1, "text": { "key": "mymod.title" } } }
{ "type": "paragraph", "data": { "spans": [ { "text": "mymod.p1", "translate": true } ] } }
{ "type": "image", "data": { "src": "mymod:textures/icon.png", "width": 48, "height": 48 } }
```

### 6.2 `TranslatableText`

```java
public record TranslatableText(String key, List<String> args)
```

| 方法 | 说明 |
|------|------|
| `new TranslatableText(key)` | 无参 |
| `TranslatableText.of(key, args...)` | 有参 |
| `resolve()` / `resolvePlain()` | 客户端布局时调用 |

启发式：`key` 含 `.` 且不含空格 → 当翻译键；否则当 **字面量** `Component.literal`。  
因此标题/段落请用 `mymod.chapter.one` 这种键，不要把整句中文直接当 key（除非有意字面显示）。

### 6.3 `BookElement`（sealed）

| 类型 | `typeId` | 字段 |
|------|----------|------|
| `Heading(level, TranslatableText text, Optional font)` | `heading` | `level` 建议 1 或 2；可选标题字体 |
| `Paragraph(List<InlineSpan> spans)` | `paragraph` | 行内富文本（span 可自带 font） |
| `LineBreak` | `br` | 单例 `INSTANCE` |
| `Divider` | `divider` | 单例 `INSTANCE` |
| `Image(src, width, height, Optional tooltipKey)` | `image` | `ResourceLocation` 纹理 |
| `Bullet(List<InlineSpan> spans)` | `bullet` | 同段落 spans |
| `Box(Optional className, List<BookElement> children)` | `box` | 对应 `[div]`，可嵌套 |

`Heading(level, text)` 两参构造仍可用（`font = empty`）。

手动构造示例：

```java
List<BookElement> body = List.of(
    new BookElement.Heading(1, new TranslatableText("mymod.book.h1")),
    new BookElement.Paragraph(List.of(
        InlineSpan.key("mymod.book.a"),
        InlineSpan.literal(" "),
        InlineSpan.key("mymod.book.b", StyleFlags.EMPTY.withBold(true))
    )),
    BookElement.Divider.INSTANCE,
    new BookElement.Image(
        ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/icon.png"),
        32, 32,
        Optional.of("mymod.book.tip")
    )
);
AdaptiveBookContent content = AdaptiveBookContent.ofElements(
    new TranslatableText("mymod.book.title"),
    body
);
```

### 6.4 `InlineSpan`

```java
public record InlineSpan(
    String text,
    boolean translate,           // true = text 为翻译键
    StyleFlags style,
    Optional<LinkAction> link
)
```

| 工厂 | 含义 |
|------|------|
| `key(translationKey)` | 翻译 span |
| `key(key, style)` / `key(key, style, link)` | 带样式/链接 |
| `literal(text)` / `literal(text, style)` / `literal(..., link)` | 字面量 |

`resolvePlain()`：翻译或原样，供布局测宽与搜索。

### 6.5 `StyleFlags`

```java
public record StyleFlags(
    boolean bold,
    boolean italic,
    boolean underline,
    Optional<Integer> color,
    Optional<ResourceLocation> font
)
```

- `EMPTY`：全关
- `withBold` / `withItalic` / `withUnderline` / `withColor(Integer)`（`null` 清颜色）
- `withFont(ResourceLocation)`（`null` 清字体覆盖）
- `merge(other)`：布尔 OR；颜色 / 字体均 **other 优先**

颜色为 **RGB 整数**（如 `0xCC5500`），与标签 `#RRGGBB` 一致。  
`font` 为原版/资源包字体 id（如 `minecraft:alt`、`minecraft:uniform`、`mymod:my_font`）。

### 6.6 `LinkAction`（sealed）

| 变体 | 工厂 | 点击行为 |
|------|------|----------|
| `None` | `LinkAction.none()` | 忽略 |
| `CommandId(id)` | `LinkAction.commandId(id)` 或 `FlexiBookAPI.commandAction(id)` | 查注册表，未注册则拒绝 |
| `Url(url)` | `LinkAction.url(url)` 或 `FlexiBookAPI.urlAction(url)` | 仅 `http://` / `https://`，`ConfirmLinkScreen` 后 `openUri` |

Codec 校验：非法 URL 在反序列化阶段可失败；客户端 `LinkHandler` 再次校验协议。

---

## 7. 标签语法（Markup）

解析器：`TagParser.parse(String)` → `List<BookElement>`。  
**容错**：未知/残缺标签尽量跳过并打日志，**不向调用方抛异常**；整段失败时退回整页字面段落。

### 7.1 总表

| 标签 | 块/行内 | 说明 |
|------|---------|------|
| `[h1]...[/h1]` / `[h1 font="ns:id"]` | 块 | 一级标题；内容宜为翻译键；可选标题字体 |
| `[h2]...[/h2]` / `[h2 font="ns:id"]` | 块 | 二级标题 |
| `[p]...[/p]` | 块 | 段落，可含行内标签 |
| `[bullet]...[/bullet]` | 块 | 列表项（自动缩进 + 项目符号布局） |
| `[br]` / `[br/]` | 块或行内 | 换行 |
| `[divider]` / `[divider/]` | 块 | 分隔线 |
| `[img ...]` / `[img .../]` | 块 | 图片，自闭合 |
| `[link ...]...[/link]` | 块或行内 | 链接；可带 `font=` |
| `[b]...[/b]` | 行内 | 粗体 |
| `[i]...[/i]` | 行内 | 斜体 |
| `[u]...[/u]` | 行内 | 下划线 |
| `[color=#RRGGBB]...[/color]` | 行内 | 颜色 |
| `[font font="ns:id"]...[/font]` / `[font=ns:id]...[/font]` | 行内 | **切换字体**（可与 b/i/color 嵌套） |
| `[div class="..."]...[/div]` | 块 | 容器 `Box`，可嵌套块级标签 |

### 7.2 属性

**图片**

```text
[img src="mymod:textures/gui/icon.png" width="48" height="48" tooltip="mymod.tip"/]
```

| 属性 | 默认 | 说明 |
|------|------|------|
| `src` | `flexibook:textures/gui/icon.png` | 可带命名空间；无 `:` 时默认 `flexibook:` |
| `width` / `height` | 48 | 逻辑像素占位 |
| `tooltip` | 无 | 翻译键 |

**链接**

```text
[link cmd="mymod:open_map"]mymod.book.go[/link]
[link url="https://example.com"]mymod.book.web[/link]
[link cmd="mymod:x" color="#00AAFF"]...[/link]
[link cmd="mymod:x" font="minecraft:alt"]...[/link]
```

| 属性 | 说明 |
|------|------|
| `cmd` | 动作 ID（须事先 `registerCommandAction`） |
| `url` | 仅 http(s) |
| `color` | 可选，覆盖默认链接色 |
| `font` | 可选，链接文字字体 |

`cmd` 与 `url` 同时存在时解析侧 **优先 `cmd`**。

**字体 / 标题字体**

```text
[font font="minecraft:alt"]mymod.book.runes[/font]
[font=minecraft:uniform]mymod.book.mono[/font]
[h1 font="mymod:fancy"]mymod.book.title_key[/h1]
[h2 font="minecraft:alt"]mymod.book.sub[/h2]
```

| 标签 | 属性 | 说明 |
|------|------|------|
| `[font]` | `font="ns:id"` 或 `[font=ns:id]` | 行内切换字体，关闭标签后恢复外层样式 |
| `[h1]` / `[h2]` | `font="ns:id"` | 仅该标题使用指定字体 |

详见 [§12 字体](#12-字体每书--行内)。

**div**

```text
[div class="note"]
[p]mymod.note.body[/p]
[/div]
```

`class` 写入 `Box.className`；v1 主题 **未**按 class 换肤，可供后续或自研扩展识别。

### 7.3 转义

- `\[` → 字面 `[`
- `\]` → 字面 `]`

### 7.4 块外松散文本

标签外的非空白文本会收成 **字面量段落**（`translate=false`）。生产内容请始终包在 `[p]` / `[h1]` 等标签内，并用翻译键。

### 7.5 行内「是否像翻译键」

`TagParser` 刷写 span 时：文本含 `.` 且不含空格 → `translate=true`，否则字面量。  
与 `TranslatableText` 的启发式一致。

### 7.6 完整 markup 样例

```text
[h1]mymod.guide.title[/h1]
[p]mymod.guide.intro[/p]
[divider]
[h2]mymod.guide.section[/h2]
[bullet]mymod.guide.b1[/bullet]
[bullet]mymod.guide.b2[/bullet]
[p]普通说明里夹 [b]mymod.guide.bold_key[/b] 与 [color=#CC5500]mymod.guide.warm[/color]。[/p]
[p]字面中括号：\[不是标签\][/p]
[p]默认字体后接 [font font="minecraft:alt"]mymod.guide.alt_run[/font] 再回来。[/p]
[h2 font="minecraft:uniform"]mymod.guide.mono_h2[/h2]
[link cmd="mymod:open_map"]mymod.guide.open_map[/link]
[link url="https://neoforged.net/"]mymod.guide.docs[/link]
[img src="mymod:textures/gui/diagram.png" width="64" height="64" tooltip="mymod.guide.diagram"/]
```

注意：`[p]` 内混排 **字面中文 + 翻译键** 时，键必须单独成段且符合「有点号无空格」规则；更稳妥的做法是整段都用键，或用 Builder 的多个 `InlineSpan`。

---

## 8. 链接与安全动作

### 8.1 注册

```java
// 建议在模组构造 / 公共 setup 调用一次
FlexiBookAPI.registerCommandAction("mymod:give_kit", ctx -> {
    ctx.message("mymod.kit.hint");
    // 若需服务端逻辑：请自建网络包，不要指望这里直接改 ServerPlayer
});
```

底层：`LinkActionRegistry.register(id, consumer)`。  
`id` / `action` 为空则静默忽略。后注册同 id 会 **覆盖**。

查询：`LinkActionRegistry.isRegistered(id)` / `get(id)`。

### 8.2 点击路径（客户端）

```
AdaptiveBookScreen 点击 ClickArea
  → LinkHandler.handle(action, parentScreen)
       CommandId → Registry.get → 无则 warn + flexibook.link.unknown_action
       Url       → 协议检查 → ConfirmLinkScreen → Util.getPlatform().openUri
       None      → 忽略
```

### 8.3 内置示例动作

| ID | 行为 |
|----|------|
| `flexibook:say_hi` | `ctx.message("flexibook.action.say_hi")` |

### 8.4 安全约定（必读）

1. **禁止**把玩家或书籍里的任意字符串当 shell / brigadier 执行。  
2. 只暴露你明确实现的 `modid:action` 白名单。  
3. URL 不要使用 `file:`、`javascript:` 等；即便写入也会被拒。  
4. 动作 handler 默认只适合 **客户端反馈**；跨端玩法请自行设计包与权限。

---

## 9. 物品与 DataComponent

| 注册名 | 说明 |
|--------|------|
| 物品 `flexibook:flexi_book` | `ModItems.FLEXI_BOOK`，`stacksTo(1)` |
| 组件 `flexibook:adaptive_book_content` | `ModDataComponents.ADAPTIVE_BOOK_CONTENT` |
| 创造栏 `flexibook` | 空白书 + Demo Guide |

### 9.1 读写组件

```java
// 写
stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), content);

// 读
AdaptiveBookContent c = stack.get(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get());
if (c == null || c.isEmpty()) {
    // 空书
}
```

等价于 API：

```java
ItemStack stack = FlexiBookAPI.createBook(content);
```

### 9.2 物品展示名与 Tooltip

- `getName`：若有组件，用 `content.title().resolve()`  
- Tooltip：固定说明 + 空书时额外 `flexibook.item.flexi_book.empty`

### 9.3 发放方式建议

| 场景 | 做法 |
|------|------|
| 成就/任务奖励 | 服务端 `player.addItem(FlexiBookAPI.createBook(...))` |
| 创造栏 | 自建 `CreativeModeTab` 或复用逻辑与 Demo 相同 |
| 命令 | 自建 brigadier，内部 `createBook`（模组 v1 不强制自带 give 命令） |
| 战利品表 | 需自定义 loot function 写 DataComponent（v1 未提供内置 function，可在自己的模组里写） |

组件带 `persistent` + `networkSynchronized` + `cacheEncoding`，随物品存档与同步。

---

## 10. 打开界面

### 10.1 玩家右键

`FlexiBookItem.use`：仅 `level.isClientSide` 时打开；返回 `sidedSuccess`。  
实现上通过 **反射** 调用 `ClientModEvents.openBook(stack)`，避免专用服务器加载 `Screen`。

### 10.2 其他模组主动打开（仅客户端）

```java
// 必须已在客户端线程 / 客户端类中
ClientModEvents.openBook(stack);
// 等价于
// Minecraft.getInstance().setScreen(new AdaptiveBookScreen(stack));
```

**不要**在服务端或公共类的静态字段里引用 `AdaptiveBookScreen`。

### 10.3 Screen 交互（玩家侧）

| 操作 | 行为 |
|------|------|
| 左/右方向键、翻页按钮 | 翻页 |
| 点击链接区域 | `LinkHandler` |
| 底部搜索框 | 当前布局文本高亮匹配（空查询 = 全本） |
| 切换语言后重开书 | 重新翻译 + 重新布局（缓存 key 含语言） |

### 10.4 讲台

v1 **不支持**。`FlexiBookAPI.LecternCompat` 仅为未来扩展占位。

---

## 11. 国际化（i18n）

### 11.1 原则

- 写入书的是 **键**（及可选 string args），不是某一语言的最终句子。  
- 译文放在 **打开书的客户端** 的 `assets/<modid>/lang/*.json`。  
- 中文往往更长 → 布局引擎可能降低 `scale` 或启用双栏，页数变化是预期行为。

### 11.2 键命名建议

```text
mymod.book.<guide_id>.title
mymod.book.<guide_id>.ch1
mymod.book.<guide_id>.p1
mymod.action.<name>.feedback
```

### 11.3 lang 示例

`assets/mymod/lang/en_us.json`：

```json
{
  "mymod.book.title": "Field Manual",
  "mymod.book.ch1": "Getting Started",
  "mymod.book.p1": "Welcome to the guide.",
  "mymod.book.click_me": "Open map",
  "mymod.map.opened": "Map overlay enabled."
}
```

`zh_cn.json`：

```json
{
  "mymod.book.title": "实地手册",
  "mymod.book.ch1": "入门",
  "mymod.book.p1": "欢迎阅读本指南。",
  "mymod.book.click_me": "打开地图",
  "mymod.map.opened": "已启用地图叠加层。"
}
```

### 11.4 字面量适用场景

- 调试、动态拼接的非翻译文本：`pLiteral` / `InlineSpan.literal`  
- 不要把需要多语言的正文写成字面量。

---

## 12. 字体（每书 / 行内）

字体 id 与原版聊天组件一致：资源位于 `assets/<namespace>/font/<path>.json`，引用写作 `namespace:path`。

### 12.0 内置默认：`flexibook:default`

FlexiBook **自带**书用默认字体，不依赖 `minecraft:default`：

| id | 说明 |
|----|------|
| `flexibook:default` | 模组内置 unihex（GNU Unifont 17.0.05 HEX/ZIP）+ space provider。书未声明 `font` 时布局/绘制/页码/标题均解析到此 id |
| `FlexiBookFonts.DEFAULT` / `content.resolvedFont()` | Java 侧常量与解析入口 |

资源文件：

- `assets/flexibook/font/default.json`
- `assets/flexibook/font/unifont_all-17.0.05.zip`
- `assets/flexibook/font/LICENSE-unifont.txt`

编辑器与游戏消费**同一 ZIP**，按 Minecraft `UnihexProvider` 度量对齐 advance（含粗体 +0.5 后 `ceil`）。

其它仍可选用的字体 id（需客户端资源存在）：

| id | 说明 |
|----|------|
| `minecraft:alt` | 附魔台风格（显式覆盖时） |
| `minecraft:uniform` | 原版 Unicode 字体栈 |
| `minecraft:illageralt` | 灾厄村民风格 |
| `mymod:…` | 你自己的 font json |

普通游戏 UI（搜索框、按钮、tooltip）仍走 Minecraft UI 字体，不受书默认字体影响。

### 12.1 优先级

```
行内 StyleFlags.font / Heading.font
        ↓ 若无
书级 AdaptiveBookContent.defaultFont（JSON 字段 "font"）
        ↓ 若无
flexibook:default   ← 不再落到 minecraft:default
```

同一本书内可以：

- 不写 `font` → 自动 `flexibook:default`  
- 只设书级 `defaultFont` → 全书统一为该 id  
- 只在部分 span / 标题设 font → 混排  
- 两者都设 → 局部覆盖书级默认  

布局测宽与绘制都走 `Style.withFont(...)`，换行宽度按真实字体 advance 计算。

### 12.2 Builder 示例

```java
import io.github.PhantomDaze.flexibook.content.FlexiBookFonts;

ResourceLocation fancy = ResourceLocation.fromNamespaceAndPath("mymod", "fancy");
ResourceLocation mono  = ResourceLocation.withDefaultNamespace("uniform");

ItemStack book = FlexiBookAPI.builder("styled_guide")
    .titleKey("mymod.book.title")
    // 可省略：未设置时 layout/screen 仍用 FlexiBookFonts.DEFAULT
    .defaultFont(FlexiBookFonts.DEFAULT)
    .h1("mymod.book.h1")
    .h2("mymod.book.code_title", mono)            // 标题单独 mono
    .p("mymod.book.body")
    .font("mymod.book.quote", mono)
    .pRaw("[p]普通 [font font=\"minecraft:alt\"]符文[/font] 混排[/p]")
    .buildItem();
```

### 12.3 仅改书级字体

```java
AdaptiveBookContent c = AdaptiveBookContent.ofMarkup(title, markup)
    .withDefaultFont(ResourceLocation.fromNamespaceAndPath("mymod", "fancy"));
// 或
AdaptiveBookContent.ofElements(title, elements, Optional.of(fontId));

// 解析后的有效字体（显式或 flexibook:default）
ResourceLocation used = c.resolvedFont();
```

### 12.4 注意

- **缺省语义变更**：JSON/Builder 不写 `font` 时，书页文字使用 `flexibook:default`，不是原版 `minecraft:default`。  
- 显式自定义字体必须在 **打开书的客户端** 资源里存在；缺失时原版可能回退显示异常。  
- 自定义 TTF/位图/unihex 请按原版 font json 规范打包；编辑器**仅**内置预览 `flexibook:default`，其它 font id 会提示不支持并回退预览。  
- 服务端只存 `ResourceLocation` 字符串；实际字形仅客户端解析。  
- Unifont 许可见 jar 内 `LICENSE-unifont.txt`（OFL / GPL font exception）；更新脚本：`scripts/update-unifont.sh`（不会在普通 build 联网）。
- 实施规格与验收清单见 [`UNIFIED_FONT_PLAN.md`](./UNIFIED_FONT_PLAN.md)（已落地；改字体策略只改该文档 + 本节）。

---

## 13. 布局与主题

布局算法仍为内部实现（无第三方替换 SPI）。**主题（BookTheme）已数据化注册**：代码注册 + 资源包 JSON。

### 13.1 自适应策略（摘要）

1. 解析 elements → 当前语言纯文本 → 测宽分页  
2. 初始 `scale=1.0`、`columns=1`  
3. 页数过多或过挤 → 降低 scale（约至 0.6）→ 再尝试 `columns=2`  
4. CJK 占比高时起始 scale 略降  
5. `LayoutCache`：内容哈希 + 语言 + GUI scale + theme revision + font；登出 / 主题重载清空  

默认内容区约（`flexibook:default`）：宽 160、高 185 逻辑像素（见 `BookTheme.baseParams()`）。

### 13.2 内置示例主题

| id | 说明 |
|----|------|
| `flexibook:default` | 羊皮纸样例（现有默认间距/颜色/纹理；图片 **STRETCH**） |
| `flexibook:contain` | 同上，图片 **CONTAIN**（等比居中） |

代码常量：`BookThemes.DEFAULT` / `BookThemes.CONTAIN`。可被同 id 的资源 JSON 在客户端重载时覆盖。

### 13.3 代码注册主题

```java
// 公共 setup 即可（纯数据，不碰 Screen）
BookTheme dark = BookTheme.builder()          // 从 default 样例拷贝
    .pageTextColor(0xE0E0E0)
    .linkColor(0x7FDBFF)
    .imageFit(ImageFit.CONTAIN)
    .revision(1)
    .build();
FlexiBookAPI.registerTheme(
    ResourceLocation.fromNamespaceAndPath("mymod", "dark"),
    dark
);

// 书引用主题
ItemStack book = FlexiBookAPI.builder("guide")
    .titleKey("mymod.book.title")
    .theme("mymod:dark")
    .p("mymod.book.body")
    .buildItem();
// 或 content.withThemeId(...)
```

打开书时：`AdaptiveBookScreen` → `BookThemeRegistry.resolve(content.themeId())`；未知 id 警告并回退 `flexibook:default`。

### 13.4 资源包 JSON 主题

路径：`assets/<namespace>/flexibook/themes/<path>.json`  
id：`<namespace>:<path>`（例：`assets/mymod/flexibook/themes/dark.json` → `mymod:dark`）

本模组样例：

- `assets/flexibook/flexibook/themes/default.json`
- `assets/flexibook/flexibook/themes/contain.json`

必填：`book_texture`、`widgets_texture`（`ResourceLocation` 字符串）。其余字段均有默认值，可只写要改的项：

| 字段 | 默认（约） | 含义 |
|------|------------|------|
| `book_tex_width` / `book_tex_height` | 192 / 216 | 书页绘制尺寸 |
| `texture_sheet_size` | 256 | 贴图 sheet（7 参数 blit） |
| `content_left` / `content_top` | 16 / 10 | 正文原点相对书左上 |
| `title_offset_y` | 5 | 标题 Y（相对 topPos） |
| `content_offset_y` | 4 | 正文相对 `content_top` 的额外下移 |
| `page_label_inset_y` | 18 | 页码距书底 |
| `page_content_width` / `page_content_height` | 160 / 185 | 布局内容区 |
| `line_height` / `paragraph_gap` / `heading_gap` | 9 / 3 / 5 | 排版 |
| `gutter` / `bullet_indent` / `divider_height` | 10 / 10 / 6 | 双栏/列表/分隔线 |
| `page_text_color` 等 | RGB int | 正文/链接/高亮/分隔线色 |
| `image_fit` | `"stretch"` | `"stretch"` \| `"contain"` |
| `revision` | 1 | 参与布局缓存键；改主题请递增 |

客户端资源重载时 `BookThemeReloadListener` 会 bootstrap 内置样例再加载 JSON，并清空布局缓存。

### 13.5 可替换纹理（被主题引用）

| 路径 | 用途 |
|------|------|
| `assets/flexibook/textures/gui/book.png` | 默认书页背景（256 sheet，绘制 192×216） |
| `assets/flexibook/textures/gui/book_widgets.png` | 按钮等 |
| `assets/flexibook/textures/gui/icon.png` | 示例/默认图 |
| `assets/flexibook/textures/item/flexi_book.png` | 物品图标 |

自定义主题可把 `book_texture` / `widgets_texture` 指到你自己的路径。

### 13.6 图片元素纹理与比例

`BookElement.Image.src` 必须是客户端能加载的纹理 `ResourceLocation`（通常 `textures/...png`）。缺失时渲染可能空白或粉黑，请自备资源。

布局始终按元素声明的 **逻辑** `width`×`height` 占位（过宽会再按栏宽等比缩小）。PNG 真实分辨率可以与之不同；**绘制**时由主题的 `image_fit` 决定：

| `ImageFit` | 行为 |
|------------|------|
| `STRETCH`（**default 样例**） | 整图拉伸铺满逻辑框；宽高比不一致时会变形 |
| `CONTAIN`（**contain 样例**） | 读取 PNG 像素尺寸，等比缩放入框内并居中（可能上下/左右留白） |

```java
// 书级选用 keep-aspect 示例主题
FlexiBookAPI.builder("guide").theme(FlexiBookAPI.containThemeId())...
// 或自己的主题 .imageFit(ImageFit.CONTAIN)
```

`width`/`height` 仍表示「占位框」；`CONTAIN` 只改变框内像素，不改变翻页布局。

---

## 14. 数据化书籍注册

书籍内容（`AdaptiveBookContent`）支持通过资源包 JSON 定义，路径与主题一致：

### 14.1 资源路径
```
assets/<namespace>/flexibook/books/<path>.json
```
生成的 id 为 `<namespace>:<path>`（例如 `assets/mymod/flexibook/books/guide.json` → `mymod:guide`）。

### 14.2 JSON 格式
直接对齐 `AdaptiveBookContent` 的 codec 字段：

```jsonc
{
  "title": { "key": "mymod.book.guide.title" },
  "elements": [
    { "type": "heading", "data": { "level": 1, "text": { "key": "mymod.book.guide.h1" } } },
    { "type": "paragraph", "data": { "spans": [ { "text": "mymod.book.guide.p1", "translate": true } ] } },
    { "type": "image", "data": { "src": "mymod:textures/gui/icon.png", "width": 48, "height": 48 } }
  ],
  "font": "flexibook:default",   // 可选；省略时运行时也解析为 flexibook:default
  "theme": "mymod:dark"          // 可选，主题 id
}
```

也支持 raw 形态（与双形态存储一致）：

```json
{
  "title": { "key": "mymod.book.guide.title" },
  "raw": "[h1]mymod.book.guide.h1[/h1][p]mymod.book.guide.p1[/p]",
  "theme": "mymod:default"
}
```

规则：
- 同时存在时 **elements 优先**。
- `title` 必填（`TranslatableText` 形态）。
- 字体/主题 id 缺省或未知时按运行时规则回退。

### 14.3 加载与覆盖
- 代码可在任意公共侧通过 `FlexiBookAPI.registerBookContent(id, content)` 预注册。
- 客户端资源重载时：
  1. 清理上一次资源来源注册的书籍 id。
  2. 调用 `bootstrap()`（当前为空，可用于未来内置）。
  3. 扫描 `flexibook/books` 下所有 `*.json`，用 `AdaptiveBookContent.CODEC` 解析并注册。
- **资源定义会覆盖同 id 的代码注册**，直到下次重载。
- 重载后会清空 `BookLayoutEngine` 缓存。

### 14.4 API 表面
```java
// 注册（代码路径）
FlexiBookAPI.registerBookContent("mymod:guide", content);

// 查询
Optional<AdaptiveBookContent> c = FlexiBookAPI.getBookContent(id);
AdaptiveBookContent resolved = FlexiBookAPI.resolveBookContent(id); // 未知返回 EMPTY
Collection<ResourceLocation> ids = FlexiBookAPI.bookContentIds();

// 直接创建物品（推荐用于模板继承）
ItemStack book = FlexiBookAPI.createBookFromDefinition(ResourceLocation.parse("flexibook:demo_guide"));

// 拉模板后微调：AdaptiveBookContent 为 record，override 必须 return 新实例
ItemStack tweaked = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.parse("flexibook:demo_guide"),
    c -> c.withThemeId(ResourceLocation.fromNamespaceAndPath("mymod", "dark"))
);
```

`resolveBookContent` 未知时返回 `AdaptiveBookContent.EMPTY`（标题为空书，元素为空）。

### 14.5 模板继承（其他模组用法）

**原则**：物品 ID / 命名空间由使用方决定；内容与主题 id 来自提供 JSON 的 namespace（资源包或其它 mod jar）。二者可以不同。

#### 14.5.1 分发方式
| 方式 | 做法 | 适用 |
|------|------|------|
| 资源包 | 玩家把导出的 pack 丢进 `resourcepacks/` 并启用 | 内容作者独立分发、热重载 |
| 打进 mod jar | `src/main/resources/assets/<ns>/flexibook/{books,themes}/...` | 随 mod 安装，无额外资源包 |

客户端 F3+T / 资源重载后，资源定义会覆盖同 id 的代码注册。

#### 14.5.2 依赖
在使用方 `build.gradle` / mods.toml 中声明对 `flexibook` 的依赖（版本范围按发布约定）。公共侧可调用 `io.github.PhantomDaze.flexibook.api.FlexiBookAPI`；主题/书籍注册表在客户端资源重载后对数据书生效。

#### 14.5.3 推荐：从定义创建 FlexiBook 物品
```java
// 内容 id = 资源路径 assets/myguide/flexibook/books/guide.json → myguide:guide
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
// stack 物品为 flexibook:flexi_book，DataComponent 已写入内容
```

#### 14.5.4 自己的物品 + 拉取内容
若要用自定义 Item（仍需能打开 AdaptiveBookScreen / 携带同一 DataComponent）：
```java
ItemStack stack = new ItemStack(MyItems.FIELD_GUIDE.get());
AdaptiveBookContent c = FlexiBookAPI.resolveBookContent(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), c);
```
未知 id 时 `resolveBookContent` 返回 `EMPTY`；`getBookContent` 返回 `Optional.empty()`。

#### 14.5.5 Override 微调模板
`AdaptiveBookContent` 不可变。使用 `Function` 重载并 **返回** 修改后的实例：
```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"),
    c -> c
        .withThemeId(ResourceLocation.fromNamespaceAndPath("myguide", "main"))
        .withDefaultFont(ResourceLocation.parse("flexibook:default"))
);
```
也可用 `AdaptiveBookContent.ofElements(...)` / `ofMarkup(...)` 整本重建。`override == null` 或返回 `null` 时保持模板原文。

#### 14.5.6 主题引用
书 JSON 的 `"theme": "myguide:main"` 对应 `assets/myguide/flexibook/themes/main.json`。  
主题内 `book_texture` / `widgets_texture` 指向 `assets/<texNs>/textures/...`（可与内容 namespace相同）。编辑器「导出资源包」会把纹理改写进同一 namespace，便于自包含分发。

### 14.6 内置示例
本模组在资源内提供与 `ExampleBooks.demoGuide()` 内容对齐的示例：
```
assets/flexibook/flexibook/books/demo_guide.json
```
其 id 为 `flexibook:demo_guide`，可用于验证加载与布局一致性。

---

## 15. 完整示例

### 15.1 模组内发放任务手册 + 注册动作

```java
public final class MyBooks {
    public static final String ACTION_CLAIM = "mymod:claim_intro_reward";

    public static void init() {
        FlexiBookAPI.registerCommandAction(ACTION_CLAIM, ctx ->
            ctx.message("mymod.book.intro.claimed_hint")
        );
    }

    public static ItemStack introBook() {
        return FlexiBookAPI.builder("intro")
            .titleKey("mymod.book.intro.title")
            .h1("mymod.book.intro.h1")
            .p("mymod.book.intro.p1")
            .p("mymod.book.intro.p2")
            .bullet("mymod.book.intro.step1")
            .bullet("mymod.book.intro.step2")
            .divider()
            .link("mymod.book.intro.claim", FlexiBookAPI.commandAction(ACTION_CLAIM))
            .link("mymod.book.intro.wiki", FlexiBookAPI.urlAction("https://example.com/wiki"))
            .buildItem();
    }
}
```

### 15.2 与 Demo Guide 对齐的 Builder 风格

参考本模组 `io.github.PhantomDaze.flexibook.data.ExampleBooks#demoGuide`：

```java
new AdaptiveBookBuilder("demo_guide")
    .titleKey("flexibook.book.demo.title")
    .h1("flexibook.book.demo.h1")
    .p("flexibook.book.demo.intro")
    .divider()
    .h2("flexibook.book.demo.features")
    .bullet("flexibook.book.demo.feature.adaptive")
    // ...
    .link("flexibook.book.demo.link_hi", FlexiBookAPI.commandAction("flexibook:say_hi"))
    .link("flexibook.book.demo.link_web", FlexiBookAPI.urlAction("https://neoforged.net/"))
    .pRaw("[p][b]flexibook.book.demo.bold_sample[/b] ...[/p]")
    .buildItem();
```

### 15.3 运行时替换已有书的内容

```java
ItemStack stack = player.getMainHandItem();
if (stack.is(ModItems.FLEXI_BOOK.get())) {
    AdaptiveBookContent next = AdaptiveBookContent.ofMarkup(
        new TranslatableText("mymod.book.updated_title"),
        "[p]mymod.book.updated_body[/p]"
    );
    stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), next);
}
```

客户端若正打开旧 Screen，需关闭重开才会看到新布局。

### 15.4 仅解析标签（工具/测试）

```java
List<BookElement> elements = TagParser.parse("[h1]a.b[/h1][p]c.d[/p]");
// 不依赖 Screen；可在单元测试中断言 size/类型
```

本仓库测试：`src/test/java/.../TagParserTest`、`AdaptiveBookBuilderTest`、`ContentModelTest`。

---

## 16. 限制与常见问题

### 15.1 v1 明确不做

- 讲台放置与同步阅读  
- 劫持或兼容原版 `written_book` / 成书与书与笔编辑  
- 书内所见即所得编辑器  
- 表格、自动 TOC、Web GUI  
- 把任意命令字符串当 `cmd` 执行  

### 15.2 FAQ

**Q: 为什么书里显示翻译键本身？**  
A: 当前语言 json 缺键，或键名含空格/不含 `.` 被当成字面量。检查 lang 与键格式。

**Q: 点链接没反应 / 提示 unknown action？**  
A: 未 `registerCommandAction`，或 id 与标签/Builder 不一致（区分大小写，建议全程 `modid:name`）。

**Q: 专用服务器崩溃 NoClassDefFoundError: Screen？**  
A: 公共代码直接引用了 `client.*`。改为只依赖 `FlexiBookAPI` / `content` / `registry`；打开界面用物品右键或客户端模块内 `ClientModEvents.openBook`。

**Q: `fromMarkup` 和前面的 `.p()` 一起用，raw 没生效？**  
A: 只要 elements 非空就走结构化路径。只用 raw 时不要混用 `h1/p/...`，或改用 `pRaw` 合并进 elements。

**Q: 图片不显示？**  
A: 确认 `src` 命名空间与路径存在于资源包；尺寸是否过大导致翻页后才看见。

**Q: 自定义字体不生效？**  
A: 检查 id 是否与 `assets/.../font/*.json` 一致；书级用 `defaultFont`，行内用 `[font]` / `StyleFlags.withFont`。混排时确认局部 font 没有被空 `withFont(null)` 清掉。布局缓存含 **resolved** font key，改完组件后需重开书。省略 `font` 时走内置 `flexibook:default`，不是 `minecraft:default`。

**Q: 为什么和原版聊天/书籍看起来不一样？**  
A: 书内默认故意使用模组自带 unihex（`flexibook:default`），与编辑器同一 ZIP，便于中西文与分页对齐。需要原版外观时请**显式**设书级或行内 font（如 `minecraft:default` / `minecraft:uniform`），并保证客户端资源存在。

**Q: 编辑器里自定义 font id 预览不对？**  
A: 编辑器默认只内置解析/绘制 `flexibook:default` unihex；其它 id 会保留在 JSON 中，预览回退到同一 unihex（见 §12.4）。游戏内仍按客户端真实 font 资源渲染。

**Q: 能否在服务端打开书？**  
A: 不能。Screen 仅客户端。服务端只负责给 `ItemStack` 或改组件。

**Q: 许可证？**  
A: GPLv3。依赖并分发时请遵守 GPL 义务。

**Q: JDK？**  
A: 构建 FlexiBook 与兼容模组时建议 **JDK 21**（与 NeoForge 1.21.1 工具链一致）。

---

## 17. 包与符号索引

| 包 | 用途 | 对依赖方 |
|----|------|----------|
| `api` | `FlexiBookAPI`, `AdaptiveBookBuilder` | **稳定入口** |
| `content` | 组件载荷、元素、链接、样式；`FlexiBookFonts` / `AdaptiveBookContent.resolvedFont()` | 读写内容时使用 |
| `registry` | `ModItems`, `ModDataComponents`, `ModCreativeTabs` | 物品/组件引用 |
| `parse` | `TagParser` | 可选；Builder 已封装 |
| `data` | `ExampleBooks` | 参考实现，非 API 承诺 |
| `layout` | 布局引擎 | 内部；勿强依赖 |
| `client.theme` | `BookTheme` / `BookThemes` / `BookThemeRegistry` / `ImageFit` | 主题数据可公共注册；ReloadListener / Screen **仅客户端** |
| `client.*` | Screen / LinkHandler / TextureSizeCache | **仅客户端** |
| `item` | `FlexiBookItem` | 一般无需继承 |

| 常量 | 值 |
|------|-----|
| Mod id | `flexibook` |
| Item id | `flexibook:flexi_book` |
| Component id | `flexibook:adaptive_book_content` |
| 示例动作 | `flexibook:say_hi` |
| 默认主题 | `flexibook:default` |
| 默认字体 | `flexibook:default`（`FlexiBookFonts.DEFAULT`，unihex Unifont） |
| 等比图主题 | `flexibook:contain` |

---

## 修订

| 版本 | 说明 |
|------|------|
| 1.0.0 | 与模组 v1 首发 API 对齐的调用说明 |
| 1.0.0+font | 书级 `defaultFont` + 行内/标题 `[font]` / `StyleFlags.font` |
| 1.0.0+theme | 可注册 `BookTheme`；内容字段 `theme`；内置 default/contain 样例 + JSON 资源 |
| 1.0.0+unihex | 内置 `flexibook:default`（unihex Unifont）；`resolvedFont()`；缺省不再 `minecraft:default`；编辑器同 ZIP |

问题与扩展建议可对照 [`DESIGN.md`](./DESIGN.md)；讲台等能力以后续版本 changelog 为准。
