package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
//? if >=1.20.2 {
import net.minecraft.client.gui.screens.GenericMessageScreen;
//?}
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
//? if >=1.21.11 && <26.1.2 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?}
//? if <1.21.11 {
/*import net.minecraft.world.level.GameRules;
*///?}
//? if >=1.21.4 && <26.1.2 {
/*import net.minecraft.world.flag.FeatureFlagSet;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Dev-only client smoke harness.
 * Enable with {@code -Dflexibook.autoSmoke=true}.
 *
 * <p>Flow: title → enable {@code dev/smoke/resourcepacks/*} → create creative test world →
 * give book → open GUI → read for N seconds → quit. Used by sequential multi-loader IDE/Gradle runs.
 *
 * <p>Book under test is read from {@code dev/smoke/book.txt} (first non-empty, non-{@code #} line):
 * {@code demo} for the built-in guide, or a registered id such as {@code fieldnotes:journal}.
 *
 * <p>Optional system properties:
 * <ul>
 *   <li>{@code flexibook.autoSmoke.readSeconds} — default 10</li>
 *   <li>{@code flexibook.autoSmoke.timeoutSeconds} — default 180</li>
 *   <li>{@code flexibook.autoSmoke.resourcePacksDir} — absolute/relative path to shared packs dir
 *       (default: walk up from {@code user.dir} for {@code dev/smoke/resourcepacks})</li>
 *   <li>{@code flexibook.autoSmoke.dir} — smoke root containing {@code book.txt} + {@code resourcepacks/}
 *       (default: walk up for {@code dev/smoke})</li>
 * </ul>
 */
