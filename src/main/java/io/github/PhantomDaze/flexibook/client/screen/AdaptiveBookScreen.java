package io.github.PhantomDaze.flexibook.client.screen;

import io.github.PhantomDaze.flexibook.client.link.LinkHandler;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;
import io.github.PhantomDaze.flexibook.layout.RenderedElement;
import io.github.PhantomDaze.flexibook.layout.RenderedPage;
import io.github.PhantomDaze.flexibook.registry.ModDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Single-page adaptive book screen with search and safe link clicks.
 * Rendering order matches vanilla {@code BookViewScreen}: background (no menu blur)
 * is drawn in {@link #renderBackground}, then widgets via {@code super.render},
 * then page content so the blur pass never treats the book as world UI.
 */
public class AdaptiveBookScreen extends Screen {
    private final ItemStack bookStack;
    private final BookTheme theme;

    private AdaptiveBookContent content;
    private List<RenderedPage> pages = List.of();
    private int pageIndex;
    private String languageCode = "";
    private String searchQuery = "";

    private EditBox searchBox;
    private Button prevButton;
    private Button nextButton;

    private int leftPos;
    private int topPos;

    public AdaptiveBookScreen(ItemStack bookStack) {
        this(bookStack, BookTheme.DEFAULT);
    }

    public AdaptiveBookScreen(ItemStack bookStack, BookTheme theme) {
        super(Component.translatable("flexibook.screen.title"));
        this.bookStack = bookStack.copy();
        this.theme = theme;
        this.content = bookStack.getOrDefault(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), AdaptiveBookContent.EMPTY);
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - theme.bookTexWidth()) / 2;
        this.topPos = (this.height - theme.bookTexHeight()) / 2 - 10;

        String lang = this.minecraft != null ? this.minecraft.getLanguageManager().getSelected() : "en_us";
        if (!lang.equals(this.languageCode)) {
            this.languageCode = lang;
            BookLayoutEngine.clearCache();
        }
        relayout();

        // Shorter search row; buttons stay 20px tall and sit slightly lower for visual balance.
        int btnY = topPos + theme.bookTexHeight() + 2;
        int searchH = 14;
        int searchY = btnY + (20 - searchH) / 2;
        this.prevButton = Button.builder(Component.translatable("flexibook.screen.prev"), b -> changePage(-1))
                .bounds(leftPos, btnY, 50, 20)
                .build();
        this.nextButton = Button.builder(Component.translatable("flexibook.screen.next"), b -> changePage(1))
                .bounds(leftPos + theme.bookTexWidth() - 50, btnY, 50, 20)
                .build();
        addRenderableWidget(prevButton);
        addRenderableWidget(nextButton);

        this.searchBox = new EditBox(this.font, leftPos + 54, searchY, theme.bookTexWidth() - 108, searchH,
                Component.translatable("flexibook.screen.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("flexibook.screen.search_hint"));
        this.searchBox.setResponder(value -> {
            this.searchQuery = value == null ? "" : value;
            this.pageIndex = 0;
            relayout();
            updateButtons();
        });
        addRenderableWidget(searchBox);

        updateButtons();
    }

    private void relayout() {
        if (this.minecraft == null) {
            return;
        }
        int guiScale = (int) this.minecraft.getWindow().getGuiScale();
        this.pages = BookLayoutEngine.layout(content, this.font, theme, languageCode, guiScale, searchQuery);
        this.pageIndex = Mth.clamp(pageIndex, 0, Math.max(0, pages.size() - 1));
    }

    private void changePage(int delta) {
        pageIndex = Mth.clamp(pageIndex + delta, 0, Math.max(0, pages.size() - 1));
        updateButtons();
    }

    private void updateButtons() {
        if (prevButton != null) {
            prevButton.active = pageIndex > 0;
        }
        if (nextButton != null) {
            nextButton.active = pageIndex < pages.size() - 1;
        }
    }

    /**
     * Same path as vanilla books: dim the world without the in-world menu blur pass,
     * then draw the book texture as part of the screen background (not pre-blur content).
     */
    /**
     * Same path as vanilla {@code BookViewScreen}: dim without menu blur, then blit the book
     * panel with the 7-arg {@link GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int)}
     * overload (u/v + size, texture sheet defaults to 256×256).
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        // GuiGraphics.blit(location, x, y, uOffset, vOffset, uWidth, vHeight) → sheet 256×256
        graphics.blit(
                theme.bookTexture(),
                leftPos,
                topPos,
                0,
                0,
                theme.bookTexWidth(),
                theme.bookTexHeight()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Blur/menu background + book texture + buttons/search (must run before page ink)
        super.render(graphics, mouseX, mouseY, partialTick);

        // title (uses book default font when set)
        Component title = content.title().resolve();
        if (content.defaultFont().isPresent()) {
            title = title.copy().withStyle(Style.EMPTY.withFont(content.defaultFont().get()));
        }
        int titleX = leftPos + (theme.bookTexWidth() - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, topPos + 5, theme.pageTextColor(), false);

        int contentX = leftPos + theme.contentLeft();
        // Sit just under the title with a small gap.
        int contentY = topPos + theme.contentTop() + 4;

        if (!pages.isEmpty()) {
            RenderedPage page = pages.get(pageIndex);
            for (RenderedElement el : page.elements()) {
                renderElement(graphics, el, contentX, contentY);
            }

            // hover tooltip for links / images
            double localX = mouseX - contentX;
            double localY = mouseY - contentY;
            Optional<Component> tip = Optional.empty();
            for (RenderedPage.ClickArea area : page.clickAreas()) {
                if (area.contains(localX, localY)) {
                    tip = Optional.of(Component.translatable("flexibook.link.tooltip", area.label()));
                    break;
                }
            }
            if (tip.isEmpty()) {
                for (RenderedElement el : page.elements()) {
                    if (el instanceof RenderedElement.ImageBlock img) {
                        if (localX >= img.x() && localY >= img.y()
                                && localX < img.x() + img.width() && localY < img.y() + img.height()) {
                            if (img.tooltipKey().isPresent()) {
                                tip = Optional.of(Component.translatable(img.tooltipKey().get()));
                            }
                            break;
                        }
                    }
                }
            }
            tip.ifPresent(t -> graphics.renderTooltip(font, t, mouseX, mouseY));
        }

        // page number
        String pageLabel = Component.translatable("flexibook.screen.page", pageIndex + 1, Math.max(1, pages.size())).getString();
        int pageLabelX = leftPos + (theme.bookTexWidth() - font.width(pageLabel)) / 2;
        // Footer band of the taller book texture (layout content height unchanged).
        graphics.drawString(font, pageLabel, pageLabelX, topPos + theme.bookTexHeight() - 18, theme.pageTextColor(), false);
    }

    private void renderElement(GuiGraphics graphics, RenderedElement el, int originX, int originY) {
        switch (el) {
            case RenderedElement.TextLine line -> {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(originX + line.x(), originY + line.y(), 0);
                pose.scale(line.scale(), line.scale(), 1f);

                StyleFlags flags = line.style();
                int baseColor = theme.pageTextColor();
                if (flags.color().isPresent()) {
                    baseColor = flags.color().get() & 0xFFFFFF;
                } else if (line.link().isPresent()) {
                    baseColor = theme.linkColor();
                }

                Style mcStyle = toMinecraftStyle(flags, line.link().isPresent(), baseColor);
                Component text = Component.literal(line.text()).withStyle(mcStyle);

                if (line.highlight()) {
                    int w = Math.max(1, font.width(text));
                    graphics.fill(-1, -1, w + 1, Math.round(line.height()) + 1, 0x66FFD54F);
                }

                // Style carries bold/italic/underline — do not double-draw (that looked broken, especially on CJK).
                graphics.drawString(font, text, 0, 0, baseColor, false);
                pose.popPose();
            }
            case RenderedElement.ImageBlock image -> {
                int x = originX + Math.round(image.x());
                int y = originY + Math.round(image.y());
                graphics.blit(image.texture(), x, y, 0, 0, image.width(), image.height(), image.width(), image.height());
            }
            case RenderedElement.DividerLine divider -> {
                int x = originX + Math.round(divider.x());
                int y = originY + Math.round(divider.y() + divider.height() / 2f);
                int w = Math.round(divider.width());
                graphics.fill(x, y, x + w, y + 1, 0xFF000000 | theme.dividerColor());
            }
        }
    }

    /**
     * Map FlexiBook style flags onto vanilla chat {@link Style} so Font applies real bold/italic/underline/font metrics.
     */
    static Style toMinecraftStyle(StyleFlags flags, boolean link, int rgb) {
        Style style = Style.EMPTY.withColor(rgb);
        if (flags.bold()) {
            style = style.withBold(true);
        }
        if (flags.italic()) {
            style = style.withItalic(true);
        }
        if (flags.underline() || link) {
            style = style.withUnderlined(true);
        }
        if (flags.font().isPresent()) {
            style = style.withFont(flags.font().get());
        }
        return style;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !pages.isEmpty()) {
            int contentX = leftPos + theme.contentLeft();
            int contentY = topPos + theme.contentTop() + 0;
            double localX = mouseX - contentX;
            double localY = mouseY - contentY;
            RenderedPage page = pages.get(pageIndex);
            for (RenderedPage.ClickArea area : page.clickAreas()) {
                if (area.contains(localX, localY)) {
                    LinkHandler.handle(area.action(), this);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // left / right arrows
        if (keyCode == 263 /* LEFT */ || keyCode == 65 /* A */) {
            changePage(-1);
            return true;
        }
        if (keyCode == 262 /* RIGHT */ || keyCode == 68 /* D */) {
            changePage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
