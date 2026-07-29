# Legacy editor font assets (unused by default path)

Supported old `McAtlasTextMeasurer` (ascii.png + Unifont OTF).
Default preview uses `assets/flexibook/font/unifont_all-17.0.05.zip` via `UnihexFont.ts`.

Kept outside `assets/` so Vite’s dynamic `new URL('../../assets/…')` does not ship them.
