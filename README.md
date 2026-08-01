# FlexiBook

Adaptive layout books for **Minecraft 26.2 / 26.1.2 / 1.21.4 / 1.21.1 (NeoForge)**, **1.20.1 (Forge)**, and **Fabric 1.20.1 / 1.21.1 / 1.21.4 / 1.21.11**.

> Official NeoForge does **not** ship a 1.20.1 line (it starts at 1.20.2). The 1.20.1 artifact targets **LexForge 47.x**.

Custom DataComponent (26.x / 1.21.x) / ItemStack NBT (1.20.1) + client layout engine + `AdaptiveBookScreen`, keeping a book-like interaction while supporting:

- HTML-subset markup (`[h1]` / `[p]` / `[b]` / `[color]` / `[img]` / `[link]` …)
- Resolve translation keys and reflow pagination when the book opens (current client language)
- Auto scale-down for long content; optional two-column layout
- Safe links: registered command ids only, or confirmed http(s) URLs
- In-book search highlight
- **Built-in default font** `flexibook:default` (unihex Unifont; omitting `font` does **not** fall back to `minecraft:default`)
- `AdaptiveBookBuilder` / `FlexiBookAPI` for other mods
- Data-driven themes (`assets/<ns>/flexibook/themes/<id>.json`)

| Doc | Description |
|-----|-------------|
| **[docs/API.md](./docs/API.md)** | Full API for other mods (fonts §12, data books §14) — **English** |
| [docs/API.zh-CN.md](./docs/API.zh-CN.md) | Full API — **中文** |
| [editor/README.md](./editor/README.md) | Companion real-time editor (Electron; same unihex ZIP as the game) |
| [docs/EDITOR_PACK_GUIDE.md](./docs/EDITOR_PACK_GUIDE.md) | Pack export/import & `/flexibook give` (detailed) |
| [docs/README.md](./docs/README.md) | Design / plans / todo index |
| [README.zh-CN.md](./README.zh-CN.md) | **中文说明** |

## Requirements

| Target | Loader | JDK (toolchain) | Notes |
|--------|--------|-----------------|--------|
| **26.2** | NeoForge 26.2.x (beta) | **25** | DataComponent; `setScreenAndShow` |
| **26.1.2** | NeoForge 26.1.2.x | **25** | DataComponent; `Identifier` + extract GUI |
| **1.21.4** | NeoForge 21.4.x | **21** | DataComponent payload |
| **1.21.1** | NeoForge 21.1.x | **21** | DataComponent payload |
| **1.20.1** | Forge 47.4.x | **17** | NBT payload (`flexibook:content`) |
| **1.21.11-fabric** | Fabric Loader + API | **21** | DataComponent; `Identifier` + hybrid GUI |
| **1.21.4-fabric** | Fabric Loader + API | **21** | DataComponent payload |
| **1.21.1-fabric** | Fabric Loader + API | **21** | DataComponent payload |
| **1.20.1-fabric** | Fabric Loader + API | **17** | NBT payload (`flexibook:content`) |

- **Gradle 8.14.x** (wrapper) + **Fabric Loom 1.13.x**. Stonecutter multi-version; active defaults to **1.21.1** (switch freely).
- Gradle itself should run on **JDK 21** (`org.gradle.java.home` is pinned). JDK 25/26 breaks the Kotlin DSL used by Stonecutter.

## Git branches (by JDK toolchain)

| Branch | Contents |
|--------|----------|
| **`master`** | Full multi-version monorepo (all JDK lines below) |
| **`java17`** | MC **1.20.1** Forge + Fabric only (toolchain **17**) |
| **`java21`** | MC **1.21.1 / 1.21.4** NeoForge + Fabric, **1.21.11** Fabric (toolchain **21**) |
| **`java25`** | MC **26.1.2 / 26.2** NeoForge (toolchain **25**) |

Checkout the branch that matches the JDK you develop against; each slim branch only registers its own Stonecutter version nodes under `versions/`.

## Build

```bash
# JDK for Gradle daemon (must be 21 — see gradle.properties org.gradle.java.home):
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Active version only (default 1.21.1):
./gradlew build
# jar: versions/<active>/build/libs/flexibook-1.0.0+<mc>.jar

# Disk layout groups nodes by JDK toolchain prefix:
#   versions/java17-{1.20.1,1.20.1-fabric}          (JDK 17)
#   versions/java21-{1.21.1,1.21.1-fabric,1.21.4,
#                    1.21.4-fabric,1.21.11-fabric}  (JDK 21)
#   versions/java25-{26.1.2,26.2}                   (JDK 25)
# Project name ≠ MC version: Stonecutter //? if uses the second arg (MC version).

# All version nodes:
./gradlew chiseledBuild
# jars: versions/<node>/build/libs/flexibook-1.0.0+<mc>.jar

# Switch shared sources (processes //? comments in src/):
./gradlew "Set active project to java17-1.20.1"
./gradlew "Set active project to java17-1.20.1-fabric"
./gradlew "Set active project to java21-1.21.1"
./gradlew "Set active project to java21-1.21.1-fabric"
./gradlew "Set active project to java21-1.21.4"
./gradlew "Set active project to java21-1.21.4-fabric"
./gradlew "Set active project to java21-1.21.11-fabric"
./gradlew "Set active project to java25-26.1.2"
./gradlew "Set active project to java25-26.2"

./gradlew :java25-26.2:runClient
./gradlew :java25-26.1.2:runClient
./gradlew :java21-1.21.4:runClient
./gradlew :java21-1.21.1:runClient
./gradlew :java17-1.20.1:runClient
./gradlew :java21-1.21.11-fabric:runClient
./gradlew :java21-1.21.4-fabric:runClient
./gradlew :java21-1.21.1-fabric:runClient
./gradlew :java17-1.20.1-fabric:runClient
```

