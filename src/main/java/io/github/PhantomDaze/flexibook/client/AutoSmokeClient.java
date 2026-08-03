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
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
//? if >=1.21.4 {
/*import net.minecraft.world.flag.FeatureFlagSet;
*///?}
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
 *
 * <p>Enable with {@code -Dflexibook.autoSmoke=true}. Flow: title → enable every pack under
 * {@code dev/smoke/resourcepacks/} → create creative test world → give book → open GUI →
 * read for N seconds → quit.</p>
 *
 * <p>Book under test is read from {@code dev/smoke/book.txt} (first non-empty, non-{@code #} line):
 * {@code demo} for the built-in guide, or a registered id such as {@code fieldnotes:journal}.</p>
 *
 * <p>Optional system properties:
 * <ul>
 *   <li>{@code flexibook.autoSmoke.readSeconds} — default 10</li>
 *   <li>{@code flexibook.autoSmoke.timeoutSeconds} — default 180</li>
 *   <li>{@code flexibook.autoSmoke.resourcePacksDir} — shared packs dir
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
    private static final long WORLD_SEED = 1L;
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

    private AutoSmokeClient() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Called once from the loader's client initialization path. */
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

    /** Called once per client tick by NeoForge or Fabric when enabled. */
    public static void onClientTick() {
        if (!ENABLED || phase == Phase.FINISHED) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (startedAtMs < 0L) {
            startedAtMs = now;
            LOGGER.info("waiting for title screen...");
        }
        if (now - startedAtMs > TIMEOUT_SECONDS * 1000L) {
            fail("timed out after " + TIMEOUT_SECONDS + "s in phase " + phase);
            return;
        }

        try {
            switch (phase) {
                case WAIT_TITLE -> waitForTitle(mc);
                case APPLY_PACKS -> applyPacks(mc);
                case WAIT_PACKS -> waitForPacks(mc);
                case CREATE_WORLD -> createWorld(mc);
                case WAIT_WORLD -> waitForWorld(mc);
                case OPEN_BOOK -> openBook(mc, now);
                case READING -> readBook(mc, now);
                case FINISHED -> {
                }
            }
        } catch (Throwable t) {
            LOGGER.error("uncaught error in phase {}", phase, t);
            fail(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void waitForTitle(Minecraft mc) {
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || mc.level != null || mc.player != null) {
            titleStableTicks = 0;
            return;
        }
        titleStableTicks++;
        if (titleStableTicks >= 40) {
            phase = Phase.APPLY_PACKS;
            LOGGER.info("title ready -> applying smoke resource packs");
        }
    }

    private static void applyPacks(Minecraft mc) {
        if (packsApplied) {
            phase = Phase.WAIT_PACKS;
            return;
        }
        boolean reloading = applySmokeResourcePacks(mc);
        packsApplied = true;
        if (reloading) {
            packsStableTicks = 0;
            phase = Phase.WAIT_PACKS;
            LOGGER.info("resource packs changed -> waiting for reload");
        } else {
            phase = Phase.CREATE_WORLD;
            LOGGER.info("no smoke pack changes -> creating world '{}'", WORLD_ID);
        }
    }

    private static void waitForPacks(Minecraft mc) {
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) {
            packsStableTicks = 0;
            return;
        }
        packsStableTicks++;
        if (packsStableTicks >= 20) {
            phase = Phase.CREATE_WORLD;
            LOGGER.info("packs ready -> creating world '{}'", WORLD_ID);
        }
    }

    private static void createWorld(Minecraft mc) {
        if (createRequested) {
            phase = Phase.WAIT_WORLD;
            return;
        }
        deleteWorldIfPresent(mc);
        LevelSettings settings = new LevelSettings(
                "FlexiBook Smoke",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                //? if >=1.21.4 {
                /*new GameRules(FeatureFlagSet.of()),
                *///?} else {
                new GameRules(),
                //?}
                WorldDataConfiguration.DEFAULT);
        WorldOpenFlows flows = mc.createWorldOpenFlows();
        flows.createFreshLevel(
                WORLD_ID,
                settings,
                //? if >=1.21.4 {
                WorldOptions.testWorldWithRandomSeed(),
                //?} else {
                /*new WorldOptions(WORLD_SEED, false, false),
                *///?}
                //? if >=1.21.4 {
                WorldPresets::createFlatWorldDimensions,
                //?} else {
                /*WorldPresets::createNormalWorldDimensions,
                *///?}
                new TitleScreen());
        createRequested = true;
        phase = Phase.WAIT_WORLD;
        LOGGER.info("createFreshLevel requested");
    }

    private static void waitForWorld(Minecraft mc) {
        if (mc.player == null || mc.level == null || isLoadingScreen(mc.screen)) {
            worldStableTicks = 0;
            return;
        }
        worldStableTicks++;
        if (worldStableTicks >= 20) {
            phase = Phase.OPEN_BOOK;
            LOGGER.info("world ready -> opening book '{}'", readBookSpec());
        }
    }

    private static void openBook(Minecraft mc, long now) {
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
        /*mc.player.getInventory().setSelectedSlot(0);
        *///?} else {
        mc.player.getInventory().selected = 0;
        //?}
        ClientModEvents.openBook(mc.player.getItemInHand(InteractionHand.MAIN_HAND));
        if (!(mc.screen instanceof AdaptiveBookScreen)) {
            ClientModEvents.openBook(book);
        }
        if (!(mc.screen instanceof AdaptiveBookScreen)) {
            fail("AdaptiveBookScreen did not open (screen=" + screenName(mc) + ")");
            return;
        }
        readingStartedAtMs = now;
        phase = Phase.READING;
        LOGGER.info("book open -> reading for {}s", READ_SECONDS);
    }

    private static void readBook(Minecraft mc, long now) {
        if (!(mc.screen instanceof AdaptiveBookScreen)) {
            fail("book screen closed early (screen=" + screenName(mc) + ")");
            return;
        }
        if (now - readingStartedAtMs < READ_SECONDS * 1000L) {
            return;
        }
        pass();
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
                LOGGER.info("synced smoke pack '{}' -> {}", name, dst);
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
        ResourceLocation id = ResourceLocation.tryParse(spec);
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

    private static String screenName(Minecraft mc) {
        return mc.screen == null ? "null" : mc.screen.getClass().getName();
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
            fail("could not delete old world: " + e);
        }
    }

    private static void pass() {
        phase = Phase.FINISHED;
        LOGGER.info("PASS - book stayed open for {}s", READ_SECONDS);
        System.out.println("[FlexiBook AutoSmoke] PASS");
        Minecraft.getInstance().stop();
    }

    private static void fail(String reason) {
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.FINISHED;
        LOGGER.error("FAIL - {}", reason);
        System.err.println("[FlexiBook AutoSmoke] FAIL: " + reason);
        try {
            Minecraft.getInstance().stop();
        } finally {
            Runtime.getRuntime().halt(2);
        }
    }
}
