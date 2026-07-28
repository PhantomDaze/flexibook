package io.github.PhantomDaze.flexibook.client.theme;

import net.minecraft.util.StringRepresentable;

/**
 * How book images fill their declared logical box ({@code width}×{@code height} on the element).
 *
 * <ul>
 *   <li>{@link #STRETCH} — fill the whole box (may distort if aspect ratios differ).</li>
 *   <li>{@link #CONTAIN} — keep the texture's aspect ratio, scale to fit inside the box, center
 *       remaining space (letterbox / pillarbox).</li>
 * </ul>
 */
public enum ImageFit implements StringRepresentable {
    STRETCH("stretch"),
    CONTAIN("contain");

    private final String name;

    ImageFit(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
