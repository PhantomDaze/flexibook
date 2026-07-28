package io.github.PhantomDaze.flexibook.layout;

import io.github.PhantomDaze.flexibook.content.LinkAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RenderedPage {
    private final List<RenderedElement> elements = new ArrayList<>();
    private final List<ClickArea> clickAreas = new ArrayList<>();

    public List<RenderedElement> elements() {
        return elements;
    }

    public List<ClickArea> clickAreas() {
        return clickAreas;
    }

    public void add(RenderedElement element) {
        elements.add(element);
        if (element instanceof RenderedElement.TextLine line && line.link().isPresent()) {
            clickAreas.add(new ClickArea(
                    line.x(),
                    line.y(),
                    line.width() * line.scale(),
                    line.height() * line.scale(),
                    line.link().get(),
                    line.text()
            ));
        }
    }

    public record ClickArea(float x, float y, float w, float h, LinkAction action, String label) {
        public boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
