# FlexiBook Electron Editor

Real-time theme + book content editor (Electron / browser). Uses a pure TypeScript port of `BookLayoutEngine` and the **same Unihex ZIP** as in-game `flexibook:default`.

## Status

Working desktop / web app:

- Live layout + preview (book background, images, STRETCH/CONTAIN)
- Theme editing (layout metrics, colors, imageFit, custom textures)
- Structured content editing (elements, multi-span style/color/font/link, box children)
- Tabs: **Theme | Content | Lang | Fonts**
- Workspace modes: **预览** (book canvas) | **内容编辑** (large translation value editor, no book preview)
- Multi-language tables (add `ja_jp` etc.), real-time `localStorage` cache — language switch does not drop drafts
- Manual **重新布局** in preview mode
- Visual GUI scale 1/2/3/4 (draw only, no reflow)
- Search highlight; page nav is simple editor chrome (in-game: vanilla buttons)
- Native open/save for theme/content JSON (Electron); browser file input / download fallback
- **Export resource packs**: top bar **导出完整资源包…**; each tab has its own partial export (theme / textures / content / lang / fonts only)
- **Import resource packs**: top bar **导入资源包…** (ZIP or pack folder) → theme / contents / lang / fonts / textures
- **Workspace draft**: IndexedDB autosave of unexported edits (theme/content/lang/textures/fonts); survives reload; **清草稿** to reset
- Custom TTF/OTF import (approx FontFace preview) + pack embed
- Default font: `flexibook:default` unihex (parity path)

## Quick Start

```bash
cd editor
npm install

# Full Electron + Vite HMR
npm run dev:electron

# Browser only → http://localhost:5173/
npm run dev

# Production package
npm run build
```

## Export resource pack (6-part layout)

### Full pack (top bar only)

Top bar **导出完整资源包…** → folder/zip `{ns}_pack/`:

```
{ns}_pack/
  pack.mcmeta
  HOW_TO_USE.txt
  assets/{ns}/
    lang/{en_us,zh_cn,…}.json          # vanilla lang
    textures/gui/book.png
    font/…                             # optional custom TTF/OTF + json
    flexibook/
      themes/{themeId}.json
      contents/{bookId}.json           # body (title + elements, translation keys)
      books/{bookId}.json              # index: { "content", "theme", "font"? }
```

### Partial packs (per panel)

Each tab exports **only its section** (+ `pack.mcmeta` / `HOW_TO_USE.txt`). Same namespace packs stack in-game.

| Entry | Includes | Typical folder suffix |
|-------|----------|------------------------|
| Theme → **导出主题资源包…** | `flexibook/themes/*.json` | `{ns}_theme_pack` |
| Theme → **导出纹理资源包…** | `textures/gui/book.png` | `{ns}_tex_pack` |
| Content → **导出内容资源包…** | `flexibook/contents` + `books` index | `{ns}_content_pack` |
| Lang → **导出翻译资源包…** | `lang/*.json` | `{ns}_lang_pack` |
| Fonts → **导出字体资源包…** | `font/*.json` + ttf/otf | `{ns}_fonts_pack` |

| Part | Path | Role |
|------|------|------|
| **books** | `flexibook/books/` | Index only (`content` + `theme` + optional `font`) |
| **contents** | `flexibook/contents/` | Body (`AdaptiveBookContent`) |
| **themes** | `flexibook/themes/` | Layout / colors / texture RLs |
| **lang** | `lang/` | Vanilla language files |
| **fonts** | `font/` | Vanilla ttf providers |
| **textures** | `textures/` | Vanilla textures |

In-game after enabling the pack (F3+T):

```java
FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"));
```

Or command (mod):

```text
/flexibook give <bookId> [player]
# e.g. /flexibook give fieldnotes:journal
```

Automated: `npm run test:pack` (includes theme-only / lang-only asserts); `npm run test:import`; Java `PackExportFixtureCodecTest`.

### Import resource pack

Top bar **导入资源包…**:

| Host | Source |
|------|--------|
| Electron | ZIP file **or** pack root directory (`pack.mcmeta` / `assets/`) |
| Browser | ZIP only |

Loads whatever is present (partial packs OK): theme JSON, contents + books index, `lang/*.json`, `font/*.json`+ttf/otf, `textures/gui/book*.png`. Export form defaults update to imported `namespace` / themeId / bookId. Lang merges into current tables; fonts merge by id.

### Workspace draft (persistence)

Unexported work is autosaved (~0.9s debounce) to **IndexedDB** (`flexibook-editor` / `workspace` / `draft`):

- theme, content, lang tables
- custom texture PNG bytes
- custom font TTF/OTF bytes + provider knobs
- UI: active lang, left tab, pack id defaults

Lang tables are **also** mirrored to `localStorage` (lightweight). Status bar shows last draft save time. **清草稿** clears IDB (reload then uses demo + asset lang seeds).

## Architecture

- `src/shared/types.ts` — mirrors Java models
- `src/shared/layout.ts` — layout engine port + cache
- `src/shared/UnihexFont.ts` — unihex measure/draw
- `src/shared/FontRouter.ts` + `BrowserFont.ts` — custom TTF routing (approx)
- `src/shared/TagParser.ts` — markup → elements (shared; Content UI is structured-only)
- `src/shared/markupHighlight.ts` + `MarkupEditor.tsx` — syntax highlight (translation workspace)
- `src/shared/packExport.ts` — 6-part pack builder
- `src/shared/packImport.ts` — zip/dir → editor pieces
- `src/shared/workspaceDraft.ts` — IndexedDB draft autosave
- `src/shared/modJson.ts` — theme/content wire JSON
- `src/shared/langTables.ts` — multi-lang tables + localStorage

Renderer: `App.tsx`, `ThemePanel`, `ContentPanel`, `LangPanel`, `FontPanel`, `PreviewCanvas`, `PackExportForm`.

Assets: `assets/{lang,themes,contents,books,textures}/`, font ZIP synced from mod via `scripts/sync-font-assets.mjs`.

## Key behaviors

- Layout parity with mod when using unihex + same theme/content/lang
- Custom TTF preview is **approximate** (browser FontFace ≠ MC advance)
- Unregistered external font ids → banner + unihex fallback
- Content panel is structured elements only (no raw markup textarea)
- Translation authoring: workspace **内容编辑** + Lang tab
- Pack export rewrites theme texture paths and custom font ids into pack namespace

## Commands

```bash
npm run dev:electron    # sync fonts + main + Vite + Electron
npm run dev             # sync fonts + Vite only
npm run build           # sync fonts + tsc + vite + electron-builder
npm run build:main      # main/preload only
npm run test:font
npm run test:parser
npm run test:layout
npm run test:pack
npm run test:import
npm run test:markup
npm run test            # all of the above
```

## Roadmap

- [x] Native open/save UI
- [x] Multi-span + TagParser port
- [x] Lang tables + content-edit workspace
- [x] Custom TTF import/export
- [x] 6-part pack (books index / contents / themes / lang / fonts / textures)
- [x] Pack import (zip / directory)
- [x] Workspace draft persistence (IndexedDB)
- [ ] Better search highlight drawing
- [ ] True TTF metrics parity with MC

## Verification

- [x] Theme/layout live reflow; colors redraw-only
- [x] imageFit visual only
- [x] Lang switch + cached tables
- [x] Pack export path set includes contents + books index
- [x] Default unihex measure/draw for title/body/page label

See [`../docs/API.md`](../docs/API.md) §12 / §14, [`../docs/todo.md`](../docs/todo.md), [`../docs/UNIFIED_FONT_PLAN.md`](../docs/UNIFIED_FONT_PLAN.md).
