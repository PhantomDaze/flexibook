package io.github.PhantomDaze.flexibook.api;

import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.content.TranslatableText;
import io.github.PhantomDaze.flexibook.parse.TagParser;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import io.github.PhantomDaze.flexibook.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder for adaptive books. Prefers structured elements; use {@link #fromMarkup}
 * when you already have tag source text.
 * <p>
 * Fonts: {@link #defaultFont(ResourceLocation)} sets the book-wide font;
 * {@link #font(String, ResourceLocation)} / markup {@code [font font="ns:id"]} set per-run fonts.
 * <p>
 * Theme: {@link #theme(ResourceLocation)} selects a registered book chrome/layout theme
 * ({@code flexibook:default} when omitted).
 */
public final class AdaptiveBookBuilder {
    private TranslatableText title = new TranslatableText("flexibook.book.untitled");
    private final List<BookElement> elements = new ArrayList<>();
    private String rawMarkup;
    private ResourceLocation defaultFont;
    private ResourceLocation themeId;

    public AdaptiveBookBuilder(String guideId) {
        // guideId reserved for future TOC / identity; unused in v1 storage
    }

    public AdaptiveBookBuilder titleKey(String key, String... args) {
        this.title = TranslatableText.of(key, args);
        return this;
    }

    public AdaptiveBookBuilder title(TranslatableText title) {
        this.title = title;
        return this;
    }

    /**
     * Book-wide default font resource id (e.g. {@code minecraft:default}, {@code mymod:fancy}).
     * Overridden by per-span / heading fonts.
     */
    public AdaptiveBookBuilder defaultFont(ResourceLocation font) {
        this.defaultFont = font;
        return this;
    }

    public AdaptiveBookBuilder defaultFont(String fontId) {
        ResourceLocation rl = ResourceLocation.tryParse(fontId);
        if (rl != null) {
            this.defaultFont = rl;
        }
        return this;
    }

    /**
     * Theme id registered via {@link FlexiBookAPI#registerTheme} or
     * {@code assets/<ns>/flexibook/themes/<path>.json}. Example: {@code flexibook:default}.
     */
    public AdaptiveBookBuilder theme(ResourceLocation themeId) {
        this.themeId = themeId;
        return this;
    }

    public AdaptiveBookBuilder theme(String themeId) {
        ResourceLocation rl = ResourceLocation.tryParse(themeId);
        if (rl != null) {
            this.themeId = rl;
        }
        return this;
    }

    public AdaptiveBookBuilder h1(String key) {
        elements.add(new BookElement.Heading(1, new TranslatableText(key)));
        return this;
    }

    public AdaptiveBookBuilder h1(String key, ResourceLocation font) {
        elements.add(new BookElement.Heading(1, new TranslatableText(key), Optional.ofNullable(font)));
        return this;
    }

    public AdaptiveBookBuilder h2(String key) {
        elements.add(new BookElement.Heading(2, new TranslatableText(key)));
        return this;
    }

    public AdaptiveBookBuilder h2(String key, ResourceLocation font) {
        elements.add(new BookElement.Heading(2, new TranslatableText(key), Optional.ofNullable(font)));
        return this;
    }

    public AdaptiveBookBuilder p(String key) {
        elements.add(new BookElement.Paragraph(List.of(InlineSpan.key(key))));
        return this;
    }

    /** Paragraph using a specific font for the whole run. */
    public AdaptiveBookBuilder p(String key, ResourceLocation font) {
        StyleFlags style = font == null ? StyleFlags.EMPTY : StyleFlags.EMPTY.withFont(font);
        elements.add(new BookElement.Paragraph(List.of(InlineSpan.key(key, style))));
        return this;
    }

    public AdaptiveBookBuilder pLiteral(String text) {
        elements.add(new BookElement.Paragraph(List.of(InlineSpan.literal(text))));
        return this;
    }

    public AdaptiveBookBuilder pLiteral(String text, ResourceLocation font) {
        StyleFlags style = font == null ? StyleFlags.EMPTY : StyleFlags.EMPTY.withFont(font);
        elements.add(new BookElement.Paragraph(List.of(InlineSpan.literal(text, style))));
        return this;
    }

    /**
     * Inline-styled paragraph: translation key drawn in {@code font}.
     * Prefer this over nesting when the whole line shares one face.
     */
    public AdaptiveBookBuilder font(String key, ResourceLocation font) {
        return p(key, font);
    }

    /** Parse a markup fragment and append its elements. */
    public AdaptiveBookBuilder pRaw(String markupFragment) {
        elements.addAll(TagParser.parse(markupFragment));
        return this;
    }

    public AdaptiveBookBuilder bullet(String key) {
        elements.add(new BookElement.Bullet(List.of(InlineSpan.key(key))));
        return this;
    }

    public AdaptiveBookBuilder bullet(String key, ResourceLocation font) {
        StyleFlags style = font == null ? StyleFlags.EMPTY : StyleFlags.EMPTY.withFont(font);
        elements.add(new BookElement.Bullet(List.of(InlineSpan.key(key, style))));
        return this;
    }

    public AdaptiveBookBuilder image(ResourceLocation texture, int w, int h) {
        elements.add(new BookElement.Image(texture, w, h, Optional.empty()));
        return this;
    }

    public AdaptiveBookBuilder image(ResourceLocation texture, int w, int h, String tooltipKey) {
        elements.add(new BookElement.Image(texture, w, h, Optional.ofNullable(tooltipKey)));
        return this;
    }

    public AdaptiveBookBuilder link(String textKey, LinkAction action) {
        StyleFlags style = StyleFlags.EMPTY.withColor(0x0000EE).withUnderline(true);
        elements.add(new BookElement.Paragraph(List.of(InlineSpan.key(textKey, style, action))));
        return this;
    }

    public AdaptiveBookBuilder link(String textKey, LinkAction action, ResourceLocation font) {
        StyleFlags style = StyleFlags.EMPTY.withColor(0x0000EE).withUnderline(true);
        if (font != null) {
            style = style.withFont(font);
        }
        elements.add(new BookElement.Paragraph(List.of(InlineSpan.key(textKey, style, action))));
        return this;
    }

    public AdaptiveBookBuilder divider() {
        elements.add(BookElement.Divider.INSTANCE);
        return this;
    }

    public AdaptiveBookBuilder br() {
        elements.add(BookElement.LineBreak.INSTANCE);
        return this;
    }

    public AdaptiveBookBuilder element(BookElement element) {
        elements.add(element);
        return this;
    }

    public AdaptiveBookBuilder fromMarkup(String markup) {
        this.rawMarkup = markup;
        return this;
    }

    public AdaptiveBookContent buildContent() {
        Optional<ResourceLocation> font = Optional.ofNullable(defaultFont);
        Optional<ResourceLocation> theme = Optional.ofNullable(themeId);
        if (rawMarkup != null && elements.isEmpty()) {
            return AdaptiveBookContent.ofMarkup(title, rawMarkup, font, theme);
        }
        return AdaptiveBookContent.ofElements(title, elements, font, theme);
    }

    public ItemStack buildItem() {
        ItemStack stack = new ItemStack(ModItems.FLEXI_BOOK.get());
        stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), buildContent());
        return stack;
    }
}
