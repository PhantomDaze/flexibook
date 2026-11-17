# FlexiBook

Adaptive layout books for **Minecraft 1.20.1 → 26.3** on **Forge / NeoForge / Fabric**.

Custom DataComponents (or legacy NBT on 1.20.1), a client layout engine and `AdaptiveBookScreen`, keeping a book-like interaction while supporting:

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

## Branches

`unified` is the **only development branch**. It contains one shared `src/` tree and **13 version nodes** under `versions/`, preprocessed by Stonecutter.

The old per-JDK branches (`main`, `1.20.1`, `java21`, `java25`) are kept only as history snapshots; do not develop on them.

## Version nodes

Every node compiles the same shared source tree. `mod_version = 1.0.1`.

| Node | Minecraft | Loader | JDK toolchain | Build |
|------|-----------|--------|---------------|-------|
| `1.20.1` | 1.20.1 | Forge 47.4.9 | 17 | legacy (Gradle 8.14.3) |
| `1.20.1-fabric` | 1.20.1 | Fabric 0.19.3 | 17 | legacy (Gradle 8.14.3) |
| `1.21.1` | 1.21.1 | NeoForge 21.1.244 | 21 | main (Gradle 9.6.1) |
| `1.21.1-fabric` | 1.21.1 | Fabric 0.19.3 | 21 | main |
| `1.21.4` | 1.21.4 | NeoForge 21.4.157 | 21 | main |
| `1.21.4-fabric` | 1.21.4 | Fabric 0.19.3 | 21 | main |
| `1.21.11-fabric` | 1.21.11 | Fabric 0.19.3 | 21 | main |
| `26.1.2` | 26.1.2 | NeoForge 26.1.2.93 | 25 | main |
| `26.1.2-fabric` | 26.1.2 | Fabric 0.19.3 | 25 | main |
| `26.2` | 26.2 | NeoForge 26.2.0.41-beta | 25 | main |
| `26.2-fabric` | 26.2 | Fabric 0.19.3 | 25 | main |
| `26.3` | 26.3 | NeoForge 26.3.0.110 | 25 | main |
| `26.3-fabric` | 26.3 | Fabric 0.19.3 | 25 | main |

### Plan B: two Gradle builds

The 13 nodes are split across two Gradle builds because the loaders demand incompatible Gradle versions:

- **Main build** — Gradle **9.6.1** + ModDevGradle **2.0.143** + Loom **1.17.17**, 11 nodes (`1.21.x`, `26.x`).
- **Legacy build** — Gradle **8.14.3** + ForgeGradle **6.0.54** + Loom **1.13.6**, 2 nodes (`1.20.1`, `1.20.1-fabric`), driven by `legacy-gradle8/gradlew` with `-c settings.legacy.gradle.kts`.

Root cause: ForgeGradle 6's `MCPRuntime.ExecuteFunction` fails with `project services has been closed` on Gradle 9.6.1 / 8.14.5 — only 8.14.3 still completes the cold artifact generation. Conversely Loom 1.17.17 declares plugin `api-version` 9.5.0 and cannot load on Gradle 8. Hence one shared source tree, two wrappers, two settings files.

## Requirements

- **JDK 21** for the Gradle daemon of all `1.20.1` / `1.21.x` work.
- **JDK 25** for the Gradle daemon of all `26.x` work.
- **JDK 17** toolchain for `1.20.1` nodes (provisioned automatically: `org.gradle.java.installations.auto-download=true`).
- Gradle wrappers are committed: `./gradlew` = 9.6.1, `legacy-gradle8/gradlew` = 8.14.3.
- Stonecutter 0.6.2.

### JDK selection

There is **no hard-coded `org.gradle.java.home`**. The Gradle daemon JVM comes from `JAVA_HOME` on every invocation, so pick it per pass:

| Work | `JAVA_HOME` | Node toolchain |
|------|-------------|----------------|
| `1.20.1*` (legacy build) | JDK 21 | 17 |
| `1.21.x` (main build, Pass A) | JDK 21 | 21 |
| `26.x` (main build, Pass B) | JDK 25 | 25 |

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk   # or java-25-openjdk / java-17-openjdk
```

### NeoForm 26.3-1 workaround (26.3 nodes only)

- **Symptom**: building the 26.3 NeoForge node fails in `createMinecraftArtifacts`, with javac reporting that `HolderSet` overrides `contents()` with weaker access privileges.
- **Cause**: a defect in the artifacts produced by NeoForm configuration `26.3-1`, the only NeoForm config for 26.3, so it cannot be avoided by switching NeoForge versions. **This is an upstream problem, not a defect in this mod.**
- **Fix**: run `bash scripts/patch-neoform-263.sh`. It appends the missing patch to the cached `neoform-26.3-1.zip`, first backing the original up as `<zip>.orig`. The script is idempotent; `--check` only reports status without modifying anything.
- **Impact**: only affects people building the 26.3 NeoForge node from source. Published jars are unaffected, and other nodes (26.1.2 / 26.2, etc.) are unaffected.
- **Removal**: once NeoForm ships a fixed config (e.g. `26.3-2`) or NeoForge changes its baseline, this script and this note can be deleted.

## Build

```bash
# Single node in the main build (JDK21 for 1.21.x, JDK25 for 26.x)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :1.21.4:build --console=plain
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew :26.1.2:build --console=plain

# Whole main build, one pass at a time (build includes tests)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew buildPassA --console=plain
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew buildPassB --console=plain

# Legacy 1.20.1 nodes: Gradle 8.14.3 wrapper + legacy settings file
JAVA_HOME=/usr/lib/jvm/java-21-openjdk legacy-gradle8/gradlew \
    -c settings.legacy.gradle.kts \
    -Pstonecutter.active=1.20.1-fabric \
    :1.20.1:build --console=plain --stacktrace

# Everything (13 nodes, three JVM passes)
bash scripts/build-all.sh          # override JDK21=... / JDK25=... if needed
```

`./gradlew chiseledBuild` also exists, but it visits every node in **one** daemon; use the pass tasks or `scripts/build-all.sh` when 26.x and 1.21.x must run under different JDKs.

No `Set active` step is required: `build.gradle` preprocesses the shared `src/` into `versions/<node>/build/chiseledSrc` for every node on demand.

### Artifacts

```text
versions/<node>/build/libs/flexibook-1.0.1+<mc>.jar          # distributable
versions/<node>/build/libs/flexibook-1.0.1+<mc>-sources.jar  # sources
```

Loaders differ per node as expected (`META-INF/mods.toml` for Forge, `META-INF/neoforge.mods.toml` for NeoForge, `fabric.mod.json` for Fabric).

## Client smoke tests

The harness launches a real client, enables the smoke resource packs, creates a creative world, opens a book and keeps it open for N seconds. Each run ends with `[FlexiBook AutoSmoke] PASS` or `[FlexiBook AutoSmoke] FAIL: …`; the Gradle smoke tasks fail on the FAIL marker even when the game process exits 0.

```bash
# All 13 nodes (Pass L → A → B, each pass with its own JAVA_HOME)
./gradlew smokeAllClients

# One pass at a time
./gradlew smokeAllClientsPassL   # 1.20.1 Forge/Fabric, legacy-gradle8, JDK21
./gradlew smokeAllClientsPassA   # 1.21.x five nodes, JDK21
./gradlew smokeAllClientsPassB   # 26.x six nodes, JDK25

# Compatibility aliases (old IDE configs still work)
./gradlew autoSmokeClients        # original 26.1.2/26.2 four nodes
./gradlew autoSmokeAllClients     # 1.21.x five nodes
./gradlew smokeTestAllClients     # 1.20.1 two nodes
```

Timings (defaults) can be overridden; the legacy `flexibook.smokeTest.*` names are still accepted:

```bash
./gradlew smokeAllClientsPassA \
    -Pflexibook.autoSmoke.readSeconds=10 \
    -Pflexibook.autoSmoke.timeoutSeconds=300
```

### Fixtures (`dev/smoke`)

```text
dev/smoke/
├── book.txt                 # book under test
└── resourcepacks/
    └── note_pack/           # any folder or .zip here is copied into run/resourcepacks/
```

- `book.txt`: the **first non-empty, non-`#` line** wins. `demo` means the built-in `ExampleBooks.demoGuide()`; anything else is a registered id such as `note:note11`.
- Every folder/`.zip` under `dev/smoke/resourcepacks/` is copied into the node's `run/resourcepacks/` and force-enabled by `AutoSmokeClient`.
- `syncSmokeResourcePacks` / `syncSmokeBookId` generate the per-node copies (`versions/<node>/run/`), and every `runClient` / `runClientAutoSmoke` depends on them.