## In-game

Creative tab **FlexiBook**:

- Blank `flexi_book`
- **FlexiBook Field Guide** sample (EN/ZH lang; adaptive layout, i18n, rich text, images, links, search)

Right-click to open; arrow keys or buttons to turn pages; search at the bottom.

Data-driven books from a resource pack (after enabling the pack, **F3+T**):

```text
/flexibook list
/flexibook give <namespace:bookId>
# e.g. /flexibook give note:note11
```

Requires permission level ≥ 2. Creative only lists blank + built-in demo; custom packs use the command.

## Integrating other mods

Full reference (dependencies, Builder, markup, link safety, components, i18n, theme JSON, FAQ): **[docs/API.md](./docs/API.md)**.

```java
ItemStack book = FlexiBookAPI.builder("my_guide")
    .titleKey("mymod.book.title")
    // .defaultFont(...) optional → runtime resolvedFont() = flexibook:default
    .h1("mymod.book.ch1")
    .p("mymod.book.p1")
    .font("mymod.book.quote", ResourceLocation.withDefaultNamespace("uniform")) // per-paragraph override
    .bullet("mymod.book.b1")
    .image(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/icon.png"), 48, 48)
    .link("mymod.book.click", FlexiBookAPI.commandAction("mymod:open_map"))
    .divider()
    .fromMarkup("[p]mymod.book.extra[/p]") // whole-book raw only if elements are empty
    .buildItem();

// Safe actions only (do not register arbitrary shell)
FlexiBookAPI.registerCommandAction("mymod:open_map", ctx -> ctx.message("mymod.map.opened"));
```

Raw markup only (no explicit `font` still uses the built-in default):

```java
AdaptiveBookContent content = AdaptiveBookContent.ofMarkup(
    new TranslatableText("mymod.book.title"),
    """
    [h1]mymod.book.ch1[/h1]
    [p]mymod.book.p1[/p]
    [bullet]mymod.book.b1[/bullet]
    """
);
// content.resolvedFont() → flexibook:default
ItemStack stack = FlexiBookAPI.createBook(content);
```

DataComponent id: `flexibook:adaptive_book_content`.

Themes register from resource pack JSON: `assets/<namespace>/flexibook/themes/<path>.json` → id `namespace:path`.  
Font priority: `span/heading font` → book-level `font` → **`flexibook:default`** (see [docs/API.md](./docs/API.md) §12).

### Six-part pack layout

```
assets/<ns>/
  lang/*.json
  font/*
  textures/gui/book.png
  textures/item/flexi_book.png   # optional: overrides flexibook item icon when under assets/flexibook/...
  flexibook/
    books/<id>.json              # index: content + theme + optional font
    contents/<id>.json           # body
    themes/<id>.json
```

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"));
```

## Markup cheat sheet

| Tag | Role |
|-----|------|
| `[h1]` `[h2]` | Headings |
| `[p]` | Paragraph (inline styles nest) |
| `[b]` `[i]` `[u]` | Bold / italic / underline |
| `[color=#RRGGBB]` | Color |
| `[font font="ns:id"]` / `[font=ns:id]` | Inline font (overrides book `font`) |
| `[br]` | Line break |
| `[divider]` | Divider |
| `[img src="..." width="48" height="48" /]` | Image (resource path) |
| `[link cmd="modid:action"]` / `[link url="https://..."]` | Safe link |
| `[bullet]` | List item |
| `[div class="..."]` | Box container |

Escape: `\[` `\]`.

## v1 scope

**Done:** DataComponent, dual storage (elements or rawMarkup), TagParser, adaptive layout + cache, single-page Screen, Builder, bilingual sample book, search, data-driven themes (default + contain + JSON reload), built-in `flexibook:default` unihex (same ZIP as editor), standalone editor with Unihex preview, pack export/import, `/flexibook give|list`.

**Not in v1 / simplified:**

- Full lectern support (`FlexiBookAPI.LecternCompat` stub only)
- No hijacking vanilla `written_book`
- In-book WYSIWYG edit, tables, auto TOC, 3D page-turn animation

## License

[GNU GPL v3](./LICENSE) © PhantomDaze
