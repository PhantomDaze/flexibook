# FlexiBook Electron Editor

Real-time theme + book content editor (Electron / browser). Pure TypeScript port of `BookLayoutEngine` and the **same Unihex ZIP** as in-game `flexibook:default`.

[中文说明](./README.zh-CN.md)

## Status

Working desktop / web app:

- Live layout + preview (book background, images, STRETCH/CONTAIN)
- Theme editing (layout metrics, colors, imageFit, custom book panel + **item icon** textures)
- Structured content editing (elements, multi-span style/color/font/link, box children)
- Tabs: **Theme | Content | Lang | Fonts**
- Workspace modes: **Preview** (book canvas) | **Content edit** (large translation editor, no book preview)
- **Editor UI locale** (top bar **UI**): Chinese / English — chrome only; separate from book preview language. Stored in `localStorage` (`flexibook.editor.uiLocale.v1`)
- Multi-language tables (add `ja_jp`, etc.), live cache — switching language does not drop drafts
- Manual **Relayout** in preview mode
- Visual GUI scale 1/2/3/4 (draw only, no reflow)
- Search highlight; page nav is simple editor chrome (in-game: vanilla buttons)
- Native open/save for theme/content JSON (Electron); browser file input / download fallback
- **Export resource packs**: top bar **Export full pack…**; Theme / Content / Lang / Fonts each expose partial export (theme JSON + textures under the **Theme** tab)
- **Import resource packs**: top bar **Import pack…** (ZIP or pack folder) → theme / contents / lang / fonts / textures
- **Workspace draft**: IndexedDB autosave of unexported work; **Clear draft** to reset
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

Top bar **Export full pack…** → folder/zip `{ns}_pack/`:

```
{ns}_pack/
  pack.mcmeta
  HOW_TO_USE.txt
  assets/{ns}/
    lang/{en_us,zh_cn,…}.json          # vanilla lang
    textures/gui/book.png
    textures/item/flexi_book.png       # optional item icon (+ flexibook override)
    font/…                             # optional custom TTF/OTF + json
    flexibook/
      themes/{themeId}.json
      contents/{bookId}.json           # body (title + elements, translation keys)
      books/{bookId}.json              # index: { "content", "theme", "font"? }
```

### Partial packs (per panel)

Each tab exports **only its section** (+ `pack.mcmeta` / `HOW_TO_USE.txt`). Same-namespace packs stack in-game.

| Entry (UI) | Includes | Typical folder suffix |
|------------|----------|------------------------|
| Theme → **Textures / background** → **Export textures pack…** | `textures/gui/book.png` + `textures/item/flexi_book.png` (also writes `assets/flexibook/textures/item/flexi_book.png`) | `{ns}_tex_pack` |
| Theme → **Export theme** → **Export theme pack…** | `flexibook/themes/*.json` | `{ns}_theme_pack` |
| Theme → **Export theme** → **Export theme JSON** | single theme file (not a pack) | — |
| Content → sticky → **Export content pack…** | `flexibook/contents` + `books` index | `{ns}_content_pack` |
| Lang → **Translation tables** → **Export lang pack…** | `lang/*.json` | `{ns}_lang_pack` |
| Fonts → **Custom fonts** → **Export fonts pack…** | `font/*.json` + ttf/otf | `{ns}_fonts_pack` |

Theme sticky bar: open/save/reset only. Texture vs theme pack buttons live in their own Theme sections.

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

Or mod command:

```text
/flexibook give <bookId> [player]
# e.g. /flexibook give fieldnotes:journal
```

Automated: `npm run test:pack` (theme-only / lang-only / textures asserts); `npm run test:import`; Java `PackExportFixtureCodecTest`.

### Import resource pack

Top bar **Import pack…**:

| Host | Source |
|------|--------|
| Electron | ZIP **or** pack root directory (`pack.mcmeta` / `assets/`) |
| Browser | ZIP only |

Loads whatever is present (partial packs OK): theme JSON, contents + books index, `lang/*.json`, `font/*.json`+ttf/otf, `textures/gui/book.png`, `textures/item/flexi_book.png`. Export form defaults update to imported `namespace` / themeId / bookId. Lang merges into current tables; fonts merge by id.

### Workspace draft (persistence)

Unexported work is autosaved (~0.9s debounce) to **IndexedDB** (`flexibook-editor` / `workspace` / `draft`):

- theme, content, lang tables
- custom book + item texture PNG bytes
- custom font TTF/OTF bytes + provider knobs
- UI: active book lang, left tab, pack id defaults

Lang tables are **also** mirrored to `localStorage`. Status bar shows last draft save time. **Clear draft** wipes IDB (reload then uses demo + asset lang seeds).

## Architecture

- `src/shared/types.ts` — mirrors Java models
- `src/shared/layout.ts` — layout engine port + cache
- `src/shared/UnihexFont.ts` — unihex measure/draw
- `src/shared/FontRouter.ts` + `BrowserFont.ts` — custom TTF routing (approx)
- `src/shared/TagParser.ts` — markup → elements
- `src/shared/markupHighlight.ts` + `MarkupEditor.tsx` — syntax highlight (translation workspace)
- `src/shared/packExport.ts` — 6-part pack builder
- `src/shared/packImport.ts` — zip/dir → editor pieces
- `src/shared/workspaceDraft.ts` — IndexedDB draft autosave
- `src/shared/modJson.ts` — theme/content wire JSON
- `src/shared/langTables.ts` — multi-lang tables + localStorage
- `src/shared/uiI18n.ts` — editor chrome i18n (`zh_cn` / `en_us`)

Renderer: `App.tsx`, `ThemePanel`, `ContentPanel`, `LangPanel`, `FontPanel`, `PreviewCanvas`, `PackExportForm`, `UiI18n`.

Assets: `assets/{lang,themes,contents,books,textures}/`, font ZIP synced from the mod via `scripts/sync-font-assets.mjs`.

## Key behaviors

- Layout parity with the mod when using unihex + same theme/content/lang
- Custom TTF preview is **approximate** (browser FontFace ≠ MC advance)
- Unregistered external font ids → banner + unihex fallback
- Content panel is structured elements only (no raw markup textarea)
- Translation authoring: workspace **Content edit** + Lang tab
- Pack export rewrites theme texture paths and custom font ids into the pack namespace
- Item icon export overrides `flexibook:item/flexi_book` so creative/hotbar icons change after enabling the pack

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
- [x] Editor UI locale (zh/en)
- [x] Custom item icon texture
- [ ] Better search highlight drawing
- [ ] True TTF metrics parity with MC

## Verification

- [x] Theme/layout live reflow; colors redraw-only
- [x] imageFit visual only
- [x] Lang switch + cached tables
- [x] Pack export path set includes contents + books index
- [x] Default unihex measure/draw for title/body/page label

See [`../docs/EDITOR_PACK_GUIDE.md`](../docs/EDITOR_PACK_GUIDE.md) (export/import & in-game give), [`../docs/API.md`](../docs/API.md) §12 / §14, and [`../README.md`](../README.md).
