# Shared client smoke resource packs

Place Minecraft resource packs here (folder with `pack.mcmeta`, or `.zip`).

When running smoke with `-Pflexibook.smokeTest=true`, Gradle copies everything in this
directory into each version node's `run/resourcepacks/` before `runClient`.

Enable specific packs and pick the book:

```bash
./gradlew smokeTestAllClients \
  -Pflexibook.smokeTest=true \
  -Pflexibook.smokeTest.resourcePacks=my_pack \
  -Pflexibook.smokeTest.bookId=myns:mybook \
  -Pflexibook.smokeTest.readSeconds=10
```

- `resourcePacks` — comma-separated folder or zip names under this directory (optional `file/` prefix).
  Empty = copy only; packs are not force-enabled.
- `bookId` — `namespace:path` registered via `assets/.../flexibook/books/`.  
  Empty / omitted = built-in `ExampleBooks.demoGuide()`.

Packs must be valid resource packs (`pack.mcmeta` present). After enable, the smoke
waits for resource reload, then opens the configured book.
