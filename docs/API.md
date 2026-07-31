# FlexiBook API Reference

API / data / markup guide for other mods and datapack authors.  
Version: `flexibook` **1.0.0** · Minecraft **1.21.1** · NeoForge **21.1.x**.

**中文:** [`API.zh-CN.md`](./API.zh-CN.md).

Quick start: [`../README.md`](../README.md). Doc index: [`README.md`](./README.md).

---

## Table of contents

1. [Dependencies](#1-dependencies)
2. [Core concepts](#2-core-concepts)
3. [Quick start](#3-quick-start)
4. [FlexiBookAPI](#4-flexibookapi)
5. [AdaptiveBookBuilder](#5-adaptivebookbuilder)
6. [Content model](#6-content-model)
7. [Markup syntax](#7-markup-syntax)
8. [Links and safe actions](#8-links-and-safe-actions)
9. [Item and DataComponent](#9-item-and-datacomponent)
10. [Opening the UI](#10-opening-the-ui)
11. [Internationalization (i18n)](#11-internationalization-i18n)
12. [Fonts (per book / inline)](#12-fonts-per-book--inline)
13. [Layout and themes](#13-layout-and-themes)
14. [Data-driven books](#14-data-driven-books)
15. [Full examples](#15-full-examples)
16. [Limits and FAQ](#16-limits-and-faq)
17. [Package and symbol index](#17-package-and-symbol-index)

---

## 1. Dependencies

### 1.1 Gradle (recommended)

Put the build artifact in a local `libs/`, or publish to your own Maven:

```gradle
repositories {
    // Local jar example
    flatDir { dirs 'libs' }
}

dependencies {
    // Needed at compile and runtime (item, component, open book)
    implementation name: 'flexibook-1.0.0'
    // If installed to mavenLocal:
    // implementation 'io.github.PhantomDaze.flexibook:flexibook:1.0.0'
}
```

Declare the dependency in `mods.toml` (`required` / `optional` as needed):

```toml
[[dependencies.mymod]]
    modId="flexibook"
    type="required"          # or optional
    versionRange="[1.0.0,)"
    ordering="NONE"
    side="BOTH"
```

| Field | Value |
|-------|--------|
| modid | `flexibook` |
| Root package | `io.github.PhantomDaze.flexibook` |
| Public façade | `...api.FlexiBookAPI` / `...api.AdaptiveBookBuilder` |
| License | GPL-3.0-only (derivatives must comply with GPLv3) |

### 1.2 Typical imports

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

Client-only (**do not** touch from common static init on dedicated server):

```java
import io.github.PhantomDaze.flexibook.client.ClientModEvents;
// AdaptiveBookScreen / LinkHandler / BookThemeReloadListener are client-only
// BookTheme / BookThemeRegistry / BookThemes are pure data — common side may registerTheme
```

---

## 2. Core concepts

```
ItemStack(flexibook:flexi_book)
    └── DataComponent flexibook:adaptive_book_content
            └── AdaptiveBookContent
                    ├── title: TranslatableText          // translated when opened
                    ├── defaultFont: Optional<RL>        // book-wide default font
                    ├── themeId: Optional<RL>            // theme id → BookThemeRegistry
                    └── payload (one of two; elements win)
                          ├── rawMarkup: String          // [h1]... source
                          └── elements: List<BookElement>
                                └── InlineSpan.style.font / Heading.font may override
```

| Concept | Notes |
|---------|--------|
| **Dual storage** | Structured `BookElement` list **or** raw markup string; if both present, **elements win** |
| **Deferred translation** | Title/body keys resolve at **open / layout** in the current client language — not baked at write time |
| **Font layers** | Inline/heading `font` → book `defaultFont` → built-in `flexibook:default` (unihex); never silently falls to `minecraft:default` |
| **Theme registry** | `BookTheme` by `ResourceLocation`; book stores `theme` id, default `flexibook:default` |
| **Safe links** | `cmd` only runs registered action ids; `url` is http(s) only with a confirm dialog |
| **Custom item** | `flexibook:flexi_book` — does **not** hijack vanilla `written_book` |
| **Client-only Screen** | No Menu; right-click opens `AdaptiveBookScreen` on the client |

Runtime path:

```
AdaptiveBookContent.resolveElements()
  → elements already present? use them
  → else TagParser.parse(rawMarkup)
  → BookLayoutEngine (scale / two-column / cache)
  → AdaptiveBookScreen single-page draw + paging + search highlight
```

---

## 3. Quick start

### 3.1 Builder → right-clickable book

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

### 3.2 Register link actions (common mod init)

```java
// Safe on common side; handler may only show client feedback
FlexiBookAPI.registerCommandAction("mymod:open_map", ctx -> {
    ctx.message("mymod.map.opened"); // translation key → local player feedback
});
```

### 3.3 Markup string only

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

Façade: `io.github.PhantomDaze.flexibook.api.FlexiBookAPI` (not instantiable).

| Method | Description |
|--------|-------------|
| `builder(String guideId)` | New `AdaptiveBookBuilder`. `guideId` is **reserved** in v1 (future TOC/identity); not written to the component |
| `createBook(AdaptiveBookContent content)` | Create `flexi_book` item and set the content component |
| `registerCommandAction(String id, Consumer<ActionContext> action)` | Safe command link; prefer `modid:name` |
| `commandAction(String id)` | Build `LinkAction.CommandId` for Builder / elements |
| `urlAction(String url)` | Build `LinkAction.Url` (runtime still checks http(s)) |
| `registerDefaultActions()` | Registers sample `flexibook:say_hi`; FlexiBook already calls this — external mods usually need not |
| `defaultThemeId()` / `containThemeId()` | Built-in sample theme ids: `flexibook:default` / `flexibook:contain` |
| `registerTheme(id, BookTheme)` | Register or replace theme (common-safe; pure data) |
| `getTheme(id)` / `resolveTheme(id)` / `themeIds()` | Query; `resolve` falls back to default sample for unknown ids |
| `LecternCompat` | **Empty interface + TODO**: no lectern in v1 |

### 4.1 `ActionContext`

```java
public interface LinkActionRegistry.ActionContext {
    /** Show one translated message to the local player (when a client player exists). */
    void message(String translationKey, Object... args);
}
```

Notes:

- Registry is on the **common** side (`ConcurrentHashMap`) — safe on dedicated servers.
- Clicks run on the **client**; opening screens or mutating client state must stay client-only (or branch inside the handler).
- Arbitrary `/command` strings are **not** executed; unknown ids are blocked with `flexibook.link.unknown_action`.

---

## 5. AdaptiveBookBuilder

Class: `io.github.PhantomDaze.flexibook.api.AdaptiveBookBuilder`.

Fluent API; finish with:

- `buildContent()` → `AdaptiveBookContent`
- `buildItem()` → `ItemStack` of `ModItems.FLEXI_BOOK` with component

### 5.1 Method overview

| Method | Role |
|--------|------|
| `titleKey(String key, String... args)` | Title translation key + optional args |
| `title(TranslatableText title)` | Set title object directly |
| `defaultFont(ResourceLocation)` / `defaultFont(String id)` | **Book-wide** explicit default font; omit → layout/draw use `flexibook:default` |
| `theme(ResourceLocation)` / `theme(String id)` | Theme id (component field `theme`); omit → open with `flexibook:default` |
| `h1(String key)` / `h2(String key)` | Heading levels 1/2 (keys) |
| `h1(String key, ResourceLocation font)` / `h2(..., font)` | Heading with font |
| `p(String key)` | Paragraph, single translation span |
| `p(String key, ResourceLocation font)` | Whole paragraph font |
| `font(String key, ResourceLocation font)` | Same as `p(key, font)`, clearer name |
| `pLiteral(String text)` / `pLiteral(text, font)` | Paragraph, literal (**no** i18n) |
| `pRaw(String markupFragment)` | Parse a markup fragment and **append** to elements |
| `bullet(String key)` / `bullet(key, font)` | List item |
| `image(ResourceLocation, w, h)` | Image |
| `image(ResourceLocation, w, h, tooltipKey)` | Image + hover translation key |
| `link(String textKey, LinkAction action)` | Clickable link paragraph (default blue + underline) |
| `link(textKey, action, font)` | Link + font |
| `divider()` | Divider |
| `br()` | Block line break |
| `element(BookElement)` | Append any structured element |
| `fromMarkup(String markup)` | Set **whole-book** raw source (see below) |
| `buildContent()` / `buildItem()` | Produce |

### 5.2 `fromMarkup` vs elements priority

`buildContent()`:

```text
if (rawMarkup != null && elements is empty)
    → AdaptiveBookContent.ofMarkup(title, rawMarkup)
else
    → AdaptiveBookContent.ofElements(title, elements)
```

Meaning:

- **Only** `fromMarkup(...)`, no `h1/p/...` → store raw; parse on open.
- Any `h1/p/pRaw/...` → store **structured elements**; later `fromMarkup` does **not** override accumulated elements (raw is not written).
- `pRaw` parses a fragment into elements — different from `fromMarkup`.

Recommendations:

- Programmatic chapters → structured Builder methods.
- Whole-page markup from files/config → `fromMarkup` or `AdaptiveBookContent.ofMarkup`.
- Structured body with occasional rich runs → `pRaw("[p][b]key[/b][/p]")`.

### 5.3 Default title

Unset title defaults to `flexibook.book.untitled`.

---

## 6. Content model

### 6.1 `AdaptiveBookContent`

```java
public record AdaptiveBookContent(
    TranslatableText title,
    Optional<String> rawMarkup,              // codec field "raw"
    Optional<List<BookElement>> elements,    // codec field "elements"
    Optional<ResourceLocation> defaultFont,  // codec field "font"
    Optional<ResourceLocation> themeId       // codec field "theme"
)
```

| Factory / member | Description |
|------------------|-------------|
| `EMPTY` | Empty-book title key + empty elements |
| `ofElements(title, list)` / `…(title, list, font)` / `…(title, list, font, theme)` | Structure only |
| `ofMarkup(title, markup)` / `…(title, markup, font)` / `…(title, markup, font, theme)` | Raw only |
| `withDefaultFont(ResourceLocation)` | Copy with book font |
| `withThemeId(ResourceLocation)` | Copy with theme id |
| `defaultFont()` | Explicit book default (Optional); overridden by span/heading font |
| `resolvedFont()` | Explicit `defaultFont` if set, else `FlexiBookFonts.DEFAULT` (`flexibook:default`) |
| `themeId()` | Theme registry id; missing/unknown → theme `flexibook:default` |
| `resolveElements()` | Unified `List<BookElement>` before layout/render |
| `isEmpty()` | No elements after resolve and blank raw |
| `CODEC` / `STREAM_CODEC` | Persistence + network |

**Serialization (matches codec)**:

```jsonc
{
  "title": { "key": "mymod.book.title", "args": [] },
  "font": "flexibook:default",    // optional; omit still resolves to flexibook:default
  "theme": "flexibook:contain",   // optional theme id
  // one of:
  "raw": "[h1]mymod.book.ch1[/h1]\n[p]mymod.book.p1[/p]",
  // or
  "elements": [
    { "type": "heading", "data": { "level": 1, "text": { "key": "mymod.book.ch1" } } }
  ]
}
```

`BookElement` uses `Codec.STRING.dispatch` (`type` → `data`). Prefer Builder / Java API over hand-written element trees.

Example element JSON:

```json
{ "type": "heading", "data": { "level": 1, "text": { "key": "mymod.title" } } }
{ "type": "paragraph", "data": { "spans": [ { "text": "mymod.p1", "translate": true } ] } }
{ "type": "image", "data": { "src": "mymod:textures/icon.png", "width": 48, "height": 48 } }
```

### 6.2 `TranslatableText`

```java
public record TranslatableText(String key, List<String> args)
```

| Method | Description |
|--------|-------------|
| `new TranslatableText(key)` | No args |
| `TranslatableText.of(key, args...)` | With args |
| `resolve()` / `resolvePlain()` | Called at client layout |

Heuristic: `key` contains `.` and no spaces → translation key; otherwise **literal** `Component.literal`.  
Use keys like `mymod.chapter.one` for titles/paragraphs; do not put full sentences as keys unless you want them shown literally.

### 6.3 `BookElement` (sealed)

| Type | `typeId` | Fields |
|------|----------|--------|
| `Heading(level, TranslatableText text, Optional font)` | `heading` | `level` 1 or 2; optional heading font |
| `Paragraph(List<InlineSpan> spans)` | `paragraph` | Inline rich text (spans may carry font) |
| `LineBreak` | `br` | Singleton `INSTANCE` |
| `Divider` | `divider` | Singleton `INSTANCE` |
| `Image(src, width, height, Optional tooltipKey)` | `image` | `ResourceLocation` texture |
| `Bullet(List<InlineSpan> spans)` | `bullet` | Same as paragraph spans |
| `Box(Optional className, List<BookElement> children)` | `box` | `[div]`; nestable |

Two-arg `Heading(level, text)` still works (`font = empty`).

Manual construction:

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
    boolean translate,           // true = text is a translation key
    StyleFlags style,
    Optional<LinkAction> link
)
```

| Factory | Meaning |
|---------|---------|
| `key(translationKey)` | Translation span |
| `key(key, style)` / `key(key, style, link)` | With style/link |
| `literal(text)` / `literal(text, style)` / `literal(..., link)` | Literal |

`resolvePlain()`: translated or as-is for measure/search.

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

- `EMPTY`: all off  
- `withBold` / `withItalic` / `withUnderline` / `withColor(Integer)` (`null` clears color)  
- `withFont(ResourceLocation)` (`null` clears font override)  
- `merge(other)`: booleans OR; color/font prefer **other**

Color is **RGB int** (e.g. `0xCC5500`), same as `#RRGGBB` in markup.  
`font` is a vanilla/resource font id (e.g. `minecraft:alt`, `minecraft:uniform`, `mymod:my_font`).

### 6.6 `LinkAction` (sealed)

| Variant | Factory | Click behavior |
|---------|---------|----------------|
| `None` | `LinkAction.none()` | Ignore |
| `CommandId(id)` | `LinkAction.commandId(id)` or `FlexiBookAPI.commandAction(id)` | Registry lookup; reject if unregistered |
| `Url(url)` | `LinkAction.url(url)` or `FlexiBookAPI.urlAction(url)` | `http://` / `https://` only; `ConfirmLinkScreen` then `openUri` |

Codec may fail illegal URLs at deserialize time; client `LinkHandler` checks protocol again.

---

## 7. Markup syntax

Parser: `TagParser.parse(String)` → `List<BookElement>`.  
**Tolerant**: unknown/broken tags are skipped with logs where possible — **no throw** to callers; total failure falls back to one literal page paragraph.

### 7.1 Tag table

| Tag | Block/inline | Notes |
|-----|--------------|--------|
| `[h1]...[/h1]` / `[h1 font="ns:id"]` | Block | H1; content should be a key; optional font |
| `[h2]...[/h2]` / `[h2 font="ns:id"]` | Block | H2 |
| `[p]...[/p]` | Block | Paragraph; may nest inline tags |
| `[bullet]...[/bullet]` | Block | List item (indent + bullet layout) |
| `[br]` / `[br/]` | Block or inline | Line break |
| `[divider]` / `[divider/]` | Block | Divider |
| `[img ...]` / `[img .../]` | Block | Image, self-closing |
| `[link ...]...[/link]` | Block or inline | Link; optional `font=` |
| `[b]...[/b]` | Inline | Bold |
| `[i]...[/i]` | Inline | Italic |
| `[u]...[/u]` | Inline | Underline |
| `[color=#RRGGBB]...[/color]` | Inline | Color |
| `[font font="ns:id"]...[/font]` / `[font=ns:id]...[/font]` | Inline | **Font switch** (nestable with b/i/color) |
| `[div class="..."]...[/div]` | Block | `Box` container; may nest blocks |

### 7.2 Attributes

**Image**

```text
[img src="mymod:textures/gui/icon.png" width="48" height="48" tooltip="mymod.tip"/]
```

| Attr | Default | Notes |
|------|---------|--------|
| `src` | `flexibook:textures/gui/icon.png` | May include namespace; no `:` → default `flexibook:` |
| `width` / `height` | 48 | Logical pixel box |
| `tooltip` | none | Translation key |

**Link**

```text
[link cmd="mymod:open_map"]mymod.book.go[/link]
[link url="https://example.com"]mymod.book.web[/link]
[link cmd="mymod:x" color="#00AAFF"]...[/link]
[link cmd="mymod:x" font="minecraft:alt"]...[/link]
```

| Attr | Notes |
|------|--------|
| `cmd` | Action id (must `registerCommandAction` first) |
| `url` | http(s) only |
| `color` | Optional override of default link color |
| `font` | Optional link text font |

If both `cmd` and `url` are present, parser prefers **`cmd`**.

**Font / heading font**

```text
[font font="minecraft:alt"]mymod.book.runes[/font]
[font=minecraft:uniform]mymod.book.mono[/font]
[h1 font="mymod:fancy"]mymod.book.title_key[/h1]
[h2 font="minecraft:alt"]mymod.book.sub[/h2]
```

| Tag | Attr | Notes |
|-----|------|--------|
| `[font]` | `font="ns:id"` or `[font=ns:id]` | Inline font until close tag |
| `[h1]` / `[h2]` | `font="ns:id"` | Heading-only font |

See [§12 Fonts](#12-fonts-per-book--inline).

**div**

```text
[div class="note"]
[p]mymod.note.body[/p]
[/div]
```

`class` is stored on `Box.className`; v1 themes do **not** restyle by class (reserved for extensions).

### 7.3 Escapes

- `\[` → literal `[`
- `\]` → literal `]`

### 7.4 Loose text outside blocks

Non-blank text outside tags becomes a **literal paragraph** (`translate=false`). Production content should always sit in `[p]` / `[h1]` / etc. with translation keys.

### 7.5 “Looks like a translation key”

When flushing spans, `TagParser` sets `translate=true` if text contains `.` and no spaces — same heuristic as `TranslatableText`.

### 7.6 Full markup sample

```text
[h1]mymod.guide.title[/h1]
[p]mymod.guide.intro[/p]
[divider]
[h2]mymod.guide.section[/h2]
[bullet]mymod.guide.b1[/bullet]
[bullet]mymod.guide.b2[/bullet]
[p]Normal text with [b]mymod.guide.bold_key[/b] and [color=#CC5500]mymod.guide.warm[/color].[/p]
[p]Literal brackets: \[not a tag\][/p]
[p]Default font then [font font="minecraft:alt"]mymod.guide.alt_run[/font] and back.[/p]
[h2 font="minecraft:uniform"]mymod.guide.mono_h2[/h2]
[link cmd="mymod:open_map"]mymod.guide.open_map[/link]
[link url="https://neoforged.net/"]mymod.guide.docs[/link]
[img src="mymod:textures/gui/diagram.png" width="64" height="64" tooltip="mymod.guide.diagram"/]
```

Note: mixing **literal prose + translation keys** inside one `[p]` requires keys to be separate runs matching the “dot, no space” rule. Safer: entire paragraphs as keys, or Builder multi-`InlineSpan`.

---

## 8. Links and safe actions

### 8.1 Registration

```java
// Call once from mod constructor / common setup
FlexiBookAPI.registerCommandAction("mymod:give_kit", ctx -> {
    ctx.message("mymod.kit.hint");
    // Server gameplay: send your own network packet — do not expect ServerPlayer here
});
```

Underlying: `LinkActionRegistry.register(id, consumer)`.  
Null `id` / `action` are ignored. Later registration with the same id **replaces**.

Query: `LinkActionRegistry.isRegistered(id)` / `get(id)`.

### 8.2 Click path (client)

```
AdaptiveBookScreen click ClickArea
  → LinkHandler.handle(action, parentScreen)
       CommandId → Registry.get → else warn + flexibook.link.unknown_action
       Url       → protocol check → ConfirmLinkScreen → Util.getPlatform().openUri
       None      → ignore
```

### 8.3 Built-in sample action

| ID | Behavior |
|----|----------|
| `flexibook:say_hi` | `ctx.message("flexibook.action.say_hi")` |

### 8.4 Safety rules (required reading)

1. **Never** run arbitrary player/book strings as shell or brigadier.  
2. Only expose a whitelist of `modid:action` you implement.  
3. Do not use `file:`, `javascript:`, etc. for URLs — they are rejected.  
4. Handlers are for **client feedback** by default; cross-side gameplay needs your own packets and permissions.

---

## 9. Item and DataComponent

| Registry name | Notes |
|---------------|--------|
| Item `flexibook:flexi_book` | `ModItems.FLEXI_BOOK`, `stacksTo(1)` |
| Component `flexibook:adaptive_book_content` | `ModDataComponents.ADAPTIVE_BOOK_CONTENT` |
| Creative tab `flexibook` | Blank book + Demo Guide |

### 9.1 Read/write component

```java
// Write
stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), content);

// Read
AdaptiveBookContent c = stack.get(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get());
if (c == null || c.isEmpty()) {
    // empty book
}
```

API equivalent:

```java
ItemStack stack = FlexiBookAPI.createBook(content);
```

### 9.2 Display name and tooltip

- `getName`: if component present, `content.title().resolve()`  
- Tooltip: fixed blurb + empty-book line `flexibook.item.flexi_book.empty`

### 9.3 How to give books

| Scenario | Approach |
|----------|----------|
| Quest/reward | Server `player.addItem(FlexiBookAPI.createBook(...))` |
| Creative | Own `CreativeModeTab` or same pattern as Demo |
| Command | Own brigadier wrapping `createBook`, or mod **`/flexibook give <id>`** for data-driven books |
| Loot tables | Custom loot function writing the DataComponent (v1 ships no built-in function) |

Component uses `persistent` + `networkSynchronized` + `cacheEncoding` — saves and syncs with the stack.

---

## 10. Opening the UI

### 10.1 Player right-click

`FlexiBookItem.use`: opens only when `level.isClientSide`; returns `sidedSuccess`.  
Uses **reflection** to call `ClientModEvents.openBook(stack)` so dedicated servers never load `Screen`.

### 10.2 Other mods opening the book (client only)

```java
// Must be on the client thread / in a client class
ClientModEvents.openBook(stack);
// equivalent to
// Minecraft.getInstance().setScreen(new AdaptiveBookScreen(stack));
```

**Do not** reference `AdaptiveBookScreen` from server or common static fields.

### 10.3 Screen interaction

| Input | Behavior |
|-------|----------|
| Left/right arrows, page buttons | Turn page |
| Click link hitbox | `LinkHandler` |
| Bottom search | Highlight matches in current layout (empty query = full book) |
| Reopen after language change | Re-translate + relayout (cache key includes language) |

### 10.4 Lectern

**Not supported** in v1. `FlexiBookAPI.LecternCompat` is a future placeholder only.

---

## 11. Internationalization (i18n)

### 11.1 Principles

- Books store **keys** (and optional string args), not final sentences.  
- Translations live in **the opening client’s** `assets/<modid>/lang/*.json`.  
- CJK is often longer → engine may lower `scale` or use two columns; page count changes are expected.

### 11.2 Key naming

```text
mymod.book.<guide_id>.title
mymod.book.<guide_id>.ch1
mymod.book.<guide_id>.p1
mymod.action.<name>.feedback
```

### 11.3 lang examples

`assets/mymod/lang/en_us.json`:

```json
{
  "mymod.book.title": "Field Manual",
  "mymod.book.ch1": "Getting Started",
  "mymod.book.p1": "Welcome to the guide.",
  "mymod.book.click_me": "Open map",
  "mymod.map.opened": "Map overlay enabled."
}
```

`zh_cn.json`:

```json
{
  "mymod.book.title": "实地手册",
  "mymod.book.ch1": "入门",
  "mymod.book.p1": "欢迎阅读本指南。",
  "mymod.book.click_me": "打开地图",
  "mymod.map.opened": "已启用地图叠加层。"
}
```

### 11.4 When to use literals

- Debug / non-translated dynamic text: `pLiteral` / `InlineSpan.literal`  
- Do not store multilingual body text as literals.

---

## 12. Fonts (per book / inline)

Font ids match vanilla chat components: resources at `assets/<namespace>/font/<path>.json`, referenced as `namespace:path`.

### 12.0 Built-in default: `flexibook:default`

FlexiBook **ships** a book default font and does not depend on `minecraft:default`:

| id | Notes |
|----|--------|
| `flexibook:default` | Built-in unihex (GNU Unifont 17.0.05 HEX/ZIP) + space provider. When the book omits `font`, layout/draw/page label/title all resolve here |
| `FlexiBookFonts.DEFAULT` / `content.resolvedFont()` | Java constant and resolve entry |

Resources:

- `assets/flexibook/font/default.json`
- `assets/flexibook/font/unifont_all-17.0.05.zip`
- `assets/flexibook/font/LICENSE-unifont.txt`

Editor and game consume the **same ZIP**, measuring advances like Minecraft `UnihexProvider` (including bold +0.5 then `ceil`).

Other useful ids (client resources must exist):

| id | Notes |
|----|--------|
| `minecraft:alt` | Enchanting-table style (explicit override) |
| `minecraft:uniform` | Vanilla Unicode stack |
| `minecraft:illageralt` | Illager style |
| `mymod:…` | Your font json |

Normal game UI (search box, buttons, tooltips) still uses Minecraft UI fonts.

### 12.1 Priority

```
inline StyleFlags.font / Heading.font
        ↓ if absent
book AdaptiveBookContent.defaultFont (JSON "font")
        ↓ if absent
flexibook:default   ← not minecraft:default
```

One book can:

- Omit `font` → automatic `flexibook:default`  
- Set only book `defaultFont` → whole book  
- Set only some span/heading fonts → mixed  
- Set both → local overrides book default  

Measure and draw both use `Style.withFont(...)`; wrap width uses real advances.

### 12.2 Builder example

```java
import io.github.PhantomDaze.flexibook.content.FlexiBookFonts;

ResourceLocation fancy = ResourceLocation.fromNamespaceAndPath("mymod", "fancy");
ResourceLocation mono  = ResourceLocation.withDefaultNamespace("uniform");

ItemStack book = FlexiBookAPI.builder("styled_guide")
    .titleKey("mymod.book.title")
    // optional: without this, layout/screen still use FlexiBookFonts.DEFAULT
    .defaultFont(FlexiBookFonts.DEFAULT)
    .h1("mymod.book.h1")
    .h2("mymod.book.code_title", mono)            // heading-only mono
    .p("mymod.book.body")
    .font("mymod.book.quote", mono)
    .pRaw("[p]Normal [font font=\"minecraft:alt\"]runes[/font] mixed[/p]")
    .buildItem();
```

### 12.3 Book-level font only

```java
AdaptiveBookContent c = AdaptiveBookContent.ofMarkup(title, markup)
    .withDefaultFont(ResourceLocation.fromNamespaceAndPath("mymod", "fancy"));
// or
AdaptiveBookContent.ofElements(title, elements, Optional.of(fontId));

// Effective font after resolve (explicit or flexibook:default)
ResourceLocation used = c.resolvedFont();
```

### 12.4 Notes

- **Default semantics**: omitting `font` in JSON/Builder uses `flexibook:default`, not vanilla `minecraft:default`.  
- Explicit custom fonts must exist on the **client that opens the book**; missing fonts may render oddly.  
- Custom TTF/bitmap/unihex: follow vanilla font json. The editor can **import TTF/OTF** with approximate preview (FontFace, not pixel-perfect MC). Pack export writes `assets/<ns>/font/*.json` (`type: ttf`) + files and rewrites font ids. Unimported external ids still fall back to unihex in the editor with a banner.  
- Lang tables can be edited in the editor Lang panel; full export writes `assets/<ns>/lang/*.json`.  
- Server only stores `ResourceLocation` strings; glyphs resolve client-side.  
- Unifont license: `LICENSE-unifont.txt` in the jar (OFL / GPL font exception); update via `scripts/update-unifont.sh` (no network on normal builds).  
- Built-in font resources: `assets/flexibook/font/` (`default.json`, unihex ZIP, `LICENSE-unifont.txt`). Policy summary is this section only.

---

## 13. Layout and themes

Layout algorithm is internal (no third-party SPI). **Themes (`BookTheme`) are data-driven**: code registration + resource JSON.

### 13.1 Adaptive strategy (summary)

1. Resolve elements → plain text for current language → measure and paginate  
2. Start `scale=1.0`, `columns=1`  
3. Too many / too dense pages → lower scale (≈0.6) → try `columns=2`  
4. High CJK ratio slightly lowers starting scale  
5. `LayoutCache`: content hash + language + GUI scale + theme revision + font; cleared on logout / theme reload  

Default content area (`flexibook:default`): width 160, height 185 logical px (`BookTheme.baseParams()`).

### 13.2 Built-in sample themes

| id | Notes |
|----|--------|
| `flexibook:default` | Parchment sample (default metrics/colors/texture; images **STRETCH**) |
| `flexibook:contain` | Same, images **CONTAIN** (keep aspect, center) |

Constants: `BookThemes.DEFAULT` / `BookThemes.CONTAIN`. Same-id resource JSON can override on client reload.

### 13.3 Registering themes in code

```java
// Common setup is fine (pure data; no Screen)
BookTheme dark = BookTheme.builder()          // copy from default sample
    .pageTextColor(0xE0E0E0)
    .linkColor(0x7FDBFF)
    .imageFit(ImageFit.CONTAIN)
    .revision(1)
    .build();
FlexiBookAPI.registerTheme(
    ResourceLocation.fromNamespaceAndPath("mymod", "dark"),
    dark
);

// Book references theme
ItemStack book = FlexiBookAPI.builder("guide")
    .titleKey("mymod.book.title")
    .theme("mymod:dark")
    .p("mymod.book.body")
    .buildItem();
// or content.withThemeId(...)
```

On open: `AdaptiveBookScreen` → `BookThemeRegistry.resolve(content.themeId())`; unknown id warns and falls back to `flexibook:default`.

### 13.4 Resource pack JSON themes

Path: `assets/<namespace>/flexibook/themes/<path>.json`  
id: `<namespace>:<path>` (e.g. `assets/mymod/flexibook/themes/dark.json` → `mymod:dark`)

Shipped samples:

- `assets/flexibook/flexibook/themes/default.json`
- `assets/flexibook/flexibook/themes/contain.json`

Required: `book_texture` (`ResourceLocation` string). All other fields have defaults — only override what you need:

| Field | Default (approx) | Meaning |
|-------|------------------|---------|
| `book_tex_width` / `book_tex_height` | 192 / 216 | Drawn book panel size |
| `texture_sheet_size` | 2048 | Texture sheet edge (full sheet mapped into panel) |
| `content_left` / `content_top` | 16 / 10 | Content origin relative to book top-left |
| `title_offset_y` | 5 | Title Y (from topPos) |
| `content_offset_y` | 4 | Extra Y under `content_top` |
| `page_label_inset_y` | 18 | Page label inset from book bottom |
| `page_content_width` / `page_content_height` | 160 / 185 | Layout content area |
| `line_height` / `paragraph_gap` / `heading_gap` | 9 / 3 / 5 | Typography |
| `gutter` / `bullet_indent` / `divider_height` | 10 / 10 / 6 | Columns / lists / divider |
| `page_text_color` etc. | RGB int | Body / link / highlight / divider |
| `image_fit` | `"stretch"` | `"stretch"` \| `"contain"` |
| `revision` | 1 | Layout cache key; bump when changing theme |

On client reload, `BookThemeReloadListener` bootstraps built-ins, loads JSON, clears layout cache.

### 13.5 Replaceable textures

| Path | Role |
|------|------|
| `assets/flexibook/textures/gui/book.png` | Default book background (fixed **2048×2048** sheet → `book_tex_width`×`book_tex_height`, default 192×216) |
| `assets/flexibook/textures/gui/icon.png` | Sample / default image |
| `assets/flexibook/textures/item/flexi_book.png` | **Item icon** (`flexibook:flexi_book`, model `layer0` → `flexibook:item/flexi_book`) |

Custom themes may point `book_texture` anywhere. Page buttons use vanilla GUI — no custom widget textures.

**Item icon:** the item is still `flexibook:flexi_book` (resource packs cannot register a new item). Change the look by overriding  
`assets/flexibook/textures/item/flexi_book.png` (prefer 16×16). Editor Theme → item icon picker; texture pack export writes both  
`assets/<ns>/textures/item/flexi_book.png` (round-trip) and `assets/flexibook/textures/item/flexi_book.png` (in-game override).

**Partial alpha / soft edges:** `AdaptiveBookScreen` enables GL blend (`RenderSystem.enableBlend` + `defaultBlendFunc`) when drawing the book background and inline images. 1.21.1 `GuiGraphics.blit` does not enable blend by default — without it, only fully transparent texels vanish and soft alpha becomes solid. Use PNGs with a real alpha channel.

Editor partial export (Theme tab): **Textures → export textures pack** (book + item); **Export theme → theme pack / theme JSON**. Full pack still uses the top bar.

### 13.6 Image elements: texture and fit

`BookElement.Image.src` must be a client-loadable texture `ResourceLocation` (usually under `textures/`). Missing textures may be blank or magenta/black.

Layout always reserves the declared logical `width`×`height` (may shrink further to column width). PNG pixel size may differ; **draw** uses theme `image_fit`:

| `ImageFit` | Behavior |
|------------|----------|
| `STRETCH` (**default** sample) | Stretch to fill logical box; aspect mismatch distorts |
| `CONTAIN` (**contain** sample) | Read PNG size, scale to fit box, center (letterbox possible) |

```java
// Book uses keep-aspect sample theme
FlexiBookAPI.builder("guide").theme(FlexiBookAPI.containThemeId())...
// or your theme .imageFit(ImageFit.CONTAIN)
```

`width`/`height` remain the layout box; `CONTAIN` only changes pixels inside. Inline images also draw with blend so soft edges composite correctly.

---

## 14. Data-driven books

Resource pack / mod jar book assets split into **6 parts** (3 are vanilla-forced paths):

| Part | Path | Role |
|------|------|------|
| **books** | `assets/<ns>/flexibook/books/<id>.json` | **Index**: points at content + theme (+ optional font) |
| **contents** | `assets/<ns>/flexibook/contents/<id>.json` | **Body**: title / elements or raw (keys) |
| **themes** | `assets/<ns>/flexibook/themes/<id>.json` | Layout and style |
| **lang** | `assets/<ns>/lang/<code>.json` | Per-language strings (**vanilla** path) |
| **fonts** | `assets/<ns>/font/<path>.json` + `.ttf`/`.otf` | Custom fonts (**vanilla** ttf provider) |
| **textures** | `assets/<ns>/textures/...` | Book/icons/images (**vanilla** path) |

### 14.1 books index format

```
assets/<namespace>/flexibook/books/<path>.json
→ id = <namespace>:<path>
```

```json
{
  "content": "myguide:guide",
  "theme": "myguide:main",
  "font": "myguide:title"
}
```

| Field | Required | Notes |
|-------|----------|--------|
| `content` | yes | Content body id (`flexibook/contents/`) |
| `theme` | no | Theme id; else body theme or `flexibook:default` |
| `font` | no | Book font; overrides body `font` |

### 14.2 contents body format

```
assets/<namespace>/flexibook/contents/<path>.json
→ id = <namespace>:<path>
```

Matches `AdaptiveBookContent` codec (theme is **not** required on the body; books index owns theme):

```jsonc
{
  "title": { "key": "mymod.book.guide.title" },
  "elements": [
    { "type": "heading", "data": { "level": 1, "text": { "key": "mymod.book.guide.h1" } } },
    { "type": "paragraph", "data": { "spans": [ { "text": "mymod.book.guide.p1", "translate": true } ] } }
  ],
  "font": "flexibook:default"
}
```

`"raw": "[h1]...[/h1]"` is also valid. If both exist, **elements win**.

### 14.3 Load and override

On client resource reload (`BookContentReloadListener`):

1. Clear previous resource-written contents / books  
2. Bootstrap code registrations  
3. Scan `flexibook/contents/*.json` → `BookContentRegistry`  
4. Scan `flexibook/books/*.json` → **only** `BookDefinition` indexes (must have `"content"`; full content JSON in books fails parse)  
5. Clear layout cache  

Themes still load via `BookThemeReloadListener` on `flexibook/themes/`.  
lang / font / textures use vanilla resource loading — no FlexiBook-specific registry.

### 14.4 API surface

```java
// Body
FlexiBookAPI.registerBookContent("mymod:guide_body", content);
Optional<AdaptiveBookContent> body = FlexiBookAPI.getBookContent(id);
AdaptiveBookContent body2 = FlexiBookAPI.resolveBookContent(id); // unknown → EMPTY

// Index definition
FlexiBookAPI.registerBookDefinition("mymod:guide",
    new BookDefinition(
        ResourceLocation.parse("mymod:guide_body"),
        Optional.of(ResourceLocation.parse("mymod:dark")),
        Optional.empty()));
Optional<BookDefinition> def = FlexiBookAPI.getBookDefinition(id);

// Resolve full book (index → body + theme/font merge)
AdaptiveBookContent full = FlexiBookAPI.resolveBook(ResourceLocation.parse("mymod:guide"));

// Create item (recommended)
ItemStack book = FlexiBookAPI.createBookFromDefinition(ResourceLocation.parse("flexibook:demo_guide"));

ItemStack tweaked = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.parse("flexibook:demo_guide"),
    c -> c.withThemeId(ResourceLocation.fromNamespaceAndPath("mymod", "dark"))
);
```

### 14.5 Template inheritance (other mods)

**Principle:** item id / namespace is chosen by the consumer; content and theme ids come from the JSON provider’s namespace (resource pack or another mod jar). They may differ.

#### 14.5.1 Distribution

| Method | How | Use when |
|--------|-----|----------|
| Resource pack | Drop exported pack into `resourcepacks/` and enable | Content authors, hot reload |
| Inside mod jar | `src/main/resources/assets/<ns>/flexibook/{books,contents,themes}/...` + vanilla `lang/`/`font/`/`textures/` | Ships with the mod |

After F3+T / reload, resource definitions override same-id code registrations.

#### 14.5.2 Dependency

Declare `flexibook` in consumer `build.gradle` / mods.toml. Common code may call `io.github.PhantomDaze.flexibook.api.FlexiBookAPI`.

#### 14.5.3 Recommended: item from definition

```java
// Index id = assets/myguide/flexibook/books/guide.json → myguide:guide
// content field → assets/myguide/flexibook/contents/....json
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
// stack is flexibook:flexi_book with merged content written
```

#### 14.5.4 Own item + pull content

```java
ItemStack stack = new ItemStack(MyItems.FIELD_GUIDE.get());
AdaptiveBookContent c = FlexiBookAPI.resolveBook(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), c);
```

#### 14.5.5 Override tweak

`AdaptiveBookContent` is immutable. Use a `Function` overload and **return** a new instance:

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"),
    c -> c
        .withThemeId(ResourceLocation.fromNamespaceAndPath("myguide", "main"))
        .withDefaultFont(ResourceLocation.parse("flexibook:default"))
);
```

Or rebuild with `ofElements` / `ofMarkup`. `override == null` or returning `null` keeps the template.

#### 14.5.6 Theme references

Book JSON `"theme": "myguide:main"` → `assets/myguide/flexibook/themes/main.json`.  
Theme `book_texture` points at `assets/<texNs>/textures/...` (may share content namespace). Editor pack export rewrites `book.png` into the pack namespace.

### 14.6 Built-in sample

Aligned with `ExampleBooks.demoGuide()`:

```
assets/flexibook/flexibook/books/demo_guide.json
```

id `flexibook:demo_guide` — useful to verify load/layout.

### 14.7 In-game give command

```text
/flexibook list
/flexibook give <namespace:bookId> [player]
```

Requires permission level ≥ 2. Lists registered book definition ids; gives a `flexi_book` with resolved content. Creative tab does not list custom pack books.

---

## 15. Full examples

### 15.1 Quest handbook + action

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

### 15.2 Demo Guide–style Builder

See `io.github.PhantomDaze.flexibook.data.ExampleBooks#demoGuide`:

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

### 15.3 Replace content on an existing stack

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

If a client still has the old Screen open, close and reopen to see the new layout.

### 15.4 Parse markup only (tools/tests)

```java
List<BookElement> elements = TagParser.parse("[h1]a.b[/h1][p]c.d[/p]");
// No Screen required; unit tests can assert size/types
```

Repo tests: `src/test/java/.../TagParserTest`, `AdaptiveBookBuilderTest`, `ContentModelTest`.

---

## 16. Limits and FAQ

### 16.1 Explicitly out of v1

- Lectern placement and synced reading  
- Hijacking or emulating vanilla `written_book` / book & quill editing  
- In-book WYSIWYG editor  
- Tables, auto TOC, web GUI  
- Running arbitrary command strings as `cmd`  

### 16.2 FAQ

**Q: Why does the book show raw translation keys?**  
A: Missing key in the current lang json, or the key has spaces / no `.` so it is treated as a literal. Check lang files and key shape.

**Q: Link does nothing / unknown action?**  
A: Missing `registerCommandAction`, or id mismatch (case-sensitive; prefer `modid:name` everywhere).

**Q: Dedicated server crashes with `NoClassDefFoundError: Screen`?**  
A: Common code referenced `client.*`. Depend only on `FlexiBookAPI` / `content` / `registry`; open UI via item right-click or client-module `ClientModEvents.openBook`.

**Q: `fromMarkup` after `.p()` — raw ignored?**  
A: Non-empty elements take the structured path. For raw-only books, do not mix `h1/p/...`, or use `pRaw` to merge into elements.

**Q: Image missing?**  
A: Confirm `src` namespace/path exists in a loaded pack; size may push it onto a later page.

**Q: Book background / images look fully opaque on soft edges?**  
A: Older builds blitted without blend. Current `AdaptiveBookScreen` enables blend for book background and images. If still wrong: ensure PNG has real alpha (not flattened JPG), no pack override, and F3+T reloaded.

**Q: Where are theme/texture partial exports in the editor?**  
A: Left **Theme** tab: textures under **Textures / background**; theme JSON/pack under **Export theme**. Full six-part pack: top bar. In-game give: `/flexibook give <ns:bookId>` (creative only has blank + built-in demo).

**Q: Custom font not applied?**  
A: Match id to `assets/.../font/*.json`; book-level `defaultFont`, inline `[font]` / `StyleFlags.withFont`. Ensure local font is not cleared with `withFont(null)`. Layout cache includes **resolved** font — reopen the book after changes. Omitting `font` uses `flexibook:default`, not `minecraft:default`.

**Q: Why does it look different from vanilla chat/books?**  
A: Book default intentionally uses mod unihex (`flexibook:default`), same ZIP as the editor, for CJK/Latin and pagination parity. For vanilla look, **explicitly** set book or inline font (e.g. `minecraft:default` / `minecraft:uniform`) and ensure client resources exist.

**Q: Editor preview wrong for custom font ids?**  
A: Default path is still `flexibook:default` unihex. Fonts imported in the **Fonts** panel use browser FontFace **approximation** (advances may differ). Unimported ids fall back to unihex with a banner. After pack export, the game uses real `ttf` providers.

**Q: Open books on the server?**  
A: No. Screen is client-only. Server only gives stacks or mutates components.

**Q: License?**  
A: GPLv3. Comply when depending and redistributing.

**Q: JDK?**  
A: Prefer **JDK 21** for building FlexiBook and compatible mods (NeoForge 1.21.1 toolchain).

---

## 17. Package and symbol index

| Package | Role | For dependents |
|---------|------|----------------|
| `api` | `FlexiBookAPI`, `AdaptiveBookBuilder` | **Stable entry** |
| `content` | Component payload, elements, links, styles; `FlexiBookFonts` / `resolvedFont()` | When reading/writing content |
| `registry` | `ModItems`, `ModDataComponents`, `ModCreativeTabs` | Item/component refs |
| `parse` | `TagParser` | Optional; Builder wraps it |
| `data` | `ExampleBooks` | Reference only, not API contract |
| `layout` | Layout engine | Internal; do not hard-depend |
| `client.theme` | `BookTheme` / `BookThemes` / `BookThemeRegistry` / `ImageFit` | Theme data registerable from common; ReloadListener / Screen **client-only** |
| `client.*` | Screen / LinkHandler / TextureSizeCache | **Client only** |
| `item` | `FlexiBookItem` | Usually no need to subclass |

| Constant | Value |
|----------|--------|
| Mod id | `flexibook` |
| Item id | `flexibook:flexi_book` |
| Component id | `flexibook:adaptive_book_content` |
| Sample action | `flexibook:say_hi` |
| Default theme | `flexibook:default` |
| Default font | `flexibook:default` (`FlexiBookFonts.DEFAULT`, unihex Unifont) |
| Contain theme | `flexibook:contain` |

---

## Changelog (docs)

| Version | Notes |
|---------|--------|
| 1.0.0 | API docs aligned with mod v1 launch |
| 1.0.0+font | Book `defaultFont` + inline/heading `[font]` / `StyleFlags.font` |
| 1.0.0+theme | Registerable `BookTheme`; content `theme`; default/contain samples + JSON |
| 1.0.0+unihex | Built-in `flexibook:default` (unihex Unifont); `resolvedFont()`; default no longer `minecraft:default`; editor same ZIP |
| 1.0.0+en | English API doc as default (`API.md`); Chinese at `API.zh-CN.md` |

Lectern and later features follow the project changelog / issue tracker.
