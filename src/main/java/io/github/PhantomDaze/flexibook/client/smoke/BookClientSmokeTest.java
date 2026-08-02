package io.github.PhantomDaze.flexibook.client.smoke;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.client.ClientModEvents;
import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
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

/**
 * Dev-only client smoke: create/load a creative test world, open the demo guide,
 * keep the book screen open for a few seconds, then exit.
 * <p>
 * Enabled with {@code -Dflexibook.smokeTest=true} (or Gradle {@code -Pflexibook.smokeTest=true}).
 */
public final class BookClientSmokeTest {
    private static final Logger LOGGER = FlexiBookMod.LOGGER;
    private static final String LEVEL_ID = "flexibook_smoke";
    private static final String LEVEL_NAME = "FlexiBook Smoke";
    private static final long WORLD_SEED = 1L;

    private enum Phase {
        WAIT_TITLE,
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
        LOGGER.info(
                "FlexiBook smoke test enabled (read={}s, timeout={}s)",
                readMillis / 1000L,
                overallSeconds);
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
        ItemStack book = ExampleBooks.demoGuide();
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, book.copy());
        if (!mc.player.getInventory().add(book.copy())) {
            // Inventory full is unlikely in a fresh creative world; still open held stack.
            LOGGER.warn("Smoke test: could not add spare demo book to inventory");
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
        LOGGER.info("Smoke test: demo book open, reading for {} ms", readMillis);
    }

    private static void reading(Minecraft mc, long now) {
        if (!(mc.screen instanceof AdaptiveBookScreen)) {
            fail(mc, "book screen closed early (screen=" + screenName(mc) + ")");
            return;
        }
        if (now < readingUntilMs) {
            return;
        }
        pass(mc, "demo book rendered for " + (readMillis / 1000L) + "s");
    }

    private static String screenName(Minecraft mc) {
        return mc.screen == null ? "null" : mc.screen.getClass().getName();
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
