import type { TextMeasurer } from './providers';
import type { StyleFlags } from './types';

/**
 * Minecraft 默认 ascii 字体图集测量器。
 * @deprecated 默认路径已切换到 UnihexFont（同一 ZIP 位图，与模组一致）。保留此文件仅供测试或显式使用，
 * 默认预览和布局不得使用。
 *
 * - 加载 assets/minecraft/textures/font/ascii.png (128x128, 16x16 格, 每格 8x8)
 * - 运行时扫描每格像素宽；advance = glyphWidth + 1（对齐 BitmapProvider）
 * - 空格 advance=4（space provider）；空字形 advance=1
 * - 粗体再 +1（GlyphInfo.getBoldOffset）
 * - 提供 drawText 用于预览精确绘制（离屏着色，避免污染主 canvas）
 * - 未就绪时使用回退 advance 表，仍可工作
 * - 仅 U+0020–U+007E 走 ascii 图集（码点=格索引）。Latin-1 符号（×§…）与 CJK
 *   不索引高格（那是 CP437 线框），改走 Unifont；全角 8px / 半角符号约 6px
 */

/** Logical cell size of vanilla ascii glyphs (px at gui scale 1). */
export const MC_CELL = 8;

/**
 * Fallback stack for non-atlas codepoints (CJK…).
 * Primary: bundled GNU Unifont (bitmap 8/16 — same family MC unifont is based on).
 * Then mono CJK system fonts if Unifont missing.
 */
export const MC_CJK_FONT_FAMILY = 'FlexiBook Unifont';
export const MC_CJK_FONT_STACK =
  `"${MC_CJK_FONT_FAMILY}", "Noto Sans Mono CJK SC", "Noto Sans CJK SC", monospace`;

/** Fullwidth-ish advance: 8px glyph box (matches MC cell). Bold adds +1 separately. */
export const MC_CJK_ADVANCE = MC_CELL;

/** True once Unifont (or acceptable fallback) has been loaded for canvas use. */
let cjkFontReady = false;
let cjkFontLoadPromise: Promise<boolean> | null = null;

/**
 * Ensure the CJK fallback face is available. Safe to call multiple times.
 * Resolves true if FlexiBook Unifont loaded; false if we fall back to system fonts.
 */
export function ensureCjkFontLoaded(): Promise<boolean> {
  if (cjkFontReady) return Promise.resolve(true);
  if (cjkFontLoadPromise) return cjkFontLoadPromise;

  cjkFontLoadPromise = (async () => {
    if (typeof document === 'undefined' || !('fonts' in document)) {
      cjkFontReady = true;
      return false;
    }
    // OTF moved to editor/legacy/fonts so Vite assets glob does not ship it.
    // This legacy path is unused by default preview (UnihexFont).
    const candidates = [
      '/legacy/fonts/unifont.otf',
    ];

    for (const url of candidates) {
      try {
        const face = new FontFace(MC_CJK_FONT_FAMILY, `url(${url})`, {
          style: 'normal',
          weight: '400',
        });
        const loaded = await face.load();
        document.fonts.add(loaded);
        // Warm measure path
        await document.fonts.load(`16px "${MC_CJK_FONT_FAMILY}"`);
        cjkFontReady = true;
        return true;
      } catch {
        // try next url
      }
    }
    // Still mark ready so UI doesn't hang; stack may hit system mono CJK
    cjkFontReady = true;
    return false;
  })();

  return cjkFontLoadPromise;
}

export function isCjkFontReady(): boolean {
  return cjkFontReady;
}

/**
 * Vanilla ascii.png bitmap provider maps chars[] row-major onto 16×16 cells.
 * Only U+0020–U+007E sit at cell index == codepoint. High cells are CP437-like
 * box-drawing / extras (and empty slots) — NOT Latin-1. Symbols like × U+00D7
 * live in nonlatin_european.png, so indexing the atlas by codepoint draws garbage.
 */
function isBasicAsciiAtlasCode(ch: number): boolean {
  return ch >= 0x20 && ch < 0x7f;
}

