package io.github.PhantomDaze.flexibook.layout;

/**
 * Resolves translation keys to final display strings.
 * <p>
 * Used during layout for:
 * - Measuring actual text lengths (CJK detection, wrapping)
 * - Search matching
 * - Empty page fallback text
 * <p>
 * MC side: uses Component.translatable + I18n.
 * Editor side: loads lang JSONs (en_us.json etc.) into a map.
 */
public interface TranslationProvider {
    /**
     * Resolve a translation key with optional arguments.
     * If the key does not look like a translation key, implementations should return it as-is (literal).
     */
    String get(String key, Object... args);
}
