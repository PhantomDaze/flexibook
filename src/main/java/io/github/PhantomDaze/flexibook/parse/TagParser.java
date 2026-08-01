package io.github.PhantomDaze.flexibook.parse;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.content.TranslatableText;
import io.github.PhantomDaze.flexibook.util.Compat;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight HTML-subset tag parser.
 * Supports: h1 h2 p b i u color font br divider img link bullet div, nesting, and \[ \] escapes.
 * Unknown / malformed tags are skipped with a warning — never throws to callers.
 */
public final class TagParser {
    private TagParser() {}

    public static List<BookElement> parse(String markup) {
        if (markup == null || markup.isBlank()) {
            return List.of();
        }
        try {
            Parser parser = new Parser(markup);
            return parser.parseBlocks();
        } catch (Exception e) {
            FlexiBookMod.LOGGER.warn("FlexiBook TagParser failed: {}", e.toString());
            return List.of(new BookElement.Paragraph(List.of(InlineSpan.literal(markup))));
        }
    }

    private static final class Parser {
        private final String src;
        private int pos;

        private Parser(String src) {
            this.src = src;
            this.pos = 0;
        }

        private List<BookElement> parseBlocks() {
            List<BookElement> out = new ArrayList<>();
            while (pos < src.length()) {
                skipWhitespaceNewlines();
                if (pos >= src.length()) {
                    break;
                }
                if (peekTag()) {
                    Tag tag = readTag();
                    if (tag == null) {
                        continue;
                    }
                    if (tag.closing) {
                        FlexiBookMod.LOGGER.warn("Unexpected closing tag [/{}] at {}", tag.name, pos);
                        continue;
                    }
                    switch (tag.name) {
                        case "h1" -> out.add(new BookElement.Heading(1, readTranslatableUntil("h1"), parseFontAttr(tag)));
                        case "h2" -> out.add(new BookElement.Heading(2, readTranslatableUntil("h2"), parseFontAttr(tag)));
                        case "p" -> out.add(new BookElement.Paragraph(parseInlines("p")));
                        case "bullet" -> out.add(new BookElement.Bullet(parseInlines("bullet")));
                        case "br" -> out.add(BookElement.LineBreak.INSTANCE);
                        case "divider" -> out.add(BookElement.Divider.INSTANCE);
                        case "img" -> out.add(parseImage(tag));
                        case "div" -> {
                            Optional<String> cls = Optional.ofNullable(tag.attrs.get("class"));
                            out.add(new BookElement.Box(cls, parseBlocksUntil("div")));
                        }
                        case "link" -> {
                            // bare block-level link → paragraph with single linked span
                            List<InlineSpan> spans = parseInlines("link");
                            StyleFlags style = StyleFlags.EMPTY.withColor(parseColor(tag.attrs.get("color")).orElse(0x00AAFF));
                            Optional<ResourceLocation> font = parseFontAttr(tag);
                            if (font.isPresent()) {
                                style = style.withFont(font.get());
                            }
                            LinkAction action = linkActionFrom(tag.attrs);
                            if (spans.isEmpty()) {
                                out.add(new BookElement.Paragraph(List.of(
                                        InlineSpan.literal("", style, action)
                                )));
                            } else {
                                List<InlineSpan> linked = new ArrayList<>();
                                for (InlineSpan s : spans) {
                                    linked.add(new InlineSpan(s.text(), s.translate(), s.style().merge(style), Optional.of(action)));
                                }
                                out.add(new BookElement.Paragraph(linked));
                            }
                        }
                        default -> {
                            FlexiBookMod.LOGGER.warn("Unknown block tag [{}] — treating as paragraph content", tag.name);
                            // consume until matching close if any
                            if (!tag.selfClosing) {
                                parseInlines(tag.name);
                            }
                        }
                    }
                } else {
                    // loose text becomes a paragraph
                    String loose = readUntilTagOrEnd();
                    if (!loose.isBlank()) {
                        out.add(new BookElement.Paragraph(List.of(InlineSpan.literal(loose.strip()))));
                    }
                }
            }
            return out;
        }

