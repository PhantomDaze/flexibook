package io.github.PhantomDaze.flexibook.layout;

/**
 * Mutable layout configuration for one tryLayout pass.
 */
public final class LayoutParams {
    public float scale = 1.0f;
    public int columns = 1;
    public int pageContentWidth = 114;
    public int pageContentHeight = 160;
    public int gutter = 8;
    public int lineHeight = 9;
    public int paragraphGap = 4;
    public int headingGap = 6;
    public int dividerHeight = 6;
    public int bulletIndent = 8;

    public int columnWidth() {
        if (columns <= 1) {
            return pageContentWidth;
        }
        return (pageContentWidth - gutter * (columns - 1)) / columns;
    }

    public LayoutParams copy() {
        LayoutParams p = new LayoutParams();
        p.scale = scale;
        p.columns = columns;
        p.pageContentWidth = pageContentWidth;
        p.pageContentHeight = pageContentHeight;
        p.gutter = gutter;
        p.lineHeight = lineHeight;
        p.paragraphGap = paragraphGap;
        p.headingGap = headingGap;
        p.dividerHeight = dividerHeight;
        p.bulletIndent = bulletIndent;
        return p;
    }
}
