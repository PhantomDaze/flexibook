package io.github.PhantomDaze.flexibook.content;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.Identifier;

public final class FlexiBookFonts {
    public static final Identifier DEFAULT =
            FlexiBookIds.of(FlexiBookMod.MOD_ID, "default");

    private FlexiBookFonts() {}

    /** Explicit book font if present, otherwise flexibook:default. */
    public static Identifier resolve(java.util.Optional<Identifier> explicit) {
        return explicit != null && explicit.isPresent() ? explicit.get() : DEFAULT;
    }
}