        private List<BookElement> parseBlocksUntil(String closeName) {
            List<BookElement> out = new ArrayList<>();
            while (pos < src.length()) {
                skipWhitespaceNewlines();
                if (pos >= src.length()) {
                    break;
                }
                if (peekTag()) {
                    int mark = pos;
                    Tag tag = readTag();
                    if (tag != null && tag.closing && tag.name.equals(closeName)) {
                        return out;
                    }
                    // rewind and parse as nested block by reusing block switch via temporary
                    pos = mark;
                    // parse one block element by calling parseBlocks on a synthetic single-element path:
                    // fall through: parse next block item only
                    int before = out.size();
                    // manually handle next block similar to parseBlocks single step
                    Tag next = readTag();
                    if (next == null) {
                        continue;
                    }
                    if (next.closing) {
                        if (next.name.equals(closeName)) {
                            return out;
                        }
                        continue;
                    }
                    switch (next.name) {
                        case "h1" -> out.add(new BookElement.Heading(1, readTranslatableUntil("h1"), parseFontAttr(next)));
                        case "h2" -> out.add(new BookElement.Heading(2, readTranslatableUntil("h2"), parseFontAttr(next)));
                        case "p" -> out.add(new BookElement.Paragraph(parseInlines("p")));
                        case "bullet" -> out.add(new BookElement.Bullet(parseInlines("bullet")));
                        case "br" -> out.add(BookElement.LineBreak.INSTANCE);
                        case "divider" -> out.add(BookElement.Divider.INSTANCE);
                        case "img" -> out.add(parseImage(next));
                        case "div" -> {
                            Optional<String> cls = Optional.ofNullable(next.attrs.get("class"));
                            out.add(new BookElement.Box(cls, parseBlocksUntil("div")));
                        }
                        default -> {
                            if (!next.selfClosing) {
                                parseInlines(next.name);
                            }
                        }
                    }
                    if (out.size() == before) {
                        // progress guard
                        if (pos == mark) {
                            pos++;
                        }
                    }
                } else {
                    String loose = readUntilTagOrEnd();
                    if (!loose.isBlank()) {
                        out.add(new BookElement.Paragraph(List.of(InlineSpan.literal(loose.strip()))));
                    }
                }
            }
            FlexiBookMod.LOGGER.warn("Unclosed [div] / [{}]", closeName);
            return out;
        }

        private List<InlineSpan> parseInlines(String closeName) {
            List<InlineSpan> spans = new ArrayList<>();
            StyleFlags style = StyleFlags.EMPTY;
            LinkAction pendingLink = null;
            StringBuilder buf = new StringBuilder();

            while (pos < src.length()) {
                if (src.charAt(pos) == '\\' && pos + 1 < src.length()) {
                    char n = src.charAt(pos + 1);
                    if (n == '[' || n == ']') {
                        buf.append(n);
                        pos += 2;
                        continue;
                    }
                }
                if (peekTag()) {
                    flushInline(spans, buf, style, pendingLink);
                    Tag tag = readTag();
                    if (tag == null) {
                        continue;
                    }
                    if (tag.closing) {
                        if (tag.name.equals(closeName)) {
                            return spans;
                        }
                        switch (tag.name) {
                            case "b" -> style = style.withBold(false);
                            case "i" -> style = style.withItalic(false);
                            case "u" -> style = style.withUnderline(false);
                            case "color" -> style = style.withColor(null);
                            case "font" -> style = style.withFont(null);
                            case "link" -> pendingLink = null;
                            default -> {
                            }
                        }
                        continue;
                    }
                    switch (tag.name) {
                        case "b" -> style = style.withBold(true);
                        case "i" -> style = style.withItalic(true);
                        case "u" -> style = style.withUnderline(true);
                        case "color" -> {
                            Optional<Integer> c = parseColor(tag.attrs.get("color"));
                            if (c.isEmpty()) {
                                c = parseColor(firstAttrValue(tag));
                            }
                            style = style.withColor(c.orElse(null));
                        }
                        case "font" -> {
                            Optional<ResourceLocation> f = parseFontAttr(tag);
                            if (f.isEmpty()) {
                                // [font=namespace:path] as first token
                                f = parseFontId(firstAttrValue(tag));
                            }
                            style = style.withFont(f.orElse(null));
                        }
                        case "link" -> {
                            pendingLink = linkActionFrom(tag.attrs);
                            Optional<ResourceLocation> f = parseFontAttr(tag);
                            if (f.isPresent()) {
                                style = style.withFont(f.get());
                            }
                        }
                        case "br" -> spans.add(InlineSpan.literal("\n", style));
                        default -> {
                        }
                    }
                    // special: [color=#RRGGBB] where whole assignment landed in the tag name
                    if (tag.name.startsWith("color") && tag.name.contains("=")) {
                        String[] parts = tag.name.split("=", 2);
                        if (parts.length == 2) {
                            style = style.withColor(parseColor(parts[1]).orElse(null));
                        }
                    }
                    if (tag.name.startsWith("font") && tag.name.contains("=")) {
                        String[] parts = tag.name.split("=", 2);
                        if (parts.length == 2) {
                            style = style.withFont(parseFontId(parts[1]).orElse(null));
                        }
                    }
                } else {
                    buf.append(src.charAt(pos));
                    pos++;
                }
            }
            flushInline(spans, buf, style, pendingLink);
            if (closeName != null) {
                FlexiBookMod.LOGGER.warn("Unclosed [{}]", closeName);
            }
            return spans;
        }