### `run/options.txt` and accessibility onboarding

Before every client run, `prepareSmokeOptions` ensures the node's `run/options.txt` contains `onboardAccessibility:false`.

Reason: on 1.20.2+ a clean `run/` directory opens the accessibility onboarding screen on first launch instead of `TitleScreen`. The smoke harness waits for `TitleScreen` in `WAIT_TITLE`, so without this line it waits forever. (Verified on 1.21.4: 29 s PASS with the line, timeout without it.)

## Testing (local)

Automatic GitHub CI is **disabled** — pushes and pull requests no longer start a workflow. Daily testing runs locally:

```bash
bash scripts/test-all.sh            # unit tests on all 13 nodes + JUnit summary
bash scripts/test-all.sh --smoke    # the above, then the client smoke on all 13 nodes
bash scripts/test-all.sh --smoke-only
```

`test-all.sh` mirrors what the former CI did:

- Runs `:node:test` on every node in three JVM passes — legacy 1.20.1 (Gradle 8.14.3, JDK21), Pass A 1.21.x (Gradle 9.6.1, JDK21), Pass B 26.x (Gradle 9.6.1, JDK25).
- Parses `versions/*/build/test-results/test/*.xml` and prints `tests / failures / errors / skipped` per node plus a total; exits non-zero on any failure, error or missing node.
- `--smoke` needs a display: an active desktop session (`DISPLAY` set) or `xvfb-run` installed (the script then runs under `xvfb-run -a`). It never skips silently.
- JDKs can be overridden with `JDK21=... JDK25=...`.

## GitHub workflows (manual only)

Both workflows are kept for occasional server-side runs and can **only** be started with `workflow_dispatch` (Actions → *Run workflow*):

| Workflow | Trigger | Contents |
|----------|---------|----------|
| [`.github/workflows/build.yml`](./.github/workflows/build.yml) | manual | `build-main` (JDK21+25, Gradle 9.6.1, 11 nodes) and `build-legacy` (JDK21 daemon + JDK17 toolchain, Gradle 8.14.3, 2 nodes); uploads the 13 jars |
| [`.github/workflows/client-smoke.yml`](./.github/workflows/client-smoke.yml) | manual (inputs `passes`, `read_seconds`) | Pass A (1.21.x) and optionally Pass B (26.x) via `xvfb-run` + Mesa llvmpipe |

Notes:

- ForgeGradle needs `piston-meta.mojang.com`; NeoForge MDG/NFRT downloads `version_manifest_v2.json` on every `createMinecraftArtifacts`. Have network available (applies equally to a local `test-all.sh` run).
- For offline local runs, warm the ForgeGradle cache and refresh its 1-hour etag TTL:
  `find ~/.gradle/caches/forge_gradle -type f -exec touch {} +`
- MDG's manifest download has no offline switch; without network, reuse already-generated artifacts.

## Smoke status and limitations

- **All 13 nodes pass the local client smoke** (Pass L, Pass A, Pass B).
- The former 1.20.1 Forge `WAIT_TITLE` timeout was caused by a Forge mod-loading warning: without a `pack.mcmeta`, Forge shows `LoadingErrorScreen` ("failed to load a valid ResourcePackInfo") instead of `TitleScreen`. The legacy build now generates `pack.mcmeta` for the 1.20.1 nodes; `AutoSmokeClient` also logs the actual `screen`/`overlay` classes (and, on timeout, the screen's fields) for future diagnosis.

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

**Done:** DataComponent (legacy NBT on 1.20.1), dual storage (elements or rawMarkup), TagParser, adaptive layout + cache, single-page Screen, Builder, bilingual sample book, search, data-driven themes (default + contain + JSON reload), built-in `flexibook:default` unihex (same ZIP as editor), standalone editor with Unihex preview, pack export/import, `/flexibook give|list`, multi-version build (13 nodes), client smoke harness.

**Not in v1 / simplified:**

- Full lectern support (`FlexiBookAPI.LecternCompat` stub only)
- No hijacking vanilla `written_book`
- In-book WYSIWYG edit, tables, auto TOC, 3D page-turn animation

## License

[GNU GPL v3](./LICENSE) © PhantomDaze
