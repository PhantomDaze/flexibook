package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.BookThemes;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookElement;
import io.github.PhantomDaze.flexibook.content.InlineSpan;
import io.github.PhantomDaze.flexibook.content.LinkAction;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.content.TranslatableText;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 布局引擎跨版本差分测试。
 *
 * <p>用完全确定的 {@link TextMeasurer}/{@link TranslationProvider}（不依赖 MC Font），
 * 对一组覆盖全部元素类型的书稿做布局，并把结果序列化成稳定文本。
 * 期望快照位于 {@code src/test/resources/layout-parity/expected.txt}；
 * 任意版本节点（1.20.1 → 26.2）都必须产出完全相同的文本。
 */
class LayoutParityDumpTest {

    private static final BookTheme THEME = BookThemes.DEFAULT;

    /** 确定性字体度量：CJK 宽 9，其它宽 6；粗体 +1；非默认字体 +2。 */
    private static final TextMeasurer MEASURER = (text, style, fontId) -> {
        if (text == null) {
            return 0;
        }
        int w = 0;
        boolean bold = style != null && style.bold();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int cw = c >= 0x2E80 ? 9 : 6;
            if (bold) {
                cw += 1;
            }
            w += cw;
        }
        if (fontId != null && fontId.isPresent()
                && !"flexibook:default".equals(fontId.get().toString())) {
            w += 2;
        }
        return w;
    };

    private static final Map<String, String> TEXT = Map.ofEntries(
            Map.entry("parity.title", "Parity Title"),
            Map.entry("parity.h1", "Heading One"),
            Map.entry("parity.h2", "Heading Two"),
            Map.entry("parity.p1", "The quick brown fox jumps over the lazy dog. "),
            Map.entry("parity.p2", "Second paragraph with a colored run."),
            Map.entry("parity.p3", "Custom font run."),
            Map.entry("parity.b1", "A bullet item"),
            Map.entry("parity.long",
                    "Long body sentence used to exercise wrapping and pagination. "
                            + "It keeps going with enough words to force several lines. "),
            Map.entry("parity.cjk",
                    "这是一个用于布局差分测试的中文段落，包含标点符号，并且长度足够触发自动换行。")
    );

    private static final TranslationProvider TRANSLATOR =
            (key, args) -> TEXT.getOrDefault(key, key);

    @Test
    void layoutOutputMatchesCrossVersionSnapshot() throws IOException {
        String actual = dumpAllScenarios();

        // 便于失败时排查：始终把实际输出写到节点 build 目录。
        Path actualFile = Path.of("build", "layout-parity-actual.txt");
        Files.createDirectories(actualFile.getParent());
        Files.writeString(actualFile, actual, StandardCharsets.UTF_8);

        // 允许用环境变量指向外部快照，便于在旧分支 worktree 中复用同一基准。
        String expectedPath = System.getenv("FLEXIBOOK_PARITY_EXPECTED");
        String expected;
        if (expectedPath != null && !expectedPath.isBlank()) {
            expected = Files.readString(Path.of(expectedPath), StandardCharsets.UTF_8);
        } else {
            InputStream in = LayoutParityDumpTest.class.getResourceAsStream("/layout-parity/expected.txt");
            assertNotNull(in, "缺少 src/test/resources/layout-parity/expected.txt 快照");
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 统一换行符，避免平台差异。
        expected = expected.replace("\r\n", "\n");
        actual = actual.replace("\r\n", "\n");
        assertEquals(expected, actual, "布局输出与该版本无关的快照不一致（见 build/layout-parity-actual.txt）");
    }

    private static String dumpAllScenarios() {
        StringBuilder sb = new StringBuilder();
        appendScenario(sb, "all-elements", allElements(), theTheme(), "");
        appendScenario(sb, "all-elements-search", allElements(), theTheme(), "bold");
        appendScenario(sb, "long-document", longDocument(), theTheme(), "");
        appendScenario(sb, "long-document-narrow", longDocument(), narrowTheme(), "");
        appendScenario(sb, "cjk", cjkDocument(), theTheme(), "");
        appendScenario(sb, "custom-font", customFontDocument(), theTheme(), "");
        appendScenario(sb, "nested-box", nestedBoxDocument(), theTheme(), "");
        return sb.toString();
    }

    private static BookTheme theTheme() {
        return THEME;
    }

    private static BookTheme narrowTheme() {
        return BookTheme.Builder.from(THEME).pageContentWidth(48).pageContentHeight(70).build();
    }

    private static AdaptiveBookContent allElements() {
        Identifier icon = FlexiBookIds.of("flexibook", "textures/gui/icon.png");
        Identifier fancy = FlexiBookIds.of("mymod", "fancy");
        List<BookElement> els = List.of(
                new BookElement.Heading(1, TranslatableText.of("parity.h1")),
                new BookElement.Paragraph(List.of(
                        InlineSpan.key("parity.p1"),
                        InlineSpan.literal("Bold", StyleFlags.EMPTY.withBold(true)),
                        InlineSpan.literal(" "),
                        InlineSpan.literal("Italic", StyleFlags.EMPTY.withItalic(true)),
                        InlineSpan.literal(" "),
                        InlineSpan.literal("Under", StyleFlags.EMPTY.withUnderline(true)),
                        InlineSpan.literal(" "),
                        InlineSpan.literal("Color", StyleFlags.EMPTY.withColor(0xCC5500)),
                        InlineSpan.literal(" "),
                        InlineSpan.literal("Link", StyleFlags.EMPTY, LinkAction.commandId("flexibook:say_hi")),
                        InlineSpan.literal(" "),
                        InlineSpan.key("parity.p2"),
                        InlineSpan.literal(" "),
                        InlineSpan.key("parity.p3", StyleFlags.EMPTY.withFont(fancy))
                )),
                new BookElement.Bullet(List.of(InlineSpan.key("parity.b1"))),
                new BookElement.Bullet(List.of(InlineSpan.literal("literal bullet text"))),
                BookElement.LineBreak.INSTANCE,
                BookElement.Divider.INSTANCE,
                new BookElement.Image(icon, 48, 48, Optional.of("parity.tip")),
                new BookElement.Image(icon, 300, 120, Optional.empty()),
                new BookElement.Box(Optional.of("note"), List.of(
                        new BookElement.Heading(2, TranslatableText.of("parity.h2")),
                        new BookElement.Paragraph(List.of(InlineSpan.literal("boxed body text"))),
                        new BookElement.Box(Optional.empty(), List.of(
                                new BookElement.Paragraph(List.of(InlineSpan.literal("nested box body")))
                        ))
                ))
        );
        return AdaptiveBookContent.ofElements(TranslatableText.of("parity.title"), els);
    }

    private static AdaptiveBookContent longDocument() {
        List<BookElement> els = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            els.add(new BookElement.Paragraph(List.of(InlineSpan.key("parity.long"))));
            if (i % 7 == 0) {
                els.add(BookElement.Divider.INSTANCE);
            }
            if (i % 11 == 0) {
                els.add(new BookElement.Bullet(List.of(InlineSpan.literal("bullet " + i))));
            }
        }
        return AdaptiveBookContent.ofElements(TranslatableText.of("parity.title"), els);
    }

    private static AdaptiveBookContent cjkDocument() {
        List<BookElement> els = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            els.add(new BookElement.Paragraph(List.of(InlineSpan.key("parity.cjk"))));
        }
        return AdaptiveBookContent.ofElements(TranslatableText.of("parity.title"), els);
    }

    private static AdaptiveBookContent customFontDocument() {
        Identifier fancy = FlexiBookIds.of("mymod", "fancy");
        return AdaptiveBookContent.ofElements(
                TranslatableText.of("parity.title"),
                List.of(
                        new BookElement.Paragraph(List.of(InlineSpan.key("parity.p1"))),
                        new BookElement.Heading(2, TranslatableText.of("parity.h2"), Optional.of(fancy)),
                        new BookElement.Paragraph(List.of(
                                InlineSpan.literal("plain"),
                                InlineSpan.literal("over", StyleFlags.EMPTY.withFont(fancy))
                        ))
                ),
                Optional.of(fancy)
        );
    }

    private static AdaptiveBookContent nestedBoxDocument() {
        BookElement inner = new BookElement.Box(Optional.of("inner"), List.of(
                new BookElement.Paragraph(List.of(InlineSpan.key("parity.p1"))),
                new BookElement.Bullet(List.of(InlineSpan.literal("deep bullet")))
        ));
        BookElement outer = new BookElement.Box(Optional.of("outer"), List.of(
                new BookElement.Heading(1, TranslatableText.of("parity.h1")),
                inner,
                BookElement.Divider.INSTANCE
        ));
        return AdaptiveBookContent.ofElements(TranslatableText.of("parity.title"), List.of(outer));
    }

    private static void appendScenario(StringBuilder sb, String name,
                                       AdaptiveBookContent content, BookTheme theme, String search) {
        BookLayoutEngine.clearCache();
        List<RenderedPage> pages = BookLayoutEngine.layout(
                content, MEASURER, TRANSLATOR, theme, "en_us", 2, search);
        sb.append("scenario ").append(name)
                .append(" pages=").append(pages.size())
                .append(" search=").append(search)
                .append('\n');
        for (int p = 0; p < pages.size(); p++) {
            RenderedPage page = pages.get(p);
            sb.append("page ").append(p)
                    .append(" elements=").append(page.elements().size())
                    .append(" clicks=").append(page.clickAreas().size())
                    .append('\n');
            for (RenderedElement el : page.elements()) {
                sb.append("  ").append(element(el)).append('\n');
            }
            for (RenderedPage.ClickArea area : page.clickAreas()) {
                sb.append("  C|").append(f(area.x())).append('|').append(f(area.y()))
                        .append('|').append(f(area.w())).append('|').append(f(area.h()))
                        .append('|').append(action(area.action()))
                        .append('|').append(escape(area.label())).append('\n');
            }
        }
    }

    private static String element(RenderedElement el) {
        if (el instanceof RenderedElement.TextLine line) {
            return "T|" + f(line.x()) + '|' + f(line.y()) + '|' + f(line.scale())
                    + '|' + escape(line.text())
                    + '|' + f(line.width()) + '|' + f(line.height())
                    + '|' + line.highlight()
                    + '|' + style(line.style())
                    + '|' + line.link().map(LayoutParityDumpTest::action).orElse("none");
        }
        if (el instanceof RenderedElement.ImageBlock image) {
            return "I|" + f(image.x()) + '|' + f(image.y()) + '|' + f(image.scale())
                    + '|' + image.texture()
                    + '|' + image.width() + '|' + image.height()
                    + '|' + image.tooltipKey().orElse("");
        }
        RenderedElement.DividerLine divider = (RenderedElement.DividerLine) el;
        return "D|" + f(divider.x()) + '|' + f(divider.y()) + '|' + f(divider.scale())
                + '|' + f(divider.width()) + '|' + f(divider.height());
    }

    private static String style(StyleFlags s) {
        return s.bold() + "," + s.italic() + "," + s.underline()
                + "," + s.color().map(c -> "0x" + Integer.toHexString(c)).orElse("-")
                + "," + s.font().map(Object::toString).orElse("-");
    }

    private static String action(LinkAction a) {
        if (a instanceof LinkAction.CommandId cmd) {
            return "cmd:" + cmd.id();
        }
        if (a instanceof LinkAction.Url url) {
            return "url:" + url.url();
        }
        return "none";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
