# FlexiBook 调用文档

面向其他模组与数据包作者的 **API / 数据 / 标签** 说明。  
实现版本：`flexibook` **1.0.0** · Minecraft **1.21.1** · NeoForge **21.1.x**。

设计背景与阶段规划见仓库根目录 [`Minecraft_FlexiBook_HTML_Subset_Scheme.md`](../Minecraft_FlexiBook_HTML_Subset_Scheme.md)。  
快速上手见 [`README.md`](../README.md)。

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
13. [布局与主题（只读说明）](#13-布局与主题只读说明)
14. [完整示例](#14-完整示例)
15. [限制与常见问题](#15-限制与常见问题)
16. [包与符号索引](#16-包与符号索引)

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
// AdaptiveBookScreen / LinkHandler / BookTheme 仅客户端
```

---

## 2. 核心概念

```
ItemStack(flexibook:flexi_book)
    └── DataComponent flexibook:adaptive_book_content
            └── AdaptiveBookContent
                    ├── title: TranslatableText          // 打开时再翻译
                    ├── defaultFont: Optional<RL>        // 整本书默认字体
                    └── payload（二选一，elements 优先）
                          ├── rawMarkup: String          // [h1]... 标签源
                          └── elements: List<BookElement>
                                └── InlineSpan.style.font / Heading.font 可覆盖
```

| 概念 | 说明 |
|------|------|
| **双形态存储** | 可存结构化 `BookElement` 列表，或 raw 标签字符串；两者都在时 **elements 胜出** |
| **翻译延迟** | 标题与正文键在 **打开书 / 布局** 时按当前客户端语言解析，不在写入时固化译文 |
| **字体分层** | 书级 `defaultFont` → 被 span/标题上的 `font` 覆盖；同一本书可混用多种字体 |
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
| `defaultFont(ResourceLocation)` / `defaultFont(String id)` | **整本书**默认字体（资源包 `assets/<ns>/font/<path>.json`） |
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
    Optional<ResourceLocation> defaultFont   // codec 字段名 "font"
)
```

| 工厂 / 成员 | 说明 |
|-------------|------|
| `EMPTY` | 空书标题键 + 空 elements |
| `ofElements(title, list)` / `ofElements(title, list, font)` | 只存结构 |
| `ofMarkup(title, markup)` / `ofMarkup(title, markup, font)` | 只存 raw |
| `withDefaultFont(ResourceLocation)` | 复制并设置书级字体 |
| `defaultFont()` | 整书默认字体；被 span/标题 font 覆盖 |
| `resolveElements()` | 布局/渲染前统一得到 `List<BookElement>` |
| `isEmpty()` | 解析后无元素且 raw 空白 |
| `CODEC` / `STREAM_CODEC` | 持久化 + 网络同步 |

**序列化约定（与 codec 一致）**：

```jsonc
{
  "title": { "key": "mymod.book.title", "args": [] },
  "font": "mymod:fancy",   // 可选，整书默认
  // 二选一：
  "raw": "[h1]mymod.book.ch1[/h1]\n[p]mymod.book.p1[/p]",
  // 或
  "elements": [
    { "heading": { /* 见下 dispatch 形态 */ } }
  ]
}
```

`BookElement` 使用 `Codec.STRING.dispatch`（`typeId` → `data` 字段）。手写 JSON 时请以实际 codec 为准，**优先用 Builder / Java API 写入**，避免手写 elements 树出错。

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

原版常见 id：

| id | 说明 |
|----|------|
| `minecraft:default` | 默认（不写 font 时的行为） |
| `minecraft:alt` | 附魔台风格 |
| `minecraft:uniform` | Unicode 字体 |
| `minecraft:illageralt` | 灾厄村民风格 |

也可使用你自己模组/资源包定义的字体。

### 12.1 优先级

```
行内 StyleFlags.font / Heading.font
        ↓ 若无
书级 AdaptiveBookContent.defaultFont
        ↓ 若无
原版默认字体（Style 不带 font）
```

同一本书内可以：

- 只设 `defaultFont` → 全书统一  
- 只在部分 span / 标题设 font → 混排  
- 两者都设 → 局部覆盖书级默认  

布局测宽与绘制都走 `Style.withFont(...)`，换行宽度按真实字体 advance 计算。

### 12.2 Builder 示例

```java
ResourceLocation fancy = ResourceLocation.fromNamespaceAndPath("mymod", "fancy");
ResourceLocation mono  = ResourceLocation.withDefaultNamespace("uniform");

ItemStack book = FlexiBookAPI.builder("styled_guide")
    .titleKey("mymod.book.title")
    .defaultFont(fancy)                          // 整书默认
    .h1("mymod.book.h1")                         // 用 fancy
    .h2("mymod.book.code_title", mono)            // 标题单独 mono
    .p("mymod.book.body")                        // fancy
    .font("mymod.book.quote", mono)              // 一段 mono
    .pRaw("[p]普通 [font font=\"minecraft:alt\"]符文[/font] 混排[/p]")
    .buildItem();
```

### 12.3 仅改书级字体

```java
AdaptiveBookContent c = AdaptiveBookContent.ofMarkup(title, markup)
    .withDefaultFont(ResourceLocation.withDefaultNamespace("alt"));
// 或
AdaptiveBookContent.ofElements(title, elements, Optional.of(fontId));
```

### 12.4 注意

- 字体必须在 **打开书的客户端** 资源里存在；缺失时原版通常回退，但可能显示异常。  
- 自定义 TTF/位图字体请按原版 font json 规范打包，FlexiBook 不负责加载管线。  
- 服务端只存 `ResourceLocation` 字符串；实际字形仅客户端解析。

---

## 13. 布局与主题（只读说明）

v1 **没有**稳定的「第三方替换 Layout 算法」SPI；下列便于理解表现与资源包换皮。

### 13.1 自适应策略（摘要）

1. 解析 elements → 当前语言纯文本 → 测宽分页  
2. 初始 `scale=1.0`、`columns=1`  
3. 页数过多或过挤 → 降低 scale（约至 0.6）→ 再尝试 `columns=2`  
4. CJK 占比高时起始 scale 略降  
5. `LayoutCache`：内容哈希 + 语言 + GUI scale + theme revision；登出清空  

默认内容区约（主题）：宽 160、高 154 逻辑像素（见 `BookTheme.baseParams()`）。

### 13.2 资源包可替换纹理

| 路径 | 用途 |
|------|------|
| `assets/flexibook/textures/gui/book.png` | 书页背景（默认逻辑 192×192） |
| `assets/flexibook/textures/gui/book_widgets.png` | 按钮等 |
| `assets/flexibook/textures/gui/icon.png` | 示例/默认图 |
| `assets/flexibook/textures/item/flexi_book.png` | 物品图标 |

`BookTheme.DEFAULT` 指向上述 GUI 纹理；间距/颜色目前在代码常量中，**资源包不能改数字常量**，只能换图。

### 13.3 图片元素纹理

`BookElement.Image.src` 必须是客户端能加载的纹理 `ResourceLocation`（通常 `textures/...png`）。缺失时渲染可能空白或粉黑，请自备资源。

---

## 14. 完整示例

### 14.1 模组内发放任务手册 + 注册动作

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

### 14.2 与 Demo Guide 对齐的 Builder 风格

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

### 14.3 运行时替换已有书的内容

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

### 14.4 仅解析标签（工具/测试）

```java
List<BookElement> elements = TagParser.parse("[h1]a.b[/h1][p]c.d[/p]");
// 不依赖 Screen；可在单元测试中断言 size/类型
```

本仓库测试：`src/test/java/.../TagParserTest`、`AdaptiveBookBuilderTest`、`ContentModelTest`。

---

## 15. 限制与常见问题

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
A: 检查 id 是否与 `assets/.../font/*.json` 一致；书级用 `defaultFont`，行内用 `[font]` / `StyleFlags.withFont`。混排时确认局部 font 没有被空 `withFont(null)` 清掉。布局缓存含 font key，改完组件后需重开书。

**Q: 能否在服务端打开书？**  
A: 不能。Screen 仅客户端。服务端只负责给 `ItemStack` 或改组件。

**Q: 许可证？**  
A: GPLv3。依赖并分发时请遵守 GPL 义务。

**Q: JDK？**  
A: 构建 FlexiBook 与兼容模组时建议 **JDK 21**（与 NeoForge 1.21.1 工具链一致）。

---

## 16. 包与符号索引

| 包 | 用途 | 对依赖方 |
|----|------|----------|
| `api` | `FlexiBookAPI`, `AdaptiveBookBuilder` | **稳定入口** |
| `content` | 组件载荷、元素、链接、样式 | 读写内容时使用 |
| `registry` | `ModItems`, `ModDataComponents`, `ModCreativeTabs` | 物品/组件引用 |
| `parse` | `TagParser` | 可选；Builder 已封装 |
| `data` | `ExampleBooks` | 参考实现，非 API 承诺 |
| `layout` | 布局引擎 | 内部；勿强依赖 |
| `client.*` | Screen / LinkHandler / Theme | **仅客户端** |
| `item` | `FlexiBookItem` | 一般无需继承 |

| 常量 | 值 |
|------|-----|
| Mod id | `flexibook` |
| Item id | `flexibook:flexi_book` |
| Component id | `flexibook:adaptive_book_content` |
| 示例动作 | `flexibook:say_hi` |

---

## 修订

| 版本 | 说明 |
|------|------|
| 1.0.0 | 与模组 v1 首发 API 对齐的调用说明 |
| 1.0.0+font | 书级 `defaultFont` + 行内/标题 `[font]` / `StyleFlags.font` |

问题与扩展建议可对照根目录设计文档；讲台等能力以后续版本 changelog 为准。
