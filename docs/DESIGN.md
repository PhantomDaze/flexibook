# Minecraft 自适应布局成书系统 - HTML 子集方案（FlexiBook）

**状态**：核心设计已实现（2026-07-28 设计稿；实现验证基于当前代码）  
**目标**：自适应布局 + 轻量富文本（HTML 子集标签） + 完美国际化 + 保持书本沉浸感。

---

## 概述与现状
原版成书固定尺寸、弱富文本、死文本难国际化。FlexiBook 通过自定义 `AdaptiveBookContent` DataComponent + 客户端 `BookLayoutEngine` + `AdaptiveBookScreen` 实现：

- **自适应**：自动缩放（降至 ~0.6）、可选双栏、按内容/语言（CJK 检测自动微降 scale）/GUI 缩放重排。
- **富文本**：结构化 `BookElement`（heading/paragraph/bullet/image/divider/br/box）或 `raw` 标记字符串（TagParser 解析）。
- **国际化**：内容只存翻译键；打开时解析 + 重新 layout；切换语言自动重排。
- **缓存**：layout 结果按 (contentHash + lang + guiScale + **resolvedFont** + themeRev + query) 缓存。
- **默认字体**：书内文字解析为 `flexibook:default`（内置 unihex Unifont），不静默落到 `minecraft:default`；见 [UNIFIED_FONT_PLAN.md](./UNIFIED_FONT_PLAN.md) / [API.md](./API.md) §12。

兼容 NeoForge 1.21+ 现代组件。

---

## HTML 子集标签（TagParser 支持）
解析器支持嵌套、转义 `\[ \]`、未知标签宽容跳过。

**块级**：`[h1]` `[h2]` `[p]` `[bullet]` `[br]` `[divider]` `[img src="..." width=".." height=".." tooltip=".."/]` `[div class="..."]`（用于 box 容器）。

**内联**：`[b]` `[i]` `[u]` `[color=#RRGGBB]`（或 `color="..."`） `[font font="ns:path"]` `[link cmd="..." color="..."]` `[link url="..."]`。

**存储双形态**（`AdaptiveBookContent`）：
- 推荐：`elements: [...]`（结构化，类型安全）。
- 备选：`raw: "[h1]...[/h1]..."`（Builder 快速构建用）。
优先 elements；同时支持 `title`（TranslatableText）、可选 `font`（书级，codec 字段名）、`theme`。省略 `font` 时运行时 `resolvedFont()` → `flexibook:default`。

示例结构见 `assets/.../flexibook/books/demo_guide.json`（模板显式 `"font": "flexibook:default"`）。

---

## 数据与 API
- `AdaptiveBookContent`：title + (elements | raw) + optional font/theme。使用 `CODEC` / `STREAM_CODEC`。
- `FlexiBookFonts.DEFAULT` + `content.resolvedFont()`：显式 `defaultFont` 或回退内置 unihex id。
- 物品：`stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), content)`
- Builder：`AdaptiveBookBuilder`（链式 `.h1(key).p(key).image(...).link(...)` 等；`.defaultFont` 可选）。
- 公开 API（`FlexiBookAPI`）：`builder(...)`、`createBook(content)`、`registerBookContent`、`getBookContent`、`createBookFromDefinition(id[, override])`、`registerCommandAction` 等。
- 其他 mod 可引用已注册定义创建自己的物品（模板继承）。

---

## 布局引擎（BookLayoutEngine）
核心流程：
1. 解析元素 + 翻译提供者解析为可视文本；`bookFont = content.resolvedFont()`（写入 style / cache key）。
2. 自适应尝试：从 scale=1.0 / columns=1 开始；若页数 >=60 或 overcrowded（>40 页或大 scale 下过密），逐步降 scale（步 0.1，最低 0.6）→ 切双栏（scale ~0.85）→ 接受。
3. CJK 检测：中文占比 >30% 起始 scale 降至 0.92。
4. 真实测量：游戏内 `McTextMeasurer`（`Style.withFont`）；编辑器 `UnihexFont`（同一 ZIP 位图 + MC advance 公式）+ 换行、列推进、页推进。
5. 渲染元素：TextLine / ImageBlock / DividerLine（带 x/y/scale/link/highlight）。
6. 搜索：实时高亮匹配并影响布局缓存 key。

