# Pixel parity harness (dev only)

Compares the rendered book screen of an archived branch build against the
`unified` build, for the same book, page, window size and scene.

## What the harness does

`client/AutoSmokeClient` (dev-only, never active in release runs) can capture
the last completed frame after the book is fully open:

```
-Dflexibook.autoSmoke=true
-Dflexibook.autoSmoke.screenshotDir=/tmp/parity/shots/<run>
-Dflexibook.autoSmoke.screenshotPrefix=<old|new>-<node>
-Dflexibook.autoSmoke.screenshotSettleTicks=30
```

It writes `<prefix>-page0.png` and `<prefix>-page1.png` through
`Screenshot.takeScreenshot` + `NativeImage.writeToFile` (Stonecutter-gated for
the 1.21.11/26.x API split and the 26.2 render-target move).

Determinism recipe used for the parity audit:

* Window pinned to 1280x800 (Xvfb 1600x1000 screen, `xdotool windowsize`).
* `run/options.txt`: `guiScale:2`, `lang:en_us`, `pauseOnLostFocus:false`,
  `fancyGraphics:false`, `renderClouds:false`, `particles:2`, `ao:false`,
  `biomeBlendRadius:0`, `entityShadows:false`, `maxFps:60`.
* The harness fixes world seed 1 (screenshot mode), disables daylight/weather
  cycles, sets time 6000, clears weather, removes entities, and teleports the
  camera to a fixed block platform looking straight up. The book screen pauses
  the integrated server, so nothing moves after the book opens.
* Same book fixture (`dev/smoke/book.txt` → `note:note11`) on both sides.

Run the same jar twice first: both runs must produce identical SHA-256. Only
then compare old vs new.

## Compare

```bash
python3 scripts/parity/compare_screenshots.py \
  /tmp/parity/shots/old-1.21.4-r1/old-1.21.4-page0.png \
  /tmp/parity/shots/new-1.21.4-r3/new-1.21.4-page0.png \
  --diff /tmp/parity/diff-1.21.4-page0.png --amplify 40
```

Exit status 0 = pixel-identical; 1 = differences (with pixel count, ratio,
bounding box and max channel delta); 2 = missing input or size mismatch.
