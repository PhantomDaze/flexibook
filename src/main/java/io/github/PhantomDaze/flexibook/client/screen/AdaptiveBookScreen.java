package io.github.PhantomDaze.flexibook.client.screen;

import io.github.PhantomDaze.flexibook.client.TextureSizeCache;
import io.github.PhantomDaze.flexibook.client.link.LinkHandler;
import io.github.PhantomDaze.flexibook.client.theme.BookTheme;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.client.theme.ImageFit;
import io.github.PhantomDaze.flexibook.client.theme.ImageFitMath;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.content.BookContentAccess;
import io.github.PhantomDaze.flexibook.content.StyleFlags;
import io.github.PhantomDaze.flexibook.layout.BookLayoutEngine;
import io.github.PhantomDaze.flexibook.layout.RenderedElement;
import io.github.PhantomDaze.flexibook.layout.RenderedPage;
import io.github.PhantomDaze.flexibook.util.McFonts;
//? if <1.21.4 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
//? if >=26.1.2 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.4 && <1.21.11 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Single-page adaptive book screen with search and safe link clicks.
 * <p>
 * Pre-26: background via {@code renderBackground} + content in {@code render} ({@code GuiGraphics}).
 * 26.1+: deferred extract path ({@code extractBackground} / {@code extractRenderState} + {@code GuiGraphicsExtractor}).
 */
