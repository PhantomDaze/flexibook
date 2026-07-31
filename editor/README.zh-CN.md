# FlexiBook Electron 编辑器

实时主题 + 书内容编辑器（Electron / 浏览器）。TypeScript 移植 `BookLayoutEngine`，并与游戏内 `flexibook:default` 使用**同一份 Unihex ZIP**。

[English](./README.md)

## 状态

可用的桌面 / Web 应用：

- 实时布局 + 预览（书背景、图片、STRETCH/CONTAIN）
- 主题编辑（布局参数、颜色、imageFit、自定义书页背景 + **物品图标**）
- 结构化内容编辑（元素、多 span 样式/颜色/字体/链接、box 子元素）
- 分页签：**Theme | Content | Lang | Fonts**
- 工作区模式：**预览**（书 canvas）| **内容编辑**（大翻译编辑器，不显示书预览）
- **编辑器界面语言**（顶栏 **UI**）：中文 / English — 只改 chrome，与书预览语言分离。持久化在 `localStorage`（`flexibook.editor.uiLocale.v1`）
- 多语言表（可加 `ja_jp` 等），实时缓存 — 切换语言不丢草稿
- 预览模式可手动 **重新布局**
- 视觉 GUI 缩放 1/2/3/4（仅绘制，不重排）
- 搜索高亮；翻页为编辑器简易 chrome（游戏内为原版按钮）
- 主题/内容 JSON 原生打开/保存（Electron）；浏览器降级为 file input / 下载
- **导出资源包**：顶栏 **导出完整资源包…**；Theme / Content / Lang / Fonts 各自有分项导出（主题 JSON + 纹理在 **Theme** 页）
- **导入资源包**：顶栏 **导入资源包…**（ZIP 或目录）→ 主题 / 正文 / 翻译 / 字体 / 纹理
- **工作区草稿**：未导出内容 IndexedDB 自动保存；**清草稿** 重置
- 自定义 TTF/OTF 导入（浏览器 FontFace 近似预览）+ 打进包
- 默认字体：`flexibook:default` unihex（对齐路径）

## 快速开始

```bash
cd editor
npm install

# Electron + Vite HMR
npm run dev:electron

# 仅浏览器 → http://localhost:5173/
npm run dev

# 生产打包
npm run build
```

## 导出资源包（六段布局）

### 完整包（仅顶栏）

顶栏 **导出完整资源包…** → 文件夹/zip `{ns}_pack/`：

```
{ns}_pack/
  pack.mcmeta
  HOW_TO_USE.txt
  assets/{ns}/
    lang/{en_us,zh_cn,…}.json
    textures/gui/book.png
    textures/item/flexi_book.png       # 可选物品图标（并覆盖 flexibook）
    font/…
    flexibook/
      themes/{themeId}.json
      contents/{bookId}.json
      books/{bookId}.json              # 索引：content + theme + 可选 font
```

### 分项包（各面板）

每个分页签只导出**本段**（+ `pack.mcmeta` / `HOW_TO_USE.txt`）。同 namespace 的包在游戏内可叠加。

| 入口（UI） | 内容 | 典型目录后缀 |
|------------|------|----------------|
| Theme → **纹理 / 背景** → **导出纹理资源包…** | `textures/gui/book.png` + `textures/item/flexi_book.png`（并写 `assets/flexibook/textures/item/flexi_book.png`） | `{ns}_tex_pack` |
| Theme → **导出主题** → **导出主题资源包…** | `flexibook/themes/*.json` | `{ns}_theme_pack` |
| Theme → **导出主题** → **导出主题 JSON** | 单文件 theme（非资源包） | — |
| Content → sticky → **导出内容资源包…** | `flexibook/contents` + `books` 索引 | `{ns}_content_pack` |
| Lang → **翻译表** → **导出翻译资源包…** | `lang/*.json` | `{ns}_lang_pack` |
| Fonts → **自定义字体** → **导出字体资源包…** | `font/*.json` + ttf/otf | `{ns}_fonts_pack` |

