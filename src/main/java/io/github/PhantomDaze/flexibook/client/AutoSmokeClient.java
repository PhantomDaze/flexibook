package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
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
    private static final String WORLD_ID = "flexibook_auto_smoke";
    private static final String DEFAULT_BOOK = "demo";
    private static final String BOOK_FILE_NAME = "book.txt";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROP_ENABLE, "false"));
    private static final int READ_SECONDS = Math.max(1, Integer.getInteger(PROP_READ_SECONDS, 10));
    private static final int TIMEOUT_SECONDS = Math.max(30, Integer.getInteger(PROP_TIMEOUT_SECONDS, 180));

    private enum Phase {
        WAIT_TITLE,
        APPLY_PACKS,
        WAIT_PACKS,
        CREATE_WORLD,
        WAIT_WORLD,
        OPEN_BOOK,
        READING,
        FINISHED
    }

    private static Phase phase = Phase.WAIT_TITLE;
    private static long startedAtMs = -1L;
    private static long readingStartedAtMs = -1L;
    private static int titleStableTicks;
    private static int worldStableTicks;
    private static int packsStableTicks;
    private static boolean createRequested;
    private static boolean packsApplied;
    private static String failReason;

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
                // Overlay (resource reload) or non-title screens still bootstrapping.
                if (currentOverlay(mc) != null) {
                    titleStableTicks = 0;
                    return;
                }
                Screen screen = currentScreen(mc);
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
                LevelSettings settings = new LevelSettings(
                        "FlexiBook Smoke",
                        GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                        true,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(
                        WORLD_ID,
                        settings,
                        WorldOptions.testWorldWithRandomSeed(),
                        //? if >=26.2 {
                        /*WorldPresets::createTestWorldDimensions,
                        *///?} else {
                        WorldPresets::createFlatWorldDimensions,
                        //?}
                        new TitleScreen());
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
                    phase = Phase.OPEN_BOOK;
                    LOGGER.info(
                            "in world as {} → opening book '{}'",
                            mc.player.getGameProfile().name(),
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
                mc.player.getInventory().setSelectedSlot(0);
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
                readingStartedAtMs = now;
                phase = Phase.READING;
                LOGGER.info("book open → reading for {}s", READ_SECONDS);
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
        try {
            Files.createDirectories(gamePacksDir);
            for (String name : packFileNames) {
                Path src = smokeDir.resolve(name);
                Path dst = gamePacksDir.resolve(name);
                copyPackEntry(src, dst);
                LOGGER.info("synced smoke pack '{}' → {}", name, dst);
            }
        } catch (IOException e) {
            fail("failed syncing smoke resource packs: " + e.getMessage());
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
            if (Files.exists(dst)) {
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
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("dev").resolve("smoke");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            // Also accept a directory that already is the smoke root (has book.txt or resourcepacks/).
            if (Files.isRegularFile(dir.resolve(BOOK_FILE_NAME))
                    || Files.isDirectory(dir.resolve("resourcepacks"))) {
                return dir;
            }
            dir = dir.getParent();
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

    private static boolean isLoadingScreen(Screen screen) {
        return screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen
                || screen instanceof GenericMessageScreen;
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
        LOGGER.info("PASS — book stayed open for {}s", READ_SECONDS);
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