/** Rough MC fullwidth-ish ranges → fixed 8px cell. Everything else uses half advance. */
function isFullwidthCode(ch: number): boolean {
  if (ch < 0x1100) return false;
  // Hangul Jamo / CJK / fullwidth forms / etc.
  return (
    (ch >= 0x1100 && ch <= 0x11ff) ||
    (ch >= 0x2e80 && ch <= 0xa4cf) ||
    (ch >= 0xac00 && ch <= 0xd7af) ||
    (ch >= 0xf900 && ch <= 0xfaff) ||
    (ch >= 0xfe10 && ch <= 0xfe19) ||
    (ch >= 0xfe30 && ch <= 0xfe6f) ||
    (ch >= 0xff00 && ch <= 0xff60) ||
    (ch >= 0xffe0 && ch <= 0xffe6) ||
    (ch >= 0x20000 && ch <= 0x3fffd)
  );
}

/** Fallback advance for non-atlas glyphs (Unifont path). */
function fallbackAdvance(ch: number): number {
  if (ch === 0x20) return 4;
  if (isFullwidthCode(ch)) return MC_CJK_ADVANCE;
  // Halfwidth punctuation / European symbols (× — … § …): closer to latin glyph
  return 6;
}

function cjkFontCss(sizePx: number, italic: boolean): string {
  // Minecraft bold does not select a separate font weight. Font.renderChar draws
  // the same glyph again at GlyphInfo.getBoldOffset(); drawText mirrors that below.
  return `${italic ? 'italic ' : ''}${Math.max(1, Math.round(sizePx))}px ${MC_CJK_FONT_STACK}`;
}

export class McAtlasTextMeasurer implements TextMeasurer {
  private atlas: HTMLImageElement | null = null;
  /** Per-codepoint advance (glyph pixel width + 1), matching BitmapProvider. */
  private advances: number[] = new Array(256).fill(6);
  /** Raw glyph pixel width (no +1); used when drawing bold second pass offset. */
  private glyphWidths: number[] = new Array(256).fill(5);
  private ready = false;
  private onReady?: () => void;

  constructor(onReady?: () => void) {
    this.onReady = onReady;
    this.load();
    // 预置一个与 MC 接近的回退表（常见拉丁字符）
    this.seedFallback();
  }

  private seedFallback() {
    // Typical MC ascii advances (glyphWidth + 1). Space is 4 from space provider.
    const fallback: Record<string, number> = {
      ' ': 4, '!': 2, '"': 5, '#': 6, '$': 6, '%': 6, '&': 6, "'": 3,
      '(': 5, ')': 5, '*': 5, '+': 6, ',': 2, '-': 6, '.': 2, '/': 6,
      '0': 6, '1': 6, '2': 6, '3': 6, '4': 6, '5': 6, '6': 6, '7': 6,
      '8': 6, '9': 6, ':': 2, ';': 2, '<': 5, '=': 6, '>': 5, '?': 6,
      '@': 7, 'A': 6, 'B': 6, 'C': 6, 'D': 6, 'E': 6, 'F': 6, 'G': 6,
      'H': 6, 'I': 4, 'J': 6, 'K': 6, 'L': 6, 'M': 6, 'N': 6, 'O': 6,
      'P': 6, 'Q': 6, 'R': 6, 'S': 6, 'T': 6, 'U': 6, 'V': 6, 'W': 6,
      'X': 6, 'Y': 6, 'Z': 6, '[': 4, '\\': 6, ']': 4, '^': 6, '_': 6,
      '`': 3, 'a': 6, 'b': 6, 'c': 6, 'd': 6, 'e': 6, 'f': 5, 'g': 6,
      'h': 6, 'i': 2, 'j': 6, 'k': 5, 'l': 3, 'm': 6, 'n': 6, 'o': 6,
      'p': 6, 'q': 6, 'r': 6, 's': 6, 't': 4, 'u': 6, 'v': 6, 'w': 6,
      'x': 6, 'y': 6, 'z': 6, '{': 5, '|': 2, '}': 5, '~': 7,
    };
    for (let i = 0; i < 256; i++) {
      const ch = String.fromCharCode(i);
      if (fallback[ch] != null) {
        this.advances[i] = fallback[ch];
        // Approximate glyph width = advance - 1 (except space)
        this.glyphWidths[i] = i === 0x20 ? 0 : Math.max(1, fallback[ch] - 1);
      }
    }
  }

