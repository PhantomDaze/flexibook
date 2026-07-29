package io.github.PhantomDaze.flexibook.layout;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * MC-backed TranslationProvider using Component.translatable + I18n.
 */
public final class McTranslationProvider implements TranslationProvider {
    @Override
    public String get(String key, Object... args) {
        if (key == null) return "";
        // Use the same heuristic as TranslatableText: if it does not look like a key, treat literal
        if (key.indexOf('.') < 0 || key.indexOf(' ') >= 0) {
            return key;
        }
        if (args == null || args.length == 0) {
            return I18n.get(key);
        }
        return Component.translatable(key, args).getString();
    }
}