public class AdaptiveBookScreen extends Screen {
    /** Fixed atlas size for book panel sampling (editor + game must match). */
    public static final int BOOK_TEXTURE_SHEET = 2048;

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
        super(Component.translatable("flexibook.screen.title"));
        this.bookStack = bookStack.copy();
        this.content = BookContentAccess.getOrEmpty(bookStack);
        this.theme = BookThemeRegistry.resolve(this.content.themeId());
    }

    public AdaptiveBookScreen(ItemStack bookStack, BookTheme theme) {
        super(Component.translatable("flexibook.screen.title"));
        this.bookStack = bookStack.copy();
        this.content = BookContentAccess.getOrEmpty(bookStack);
        this.theme = theme != null ? theme : BookThemeRegistry.resolve(this.content.themeId());
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

    // --- background / content draw -----------------------------------------------------------

    //? if >=26.1.2 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        blitBookPanel(graphics);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawPageContent(graphics, mouseX, mouseY);
    }
    *///?} else {
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        blitBookPanel(graphics);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        blitBookPanel(graphics);
    }
    *///?}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawPageContent(graphics, mouseX, mouseY);
    }
    //?}

    //? if >=26.1.2 {
    /*private void blitBookPanel(GuiGraphicsExtractor graphics) {
        int panelW = theme.bookTexWidth();
        int panelH = theme.bookTexHeight();
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                theme.bookTexture(),
                leftPos,
                topPos,
                0f,
                0f,
                panelW,
                panelH,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET
        );
    }

    private void drawPageContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ResourceLocation bookFont = content.resolvedFont();
        Component title = content.title().resolve();
        title = title.copy().withStyle(McFonts.withFont(Style.EMPTY, bookFont));
        int titleX = leftPos + (theme.bookTexWidth() - font.width(title)) / 2;
        graphics.text(font, title, titleX, topPos + theme.titleOffsetY(), theme.pageTextColor(), false);

        int contentX = leftPos + theme.contentLeft();
        int contentY = topPos + theme.contentTop() + theme.contentOffsetY();

        if (!pages.isEmpty()) {
            RenderedPage page = pages.get(pageIndex);
            for (RenderedElement el : page.elements()) {
                renderElement(graphics, el, contentX, contentY);
            }
            Optional<Component> tip = hoverTip(mouseX, mouseY, contentX, contentY, page);
            tip.ifPresent(t -> graphics.setTooltipForNextFrame(font, t, mouseX, mouseY));
        }

        Component pageComp = Component.translatable("flexibook.screen.page", pageIndex + 1, Math.max(1, pages.size()));
        pageComp = pageComp.copy().withStyle(McFonts.withFont(Style.EMPTY, bookFont));
        int pageLabelX = leftPos + (theme.bookTexWidth() - font.width(pageComp)) / 2;
        graphics.text(font, pageComp, pageLabelX,
                topPos + theme.bookTexHeight() - theme.pageLabelInsetY(), theme.pageTextColor(), false);
    }

    private void renderElement(GuiGraphicsExtractor graphics, RenderedElement el, int originX, int originY) {
        switch (el) {
            case RenderedElement.TextLine line -> {
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(originX + line.x(), originY + line.y());
                pose.scale(line.scale(), line.scale());

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
                graphics.text(font, text, 0, 0, baseColor, false);
                pose.popMatrix();
            }
            case RenderedElement.ImageBlock image -> {
                int boxX = originX + Math.round(image.x());
                int boxY = originY + Math.round(image.y());
                blitImage(graphics, image.texture(), boxX, boxY, image.width(), image.height());
            }
            case RenderedElement.DividerLine divider -> {
                int x = originX + Math.round(divider.x());
                int y = originY + Math.round(divider.y() + divider.height() / 2f);
                int w = Math.round(divider.width());
                graphics.fill(x, y, x + w, y + 1, 0xFF000000 | theme.dividerColor());
            }
        }
    }

    private void blitImage(GuiGraphicsExtractor graphics, ResourceLocation texture,
                           int boxX, int boxY, int boxW, int boxH) {
        if (boxW <= 0 || boxH <= 0) {
            return;
        }
        if (theme.imageFit() == ImageFit.CONTAIN) {
            var size = TextureSizeCache.getSize(texture);
            if (size.isPresent()) {
                int texW = size.get()[0];
                int texH = size.get()[1];
                ImageFitMath.Fit fit = ImageFitMath.contain(boxW, boxH, texW, texH);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        boxX + fit.offsetX(),
                        boxY + fit.offsetY(),
                        0f,
                        0f,
                        fit.drawW(),
                        fit.drawH(),
                        texW,
                        texH,
                        texW,
                        texH
                );
                return;
            }
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                boxX,
                boxY,
                0f,
                0f,
                boxW,
                boxH,
                boxW,
                boxH
        );
    }
    *///?} else {
    private void blitBookPanel(GuiGraphics graphics) {
        int panelW = theme.bookTexWidth();
        int panelH = theme.bookTexHeight();
        //? if >=1.21.11 {
        /*graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                theme.bookTexture(),
                leftPos,
                topPos,
                0f,
                0f,
                panelW,
                panelH,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET
        );
        *///?} else {
        //? if >=1.21.4 {
        /*graphics.blit(
                RenderType::guiTextured,
                theme.bookTexture(),
                leftPos,
                topPos,
                0f,
                0f,
                panelW,
                panelH,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET
        );
        *///?} else {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                theme.bookTexture(),
                leftPos,
                topPos,
                panelW,
                panelH,
                0f,
                0f,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET,
                BOOK_TEXTURE_SHEET
        );
        RenderSystem.disableBlend();
        //?}
        //?}
    }

    private void drawPageContent(GuiGraphics graphics, int mouseX, int mouseY) {
        ResourceLocation bookFont = content.resolvedFont();
        Component title = content.title().resolve();
        title = title.copy().withStyle(McFonts.withFont(Style.EMPTY, bookFont));
        int titleX = leftPos + (theme.bookTexWidth() - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, topPos + theme.titleOffsetY(), theme.pageTextColor(), false);

        int contentX = leftPos + theme.contentLeft();
        int contentY = topPos + theme.contentTop() + theme.contentOffsetY();

        if (!pages.isEmpty()) {
            RenderedPage page = pages.get(pageIndex);
            for (RenderedElement el : page.elements()) {
                renderElement(graphics, el, contentX, contentY);
            }
            Optional<Component> tip = hoverTip(mouseX, mouseY, contentX, contentY, page);
            //? if >=1.21.11 {
            /*tip.ifPresent(t -> graphics.setTooltipForNextFrame(font, t, mouseX, mouseY));
            *///?} else {
            tip.ifPresent(t -> graphics.renderTooltip(font, t, mouseX, mouseY));
            //?}
        }

        Component pageComp = Component.translatable("flexibook.screen.page", pageIndex + 1, Math.max(1, pages.size()));
        pageComp = pageComp.copy().withStyle(McFonts.withFont(Style.EMPTY, bookFont));
        int pageLabelX = leftPos + (theme.bookTexWidth() - font.width(pageComp)) / 2;
        graphics.drawString(font, pageComp, pageLabelX,
                topPos + theme.bookTexHeight() - theme.pageLabelInsetY(), theme.pageTextColor(), false);
    }

    private void renderElement(GuiGraphics graphics, RenderedElement el, int originX, int originY) {
        switch (el) {
            case RenderedElement.TextLine line -> {
                var pose = graphics.pose();
                //? if >=1.21.11 {
                /*pose.pushMatrix();
                pose.translate(originX + line.x(), originY + line.y());
                pose.scale(line.scale(), line.scale());
                *///?} else {
                pose.pushPose();
                pose.translate(originX + line.x(), originY + line.y(), 0);
                pose.scale(line.scale(), line.scale(), 1f);
                //?}

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
                graphics.drawString(font, text, 0, 0, baseColor, false);
                //? if >=1.21.11 {
                /*pose.popMatrix();
                *///?} else {
                pose.popPose();
                //?}
            }
            case RenderedElement.ImageBlock image -> {
                int boxX = originX + Math.round(image.x());
                int boxY = originY + Math.round(image.y());
                blitImage(graphics, image.texture(), boxX, boxY, image.width(), image.height());
            }
            case RenderedElement.DividerLine divider -> {
                int x = originX + Math.round(divider.x());
                int y = originY + Math.round(divider.y() + divider.height() / 2f);
                int w = Math.round(divider.width());
                graphics.fill(x, y, x + w, y + 1, 0xFF000000 | theme.dividerColor());
            }
        }
    }

    private void blitImage(GuiGraphics graphics, ResourceLocation texture,
                           int boxX, int boxY, int boxW, int boxH) {
        if (boxW <= 0 || boxH <= 0) {
            return;
        }
        //? if >=1.21.11 {
        /*if (theme.imageFit() == ImageFit.CONTAIN) {
            var size = TextureSizeCache.getSize(texture);
            if (size.isPresent()) {
                int texW = size.get()[0];
                int texH = size.get()[1];
                ImageFitMath.Fit fit = ImageFitMath.contain(boxW, boxH, texW, texH);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        boxX + fit.offsetX(),
                        boxY + fit.offsetY(),
                        0f,
                        0f,
                        fit.drawW(),
                        fit.drawH(),
                        texW,
                        texH,
                        texW,
                        texH
                );
                return;
            }
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                boxX,
                boxY,
                0f,
                0f,
                boxW,
                boxH,
                boxW,
                boxH
        );
        *///?} else {
        //? if >=1.21.4 {
        /*if (theme.imageFit() == ImageFit.CONTAIN) {
            var size = TextureSizeCache.getSize(texture);
            if (size.isPresent()) {
                int texW = size.get()[0];
                int texH = size.get()[1];
                ImageFitMath.Fit fit = ImageFitMath.contain(boxW, boxH, texW, texH);
                graphics.blit(
                        RenderType::guiTextured,
                        texture,
                        boxX + fit.offsetX(),
                        boxY + fit.offsetY(),
                        0f,
                        0f,
                        fit.drawW(),
                        fit.drawH(),
                        texW,
                        texH,
                        texW,
                        texH
                );
                return;
            }
        }
        graphics.blit(
                RenderType::guiTextured,
                texture,
                boxX,
                boxY,
                0f,
                0f,
                boxW,
                boxH,
                boxW,
                boxH
        );
        *///?} else {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            if (theme.imageFit() == ImageFit.CONTAIN) {
                var size = TextureSizeCache.getSize(texture);
                if (size.isPresent()) {
                    int texW = size.get()[0];
                    int texH = size.get()[1];
                    ImageFitMath.Fit fit = ImageFitMath.contain(boxW, boxH, texW, texH);
                    graphics.blit(
                            texture,
                            boxX + fit.offsetX(),
                            boxY + fit.offsetY(),
                            fit.drawW(),
                            fit.drawH(),
                            0f,
                            0f,
                            texW,
                            texH,
                            texW,
                            texH
                    );
                    return;
                }
            }
            graphics.blit(texture, boxX, boxY, 0, 0, boxW, boxH, boxW, boxH);
        } finally {
            RenderSystem.disableBlend();
        }
        //?}
        //?}
    }
    //?}

    private Optional<Component> hoverTip(int mouseX, int mouseY, int contentX, int contentY, RenderedPage page) {
        double localX = mouseX - contentX;
        double localY = mouseY - contentY;
        for (RenderedPage.ClickArea area : page.clickAreas()) {
            if (area.contains(localX, localY)) {
                return Optional.of(Component.translatable("flexibook.link.tooltip", area.label()));
            }
        }
        for (RenderedElement el : page.elements()) {
            if (el instanceof RenderedElement.ImageBlock img) {
                if (localX >= img.x() && localY >= img.y()
                        && localX < img.x() + img.width() && localY < img.y() + img.height()) {
                    if (img.tooltipKey().isPresent()) {
                        return Optional.of(Component.translatable(img.tooltipKey().get()));
                    }
                    break;
                }
            }
        }
        return Optional.empty();
    }

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
            style = McFonts.withFont(style, flags.font().get());
        }
        return style;
    }

    // --- input -------------------------------------------------------------------------------

    //? if >=1.21.11 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && !pages.isEmpty()) {
            int contentX = leftPos + theme.contentLeft();
            int contentY = topPos + theme.contentTop() + theme.contentOffsetY();
            double localX = event.x() - contentX;
            double localY = event.y() - contentY;
            RenderedPage page = pages.get(pageIndex);
            for (RenderedPage.ClickArea area : page.clickAreas()) {
                if (area.contains(localX, localY)) {
                    LinkHandler.handle(area.action(), this);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(event);
        }
        int keyCode = event.key();
        if (keyCode == 263 || keyCode == 65) {
            changePage(-1);
            return true;
        }
        if (keyCode == 262 || keyCode == 68) {
            changePage(1);
            return true;
        }
        return super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !pages.isEmpty()) {
            int contentX = leftPos + theme.contentLeft();
            int contentY = topPos + theme.contentTop() + theme.contentOffsetY();
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
        if (keyCode == 263 || keyCode == 65) {
            changePage(-1);
            return true;
        }
        if (keyCode == 262 || keyCode == 68) {
            changePage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