  private load() {
    const img = new Image();
    img.onload = () => {
      this.atlas = img;
      this.computeWidthsFromAtlas();
      this.ready = true;
      this.onReady?.();
    };
    img.onerror = () => {
      // keep fallback
      this.ready = false;
    };
    // Prefer the same relative pattern used for book textures (proven to work).
    // Then public root (for plain Vite dev server), then alternative.
    // ascii.png moved to editor/legacy/… — default path uses UnihexFont only.
    const candidates = [
      '/legacy/minecraft/textures/font/ascii.png',
    ];
    const tryLoad = (idx: number) => {
      if (idx >= candidates.length) return;
      img.src = candidates[idx]!;
      // chain onerror for next candidate
      img.onerror = () => tryLoad(idx + 1);
    };
    tryLoad(0);
  }

  /**
   * Inject a preloaded image (e.g. loaded via the same new URL pattern used for book textures in App).
   * This makes font parity loading consistent with other textures.
   */
  setAtlas(img: HTMLImageElement) {
    this.atlas = img;
    this.computeWidthsFromAtlas();
    this.ready = true;
    this.onReady?.();
  }

  private computeWidthsFromAtlas() {
    const img = this.atlas;
    if (!img) return;
    const canvas = document.createElement('canvas');
    canvas.width = img.width;
    canvas.height = img.height;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) return;
    ctx.drawImage(img, 0, 0);
    const { data, width } = ctx.getImageData(0, 0, img.width, img.height);

