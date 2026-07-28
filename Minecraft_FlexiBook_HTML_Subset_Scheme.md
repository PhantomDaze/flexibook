# Minecraft 自适应布局成书系统 - HTML 子集方案（FlexiBook）

**作者**：基于 Grok 设计（针对 NeoForge 1.21.1）  
**日期**：2026-07-28  
**目标**：在 Minecraft 中实现**文本布局自适应**的成书系统，同时支持类 HTML 富文本和完整的国际化。

---

## 一、项目概述

原版 Minecraft 成书（Written Book）存在严重限制：
- 固定 14 行 × 114 像素宽度
- 不支持动态缩放、分栏、自动换行优化
- 内容为死文本，难以支持多语言
- 富文本能力极弱

**FlexiBook** 方案通过**自定义 DataComponent + 客户端布局引擎 + 自定义 Screen**，实现：
- **自适应布局**（自动缩放、自动分栏、自动分页）
- **HTML 子集内容格式**（轻量富文本，类似 Markdown/HTML 标签）
- **完美国际化支持**（内容存翻译键，打开时动态解析 + 重新布局）
- 保持“书”的沉浸感（翻书动画、左右页）

该方案适合模组（尤其是像 PasterDreamAPI 这样的内容构建库）使用。

---

## 二、设计目标

- **自适应**：根据内容量、语言（中英文字符宽度差异）、GUI 缩放自动调整字号、分栏、页数。
- **富文本**：支持标题、段落、粗体、颜色、图片、链接、列表等类 HTML 元素。
- **国际化**：内容完全使用翻译键，切换语言后书本自动重新排版。
- **性能友好**：布局结果缓存，首次计算后几乎无开销。
- **可扩展**：易于添加新标签和样式。
- **兼容性**：优先使用 NeoForge 1.21.1 现代组件系统。

---

## 三、HTML 子集内容格式

我们不使用真实 HTML，而是定义一个**轻量标签子集**（类似简易 Markdown + 自定义标签），便于解析和存储。

### 推荐存储方式

**方式 A（推荐）：结构化 Component（类型安全）**
使用自定义 `AdaptiveBookContent` DataComponent，内部包含 `List<BookElement>`。

**方式 B（简单友好）：带标签的字符串**
内容存为类似 HTML 的字符串，便于 Builder 快速构建：

```text
[h1]欢迎冒险者[/h1]
[p]这是第一段，支持[b]粗体[/b]和[color=#FF0000]红色文字[/color]。[/p]

[img src="textures/gui/icon.png" width="48" height="48" /]

[h2]章节二[/h2]
[p]点击[link cmd="/tp @s 0 64 0" color="#00FF00"]这里传送[/link]到起点。[/p]

[br]
[divider]

[bullet]列表项一[/bullet]
[bullet]列表项二[/bullet]
```

### 支持的标签列表（HTML 子集）

| 标签                  | 说明                     | 属性示例                          | 渲染效果                  |
|-----------------------|--------------------------|-----------------------------------|---------------------------|
| `[h1]` / `[/h1]`     | 一级标题                 | -                                 | 大字号 + 加粗             |
| `[h2]` / `[/h2]`     | 二级标题                 | -                                 | 中等字号 + 加粗           |
| `[p]` / `[/p]`       | 段落                     | -                                 | 普通段落 + 自动换行       |
| `[b]` / `[/b]`       | 粗体                     | -                                 | 加粗                      |
| `[i]` / `[/i]`       | 斜体                     | -                                 | 斜体                      |
| `[u]` / `[/u]`       | 下划线                   | -                                 | 下划线                    |
| `[color=#RRGGBB]`    | 颜色                     | `color="#FFAA00"`                 | 指定颜色                  |
| `[br]`               | 换行                     | -                                 | 强制换行                  |
| `[divider]`          | 分隔线                   | -                                 | 水平线                    |
| `[img ... /]`        | 图片                     | `src`, `width`, `height`          | 图片占位（支持绕排）      |
| `[link cmd="..." ]`  | 链接（执行命令）         | `cmd`, `url`, `color`             | 可点击文字                |
| `[link url="..."]`   | 链接（打开网页）         | `url`                             | 可点击文字                |
| `[bullet]`           | 列表项                   | -                                 | 带项目符号                |
| `[div class="..."]`  | 容器（高级）             | `class`（可选）                   | 用于控制对齐/间距         |

**扩展建议**：
- 支持嵌套标签（[b][color=...]混合样式[/color][/b]）
- 支持转义 `\[` 和 `\]`
- 图片路径支持资源包路径

---

## 四、数据存储（NeoForge 1.21.1）

推荐使用自定义 **DataComponent**：

```java
public record AdaptiveBookContent(
    Filterable<Component> title,
    List<BookElement> elements   // 解析后的结构化元素
) implements DataComponent {
    // Codec 定义...
}
```

或者先用轻量字符串存储，打开书时再解析成结构化数据。

物品示例：
```java
ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
book.set(YourDataComponents.ADAPTIVE_BOOK_CONTENT, content);
```

---

## 五、客户端布局引擎（核心自适应逻辑）

**类名建议**：`BookLayoutEngine`

### 主要流程

1. **解析内容** + **国际化解析**
   - 遍历元素，调用 `Component.translatable(key, args).getVisualOrderText()`
   - 根据当前 `Language` 动态生成 `FormattedText`

