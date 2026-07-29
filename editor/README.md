# FlexiBook Electron Editor

Real-time theme editor + custom book content editor using **Electron** (Chromium + Node.js).

The editor runs **without Minecraft**, but produces near-identical layout and rendering by using a pure TypeScript port of the mod's `BookLayoutEngine` plus the **same Unihex Unifont ZIP** as the in-game `flexibook:default` font.

## Status

Working desktop app:
- Live layout + preview (real book background, real images, STRETCH/CONTAIN)
- Theme editing (all layout metrics, colors, imageFit)
- Structured content editing (add/remove/reorder elements, spans, bold/italic/underline, translate toggle)
- Tabbed UI (Theme | Content)
- Visual GUI scale (1/2/3/4) — does **not** affect reflow
- Search (affects layout + highlight)
- Language switch (en_us / zh_cn)
- Page-turn buttons use the real MC `book_widgets.png` texture
- Export theme/content JSON (browser download)
- **Export full resource pack** (Phase D): pack.mcmeta + textures + flexibook/themes/*.json (+ optional books) + HOW_TO_USE.txt. Electron: direct folder write via native dialog; browser fallback to .zip download. Custom textures included when chosen. UI in ThemePanel bottom section.
- Native file dialogs available in main process (UI load/save pending)
- Default font: `flexibook:default` via bundled `unifont_all-17.0.05.zip` (MC unihex metrics; no browser system-font fallback for book text)

Build artifacts (example):
- `release/FlexiBook Editor-0.1.0.AppImage`
- `release/flexibook-editor_0.1.0_amd64.snap`
- `dist/` (Vite bundle) + `dist-electron/`

## Quick Start

```bash
cd editor
npm install

# Recommended: full Electron dev with Vite HMR for renderer
npm run dev:electron

# Web-only dev server (http://localhost:5173/)
npm run dev

# Full production build + packaging (electron-builder)
npm run build
```

Run packaged AppImage / Snap directly, or use `npm run dev:electron` during development.

## Architecture

Pure TS port (no Java at runtime):

- `src/shared/types.ts` — mirrors Java records (RL, StyleFlags, InlineSpan, BookElement, AdaptiveBookContent, BookTheme, RenderedPage, etc.)
- `src/shared/layout.ts` — `layout()` replicates the engine:
  - `looksMostlyCjk`
  - `tryLayout` with binary-search word wrap
  - `placeWrappedText`, `placeInlineSpans`
  - Adaptive guard loop (scale down → columns=2)
  - `LayoutCache` keyed by content hash + lang + theme.revision + search
- Measurement / font (parity critical):
  - `src/shared/UnihexFont.ts` — loads the same ZIP as the mod (`assets/flexibook/font/unifont_all-17.0.05.zip`), ports MC 1.21.1 unihex packing + advance (`floor((right-left+1)/2)+1`, bold +0.5, width `ceil`)
  - `scripts/sync-font-assets.mjs` — `predev`/`prebuild` copies font JSON/ZIP/license from mod resources into `public/assets` + `assets`
  - `scripts/test-unihex.mjs` — `npm run test:font` (glyph bounds / advances)
  - `McAtlasTextMeasurer.ts` — **legacy** (ascii.png + browser Unifont); not used on the default preview path
- `src/shared/providers.ts` + `JsonTranslationProvider.ts`
  - Fixed-reference measurement: always 9px base; GUI scale is visual only
- `src/shared/ImageFitMath.ts` — port of Java `ImageFitMath.contain()`
- `src/shared/modJson.ts` — wire format conversion for theme/content JSON round-tripping with the mod

Renderer:
- `App.tsx` — state, Unihex load gate, layout effect, asset loading, keyboard nav, tabs, export wiring
- `ThemePanel.tsx` — numeric spinners, hex colors, imageFit, revision bump on layout keys
- `ContentPanel.tsx` — structured element list with add/remove/reorder/edit + export
- `PreviewCanvas.tsx` — draws using real textures + UnihexFont:
  - `book.png` as background (via `theme.bookTexWidth/Height`)
  - Content images via async cache + `imgVersion`
  - `contain()` centering when `imageFit === 'contain'`
  - Widgets texture split for prev/next buttons
  - Title + page label + body all measured/drawn with Unihex (no `fillText` system font)

Assets (served/copied at dev/build):
- `assets/lang/*.json` (and public copy)
- `assets/textures/gui/book.png`, `book_widgets.png`, `icon.png`
- `assets/themes/*.json` (samples: default, contain)
- `assets/books/*.json` (demo content)
- `public/assets/flexibook/font/*` — synced from mod (`default.json`, `unifont_all-17.0.05.zip`, license)
- `assets/fonts/README.md` explains purpose (not shipped to mod)

## Key Behaviors

- Layout parity: same algorithm, same constants, same cache invalidation
- Visual scale only: 1/2/3/4 changes draw size, never reflow
- Elements win: if `elements` present, `rawMarkup` is ignored
- Theme revision: changing a layout metric bumps `revision` → cache miss → relayout
- Image fit:
  - `stretch` — fill the box (may distort)
  - `contain` — aspect-preserving, centered (letterbox/pillarbox)
- Export produces JSON + full resource packs compatible with mod's data-driven themes (`flexibook/themes/`) and book content loading

## Export / Round-trip

- Export Theme → `*.json` (matches `BookTheme` shape for resource pack)
- Export Content → `*.json` (matches wire form)
- **Export full Resource Pack** (Phase D): Button in Theme panel. Produces:
  ```
  {ns}_pack/
    pack.mcmeta
    HOW_TO_USE.txt
    assets/{ns}/
      textures/gui/{book,book_widgets}.png
      flexibook/themes/{themeId}.json   (texture refs rewritten to ns:...)
      flexibook/books/{bookId}.json     (optional, theme=ns:themeId)
  ```
  - In Electron: native "select folder" → direct write (secure path checks in main).
  - Browser: downloads `{ns}_pack.zip` (unzip into resourcepacks).
  - Custom textures (if picked) are embedded; defaults fetched from editor assets.
  - Load via resource pack or drop files into a mod jar under correct ns.
- Load in-game: place under `assets/<ns>/flexibook/...` or use full pack; reference with `FlexiBookAPI.createBookFromDefinition(ResourceLocation.fromNamespaceAndPath(ns, bookId))`
- See `src/shared/modJson.ts` and `src/shared/packExport.ts` for shapes
- Automated: `npm run test:pack` (path set / rewrite / zip / real `demo_guide`); Java `PackExportFixtureCodecTest` parses the same fixtures with game codecs

For in-game usage, load equivalent JSON into the mod's data-driven theme registration or construct content via `AdaptiveBookBuilder`.

## Differences vs In-Game

- Default font path uses the **same unihex ZIP** as `flexibook:default` — advances/bold/page label should match the game when content/theme/lang match
- Explicit non-`flexibook:default` font ids are kept in data but **preview still draws with FlexiBook unihex** (no arbitrary resource-pack fonts in the editor)
- No tooltip hover rendering for images/links (visual only)
- No link click handling (preview is read-only visually)
- No full native file open/save UI yet (exports are download-based; main process has dialog ipc)

## Development Notes

- Keep `editor/` completely outside the mod JAR (guarded: `jar { from sourceSets.main.output }` in build.gradle; editor is never added to main sourceSet)
- If layout math / constants change in Java (`BookLayoutEngine`, `BookTheme`), mirror in `src/shared/layout.ts` and types
- To compare a page:
  1. Use identical theme JSON
  2. Same language
  3. Same content (Builder or exported JSON)
  4. Visual scale = 1 in editor
- Main process: `src/main/main.ts` + `preload.ts` (Electron + ipc for dialogs)
- Renderer hot-reloads via Vite when using `dev:electron`

## Commands

```bash
npm run dev:electron    # predev sync fonts + build:main + Vite + Electron
npm run dev             # predev sync fonts + Vite dev server only
npm run build           # prebuild sync fonts + tsc + vite + electron-builder
npm run build:main      # compile main/preload only (tsconfig.node.json)
npm run test:font       # Unihex parse/advance self-check against bundled ZIP
npm run preview         # preview built web bundle
npm run electron        # run electron against current dist (post-build)
```

## Roadmap (high value next)

- [x] Native open/save dialogs in main process (ipc implemented)
- [x] Theme + content export wired
- [ ] Full UI integration for native open/save (load theme/content from disk)
- [ ] Per-span editing (italic/underline/color/link in content panel)
- [ ] Raw markup textarea + lightweight TagParser port
- [ ] Better search highlight drawing
- [ ] Basic deterministic tests with a fake measurer
- [ ] Further parity: exact MC text layout edge cases

## Verification Checklist

- [x] Layout metric change → immediate reflow
- [x] Color change → redraw only
- [x] imageFit toggle → visual change, no reflow
- [x] Add/remove/reorder elements → live re-pagination
- [x] Lang switch → translations + re-layout
- [x] Search input → layout + highlight flags
- [x] Visual scale changes size only
- [x] Widgets texture → textured buttons (no arrows)
- [x] Book background → real book.png
- [x] Image renders (contain or stretch per theme)
- [x] Theme/content export produces valid JSON matching mod wire format
- [x] FlexiBook unihex (`flexibook:default`) measurement + draw active
- [x] Title + page label use same resolved font metrics as mod Screen

See [`../docs/UNIFIED_FONT_PLAN.md`](../docs/UNIFIED_FONT_PLAN.md) and [`../docs/todo.md`](../docs/todo.md).
