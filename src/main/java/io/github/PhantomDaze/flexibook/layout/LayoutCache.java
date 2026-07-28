package io.github.PhantomDaze.flexibook.layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small LRU cache for layout results.
 */
public final class LayoutCache {
    private final int capacity;
    private final Map<String, List<RenderedPage>> map;

    public LayoutCache(int capacity) {
        this.capacity = Math.max(4, capacity);
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<RenderedPage>> eldest) {
                return size() > LayoutCache.this.capacity;
            }
        };
    }

    public synchronized List<RenderedPage> get(String key) {
        return map.get(key);
    }

    public synchronized void put(String key, List<RenderedPage> pages) {
        map.put(key, pages);
    }

    public synchronized void clear() {
        map.clear();
    }
}