双栏：gutter + 列宽计算；跨页/跨列正确推进。

---

## 界面（AdaptiveBookScreen）
- 背景用主题 book 纹理（可资源包覆盖）。
- 左右双页视觉 + 翻页动画基础。
- 绘制 RenderedPage 元素（drawString + scale、blit 图片、记录点击区）；正文 style 已带解析字体。
- 翻页按钮；**页码与书标题**使用 `resolvedFont()` 的 styled `Component` 居中（与编辑器 `PreviewCanvas` 对齐）。
- 搜索框：输入触发重新 layout + 高亮。
- Tooltip（link 描述或图片 tooltipKey 翻译）；搜索框等普通 UI 仍用 MC UI 字体。
- 安全点击：LinkHandler 处理 cmd/url。
- 右键物品打开此 Screen（不劫持原版 written_book）。

---

## 主题与字体资源
- `BookTheme`：布局数字 + 颜色 + `bookTexture` / `widgetsTexture` + `imageFit`（stretch/contain） + revision（触发重排）。
- 数据化主题：`assets/<ns>/flexibook/themes/*.json`（snake_case 字段）。
- 内置主题示例：`flexibook:default`（stretch）、`flexibook:contain`。
- **内置书字体**：`assets/flexibook/font/default.json` + `unifont_all-17.0.05.zip`（unihex + space）；id `flexibook:default`。更新：`scripts/update-unifont.sh`（普通 build 不联网）。
- 编辑器 `predev`/`prebuild` 经 `sync-font-assets.mjs` 复制同一字体树；可本地覆盖预览纹理；导出时需重写为资源路径。
- 资源重载时清 layout 缓存。

---

## 实现要点与路线图回顾
已落地：DataComponent + 解析器 + 单/双栏自适应 layout + Screen 渲染 + Builder + i18n + 缓存 + 搜索 + 数据化主题/书籍 + 编辑器实时预览 + API + **统一默认字体（unihex）**。

**历史阶段建议**（供参考，已基本按序完成核心）：
1. DataComponent + 标签解析 + 单栏 → 完成。
2. 自适应 + 双栏 → 完成。
3. Screen 基础 + 翻页 + 点击 → 完成。
4. 图片/链接/tooltip/缓存 → 完成。
5. Builder + 集成 → 完成。
6. 资源包/搜索/讲台 → 搜索完成；讲台兼容 v1 标记为 future（API 有占位接口）。
7. 内置 `flexibook:default` unihex + 编辑器同一资源度量 → 完成（见 UNIFIED_FONT_PLAN）。

---

## 优缺点（简要）
**优点**：打破 14 行限制；富文本接近轻量 HTML；i18n 优秀；布局智能（中英/内容量自适）；书内默认字体自包含（中西文同一位图源）；可扩展；沉浸感。

**缺点**：需自定义 Screen；小字号可能模糊（仍可显式挂自定义 font）；首次 layout 有计算（缓存后消失）；讲台兼容需额外工作；编辑器默认只预览内置 unihex（其它 font id 需客户端资源）。

**与 Patchouli**：Patchouli 适合固定模板/合成/多媒体指南；FlexiBook 适合超长文本 + 强自适应 + 动态内容。两者可共存。

---

## 进阶扩展（仍开放）
- 简单表格（列宽自动）。
- 自动目录（TOC）。
- 动态变量（`{player}` 等）。
- 讲台兼容（v2）。
- Web 链接打开真实界面。
- 编辑器可选加载外部资源包字体（当前回退预览 unihex）。

---

**当前重点**：编辑器内容打磨与“导出完整资源包”流程（见 [todo.md](./todo.md)、[PLAN-data-driven-books-and-editor-pack-export.md](./PLAN-data-driven-books-and-editor-pack-export.md)），打通「编辑 → 导出 → 资源包/其他 mod」闭环。核心阅读/布局/数据化/默认字体已就绪。
