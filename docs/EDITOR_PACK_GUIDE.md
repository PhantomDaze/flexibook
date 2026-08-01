# Editor pack export / import & getting books in-game

Step-by-step guide for authors: build content in the FlexiBook editor, export a Minecraft resource pack, enable it in-game, and obtain the book item.

**中文:** [`EDITOR_PACK_GUIDE.zh-CN.md`](./EDITOR_PACK_GUIDE.zh-CN.md).

Related: [editor README](../editor/README.md) · [API §14 data books](./API.md#14-data-driven-books) · [root README](../README.md).

---

## Table of contents

1. [Prerequisites](#1-prerequisites)
2. [Concepts (ids and the 6-part pack)](#2-concepts-ids-and-the-6-part-pack)
3. [Author workflow (recommended)](#3-author-workflow-recommended)
4. [Export — full pack](#4-export--full-pack)
5. [Export — partial packs](#5-export--partial-packs)
6. [Import pack into the editor](#6-import-pack-into-the-editor)
7. [In-game: enable the pack](#7-in-game-enable-the-pack)
8. [In-game: get the book](#8-in-game-get-the-book)
9. [Other ways to give books (mods / code)](#9-other-ways-to-give-books-mods--code)
10. [Checklist](#10-checklist)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

| Piece | Notes |
|-------|--------|
| FlexiBook mod | Installed on the client (and server if multiplayer needs the item) |
| Editor | `cd editor && npm install` then `npm run dev:electron` or `npm run dev` |
| Minecraft | **26.2 / 26.1.2 / 1.21.4 / 1.21.1** · NeoForge（也支持 **1.20.1** Forge，见 `pack_format`） |
| Permissions | `/flexibook` needs permission level **≥ 2** (cheats / OP) |

The editor is **not** inside the mod JAR. It only produces resource packs (and optional single JSON files).

---

## 2. Concepts (ids and the 6-part pack)

### 2.1 Three ids you type at export time

| Field | Example | Becomes |
|-------|---------|---------|
| **namespace** | `myguide` | Folder under `assets/myguide/…` |
| **themeId** | `main` | `assets/myguide/flexibook/themes/main.json` → theme id `myguide:main` |
| **bookId** | `guide` | `…/books/guide.json` + `…/contents/guide.json` → book id **`myguide:guide`** |

In-game you give the book with:

```text
/flexibook give myguide:guide
```

`namespace:bookId` is the **book definition id** (the file under `flexibook/books/`), not the theme id.

### 2.2 Six pack sections

```
{ns}_pack/
  pack.mcmeta
  HOW_TO_USE.txt
  assets/{ns}/
    lang/{en_us,zh_cn,…}.json
    textures/gui/book.png
    textures/item/flexi_book.png          # optional
    font/…                                # optional custom TTF/OTF
    flexibook/
      themes/{themeId}.json
      contents/{bookId}.json              # body (title + elements / keys)
      books/{bookId}.json                 # index only: content + theme + optional font
```

| Section | Path | Role |
|---------|------|------|
| **books** | `flexibook/books/` | Index only (`content`, `theme`, optional `font`) |
| **contents** | `flexibook/contents/` | Body text / structure |
| **themes** | `flexibook/themes/` | Layout, colors, `book_texture` RL |
| **lang** | `lang/` | Vanilla language JSON |
| **fonts** | `font/` | Vanilla ttf providers + files |
| **textures** | `textures/` | Book panel + optional item icon |

**Item icon:** the item is always `flexibook:flexi_book`. Changing the hotbar/creative look means overriding  
`assets/flexibook/textures/item/flexi_book.png` (the editor does this when you export a custom item PNG).

**Hardcoded relative paths** (namespace varies):  
`textures/gui/book.png`, `textures/item/flexi_book.png`. See editor notes if you need custom RLs — pack export rewrites book texture to `{ns}:textures/gui/book.png`.

---

## 3. Author workflow (recommended)

1. **Theme** tab — metrics, colors, image fit; optional local **book background** and **item icon**.
2. **Content** tab — structured elements (title, headings, paragraphs, images, links…). Prefer **translation keys** (text with `.` and no spaces), not long literals.
3. **Lang** tab — add languages (`en_us`, `zh_cn`, `ja_jp`, …); fill keys. Top-bar language dropdown switches **preview** language (live cache).
4. **Fonts** tab (optional) — import TTF/OTF; assign as book `defaultFont` or span/heading font.
5. Preview (top bar **Preview**) — check pagination; **Relayout** if needed. Scale 1–4 is visual only.
6. **Export full pack** (top bar) — see [§4](#4-export--full-pack).
7. Copy pack into Minecraft `resourcepacks/`, enable, **F3+T**, then `/flexibook give …` — see [§7](#7-in-game-enable-the-pack)–[§8](#8-in-game-get-the-book).

**Drafts:** the editor autosaves theme/content/lang/textures/fonts to IndexedDB. **Clear draft** only wipes that cache, not exported packs.

**UI language vs book language:** top-bar **UI** (中文 / English) changes chrome only. The language dropdown next to Preview/Content edit changes **book** translations.

---

## 4. Export — full pack

### 4.1 Where

Top bar → **Export full pack…** (primary button).

### 4.2 Form fields

| Field | Required | Notes |
|-------|----------|--------|
| `namespace` | yes | `[a-z0-9_.-]+` only |
| `themeId` | yes (default `main`) | Theme file name / id path |
| `bookId` | yes (default `guide`) | Book definition + content file name |
| `pack_format` | default `34` | **1.21.1** → **34**; **1.21.4** → **46**; **26.1.2** → **84**; **26.2** → **88** (resource major); **1.20.1** → **15**. 26.x also accepts `min_format`/`max_format` in `pack.mcmeta`. |

### 4.3 What gets written

Everything in [§2.2](#22-six-pack-sections) that applies:

- Theme JSON with `book_texture` rewritten to `{namespace}:textures/gui/book.png`
- Book background PNG (custom upload or editor default)
- Item icon if you set one (also writes `assets/flexibook/textures/item/flexi_book.png`)
- Contents + books index (index points at content + theme + optional font)
- All lang tables
- Imported custom fonts (ids rewritten into the pack namespace when applicable)

### 4.4 Output location

| Host | Behavior |
|------|----------|
| **Electron** | Choose a parent folder → writes `{namespace}_pack/` (or a partial suffix for partial modes) |
| **Browser** | Downloads a **ZIP** of the same tree |

Folder/zip name uses a suffix from included parts; full pack is typically `{namespace}_pack`.

### 4.5 After export

Open `HOW_TO_USE.txt` inside the pack for a short reminder. Then follow [§7](#7-in-game-enable-the-pack).

---

## 5. Export — partial packs

Same-namespace partial packs **stack** in Minecraft (enable multiple packs). Use when you only changed one area.

| UI entry | Includes | Typical folder name |
|----------|----------|---------------------|
| Theme → **Textures / background** → **Export textures pack…** | `textures/gui/book.png` + item icon paths | `{ns}_tex_pack` |
| Theme → **Export theme** → **Export theme pack…** | `flexibook/themes/*.json` only | `{ns}_theme_pack` |
| Theme → **Export theme** → **Export theme JSON** | Single theme file (**not** a pack) | — |
| Content sticky → **Export content pack…** | `flexibook/contents` + `books` index | `{ns}_content_pack` |
| Lang → **Export lang pack…** | `lang/*.json` | `{ns}_lang_pack` |
| Fonts → **Export fonts pack…** | `font/*.json` + ttf/otf | `{ns}_fonts_pack` |

Each partial pack still includes `pack.mcmeta` + `HOW_TO_USE.txt`.

**Theme sticky bar** only has Open / Save / Reset — pack buttons sit in their sections.

To **read** a book in-game you need at least:

- `books` + `contents` (body + index)
- a **theme** (from the same or another pack/mod)
- **lang** entries for your keys (or you will see raw keys)
- **textures** if you rely on a custom book background (else missing/pink or default path failures)

---

## 6. Import pack into the editor

Top bar → **Import pack…**

| Host | Sources |
|------|---------|
| Electron | **ZIP** or **pack root folder** (`pack.mcmeta` / `assets/`) |
| Browser | **ZIP only** |

### Behavior

- Loads whatever is present (partial packs OK).
- Theme, content, lang, fonts, book PNG, item PNG.
- Export form defaults update to imported `namespace` / `themeId` / `bookId`.
- Lang tables **merge** into the current tables.
- Fonts **merge** by id.
- Textures replace the corresponding custom slots when found.

### Tips

- Import a textures-only pack to continue art work without wiping content.
- After import, check **Theme** / **Content** / **Lang** tabs and preview before re-exporting.

---

## 7. In-game: enable the pack

1. Copy the exported folder or ZIP into the world’s / instance’s **`resourcepacks`** directory  
   (dev: often `run/resourcepacks/` next to the NeoForge run client).
2. Minecraft → **Options → Resource Packs** → move the pack to the **selected** (right) column.
3. Enter the world (or stay in menu if you only need load).
4. Press **F3+T** to reload resources after any pack change.

Without the pack enabled, `/flexibook list` will not show your `namespace:bookId` (unless it was bootstrapped from the mod JAR).

---

## 8. In-game: get the book

### 8.1 Commands (primary method)

Open chat (cheats/OP):

```text
/flexibook list
```

You should see your id, e.g. `myguide:guide`, among others (`flexibook:demo_guide`, `fieldnotes:journal`, …).

```text
/flexibook give myguide:guide
```

Give to another player:

```text
/flexibook give myguide:guide Steve
```

Success puts a `flexibook:flexi_book` in the inventory (or drops it if full) with the resolved DataComponent. **Right-click** to open.

### 8.2 What creative does *not* do

The FlexiBook creative tab only offers:

- Blank `flexi_book`
- Built-in **Field Guide** / demo from code

**Custom pack books do not appear in creative.** Always use `/flexibook give` (or your own mod code — §9).

### 8.3 Id reminders

| You exported | Give command |
|--------------|--------------|
| ns=`note`, bookId=`note11` | `/flexibook give note:note11` |
| ns=`myguide`, bookId=`guide` | `/flexibook give myguide:guide` |
| Built-in demo (mod assets) | `/flexibook give flexibook:demo_guide` |

---

## 9. Other ways to give books (mods / code)

For other mods or automation (not required for pack authors):

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
player.getInventory().add(stack);
```

Optional tweak:

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"),
    c -> c.withThemeId(ResourceLocation.fromNamespaceAndPath("myguide", "main"))
);
```

Own item type + pull content:

```java
ItemStack stack = new ItemStack(MyItems.FIELD_GUIDE.get());
AdaptiveBookContent c = FlexiBookAPI.resolveBook(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), c);
```

Full detail: [API.md §14](./API.md#14-data-driven-books).

---

## 10. Checklist

**Editor**

- [ ] Namespace / themeId / bookId chosen and valid  
- [ ] Content uses translation keys; Lang tab filled for each language you care about  
- [ ] Preview looks right at 1× (and optional higher scale)  
- [ ] Full pack exported (or partials that together cover books+contents+theme+lang)  
- [ ] Custom item icon exported if you want a non-default hotbar look  

**Game**

- [ ] FlexiBook mod loaded  
- [ ] Pack in `resourcepacks/` and **enabled**  
- [ ] **F3+T** after enable/change  
- [ ] `/flexibook list` shows `namespace:bookId`  
- [ ] `/flexibook give namespace:bookId`  
- [ ] Right-click opens book; theme texture and translations correct  

---

## 11. Troubleshooting

| Symptom | Likely cause | What to do |
|---------|--------------|------------|
| `Unknown FlexiBook id` | Pack off / not reloaded / wrong id | Enable pack → F3+T → `/flexibook list` → give exact id |
| `list` missing your book | No `flexibook/books/*.json` or parse failed | Check export includes **content** part; books JSON must have `"content"` |
| Book shows raw keys | Lang pack missing or wrong ns | Export lang; keys must match content; enable pack with `assets/<ns>/lang/` |
| Default/wrong background | Theme texture path / textures pack not enabled | Enable textures pack or full pack; F3+T |
| Item icon unchanged | No override under `assets/flexibook/textures/item/` | Export textures **with** custom item PNG; ensure that pack is enabled (and above lower packs if multiple) |
| Soft alpha looks solid | Old mod without blend fix | Use current FlexiBook build (`enableBlend` on book blit); PNG must have alpha |
| Permission error on command | Not OP / cheats off | Permission level ≥ 2 |
| Editor import finds nothing | Not a FlexiBook layout | Need `assets/.../flexibook/` and/or known texture paths |
| Partial pack alone “empty” book | Missing contents or books index | Export **content** pack (or full pack) |

### Dev instance paths (typical)

```text
run/resourcepacks/<your_pack_or_zip>
```

Example already used in this repo: `run/resourcepacks/note_pack.zip` → id `note:note11`.

---

## Quick reference card

```text
# Editor
Export full pack…  →  {ns}_pack/
Import pack…       →  ZIP or folder

# Game
Options → Resource Packs → enable {ns}_pack
F3+T
/flexibook list
/flexibook give {ns}:{bookId}
# Right-click flexi_book
```

```java
FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"));
```