2. **自适应参数计算**
   - 起始：`scale = 1.0f`，`columns = 1`，`maxWidth = 114`，`maxHeight = 160`
   - 尝试布局
   - 如果页数过多或过于拥挤：
     - 先降低 `scale`（1.0 → 0.9 → 0.8 → 0.7，最低 0.6）
     - 再切换 `columns = 2`（双栏，减小单栏宽度 + gutter）
     - 最后接受更多页数

3. **真实测量布局**
   - 使用 `font.width(text)` 和 `font.split(FormattedText, maxWidth)`
   - 累加高度，超过 `maxHeightPerPage` 则新页
   - 记录每个可绘制对象的 `x, y, scale, text, style, clickAction`

4. **缓存**
   - Key = `contentHash + currentLanguage + guiScale + version`
   - 使用 `Map` 或 `LoadingCache`

### 伪代码核心

```java
public List<RenderedPage> layout(AdaptiveBookContent content, Font font) {
    List<FormattedText> resolved = resolveWithTranslations(content);
    float scale = 1.0f;
    int columns = 1;
    
    while (true) {
        List<RenderedPage> pages = tryLayout(resolved, font, scale, columns);
        if (pages.size() < 60 && !isOvercrowded(pages)) {
            return pages;
        }
        
        if (scale > 0.6f) scale -= 0.1f;
        else if (columns < 2) { columns = 2; scale = 0.9f; }
        else return pages; // 最终接受
    }
}
```

**中文优化**：检测到中文时自动降低 5-10% scale。

---

## 六、自定义渲染界面（AdaptiveBookScreen）

继承 `Screen`，实现：

- 背景使用书本纹理（可自定义资源包）
- 支持**左右双页**翻书效果（PoseStack 平移 + 裁剪）
- 每页绘制 `RenderedPage` 中的元素：
  - 文本：`guiGraphics.drawString(...)` + `scale`
  - 图片：`blit` 对应纹理
  - 可点击区域：记录 `clickAreas`，鼠标点击时处理
- 翻页按钮、页码显示
- 悬停 tooltip（用于 link 描述或图片说明）
- 可选：搜索栏（实时重新 layout 并高亮）

打开方式：
```java
Minecraft.getInstance().setScreen(new AdaptiveBookScreen(bookStack));
```

物品右键事件中拦截并打开此 Screen，而不是原版书。

---

## 七、国际化支持（与之前方案无缝衔接）

- 内容**永远只存翻译键**（如 `"pasterdream.book.welcome"`）
- 打开 Screen 时才调用 `translatable` 解析
- 切换语言后重新打开书 → 自动重新 `resolve + layout`
- 支持带参数的翻译：`Component.translatable(key, "Steve", 42)`
- 资源包正常放置 `assets/<modid>/lang/zh_cn.json`

---

## 八、Builder API（方便集成）

```java
AdaptiveBookBuilder builder = new AdaptiveBookBuilder("my_guide_book");
builder.title("pasterdream.book.my_guide.title")
       .addHeading("pasterdream.book.chapter1")
       .addParagraph("pasterdream.book.content1", "玩家名")
       .addImage("textures/gui/icon.png", 48, 48)
       .addLink("pasterdream.book.click_here", "cmd", "/say Hello");

ItemStack book = builder.build();
```

可直接暴露在你的 `ItemAPI` 或独立模块中。

---

## 九、实现路线图（建议优先级）

1. **阶段一**：定义 DataComponent + 简单标签解析器 + 单栏 layout（用 `font.split`）
2. **阶段二**：自适应缩放逻辑 + 分栏支持
3. **阶段三**：`AdaptiveBookScreen` 基础渲染 + 翻页 + 点击事件
4. **阶段四**：图片、链接、tooltip、缓存机制
5. **阶段五**：Builder API + 与 PasterDreamAPI 集成
6. **阶段六**：资源包支持、搜索、讲台兼容、fallback 到原版书

---

## 十、优缺点分析

**优点**：
- 真正打破原版 14 行限制
- 富文本能力接近轻量 HTML
- 国际化支持优秀
- 布局智能（自动适配中英文、内容量）
- 可扩展性强
- 保持书本沉浸感

**缺点**：
- 需要自定义 Screen（不能直接用原版书界面）
- 小字号可能略模糊（可用自定义字体缓解）
- 首次打开有轻微计算（缓存后消失）
- 讲台原版兼容需要额外处理
- 比纯数据驱动方案（如 Patchouli）代码量稍大

**与 Patchouli 对比**：
- Patchouli：更适合固定模板、多媒体指南书（图片+合成+多方块）
- FlexiBook：更适合**超长文本 + 强自适应 + 动态内容**的场景

两者可以共存！

---

## 十一、进阶扩展方向

- 支持简单表格（列宽自动计算）
- 自动生成目录（TOC）
- 动态变量（`{player}`、`{world_time}` 等）
- 实时重新布局（支持书本内编辑预览）
- 资源包定义主题（字体、间距、颜色方案）
- 与 WebGUI 结合：点击链接打开真正的 HTML 界面

---

**总结**

这个 **HTML 子集 + 自适应布局引擎 + 自定义 Screen** 的方案，是目前在纯 Minecraft 生态内实现“智能排版书本”最平衡、可落地的方式。

它既保留了书本的仪式感，又解决了原版最大的痛点，并完美支持你之前提到的国际化需求。

---

需要我接下来提供以下任意内容的详细实现吗？

- 完整 `BookLayoutEngine.java` 代码框架
- `AdaptiveBookScreen.java` 渲染代码
- DataComponent + Codec 定义
- 轻量标签解析器实现
- 如何集成到 PasterDreamAPI
- 示例资源包结构 + lang 文件

请直接告诉我下一步要什么！