    const CELL = 8;
    for (let i = 0; i < 256; i++) {
      const col = i % 16;
      const row = (i / 16) | 0;
      const baseX = col * CELL;
      const baseY = row * CELL;

      // Rightmost non-empty column + 1 = glyph pixel width (BitmapProvider.getActualGlyphWidth)
      let glyphW = 0;
      for (let x = CELL - 1; x >= 0; x--) {
        let hit = false;
        for (let y = 0; y < CELL; y++) {
          const idx = ((baseY + y) * width + (baseX + x)) * 4 + 3;
          if (data[idx]! > 8) {
            hit = true;
            break;
          }
        }
        if (hit) {
          glyphW = x + 1;
          break;
        }
      }

      // Vanilla BitmapProvider: advance = round(glyphW * scale) + 1  (scale=1 for ascii)
      // Space comes from space provider with advance 4 (not empty-cell +1).
      let advance: number;
      if (i === 0x20) {
        glyphW = 0;
        advance = 4;
      } else if (glyphW === 0) {
        advance = 1; // empty cell still gets +1 spacing in bitmap provider path
      } else {
        advance = glyphW + 1;
      }
      this.glyphWidths[i] = glyphW;
      this.advances[i] = advance;
    }
  }

  isReady(): boolean {
    return this.ready;
  }

  getAtlas(): HTMLImageElement | null {
    return this.atlas;
  }

  getCharWidth(code: number): number {
    if (isBasicAsciiAtlasCode(code)) return this.advances[code] ?? 6;
    return fallbackAdvance(code);
  }

  width(text: string, style: StyleFlags, _fontId?: string): number {
    if (!text) return 0;
    const bold = !!style.bold;
    let sum = 0;
    // Iterate code points (handles surrogate pairs for rare CJK ext)
    for (const chStr of text) {
      const ch = chStr.codePointAt(0)!;
      let w = isBasicAsciiAtlasCode(ch) ? this.advances[ch]! : fallbackAdvance(ch);
      if (bold) w += 1; // GlyphInfo.getBoldOffset
      sum += w;
    }
    return sum;
  }

  /**
   * 使用图集绘制文本。返回是否成功（失败时调用方应回退 fillText）。
   *
   * 着色必须在离屏 canvas 完成：destination-in / source-in 若直接作用于主 canvas
   * 会把书背景等已绘制内容一起裁掉，变成「一坨黑」。
   * 流程：离屏画白字 → source-in 当前 fillStyle → 贴回主 canvas。
   */
  drawText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, sc: number, style: StyleFlags): boolean {
    const img = this.atlas;
    if (!img || !this.ready) return false;

    const bold = !!style.bold;
    const italic = !!style.italic;
    const fill = ctx.fillStyle;

    // Offscreen glyph buffer (8×8 logical, scaled destination size)
    // Reuse a single tiny canvas; recreate if scale grows.
    const cellPx = Math.max(1, Math.ceil(8 * sc));
    if (!this.glyphCanvas || this.glyphCanvas.width < cellPx || this.glyphCanvas.height < cellPx) {
      this.glyphCanvas = document.createElement('canvas');
      this.glyphCanvas.width = cellPx;
      this.glyphCanvas.height = cellPx;
      this.glyphCtx = this.glyphCanvas.getContext('2d', { willReadFrequently: false });
    }
    const gCanvas = this.glyphCanvas;
    const g = this.glyphCtx;
    if (!g) return false;

    ctx.save();
    ctx.imageSmoothingEnabled = false;

    let cur = x;
    for (const chStr of text) {
      const ch = chStr.codePointAt(0)!;
      if (isBasicAsciiAtlasCode(ch)) {
        const col = ch % 16;
        const row = (ch / 16) | 0;
        const sx = col * 8;
        const sy = row * 8;
        const advance = this.advances[ch] || 6;
        const dw = 8 * sc;
        const dh = 8 * sc;

        // Build tinted glyph offscreen
        g.clearRect(0, 0, gCanvas.width, gCanvas.height);
        g.imageSmoothingEnabled = false;
        g.globalCompositeOperation = 'source-over';
        g.drawImage(img, sx, sy, 8, 8, 0, 0, cellPx, cellPx);
        // Keep only where glyph has alpha, fill with desired color
        g.globalCompositeOperation = 'source-in';
        g.fillStyle = fill as string;
        g.fillRect(0, 0, cellPx, cellPx);
        g.globalCompositeOperation = 'source-over';

        if (italic) {
          // shear around baseline-ish by drawing with a small x skew via transform
          ctx.save();
          ctx.transform(1, 0, -0.18, 1, y * 0.18, 0);
          ctx.drawImage(gCanvas, 0, 0, cellPx, cellPx, cur, y, dw, dh);
          if (bold) {
            ctx.drawImage(gCanvas, 0, 0, cellPx, cellPx, cur + sc, y, dw, dh);
          }
          ctx.restore();
        } else {
          ctx.drawImage(gCanvas, 0, 0, cellPx, cellPx, cur, y, dw, dh);
          if (bold) {
            // Bold: second pass offset by 1 logical px (matches MC)
            ctx.drawImage(gCanvas, 0, 0, cellPx, cellPx, cur + sc, y, dw, dh);
          }
        }

        // advance already includes glyphWidth+1; bold adds +1
        cur += (advance + (bold ? 1 : 0)) * sc;
      } else {
        // Non-atlas: Unifont / CJK stack. Includes × — … and all CJK.
        // Slot width matches width() so layout and paint stay aligned.
        const prevFont = ctx.font;
        const fontPx = Math.max(1, Math.round(MC_CELL * sc));
        ctx.font = cjkFontCss(fontPx, italic);
        try {
          (ctx as CanvasRenderingContext2D & { fontKerning?: string }).fontKerning = 'none';
        } catch { /* ignore */ }

        const logicalAdv = fallbackAdvance(ch) + (bold ? 1 : 0);
        const adv = logicalAdv * sc;
        const measured = ctx.measureText(chStr).width;
        // Center in slot; if glyph wider than slot, left-align and let it slightly overflow
        const ox = measured <= adv ? Math.floor((adv - measured) / 2) : 0;
        ctx.fillText(chStr, cur + ox, y);
        if (bold) {
          ctx.fillText(chStr, cur + ox + sc, y);
        }

        ctx.font = prevFont;
        cur += adv;
      }
    }
    ctx.restore();
    return true;
  }

  private glyphCanvas: HTMLCanvasElement | null = null;
  private glyphCtx: CanvasRenderingContext2D | null = null;
}
