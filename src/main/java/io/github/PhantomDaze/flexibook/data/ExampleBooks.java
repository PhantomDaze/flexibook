package io.github.PhantomDaze.flexibook.data;

import io.github.PhantomDaze.flexibook.FlexiBookMod;
import io.github.PhantomDaze.flexibook.api.AdaptiveBookBuilder;
import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ExampleBooks {
    private ExampleBooks() {}

    public static ItemStack demoGuide() {
        // Mixed fonts in one book: default body + alt for a heading / tagged run.
        ResourceLocation alt = ResourceLocation.withDefaultNamespace("alt");
        return new AdaptiveBookBuilder("demo_guide")
                .titleKey("flexibook.book.demo.title")
                .h1("flexibook.book.demo.h1")
                .p("flexibook.book.demo.intro")
                .divider()
                .h2("flexibook.book.demo.features")
                .bullet("flexibook.book.demo.feature.adaptive")
                .bullet("flexibook.book.demo.feature.i18n")
                .bullet("flexibook.book.demo.feature.richtext")
                .bullet("flexibook.book.demo.feature.search")
                .br()
                .p("flexibook.book.demo.body1")
                .p("flexibook.book.demo.body2")
                .image(
                        ResourceLocation.fromNamespaceAndPath(FlexiBookMod.MOD_ID, "textures/gui/icon.png"),
                        48, 48,
                        "flexibook.book.demo.image_tip"
                )
                .divider()
                .h2("flexibook.book.demo.links")
                .link("flexibook.book.demo.link_hi", FlexiBookAPI.commandAction("flexibook:say_hi"))
                .link("flexibook.book.demo.link_web", FlexiBookAPI.urlAction("https://neoforged.net/"))
                .divider()
                .h2("flexibook.book.demo.markup")
                .pRaw("[p]flexibook.book.demo.markup_sample[/p]")
                .pRaw("[p][b]flexibook.book.demo.bold_sample[/b] [i]flexibook.book.demo.italic_sample[/i] [color=#CC5500]flexibook.book.demo.color_sample[/color][/p]")
                .pRaw("[p][font font=\"minecraft:alt\"]flexibook.book.demo.font_sample[/font][/p]")
                .h2("flexibook.book.demo.font_heading", alt)
                .p("flexibook.book.demo.outro")
                .buildItem();
    }
}
