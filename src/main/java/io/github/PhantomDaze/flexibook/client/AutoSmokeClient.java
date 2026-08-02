package io.github.PhantomDaze.flexibook.client;

import io.github.PhantomDaze.flexibook.client.screen.AdaptiveBookScreen;
import io.github.PhantomDaze.flexibook.data.ExampleBooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only client smoke harness.
 * Enable with {@code -Dflexibook.autoSmoke=true}.
 *
 * <p>Flow: title → create creative test world → give demo book → open GUI →
 * read for N seconds → quit. Used by sequential multi-loader IDE/Gradle runs.
 */
public final class AutoSmokeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("FlexiBook AutoSmoke");
    private static final String PROP_ENABLE = "flexibook.autoSmoke";
    private static final String PROP_READ_SECONDS = "flexibook.autoSmoke.readSeconds";
    private static final String PROP_TIMEOUT_SECONDS = "flexibook.autoSmoke.timeoutSeconds";
    private static final String WORLD_ID = "flexibook_auto_smoke";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROP_ENABLE, "false"));
    private static final int READ_SECONDS = Math.max(1, Integer.getInteger(PROP_READ_SECONDS, 10));
    private static final int TIMEOUT_SECONDS = Math.max(30, Integer.getInteger(PROP_TIMEOUT_SECONDS, 180));

    private enum Phase {
        WAIT_TITLE,
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
    private static boolean createRequested;
    private static String failReason;

    private AutoSmokeClient() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Fabric calls this from client initializer; NeoForge uses the event subscriber below. */
    public static void bootstrap() {
        if (ENABLED) {
            LOGGER.info("enabled (readSeconds={}, timeoutSeconds={})", READ_SECONDS, TIMEOUT_SECONDS);
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
                        phase = Phase.CREATE_WORLD;
                        LOGGER.info("title ready → creating world '{}'", WORLD_ID);
                    }
                } else {
                    titleStableTicks = 0;
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
                    LOGGER.info("in world as {} → opening demo book", mc.player.getGameProfile().name());
                }
            }
            case OPEN_BOOK -> {
                if (mc.player == null) {
                    fail("player disappeared before opening book");
                    return;
                }
                var book = ExampleBooks.demoGuide();
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
