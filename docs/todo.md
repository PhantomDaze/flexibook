# FlexiBook Standalone Editors — todo

Real-time theme + book content editor (Electron, no MC required). Uses TS port of layout engine for parity.

## Goals (kept)
- Live preview of BookTheme + AdaptiveBookContent using identical layout math (TS port).
- Editor standalone under `editor/`, never in mod JAR.
- Modern UI (React + Canvas); resources copied from mod assets.
- Book text metrics: same `flexibook:default` unihex ZIP as the mod (not browser / minecraft:default).

## Completed (aggressively summarized)

- [x] Phase 0: write todo, baseline `./gradlew test`, delete old Java Swing editor (src/editor/, Gradle tasks), scaffold `editor/` (package.json, tsconfig, vite, electron).
- [x] Phase 1 (core): shared types (`types.ts`, providers); full `layout.ts` port of `BookLayoutEngine` (CJK detection, binary-search wrap, placeParagraph/Heading/Bullet/Image/Divider/Box, adaptive scale 1.0→0.6 + columns=2, LayoutCache, empty fallback). Matches Java.
- [x] Phase 2/3/8: Electron shell (main/preload, React App + split Theme/Preview, live onChange → relayout → repaint, in-memory state, search+lang+scale visual-only, CJK logic).
- [x] Phase 4: ThemePanel full controls (all layout nums/offsets/tex sizes, colors with picker, imageFit STRETCH/CONTAIN, custom texture load/override, presets reset/contain, export JSON, revision bump on layout changes).
- [x] Phase 5 partial: ContentPanel structured editing (title, add/remove/reorder P/H/Bullet/Divider/BR/Image/Box; edit key/text/translate, bold/italic/underline toggles on first span, image props). Supports elements form.
- [x] Phase 6/7: PreviewCanvas fidelity (TextLine with styles+hl+link underline, Divider, Image with ImageFitMath.contain + real size, book.png bg per theme tex, simple page nav chrome, page label/title, search highlight, async img cache). Assets resolver + copy (lang, themes, textures, flexibook font zip).
- [x] Phase 9/10: `npm run build` + electron-builder works (AppImage/Snap/dist); mod jar clean (no editor bits); Java deterministic test (fake measurer, pagination); manual parity verified via live use + matching JSON roundtrip.
- [x] Data-driven books "Phase B": `BookContentRegistry` (register/resolve/ids), `BookContentReloadListener` (flexibook/books/*.json, bootstrap+override, cache clear), wired in ClientModEvents, FlexiBookAPI (registerBookContent, resolve, createBookFromDefinition + override), demo_guide.json, API.md §14 docs.
- [x] **统一默认字体**（[`UNIFIED_FONT_PLAN.md`](./UNIFIED_FONT_PLAN.md)）：
  - 模组 `assets/flexibook/font/{default.json,unifont_all-17.0.05.zip,LICENSE-unifont.txt}` + `scripts/update-unifont.sh`
  - `FlexiBookFonts` / `AdaptiveBookContent.resolvedFont()`；layout + Screen 标题/页码；永不静默 `minecraft:default`
  - 编辑器 `UnihexFont.ts` + `sync-font-assets.mjs` + App/Preview；`npm run test:font`；legacy OTF/ascii 移出默认路径
  - 模板 `demo_guide` 显式 `"font": "flexibook:default"`；去掉 `minecraft:alt` 演示覆盖
  - 文档：API §12、editor README、本清单

Cross-check: layout numbers, wrap points, page counts, CJK scale, imageFit (no reflow), search, lang switch, theme JSON load in-game all match. Default book font = shared unihex.

## Open / Actionable

### P1 — 编辑器（已完成）
- [x] Content polish: per-span color/font/link + multi-span；raw markup + TagParser 移植；box 子元素编辑
- [x] Native open/save dialogs UI 接线（Electron `fs:readTextFile` / `fs:writeTextFile` + 面板「打开…/保存…」；浏览器降级 file input / download）
- [x] TS layout tests（fake measurer）：`npm run test:layout`；TagParser：`npm run test:parser`
- [x] 非 `flexibook:default` 的 font id：Content 面板 + Preview 横幅 + 状态栏显式提示“外部字体不支持，已回退 unihex”

### Later
- [x] Syntax-highlighted markup editor（Content raw 区轻量高亮）
- [x] 完整资源包导出（Phase D 已落地）：编辑器可导出 pack.mcmeta + textures + themes + books + HOW_TO_USE；支持 Electron 直写文件夹或 zip。详见 PLAN 文件。
- [x] 导出自动化：`npm run test:pack` + Java `PackExportFixtureCodecTest`（导出 JSON 与游戏 CODEC 对齐）。
- [x] **翻译表编辑 + 导出 lang**：Lang 面板（全表 en_us/zh_cn）、全屏键编辑、Content 键选择器；导出 `assets/<ns>/lang/*.json`
- [x] **自定义 TTF/OTF**：Fonts 面板导入；书级/行内 font；FontRouter 预览（浏览器近似）；导出 font JSON + ttf/otf 并重写 id
- [x] **分项资源包导出**：完整包仅顶栏；Theme/Content/Lang/Fonts 各自只导出本分项（`PackParts`）
- [x] **Theme 面板导出分区**：纹理导出放「纹理 / 背景」；主题资源包/JSON 放「导出主题」；sticky 仅打开/保存/重置
- [x] **资源包导入**：ZIP / 目录 → theme/content/lang/fonts/textures；`npm run test:import`
- [x] **工作区草稿持久化**：IndexedDB autosave（未导出内容跨刷新保留）；顶栏清草稿
- [x] **书背景/插图半透明**：`AdaptiveBookScreen` blit 前 `enableBlend`（软边不再变实心）
- 游戏端手测对照编辑器 1x/2x（F3+T 后标题/粗体/中文/页码）— 自动化已绿，客户端目视可选
- 游戏端手测资源包加载 / 跨模组 API（见 PLAN 验收未勾项）；自定义 pack 用 `/flexibook give <id>` + F3+T

## Rules (keep)
- Mark [x] when done.
- Mirror layout changes: Java → promptly update `editor/src/shared/layout.ts`
- Editor/ completely separate (no Gradle inclusion, no FML).
- Prefer minimal deps.
- **字体统一计划保持独立文档** [`UNIFIED_FONT_PLAN.md`](./UNIFIED_FONT_PLAN.md)，不合并进 todo / DESIGN / PLAN；状态变更只改该文件 + 本清单勾选。

See also: [`../editor/README.md`](../editor/README.md), [`README.md`](./README.md), [`API.md`](./API.md) §12.

## Starting Point (now)
**6 段资源布局已落地**：books（索引）+ contents（正文）+ themes + lang + fonts + textures。  
`createBookFromDefinition` 经 BookDefinition 解析 content/theme。books 仅为索引，无旧格式兼容。  
导入 + 草稿持久化已落地。下一项：游戏端手测资源包加载（F3+T）与跨模组 API。