        private static void flushInline(List<InlineSpan> spans, StringBuilder buf, StyleFlags style, LinkAction pendingLink) {
            if (buf.isEmpty()) {
                return;
            }
            String text = buf.toString();
            buf.setLength(0);
            boolean translate = text.indexOf('.') > 0 && text.indexOf(' ') < 0;
            if (pendingLink != null) {
                spans.add(translate
                        ? InlineSpan.key(text, style, pendingLink)
                        : InlineSpan.literal(text, style, pendingLink));
            } else {
                spans.add(translate
                        ? InlineSpan.key(text, style)
                        : InlineSpan.literal(text, style));
            }
        }

        private TranslatableText readTranslatableUntil(String closeName) {
            List<InlineSpan> spans = parseInlines(closeName);
            if (spans.isEmpty()) {
                return new TranslatableText("");
            }
            // Prefer first span as key if it looks like a key; else join literals as a synthetic keyless literal via empty key + we store as key=joined for resolve
            InlineSpan first = Compat.first(spans);
            if (first.translate()) {
                return new TranslatableText(first.text());
            }
            StringBuilder sb = new StringBuilder();
            for (InlineSpan s : spans) {
                sb.append(s.resolvePlain());
            }
            // store as non-translatable by using a unique approach: key is the literal itself with a special marker
            // Adaptive resolve uses Component.translatable which will show the key if missing — better use literal path.
            // Heading only holds TranslatableText; for literals we put the text as key and mark via args sentinel — simpler: use key = text and accept.
            return new TranslatableText(sb.toString());
        }

        private BookElement.Image parseImage(Tag tag) {
            String srcAttr = tag.attrs.getOrDefault("src", "flexibook:textures/gui/icon.png");
            ResourceLocation rl = ResourceLocation.tryParse(srcAttr.contains(":") ? srcAttr : "flexibook:" + srcAttr);
            if (rl == null) {
                rl = FlexiBookIds.of(FlexiBookMod.MOD_ID, "textures/gui/icon.png");
            }
            // bare paths like textures/gui/x.png should become flexibook:textures/...
            if (!srcAttr.contains(":")) {
                rl = FlexiBookIds.of(FlexiBookMod.MOD_ID, srcAttr);
            }
            int w = parseInt(tag.attrs.get("width"), 48);
            int h = parseInt(tag.attrs.get("height"), 48);
            Optional<String> tip = Optional.ofNullable(tag.attrs.get("tooltip"));
            return new BookElement.Image(rl, w, h, tip);
        }

        private boolean peekTag() {
            return pos < src.length() && src.charAt(pos) == '[' && (pos == 0 || src.charAt(pos - 1) != '\\');
        }

        private Tag readTag() {
            if (pos >= src.length() || src.charAt(pos) != '[') {
                return null;
            }
            int end = src.indexOf(']', pos + 1);
            if (end < 0) {
                pos = src.length();
                return null;
            }
            String inner = src.substring(pos + 1, end).trim();
            pos = end + 1;
            if (inner.isEmpty()) {
                return null;
            }
            boolean closing = inner.startsWith("/");
            if (closing) {
                inner = inner.substring(1).trim();
            }
            boolean selfClosing = inner.endsWith("/");
            if (selfClosing) {
                inner = inner.substring(0, inner.length() - 1).trim();
            }
            // color=#RRGGBB / font=ns:path as compact tag forms
            String name;
            Map<String, String> attrs = new HashMap<>();
            int sp = indexOfWhitespaceOrEnd(inner);
            String first = inner.substring(0, sp);
            String rest = sp < inner.length() ? inner.substring(sp).trim() : "";
            String firstLower = first.toLowerCase(Locale.ROOT);
            if (firstLower.startsWith("color=") || firstLower.equals("color")) {
                name = "color";
                if (first.contains("=")) {
                    attrs.put("color", first.substring(first.indexOf('=') + 1).replace("\"", "").replace("'", ""));
                }
            } else if (firstLower.startsWith("font=") || firstLower.equals("font")) {
                name = "font";
                if (first.contains("=")) {
                    attrs.put("font", first.substring(first.indexOf('=') + 1).replace("\"", "").replace("'", ""));
                }
            } else if (first.contains("=")) {
                // unusual compact form — keep raw name for later special-case handlers
                name = first;
            } else {
                name = firstLower;
            }
            if (!rest.isEmpty()) {
                parseAttrs(rest, attrs);
            }
            // self-closing void tags
            if (name.equals("br") || name.equals("divider") || name.equals("img")) {
                selfClosing = true;
            }
            return new Tag(name, closing, selfClosing, attrs, inner);
        }