Theme 底栏 sticky 只保留打开/保存/重置；纹理与主题包按钮在各自分区。

| 段 | 路径 | 作用 |
|----|------|------|
| **books** | `flexibook/books/` | 仅索引 |
| **contents** | `flexibook/contents/` | 正文 |
| **themes** | `flexibook/themes/` | 布局 / 颜色 / 纹理 RL |
| **lang** | `lang/` | 原版语言文件 |
| **fonts** | `font/` | 原版 ttf provider |
| **textures** | `textures/` | 原版纹理 |

游戏内启用包后（F3+T）：

```java
FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"));
```

或模组命令：

```text
/flexibook give <bookId> [player]
# 例：/flexibook give fieldnotes:journal
```

自动化：`npm run test:pack`；`npm run test:import`；Java `PackExportFixtureCodecTest`。

### 导入资源包

顶栏 **导入资源包…**：

| 宿主 | 来源 |
|------|------|
| Electron | ZIP **或** 资源包根目录（`pack.mcmeta` / `assets/`） |
| 浏览器 | 仅 ZIP |

有什么加载什么（分项包也可）：主题、正文+索引、`lang`、`font`、书背景、物品图标。导出会默认填入导入的 namespace / themeId / bookId。翻译表合并；字体按 id 合并。

### 工作区草稿

未导出内容约 0.9s 防抖写入 **IndexedDB**（`flexibook-editor` / `workspace` / `draft`）：

- 主题、正文、翻译表
- 自定义书背景 + 物品图标 PNG
- 自定义字体 TTF/OTF + provider 参数
- UI：当前书语言、左侧 tab、导出 id 默认值

翻译表另镜像到 `localStorage`。状态栏显示上次草稿时间。**清草稿** 清空 IDB（刷新后用 demo + 内置语言表）。

## 架构

- `src/shared/types.ts` — 对齐 Java 模型
- `src/shared/layout.ts` — 布局引擎移植 + 缓存
- `src/shared/UnihexFont.ts` — unihex 测宽/绘制
- `src/shared/FontRouter.ts` + `BrowserFont.ts` — 自定义 TTF 路由（近似）
- `src/shared/TagParser.ts` — 标签 → elements
- `src/shared/packExport.ts` / `packImport.ts` — 六段导出/导入
- `src/shared/workspaceDraft.ts` — IndexedDB 草稿
- `src/shared/uiI18n.ts` — 编辑器 chrome 中英文字典

渲染：`App.tsx`、`ThemePanel`、`ContentPanel`、`LangPanel`、`FontPanel`、`PreviewCanvas`、`PackExportForm`、`UiI18n`。

资源：`assets/{lang,themes,contents,books,textures}/`，字体 ZIP 由 `scripts/sync-font-assets.mjs` 从模组同步。

## 关键行为

- unihex + 相同主题/正文/语言时与游戏布局对齐
- 自定义 TTF 预览为**近似**（FontFace ≠ MC advance）
- 未导入的外部 font id → 横幅 + 回退 unihex
- Content 面板仅结构化元素（无 raw 大文本区）
- 物品图标导出会覆盖 `flexibook:item/flexi_book`，启用包后创造栏/手上图标会变

## 命令

```bash
npm run dev:electron
npm run dev
npm run build
npm run test            # font/parser/layout/pack/import/markup
```

## 路线图

- [x] 原生打开/保存、多 span、Lang 表、TTF、六段包、导入、草稿
- [x] 编辑器 UI 中英、自定义物品图标
- [ ] 更好的搜索高亮绘制
- [ ] 与 MC 真 TTF 度量对齐

详见 [`../docs/EDITOR_PACK_GUIDE.zh-CN.md`](../docs/EDITOR_PACK_GUIDE.zh-CN.md)（导入导出与游戏内拿书）、[`../docs/API.zh-CN.md`](../docs/API.zh-CN.md) / [`../docs/API.md`](../docs/API.md) §12 / §14、[`../README.zh-CN.md`](../README.zh-CN.md)。
