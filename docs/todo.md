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
- [x] Phase 6/7: PreviewCanvas fidelity (TextLine with styles+hl+link underline, Divider, Image with ImageFitMath.contain + real size, book.png bg per theme tex, book_widgets.png buttons wired, page nav/label/title, search highlight, async img cache). Assets resolver + copy (lang, themes, textures, flexibook font zip).
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

### P1 — 编辑器
- Content polish: per-span color/font/link + multi-span；raw markup + TagParser 移植；box 子元素编辑
- Native open/save dialogs UI 接线
- TS layout tests（fake measurer，对齐 Java 确定性测试）
- 非 `flexibook:default` 的 font id：预览 UI 显式提示“外部字体不支持，已回退 unihex”

### Later
- Syntax-highlighted markup editor
- [x] 完整资源包导出（Phase D 已落地）：编辑器可导出 pack.mcmeta + textures + themes + books + HOW_TO_USE；支持 Electron 直写文件夹或 zip。详见 PLAN 文件。
- [x] 导出自动化：`npm run test:pack` + Java `PackExportFixtureCodecTest`（导出 JSON 与游戏 CODEC 对齐）。
- 游戏端手测对照编辑器 1x/2x（F3+T 后标题/粗体/中文/页码）— 自动化已绿，客户端目视可选
- 自定义资源包字体在编辑器内的可选加载（当前仅预览内置 unihex）

## Rules (keep)
- Mark [x] when done.
- Mirror layout changes: Java → promptly update `editor/src/shared/layout.ts`
- Editor/ completely separate (no Gradle inclusion, no FML).
- Prefer minimal deps.
- **字体统一计划保持独立文档** [`UNIFIED_FONT_PLAN.md`](./UNIFIED_FONT_PLAN.md)，不合并进 todo / DESIGN / PLAN；状态变更只改该文件 + 本清单勾选。

See also: [`../editor/README.md`](../editor/README.md), [`README.md`](./README.md), [`API.md`](./API.md) §12.

## Starting Point (now)
统一字体 **已完成**。资源包导出（Phase D）**已完成**（编辑器 + codec 自动化）。下一项优先：编辑器内容打磨 / 原生文件对话框 / **游戏端手测**资源包加载。
