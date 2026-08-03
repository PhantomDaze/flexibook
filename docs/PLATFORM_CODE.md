# Version-local platform code

FlexiBook keeps layout semantics and rendering inputs shared across all version nodes:

- `layout/` owns pagination, wrapping, scale-down, columns, and click areas.
- `content/` owns book data and markup semantics.
- `client/theme/` owns theme metrics, colors, registries, and image-fit geometry.

Minecraft and loader API glue may live under the matching node directory:

```text
versions/<node>/src/main/java/io/github/PhantomDaze/flexibook/...
```

For example, each java21 node has a `client/BookScreenFactory.java`. This is the stable boundary used by the shared `ClientModEvents` entry point. A node-local factory may later select a node-local screen renderer without changing item behavior or the shared layout engine.

## Rules

1. Node-local code must only adapt Minecraft/loader APIs. Do not duplicate `BookLayoutEngine`, `BookTheme`, `RenderedPage`, or content parsing.
2. Keep the same `AdaptiveBookContent`, `BookTheme`, and `RenderedPage` inputs so page count, coordinates, scales, colors, and asset selection stay identical.
3. Put tests that are specific to a node under `versions/<node>/src/test/java`; shared model/layout tests remain under `src/test/java`.
4. Run `./gradlew chiseledBuild` after changing a shared class or build source roots.
5. A new node-local source must not use the same fully-qualified class name as a shared class unless the shared source is explicitly excluded for that node.

This convention is deliberately incremental: existing small `//?` bridges remain valid, while large rendering or lifecycle migrations can be performed one version/API generation at a time.
