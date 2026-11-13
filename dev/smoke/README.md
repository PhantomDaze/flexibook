# Smoke fixtures

These files are tracked so a clean clone can run the client smoke without any
local setup.

- `book.txt` — book under test. The first non-empty, non-`#` line wins
  (comments and blank lines are ignored by `AutoSmokeClient`). Currently
  `note:note11`, which comes from the pack below.
- `resourcepacks/` — every folder or `.zip` here is copied into the game's
  `resourcepacks/` directory and force-enabled by the smoke harness.

`note_pack/` is a deliberately minimal pack: `pack.mcmeta`, one book+content+
theme+lang set, and a 393×512 solid-colour `book.png` (~1 KB) instead of the
full artwork. It exercises the whole pack → book → theme → screen pipeline.

## Local override

Drop a bigger/real pack folder or `.zip` into `dev/smoke/resourcepacks/` and/or
edit `dev/smoke/book.txt` locally. To keep overrides out of git, either point
the harness at another directory with `-Dflexibook.autoSmoke.dir=<dir>` /
`-Dflexibook.autoSmoke.resourcePacksDir=<dir>`, or use a path listed in
`.git/info/exclude`. Generated per-node copies live in `versions/*/run/` and
are already ignored.

## Gradle entry points

- `./gradlew smokeAllClients` — all 11 nodes, three JVM passes.
- `./gradlew smokeAllClientsPassL|PassA|PassB` — one pass at a time.
- `./gradlew syncSmokeResourcePacks syncSmokeBookId` — refresh node run dirs.