public final class AutoSmokeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("FlexiBook AutoSmoke");
    private static final String PROP_ENABLE = "flexibook.autoSmoke";
    private static final String PROP_READ_SECONDS = "flexibook.autoSmoke.readSeconds";
    private static final String PROP_TIMEOUT_SECONDS = "flexibook.autoSmoke.timeoutSeconds";
    private static final String PROP_PACKS_DIR = "flexibook.autoSmoke.resourcePacksDir";
    private static final String PROP_SMOKE_DIR = "flexibook.autoSmoke.dir";
    // Deterministic screenshot mode (dev only): writes <prefix>-page0.png / <prefix>-page1.png.
    private static final String PROP_SCREENSHOT_DIR = "flexibook.autoSmoke.screenshotDir";
    private static final String PROP_SCREENSHOT_PREFIX = "flexibook.autoSmoke.screenshotPrefix";
    private static final String PROP_SCREENSHOT_SETTLE_TICKS = "flexibook.autoSmoke.screenshotSettleTicks";
    private static final String WORLD_ID = "flexibook_auto_smoke";
    // Used by the pre-1.21.4 WorldOptions constructor.
    private static final long WORLD_SEED = 1L;
    private static final String DEFAULT_BOOK = "demo";
    private static final String BOOK_FILE_NAME = "book.txt";

    private static final boolean ENABLED = Boolean.parseBoolean(propOrEnv(PROP_ENABLE, "FLEXIBOOK_AUTOSMOKE", "false"));
    private static final int READ_SECONDS = Math.max(1, intPropOrEnv(PROP_READ_SECONDS, "FLEXIBOOK_AUTOSMOKE_READ_SECONDS", 10));
    private static final int TIMEOUT_SECONDS = Math.max(30, intPropOrEnv(PROP_TIMEOUT_SECONDS, "FLEXIBOOK_AUTOSMOKE_TIMEOUT_SECONDS", 180));
    // Settling frames before each capture: let font atlas / textures / animations finish.
    private static final int SETTLE_TICKS = Math.max(5, intPropOrEnv(PROP_SCREENSHOT_SETTLE_TICKS, "FLEXIBOOK_SCREENSHOT_SETTLE_TICKS", 30));
    private static final int SHOT_TIMEOUT_TICKS = 400;
    private static final Path SCREENSHOT_DIR = resolveScreenshotDir();
    private static final String SCREENSHOT_PREFIX = propOrEnv(PROP_SCREENSHOT_PREFIX, "FLEXIBOOK_SCREENSHOT_PREFIX", "book");
    private static final boolean SCREENSHOTS_ENABLED = SCREENSHOT_DIR != null;

    private enum Phase {
        WAIT_TITLE,
        APPLY_PACKS,
        WAIT_PACKS,
        CREATE_WORLD,
        WAIT_WORLD,
        OPEN_BOOK,
        READING,
        SHOT_SETTLE_0,
        SHOT_WAIT_0,
        SHOT_SETTLE_1,
        SHOT_WAIT_1,
        FINISHED
    }

    private static Phase phase = Phase.WAIT_TITLE;
    private static long startedAtMs = -1L;
    private static long readingStartedAtMs = -1L;
    private static int titleStableTicks;
    private static String lastTitleScreenSignature;
    private static int worldStableTicks;
    private static int packsStableTicks;
    private static boolean createRequested;
    private static boolean packsApplied;
    private static String failReason;
    private static boolean worldStateApplied;
    private static int shotTicks;
    private static int shotWaitTicks;
    private static int pendingShots;

    private AutoSmokeClient() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Fabric calls this from client initializer; NeoForge uses the event subscriber below. */
    public static void bootstrap() {
        if (ENABLED) {
            LOGGER.info(
                    "enabled (readSeconds={}, timeoutSeconds={}, book={} from {}, packsDir={})",
                    READ_SECONDS,
                    TIMEOUT_SECONDS,
                    readBookSpec(),
                    resolveBookFile(),
                    resolveSmokePacksDir());
        }
    }

    public static void onClientTick() {
        if (!ENABLED || phase == Phase.FINISHED) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (startedAtMs < 0L) {
            startedAtMs = now;
            LOGGER.info("waiting for title screen…");
        }

        if (now - startedAtMs > TIMEOUT_SECONDS * 1000L) {
            // 诊断：1.20.1 Forge 曾在此超时但看不到屏幕内容，打印实际类名便于定位。
            logTimeoutDiagnostics(mc);
            fail("timed out after " + TIMEOUT_SECONDS + "s in phase " + phase);
            return;
        }

        try {
            tick(mc, now);
        } catch (Throwable t) {
            LOGGER.error("uncaught error in phase {}", phase, t);
            fail(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void tick(Minecraft mc, long now) {
        switch (phase) {
            case WAIT_TITLE -> {
                Screen screen = currentScreen(mc);
                Object overlay = currentOverlay(mc);
                logTitleScreenChanges(screen, overlay);
                // Overlay (resource reload) or non-title screens still bootstrapping.
                if (overlay != null) {
                    titleStableTicks = 0;
                    return;
                }
                if (screen instanceof TitleScreen) {
                    titleStableTicks++;
                    // a few ticks on title so sound/assets settle
                    if (titleStableTicks >= 40) {
                        phase = Phase.APPLY_PACKS;
                        LOGGER.info("title ready → applying smoke resource packs");
                    }
                } else {
                    titleStableTicks = 0;
                }
            }
            case APPLY_PACKS -> {
                if (packsApplied) {
                    phase = Phase.WAIT_PACKS;
                    return;
                }
                boolean reloading = applySmokeResourcePacks(mc);
                packsApplied = true;
                if (reloading) {
                    packsStableTicks = 0;
                    phase = Phase.WAIT_PACKS;
                    LOGGER.info("resource packs changed → waiting for reload");
                } else {
                    phase = Phase.CREATE_WORLD;
                    LOGGER.info("no smoke pack changes → creating world '{}'", WORLD_ID);
                }
            }
            case WAIT_PACKS -> {
                if (currentOverlay(mc) != null) {
                    packsStableTicks = 0;
                    return;
                }
                if (!(currentScreen(mc) instanceof TitleScreen)) {
                    packsStableTicks = 0;
                    return;
                }
                packsStableTicks++;
                if (packsStableTicks >= 20) {
                    phase = Phase.CREATE_WORLD;
                    LOGGER.info("packs ready → creating world '{}'", WORLD_ID);
                }
            }
            case CREATE_WORLD -> {
                if (createRequested) {
                    phase = Phase.WAIT_WORLD;
                    return;
                }
                if (!(currentScreen(mc) instanceof TitleScreen) && mc.player == null) {
                    // already transitioning
                    phase = Phase.WAIT_WORLD;
                    return;
                }
                deleteWorldIfPresent(mc);
                //? if >=26.1.2 {
                LevelSettings settings = new LevelSettings(
                        "FlexiBook Smoke",
                        GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                        true,
                        WorldDataConfiguration.DEFAULT);
                //?}
                //? if >=1.21.4 && <26.1.2 {
                /*LevelSettings settings = new LevelSettings(
                        "FlexiBook Smoke",
                        GameType.CREATIVE,
                        false,
                        Difficulty.PEACEFUL,
                        true,
                        new GameRules(FeatureFlagSet.of()),
                        WorldDataConfiguration.DEFAULT);
                *///?}
                //? if <1.21.4 {
                /*LevelSettings settings = new LevelSettings(
                        "FlexiBook Smoke",
                        GameType.CREATIVE,
                        false,
                        Difficulty.PEACEFUL,
                        true,
                        new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                *///?}
                mc.createWorldOpenFlows().createFreshLevel(
                        WORLD_ID,
                        settings,
                        //? if >=1.21.4 {
                        SCREENSHOTS_ENABLED
                                ? new WorldOptions(WORLD_SEED, false, false)
                                : WorldOptions.testWorldWithRandomSeed(),
                        //?} else {
                        /*new WorldOptions(WORLD_SEED, false, false),
                        *///?}
                        //? if >=26.2 {
                        /*WorldPresets::createTestWorldDimensions
                        *///?} else {
                        //? if >=1.21.4 {
                        WorldPresets::createFlatWorldDimensions
                        //?} else {
                        /*WorldPresets::createNormalWorldDimensions
                        *///?}
                        //?}
                        //? if >=1.20.2 {
                        , new TitleScreen()
                        //?}
                        );
                createRequested = true;
                phase = Phase.WAIT_WORLD;
                LOGGER.info("createFreshLevel requested");
            }
            case WAIT_WORLD -> {
                if (mc.player == null || mc.level == null) {
                    worldStableTicks = 0;
                    return;
                }
                if (isLoadingScreen(currentScreen(mc))) {
                    worldStableTicks = 0;
                    return;
                }
                // Prefer fully closed UI (in-game), allow a couple ticks if a toast remains.
                worldStableTicks++;
                if (worldStableTicks >= 20) {
                    if (SCREENSHOTS_ENABLED && !worldStateApplied) {
                        applyDeterministicWorldState(mc);
                        worldStateApplied = true;
                    }
                    phase = Phase.OPEN_BOOK;
                    LOGGER.info(
                            "in world as {} → opening book '{}'",
                            //? if >=1.21.11 {
                            mc.player.getGameProfile().name(),
                            //?} else {
                            /*mc.player.getGameProfile().getName(),
                            *///?}
                            readBookSpec());
                }
            }
            case OPEN_BOOK -> {
                if (mc.player == null) {
                    fail("player disappeared before opening book");
                    return;
                }
                ItemStack book = resolveSmokeBook();
                if (book.isEmpty()) {
                    return; // fail() already called
                }
                mc.player.getInventory().setItem(0, book);
                //? if >=1.21.11 {
                mc.player.getInventory().setSelectedSlot(0);
                //?} else {
                /*mc.player.getInventory().selected = 0;
                *///?}
                // open via the same path as item use
                ClientModEvents.openBook(mc.player.getMainHandItem().isEmpty()
                        ? book
                        : mc.player.getMainHandItem());
                // If hotbar set didn't stick before open, open the stack we built.
                if (!(currentScreen(mc) instanceof AdaptiveBookScreen)) {
                    ClientModEvents.openBook(book);
                }
                if (!(currentScreen(mc) instanceof AdaptiveBookScreen)) {
                    fail("AdaptiveBookScreen did not open");
                    return;
                }
                AdaptiveBookScreen screen = (AdaptiveBookScreen) currentScreen(mc);
                java.util.List<io.github.PhantomDaze.flexibook.layout.RenderedPage> pages = screenPages(screen);
                if (pages.isEmpty() || pages.get(0).elements().isEmpty()) {
                    fail("book rendered no pages/elements");
                    return;
                }
                LOGGER.info("book open → {} page(s) / {} element(s) on page 0",
                        pages.size(), pages.get(0).elements().size());
                if (pages.size() > 1) {
                    int before = screenPageIndex(screen);
                    changeScreenPage(screen, 1);
                    int after = screenPageIndex(screen);
                    if (after == before) {
                        fail("page turn did not advance page index");
                        return;
                    }
                    changeScreenPage(screen, -1);
                    LOGGER.info("page turn verified ({} -> {}, back to {})", before, after, screenPageIndex(screen));
                }
                if (SCREENSHOTS_ENABLED) {
                    shotTicks = 0;
                    phase = Phase.SHOT_SETTLE_0;
                    LOGGER.info("book open → screenshots enabled, settling {} ticks before page 0", SETTLE_TICKS);
                } else {
                    readingStartedAtMs = now;
                    phase = Phase.READING;
                    LOGGER.info("book open → reading for {}s", READ_SECONDS);
                }
            }
            case READING -> {
                if (!(currentScreen(mc) instanceof AdaptiveBookScreen)) {
                    fail("book screen closed early");
                    return;
                }
                if (now - readingStartedAtMs >= READ_SECONDS * 1000L) {
                    pass();
                }
            }
            case SHOT_SETTLE_0 -> {
                if (!(currentScreen(mc) instanceof AdaptiveBookScreen)) {
                    fail("book screen closed before page 0 screenshot");
                    return;
                }
                if (++shotTicks >= SETTLE_TICKS) {
                    captureScreenshot(mc, "page0");
                    shotWaitTicks = 0;
                    phase = Phase.SHOT_WAIT_0;
                }
            }
            case SHOT_WAIT_0 -> {
                if (++shotWaitTicks > SHOT_TIMEOUT_TICKS) {
                    fail("page 0 screenshot did not complete");
                    return;
                }
                if (pendingShots == 0 && shotWaitTicks >= 2) {
                    AdaptiveBookScreen screen = (AdaptiveBookScreen) currentScreen(mc);
                    changeScreenPage(screen, 1);
                    shotTicks = 0;
                    phase = Phase.SHOT_SETTLE_1;
                    LOGGER.info("page 0 screenshot done → page 1");
                }
            }
            case SHOT_SETTLE_1 -> {
                if (!(currentScreen(mc) instanceof AdaptiveBookScreen)) {
                    fail("book screen closed before page 1 screenshot");
                    return;
                }
                if (++shotTicks >= SETTLE_TICKS) {
                    captureScreenshot(mc, "page1");
                    shotWaitTicks = 0;
                    phase = Phase.SHOT_WAIT_1;
                }
            }
            case SHOT_WAIT_1 -> {
                if (++shotWaitTicks > SHOT_TIMEOUT_TICKS) {
                    fail("page 1 screenshot did not complete");
                    return;
                }
                if (pendingShots == 0 && shotWaitTicks >= 2) {
                    LOGGER.info("screenshots complete → {}", SCREENSHOT_DIR);
                    pass();
                }
            }
            case FINISHED -> {
                // no-op
            }
        }
    }

    /**
     * Copy every folder/zip under the shared smoke packs dir into the game {@code resourcepacks/}
     * directory and enable them. Returns {@code true} if a resource reload was kicked off.
     */
    private static boolean applySmokeResourcePacks(Minecraft mc) {
        Path smokeDir = resolveSmokePacksDir();
        if (!Files.isDirectory(smokeDir)) {
            LOGGER.info("smoke resourcepacks dir missing (skip): {}", smokeDir);
            return false;
        }

        List<String> packFileNames = listSmokePackEntries(smokeDir);
        if (packFileNames.isEmpty()) {
            LOGGER.info("smoke resourcepacks dir empty (skip): {}", smokeDir);
            return false;
        }

        Path gamePacksDir = mc.getResourcePackDirectory();
        // Gradle 的 syncSmokeResourcePacks 会把包直接放进 run/resourcepacks；此时
        // smokeDir 与 gamePacksDir 是同一个目录，复制会自我递归并抛 NoSuchFileException。
        boolean packsAlreadyInPlace = smokeDir.toAbsolutePath().normalize()
                .equals(gamePacksDir.toAbsolutePath().normalize());
        try {
            Files.createDirectories(gamePacksDir);
            for (String name : packFileNames) {
                Path src = smokeDir.resolve(name);
                Path dst = gamePacksDir.resolve(name);
                if (packsAlreadyInPlace) {
                    LOGGER.info("smoke pack already in game dir: {}", dst);
                    continue;
                }
                copyPackEntry(src, dst);
                LOGGER.info("synced smoke pack '{}' → {}", name, dst);
            }
        } catch (IOException e) {
            LOGGER.error("failed syncing smoke resource packs", e);
            fail("failed syncing smoke resource packs: " + e);
            return false;
        }

        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();

        Set<String> selected = new LinkedHashSet<>(repo.getSelectedIds());
        List<String> enabled = new ArrayList<>();
        boolean changed = false;
        for (String name : packFileNames) {
            String id = "file/" + name;
            if (!repo.isAvailable(id)) {
                LOGGER.warn("smoke pack not discovered by repository: {} (under {})", id, gamePacksDir);
                continue;
            }
            if (selected.add(id)) {
                changed = true;
            }
            enabled.add(id);
        }

        if (!changed) {
            LOGGER.info("smoke packs already selected: {}", enabled);
            return false;
        }

        repo.setSelected(selected);
        // Writes options + reloads resources when the selection list changed.
        mc.options.updateResourcePacks(repo);
        LOGGER.info("enabled smoke resource packs: {}", enabled);
        return true;
    }

    private static List<String> listSmokePackEntries(Path smokeDir) {
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(smokeDir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        if (name.startsWith(".")) {
                            return;
                        }
                        if (name.equalsIgnoreCase("README.md") || name.equalsIgnoreCase("README.txt")) {
                            return;
                        }
                        if (Files.isDirectory(path) || name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                            names.add(name);
                        } else {
                            LOGGER.info("ignoring non-pack entry in smoke resourcepacks: {}", name);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("could not list {}: {}", smokeDir, e.toString());
        }
        return names;
    }

    private static void copyPackEntry(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            // 不能跟随链接判断存在性：损坏的符号链接 exists=false，但 createDirectories 会报
            // FileAlreadyExistsException。这里按链接本身判断并先删除。
            if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
                deleteRecursively(dst);
            }
            copyDirectory(src, dst);
        } else {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void copyDirectory(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = dst.resolve(src.relativize(dir).toString());
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = dst.resolve(src.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Prefer explicit JVM prop; otherwise {@code <smokeDir>/resourcepacks}
     * ({@link #resolveSmokeDir()}).
     */
    private static Path resolveSmokePacksDir() {
        String prop = System.getProperty(PROP_PACKS_DIR, "").trim();
        if (!prop.isEmpty()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        return resolveSmokeDir().resolve("resourcepacks");
    }

    /** {@code dev/smoke/book.txt} — first non-empty, non-comment line. */
    private static Path resolveBookFile() {
        return resolveSmokeDir().resolve(BOOK_FILE_NAME);
    }

    /** System property first, then an optional environment variable. */
    private static String propOrEnv(String property, String env, String fallback) {
        String value = System.getProperty(property, "").trim();
        if (value.isEmpty() && env != null) {
            value = System.getenv().getOrDefault(env, "").trim();
        }
        return value.isEmpty() ? fallback : value;
    }

    private static int intPropOrEnv(String property, String env, int fallback) {
        String value = propOrEnv(property, env, "");
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("invalid integer '{}' for {} — using {}", value, property, fallback);
            return fallback;
        }
    }

    /** {@code null} unless screenshot mode is requested. */
    private static Path resolveScreenshotDir() {
        String dir = propOrEnv(PROP_SCREENSHOT_DIR, "FLEXIBOOK_SCREENSHOT_DIR", "");
        return dir.isEmpty() ? null : Path.of(dir).toAbsolutePath().normalize();
    }

    /**
     * Prefer {@code flexibook.autoSmoke.dir}; else walk up from {@code user.dir} for
     * {@code dev/smoke} (repo root or {@code versions/&lt;node&gt;}).
     */
    private static Path resolveSmokeDir() {
        String prop = System.getProperty(PROP_SMOKE_DIR, "").trim();
        if (!prop.isEmpty()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        // 先找规范的 dev/smoke（仓库根或 versions/<node>），再退回 run 目录本身。
        // 否则 run/ 里的 resourcepacks/ 会让解析结果变成 run 自己。
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("dev").resolve("smoke");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            // Also accept a directory that already is the smoke root (has book.txt or resourcepacks/).
            if (Files.isRegularFile(dir.resolve(BOOK_FILE_NAME))
                    || Files.isDirectory(dir.resolve("resourcepacks"))) {
                return dir;
            }
        }
        return Path.of("dev", "smoke").toAbsolutePath().normalize();
    }

    /**
     * Reads {@code dev/smoke/book.txt}. Supports {@code #} comments and blank lines.
     * Missing/empty file → {@code demo}.
     */
    private static String readBookSpec() {
        Path file = resolveBookFile();
        if (!Files.isRegularFile(file)) {
            LOGGER.warn("smoke book file missing ({}), using '{}'", file, DEFAULT_BOOK);
            return DEFAULT_BOOK;
        }
        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Allow trailing inline comment: fieldnotes:journal # sample
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash).trim();
                }
                if (!line.isEmpty()) {
                    return line;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("could not read {}: {} — using '{}'", file, e.toString(), DEFAULT_BOOK);
        }
        LOGGER.warn("smoke book file empty ({}), using '{}'", file, DEFAULT_BOOK);
        return DEFAULT_BOOK;
    }

    private static ItemStack resolveSmokeBook() {
        String spec = readBookSpec();
        if (DEFAULT_BOOK.equalsIgnoreCase(spec)) {
            return ExampleBooks.demoGuide();
        }
        Identifier id = Identifier.tryParse(spec);
        if (id == null) {
            fail("invalid book id in " + resolveBookFile() + ": " + spec);
            return ItemStack.EMPTY;
        }
        if (!BookDefinitionRegistry.isRegistered(id)) {
            fail("unknown book id (not in books/ registry): " + id
                    + " (from " + resolveBookFile() + ") — available=" + BookDefinitionRegistry.ids());
            return ItemStack.EMPTY;
        }
        return FlexiBookAPI.createBookFromDefinition(id);
    }

    /**
     * Freeze the parts of the world scene that would otherwise differ between runs:
     * daylight/weather cycles, mob spawning, random ticks, fire, and weather.
     * Uses the integrated server's command dispatcher; failures are non-fatal.
     */
    private static void applyDeterministicWorldState(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOGGER.warn("no integrated server available; skipping deterministic world setup");
            return;
        }
        String[] commands = {
                "gamerule doDaylightCycle false",
                "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false",
                "gamerule randomTickSpeed 0",
                "gamerule doFireTick false",
                "gamerule mobGriefing false",
                "time set 6000",
                "weather clear",
                "kill @e[type=!player]",
                // 固定相机：站在固定位置的方块上，抬头看天。书页打开后单人游戏会暂停实体，
                // 因此相机不再移动；纯天空背景也让书页外区域逐像素稳定。
                "setblock 0 199 0 minecraft:stone",
                "tp @p 0.5 200 0.5 0 -90",
        };
        for (String command : commands) {
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            } catch (Throwable t) {
                LOGGER.warn("deterministic world setup command '{}' failed: {}", command, t.toString());
            }
        }
        LOGGER.info("deterministic world state applied (time 6000, clear, cycles off)");
    }

    /** Captures the render target currently holding the last completed frame. */
    private static void captureScreenshot(Minecraft mc, String pageLabel) {
        Path path = SCREENSHOT_DIR.resolve(SCREENSHOT_PREFIX + "-" + pageLabel + ".png");
        try {
            Files.createDirectories(SCREENSHOT_DIR);
        } catch (IOException e) {
            fail("could not create screenshot dir " + SCREENSHOT_DIR + ": " + e);
            return;
        }
        pendingShots++;
        RenderTarget target = mainRenderTarget(mc);
        LOGGER.info("capturing {} ({}x{} window, target {}x{})", path,
                mc.getWindow().getWidth(), mc.getWindow().getHeight(), target.width, target.height);
        //? if >=1.21.11 {
        Screenshot.takeScreenshot(target, image -> saveScreenshot(image, path));
        //?} else {
        /*NativeImage image = Screenshot.takeScreenshot(target);
        saveScreenshot(image, path);
        *///?}
    }

    /** 26.2 把主渲染目标移到了 GameRenderer#mainRenderTarget。 */
    private static RenderTarget mainRenderTarget(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gameRenderer.mainRenderTarget();
        *///?} else {
        return mc.getMainRenderTarget();
        //?}
    }

    private static void saveScreenshot(NativeImage image, Path path) {
        try {
            image.writeToFile(path);
            LOGGER.info("screenshot written: {} ({}x{})", path, image.getWidth(), image.getHeight());
            System.out.println("[FlexiBook AutoSmoke] SHOT " + path);
        } catch (IOException e) {
            fail("screenshot write failed for " + path + ": " + e);
        } finally {
            try {
                image.close();
            } catch (Throwable ignored) {
                // best-effort release
            }
            pendingShots--;
        }
    }

    private static boolean isLoadingScreen(Screen screen) {
        return screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen
                //? if >=1.20.2 {
                || screen instanceof GenericMessageScreen
                //?}
                ;
    }

    /**
     * 记录 WAIT_TITLE 期间实际出现的 screen/overlay 类名（只在变化时输出），
     * 便于在 Forge 1.20.1 等“看得到游戏却认不出 TitleScreen”的环境里定位问题。
     */
    private static void logTitleScreenChanges(Screen screen, Object overlay) {
        String signature = describe(screen) + " | " + describe(overlay);
        if (!signature.equals(lastTitleScreenSignature)) {
            lastTitleScreenSignature = signature;
            LOGGER.info("WAIT_TITLE: screen={} overlay={}", describe(screen), describe(overlay));
        }
    }

    /** 超时瞬间打印完整状态，避免只有一句 "timed out" 无从排查。 */
    private static void logTimeoutDiagnostics(Minecraft mc) {
        LOGGER.error(
                "timeout diagnostics: phase={} screen={} overlay={} level={} player={} titleStableTicks={}",
                phase,
                describe(currentScreen(mc)),
                describe(currentOverlay(mc)),
                mc.level == null ? "null" : mc.level.getClass().getName(),
                mc.player == null ? "null" : mc.player.getClass().getName(),
                titleStableTicks);
        logScreenDetails(currentScreen(mc));
    }

    /**
     * 反射打印当前 Screen 的实例字段。用于 Forge 的 LoadingErrorScreen 这类
     * 只在界面上展示错误、不写日志的屏幕（例如 mod 加载 warning/error 列表）。
     * 反射失败只记日志，不影响 smoke 流程。
     */
    private static void logScreenDetails(Screen screen) {
        if (screen == null) {
            return;
        }
        LOGGER.error("screen details: {}", screen.getClass().getName());
        for (java.lang.reflect.Field field : screen.getClass().getDeclaredFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(screen);
                LOGGER.error("  {} = {}", field.getName(), value);
                if (value instanceof Iterable<?> iterable) {
                    int index = 0;
                    for (Object item : iterable) {
                        LOGGER.error("    [{}] {}", index++, describeItem(item));
                        if (index >= 10) {
                            LOGGER.error("    ... (truncated)");
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("  {} = <unreadable: {}>", field.getName(), t.toString());
            }
        }
    }

    private static String describeItem(Object item) {
        if (item == null) {
            return "null";
        }
        try {
            java.lang.reflect.Method format = item.getClass().getMethod("formatToString");
            return String.valueOf(format.invoke(item));
        } catch (Throwable ignored) {
            return String.valueOf(item);
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<io.github.PhantomDaze.flexibook.layout.RenderedPage> screenPages(
            AdaptiveBookScreen screen) {
        try {
            java.lang.reflect.Field f = AdaptiveBookScreen.class.getDeclaredField("pages");
            f.setAccessible(true);
            return (java.util.List<io.github.PhantomDaze.flexibook.layout.RenderedPage>) f.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read AdaptiveBookScreen.pages", e);
        }
    }

    private static int screenPageIndex(AdaptiveBookScreen screen) {
        try {
            java.lang.reflect.Field f = AdaptiveBookScreen.class.getDeclaredField("pageIndex");
            f.setAccessible(true);
            return f.getInt(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read AdaptiveBookScreen.pageIndex", e);
        }
    }

    private static void changeScreenPage(AdaptiveBookScreen screen, int delta) {
        try {
            java.lang.reflect.Method m = AdaptiveBookScreen.class.getDeclaredMethod("changePage", int.class);
            m.setAccessible(true);
            m.invoke(screen, delta);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot call AdaptiveBookScreen.changePage", e);
        }
    }

    private static Screen currentScreen(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.screen();
        *///?} else {
        return mc.screen;
        //?}
    }

    private static Object currentOverlay(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.overlay();
        *///?} else {
        return mc.getOverlay();
        //?}
    }

    private static void deleteWorldIfPresent(Minecraft mc) {
        LevelStorageSource source = mc.getLevelSource();
        if (!source.levelExists(WORLD_ID)) {
            return;
        }
        LOGGER.info("deleting previous world '{}'", WORLD_ID);
        try {
            LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD_ID);
            try {
                access.deleteLevel();
            } finally {
                access.close();
            }
        } catch (Exception e) {
            LOGGER.warn("could not delete old world (continuing): {}", e.toString());
        }
    }

    private static void pass() {
        phase = Phase.FINISHED;
        LOGGER.info(SCREENSHOTS_ENABLED
                ? "PASS — screenshots captured for {} (page0/page1)"
                : "PASS — book stayed open for {}s", SCREENSHOTS_ENABLED ? SCREENSHOT_PREFIX : READ_SECONDS);
        // Distinct marker for orchestrator scripts / CI log scraping.
        System.out.println("[FlexiBook AutoSmoke] PASS");
        Minecraft.getInstance().stop();
    }

    private static void fail(String reason) {
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.FINISHED;
        failReason = reason;
        LOGGER.error("FAIL — {}", reason);
        System.err.println("[FlexiBook AutoSmoke] FAIL: " + reason);
        try {
            Minecraft.getInstance().stop();
        } catch (Throwable t) {
            LOGGER.error("stop() failed after smoke failure", t);
            Runtime.getRuntime().halt(2);
        }
    }
}