        private void parseAttrs(String rest, Map<String, String> attrs) {
            int i = 0;
            while (i < rest.length()) {
                while (i < rest.length() && Character.isWhitespace(rest.charAt(i))) {
                    i++;
                }
                if (i >= rest.length()) {
                    break;
                }
                int eq = rest.indexOf('=', i);
                if (eq < 0) {
                    break;
                }
                String key = rest.substring(i, eq).trim().toLowerCase(Locale.ROOT);
                i = eq + 1;
                if (i >= rest.length()) {
                    break;
                }
                char q = rest.charAt(i);
                String value;
                if (q == '"' || q == '\'') {
                    int close = rest.indexOf(q, i + 1);
                    if (close < 0) {
                        value = rest.substring(i + 1);
                        i = rest.length();
                    } else {
                        value = rest.substring(i + 1, close);
                        i = close + 1;
                    }
                } else {
                    int j = i;
                    while (j < rest.length() && !Character.isWhitespace(rest.charAt(j))) {
                        j++;
                    }
                    value = rest.substring(i, j);
                    i = j;
                }
                attrs.put(key, value);
            }
        }

        private String readUntilTagOrEnd() {
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                if (src.charAt(pos) == '\\' && pos + 1 < src.length()) {
                    char n = src.charAt(pos + 1);
                    if (n == '[' || n == ']') {
                        sb.append(n);
                        pos += 2;
                        continue;
                    }
                }
                if (peekTag()) {
                    break;
                }
                sb.append(src.charAt(pos));
                pos++;
            }
            return sb.toString();
        }

        private void skipWhitespaceNewlines() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private static int indexOfWhitespaceOrEnd(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (Character.isWhitespace(s.charAt(i))) {
                    return i;
                }
            }
            return s.length();
        }

        private static Optional<Integer> parseColor(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String s = raw.trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (s.startsWith("0x") || s.startsWith("0X")) {
                s = s.substring(2);
            }
            try {
                if (s.length() == 6) {
                    return Optional.of(Integer.parseInt(s, 16));
                }
                if (s.length() == 8) {
                    return Optional.of(Integer.parseInt(s, 16));
                }
            } catch (NumberFormatException ignored) {
            }
            return Optional.empty();
        }

        private static int parseInt(String raw, int def) {
            if (raw == null) {
                return def;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        private static String firstAttrValue(Tag tag) {
            if (tag.attrs.isEmpty()) {
                return null;
            }
            return tag.attrs.values().iterator().next();
        }

        private static LinkAction linkActionFrom(Map<String, String> attrs) {
            if (attrs.containsKey("cmd")) {
                return LinkAction.commandId(attrs.get("cmd"));
            }
            if (attrs.containsKey("url")) {
                return LinkAction.url(attrs.get("url"));
            }
            return LinkAction.none();
        }

        private static Optional<ResourceLocation> parseFontAttr(Tag tag) {
            if (tag.attrs.containsKey("font")) {
                return parseFontId(tag.attrs.get("font"));
            }
            if (tag.attrs.containsKey("src") && "font".equals(tag.name)) {
                return parseFontId(tag.attrs.get("src"));
            }
            return Optional.empty();
        }

        private static Optional<ResourceLocation> parseFontId(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String s = raw.trim().replace("\"", "").replace("'", "");
            ResourceLocation rl = ResourceLocation.tryParse(s);
            return Optional.ofNullable(rl);
        }
    }

    private record Tag(String name, boolean closing, boolean selfClosing, Map<String, String> attrs, String rawInner) {}
}
