package io.github.PhantomDaze.flexibook.client.smoke;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.ClientModEvents;
import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Dev-only client smoke: enable every folder/zip pack under {@code run/resourcepacks/}
 * (Gradle copies the shared tree {@code dev/smoke/resourcepacks/} there before
 * {@code runClient}), create/load a creative test world, open a configured book
 * (or the built-in demo), keep the book screen open, then exit.
 * <p>
 * Enabled with {@code -Dflexibook.smokeTest=true} (or Gradle {@code -Pflexibook.smokeTest=true}).
 * <ul>
 *   <li>Book id from {@code run/book.txt} (synced from monorepo {@code dev/smoke/book.txt}):
 *       first non-empty, non-{@code #} line as {@code namespace:path}. Blank / missing →
 *       {@link ExampleBooks#demoGuide()}.</li>
 *   <li>All available {@code file/…} packs under {@code run/resourcepacks/} are force-enabled
 *       automatically (no pack name list required).</li>
 * </ul>
 */
public final class BookClientSmokeTest {
    private static final Logger LOGGER = FlexiBookMod.LOGGER;
    private static final String LEVEL_ID = "flexibook_smoke";
    private static final String LEVEL_NAME = "FlexiBook Smoke";
    private static final long WORLD_SEED = 1L;
    private static final String FILE_PACK_PREFIX = "file/";
    /** Relative to the game run directory (copied from {@code dev/smoke/book.txt}). */
    private static final String BOOK_ID_FILE = "book.txt";

    private enum Phase {
        WAIT_TITLE,
        ENSURE_PACKS,
        WAIT_PACK_RELOAD,
        LOADING_WORLD,
        WAIT_PLAYER,
        OPEN_BOOK,
        READING,
        FINISHED
    }

    private static boolean enabled;
    private static long readMillis = 10_000L;
    private static long overallDeadlineMs;
    private static long phaseDeadlineMs;
    private static long readingUntilMs;
    private static Phase phase = Phase.WAIT_TITLE;
    private static boolean worldStartRequested;
    private static boolean failed;
    private static String bookIdProp = "";
    private static CompletableFuture<Void> packReloadFuture;

    private BookClientSmokeTest() {}

    public static boolean isEnabled() {
        return enabled;
    }

    /** Call once from client init (Fabric) or rely on lazy init from tick (Forge). */
    public static void bootstrap() {
        if (enabled) {
            return;
        }
        if (!Boolean.getBoolean("flexibook.smokeTest")) {
            return;
        }
        enabled = true;
        readMillis = parseLongProp("flexibook.smokeTest.readSeconds", 10L) * 1000L;
        long overallSeconds = parseLongProp("flexibook.smokeTest.timeoutSeconds", 300L);
        overallDeadlineMs = System.currentTimeMillis() + overallSeconds * 1000L;
        phaseDeadlineMs = System.currentTimeMillis() + 60_000L;
        bookIdProp = readBookIdFromFile();
        LOGGER.info(
                "FlexiBook smoke test enabled (read={}s, timeout={}s, bookId={}, packs=all file/*)",
                readMillis / 1000L,
                overallSeconds,
                bookIdProp.isEmpty() ? "<demoGuide>" : bookIdProp);
    }

    /**
     * First non-empty, non-comment line of {@code run/book.txt}. Missing/blank → empty
     * (demo guide). Comments start with {@code #}.
     */
    private static String readBookIdFromFile() {
        Path path = Path.of(BOOK_ID_FILE);
        if (!Files.isRegularFile(path)) {
            LOGGER.info("Smoke test: {} not found; using demo guide", BOOK_ID_FILE);
            return "";
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                return trimmed;
            }
        } catch (IOException e) {
            LOGGER.warn("Smoke test: failed to read {}: {}", BOOK_ID_FILE, e.toString());
        }
        return "";
    }

    public static void onClientTick(Minecraft mc) {
        if (!enabled) {
            bootstrap();
            if (!enabled) {
                return;
            }
        }
        if (phase == Phase.FINISHED || mc == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > overallDeadlineMs) {
            fail(mc, "overall timeout");
            return;
        }

        try {
            tick(mc, now);
        } catch (Throwable t) {
            LOGGER.error("FlexiBook smoke test crashed", t);
            fail(mc, "exception: " + t.getMessage());
        }
    }

    private static void tick(Minecraft mc, long now) {
        switch (phase) {
            case WAIT_TITLE -> waitTitle(mc, now);
            case ENSURE_PACKS -> ensurePacks(mc, now);
            case WAIT_PACK_RELOAD -> waitPackReload(mc, now);
            case LOADING_WORLD -> loadingWorld(mc, now);
            case WAIT_PLAYER -> waitPlayer(mc, now);
            case OPEN_BOOK -> openBook(mc, now);
            case READING -> reading(mc, now);
            case FINISHED -> {
            }
        }
    }

    private static void waitTitle(Minecraft mc, long now) {
        if (!(mc.screen instanceof TitleScreen) || mc.level != null || mc.player != null) {
            return;
        }
        if (mc.getOverlay() != null) {
            return;
        }
        if (worldStartRequested) {
            return;
        }
        // Enable packs on the title screen before joining, so definitions load for OPEN_BOOK.
        phase = Phase.ENSURE_PACKS;
        phaseDeadlineMs = now + 120_000L;
        ensurePacks(mc, now);
    }

    private static void ensurePacks(Minecraft mc, long now) {
        if (now > phaseDeadlineMs) {
            fail(mc, "resource pack enable timeout");
            return;
        }
        if (mc.getOverlay() != null) {
            return;
        }

        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();

        List<String> filePackIds = availableFilePackIds(repo);
        if (filePackIds.isEmpty()) {
            LOGGER.info("Smoke test: no file/* packs under run/resourcepacks; continuing without extras");
            beginWorld(mc, now);
            return;
        }

        Set<String> selected = new LinkedHashSet<>(repo.getSelectedIds());
        boolean changed = false;
        for (String id : filePackIds) {
            if (selected.add(id)) {
                changed = true;
            }
        }
        if (!changed) {
            LOGGER.info("Smoke test: resource packs already enabled {}", filePackIds);
            beginWorld(mc, now);
            return;
        }

        LOGGER.info("Smoke test: enabling all file resource packs {}", filePackIds);
        repo.setSelected(selected);
        // Do not call Options#updateResourcePacks: it reloads and discards the future.
        // Mirror its options sync, then reload once so we can await completion.
        List<String> previous = List.copyOf(mc.options.resourcePacks);
        mc.options.resourcePacks.clear();
        mc.options.incompatibleResourcePacks.clear();
        for (Pack pack : repo.getSelectedPacks()) {
            if (pack.isFixedPosition()) {
                continue;
            }
            mc.options.resourcePacks.add(pack.getId());
            if (!pack.getCompatibility().isCompatible()) {
                mc.options.incompatibleResourcePacks.add(pack.getId());
            }
        }
        mc.options.save();
        if (mc.options.resourcePacks.equals(previous)) {
            LOGGER.info("Smoke test: options already listed packs {}", filePackIds);
            beginWorld(mc, now);
            return;
        }
        packReloadFuture = mc.reloadResourcePacks();
        phase = Phase.WAIT_PACK_RELOAD;
        phaseDeadlineMs = now + 120_000L;
    }

    private static void waitPackReload(Minecraft mc, long now) {
        if (now > phaseDeadlineMs) {
            fail(mc, "resource pack reload timeout");
            return;
        }
        if (packReloadFuture != null && !packReloadFuture.isDone()) {
            return;
        }
        if (packReloadFuture != null && packReloadFuture.isCompletedExceptionally()) {
            fail(mc, "resource pack reload failed");
            return;
        }
        if (mc.getOverlay() != null) {
            return;
        }
        LOGGER.info("Smoke test: resource packs reloaded");
        beginWorld(mc, now);
    }

    private static void beginWorld(Minecraft mc, long now) {
        if (worldStartRequested) {
            return;
        }
        worldStartRequested = true;
        phase = Phase.LOADING_WORLD;
        phaseDeadlineMs = now + 180_000L;
        LOGGER.info("Smoke test: starting world '{}'", LEVEL_ID);
        startWorld(mc);
    }

    private static void startWorld(Minecraft mc) {
        WorldOpenFlows flows = mc.createWorldOpenFlows();
        if (mc.getLevelSource().levelExists(LEVEL_ID)) {
            LOGGER.info("Smoke test: loading existing world");
            flows.loadLevel(null, LEVEL_ID);
            return;
        }
        LOGGER.info("Smoke test: creating fresh creative world");
        LevelSettings settings = new LevelSettings(
                LEVEL_NAME,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        // No structures → faster first join for a smoke run.
        WorldOptions options = new WorldOptions(WORLD_SEED, false, false);
        flows.createFreshLevel(LEVEL_ID, settings, options, WorldPresets::createNormalWorldDimensions);
    }

    private static void loadingWorld(Minecraft mc, long now) {
        if (now > phaseDeadlineMs) {
            fail(mc, "world load timeout");
            return;
        }
        if (mc.player != null && mc.level != null && mc.getOverlay() == null) {
            phase = Phase.WAIT_PLAYER;
            phaseDeadlineMs = now + 30_000L;
            LOGGER.info("Smoke test: world ready, waiting for stable player tick");
        }
    }

    private static void waitPlayer(Minecraft mc, long now) {
        if (now > phaseDeadlineMs) {
            fail(mc, "player ready timeout");
            return;
        }
        if (mc.player == null || mc.level == null || mc.getOverlay() != null) {
            return;
        }
        // One settled tick with no loading overlay before opening UI.
        phase = Phase.OPEN_BOOK;
    }

    private static void openBook(Minecraft mc, long now) {
        if (mc.player == null) {
            fail(mc, "player missing when opening book");
            return;
        }
        ItemStack book = resolveBookStack();
        if (book.isEmpty()) {
            fail(mc, "could not create book stack (bookId="
                    + (bookIdProp.isEmpty() ? "<demoGuide>" : bookIdProp) + ")");
            return;
        }
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, book.copy());
        if (!mc.player.getInventory().add(book.copy())) {
            // Inventory full is unlikely in a fresh creative world; still open held stack.
            LOGGER.warn("Smoke test: could not add spare book to inventory");
        }
        ClientModEvents.openBook(book);
        if (!(mc.screen instanceof AdaptiveBookScreen)) {
            // setScreen is sync on client thread; fail fast if something replaced it.
            fail(mc, "AdaptiveBookScreen did not open (screen=" + screenName(mc) + ")");
            return;
        }
        phase = Phase.READING;
        readingUntilMs = now + readMillis;
        phaseDeadlineMs = readingUntilMs + 5_000L;
        LOGGER.info(
                "Smoke test: book open ({}), reading for {} ms",
                bookIdProp.isEmpty() ? "demoGuide" : bookIdProp,
                readMillis);
    }

    private static ItemStack resolveBookStack() {
        if (bookIdProp == null || bookIdProp.isBlank()) {
            return ExampleBooks.demoGuide();
        }
        ResourceLocation id = FlexiBookIds.tryParse(bookIdProp);
        if (id == null) {
            LOGGER.error("Smoke test: invalid bookId '{}'", bookIdProp);
            return ItemStack.EMPTY;
        }
        if (!BookDefinitionRegistry.isRegistered(id)) {
            LOGGER.error(
                    "Smoke test: bookId '{}' not in definition registry. Known: {}",
                    id,
                    BookDefinitionRegistry.ids());
            return ItemStack.EMPTY;
        }
        return FlexiBookAPI.createBookFromDefinition(id);
    }

    private static void reading(Minecraft mc, long now) {
        if (!(mc.screen instanceof AdaptiveBookScreen)) {
            fail(mc, "book screen closed early (screen=" + screenName(mc) + ")");
            return;
        }
        if (now < readingUntilMs) {
            return;
        }
        String label = bookIdProp.isEmpty() ? "demo book" : bookIdProp;
        pass(mc, label + " rendered for " + (readMillis / 1000L) + "s");
    }

    private static String screenName(Minecraft mc) {
        return mc.screen == null ? "null" : mc.screen.getClass().getName();
    }

    /** All folder/zip packs discovered under {@code run/resourcepacks/} ({@code file/…} ids). */
    private static List<String> availableFilePackIds(PackRepository repo) {
        return repo.getAvailableIds().stream()
                .filter(id -> id.startsWith(FILE_PACK_PREFIX))
                .sorted()
                .collect(Collectors.toList());
    }

    private static void pass(Minecraft mc, String detail) {
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.FINISHED;
        LOGGER.info("FlexiBook smoke test PASSED: {}", detail);
        mc.stop();
    }

    private static void fail(Minecraft mc, String detail) {
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.FINISHED;
        failed = true;
        LOGGER.error("FlexiBook smoke test FAILED: {}", detail);
        try {
            mc.stop();
        } catch (Throwable t) {
            LOGGER.error("Smoke test stop() failed", t);
        }
        // Hard-fail so Gradle/IDE run configuration sees non-zero exit.
        System.exit(1);
    }

    private static long parseLongProp(String key, long defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid {}='{}', using {}", key, raw, defaultValue);
            return defaultValue;
        }
    }
}
