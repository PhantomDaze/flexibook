import type { TextMeasurer } from './providers';
import type { StyleFlags } from './types';
import * as fflate from 'fflate';

/**
 * Editor-side Unihex font parser and renderer matching Minecraft 1.21.1 UnihexProvider.
 * Consumes the same unifont_all-*.zip (collection of .hex files) as the mod's flexibook:default.
 *
 * - Glyph source: 16px tall, variable 8/16/24/32 px wide bitmaps
 * - Packing: MSB left in 32-bit row words
 * - Edge trim: left = clz(mask), right = 32 - ctz(mask) - 1
 * - Advance: Math.floor((right - left + 1) / 2) + 1
 * - Space override: U+0020 advance = 4 (from space provider)
 * - ZWNJ: U+200C advance = 0
 * - Missing: fallback to U+FFFD glyph if present, else advance 0
 * - Bold: +0.5 to per-glyph advance (float sum before final ceil)
 * - width(): sum per-glyph (bold-adjusted), then Math.ceil — matches Font.width
 * - draw: offscreen raster + source-in tint; bold second pass +0.5sc; italic shear; no system fillText
 */
export const FLEXIBOOK_DEFAULT_FONT = 'flexibook:default';
export const MC_CELL = 8; // logical px height after oversample 2 on 16px source

export function resolveBookFont(explicit?: string | null): string {
  return explicit && explicit.length > 0 ? explicit : FLEXIBOOK_DEFAULT_FONT;
}

interface Glyph {
  /** 16 row words, bits MSB-left (bit 31 = leftmost of 32) */
  lines: Uint32Array;
  left: number;
  right: number;
  /** Source bit width for the row encoding (8/16/24/32) */
  bitWidth: number;
}

function ctz32(n: number): number {
  n = n >>> 0;
  if (n === 0) return 32;
  let c = 0;
  if ((n & 0xffff) === 0) { c += 16; n >>>= 16; }
  if ((n & 0xff) === 0) { c += 8; n >>>= 8; }
  if ((n & 0xf) === 0) { c += 4; n >>>= 4; }
  if ((n & 0x3) === 0) { c += 2; n >>>= 2; }
  if ((n & 0x1) === 0) { c += 1; }
  return c;
}

function parseHexLine(line: string): { cp: number; glyph: Glyph } | null {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('#')) return null;
  const m = trimmed.match(/^([0-9A-Fa-f]{4,6}):(.*)$/);
  if (!m) return null;
  const cp = parseInt(m[1], 16);
  const hex = (m[2] || '').replace(/\s+/g, '').toUpperCase();
  const hexLen = hex.length;
  // 16 rows × {2,4,6,8} hex digits → total length 32/64/96/128
  if (hexLen === 0 || hexLen % 16 !== 0) return null;
  const digitsPerRow = hexLen / 16;
  if (![2, 4, 6, 8].includes(digitsPerRow)) return null;
  const bitWidth = digitsPerRow * 4;

  const lines = new Uint32Array(16);
  let mask = 0;
  for (let r = 0; r < 16; r++) {
    const rowHex = hex.substring(r * digitsPerRow, (r + 1) * digitsPerRow);
    const val = parseInt(rowHex || '0', 16) >>> 0;
    let packed: number;
    if (bitWidth === 8) {
      packed = (val & 0xff) << 24;
    } else if (bitWidth === 16) {
      packed = (val & 0xffff) << 16;
    } else if (bitWidth === 24) {
      packed = (val & 0xffffff) << 8;
    } else {
      packed = val >>> 0;
    }
    lines[r] = packed >>> 0;
    mask |= lines[r];
  }
  mask >>>= 0;

  let left: number;
  let right: number;
  if (mask === 0) {
    left = 0;
    right = bitWidth;
  } else {
    left = Math.clz32(mask);
    const tz = ctz32(mask);
    right = 32 - tz - 1;
  }
  return { cp, glyph: { lines, left, right, bitWidth } };
}

function parseAllHex(text: string, target: Map<number, Glyph>): void {
  const lines = text.split(/\r?\n/);
  for (const ln of lines) {
    const res = parseHexLine(ln);
    if (res) target.set(res.cp, res.glyph);
  }
}

/**
 * Exported for tests: parse a single .hex file content into the glyph map.
 */
export function parseUnihexHex(text: string): Map<number, Glyph> {
  const m = new Map<number, Glyph>();
  parseAllHex(text, m);
  return m;
}

export class UnihexFont implements TextMeasurer {
  private glyphs = new Map<number, Glyph>();
  private ready = false;

  private glyphCanvas: HTMLCanvasElement | null = null;
  private glyphCtx: CanvasRenderingContext2D | null = null;

  static async loadFromUrl(url: string): Promise<UnihexFont> {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Failed to fetch unihex: ${res.status}`);
    const buf = await res.arrayBuffer();
    return UnihexFont.loadFromArrayBuffer(buf);
  }

  static async loadFromArrayBuffer(buf: ArrayBuffer): Promise<UnihexFont> {
    const font = new UnihexFont();
    const bytes = new Uint8Array(buf);
    let entries: Record<string, Uint8Array>;
    try {
      entries = fflate.unzipSync(bytes);
    } catch (e) {
      // Some zips may need gunzip if single, but MC uses zip of hex; try raw
      throw new Error('Failed to unzip unihex font: ' + (e as Error).message);
    }
    for (const [name, data] of Object.entries(entries)) {
      if (name.toLowerCase().endsWith('.hex')) {
        const text = new TextDecoder().decode(data);
        parseAllHex(text, font.glyphs);
      }
    }
    font.ready = true;
    return font;
  }

  isReady(): boolean {
    return this.ready;
  }

  /**
   * Per-codepoint advance (bold adds +0.5 to the float before ceil at string level).
   */
  getAdvance(codepoint: number, bold = false): number {
    if (codepoint === 0x20) return 4;
    if (codepoint === 0x200C) return 0;
    let g = this.glyphs.get(codepoint);
    if (!g) {
      g = this.glyphs.get(0xfffd);
    }
    if (!g) {
      return bold ? 0.5 : 0;
    }
    const w = g.right - g.left + 1;
    const base = Math.floor(w / 2) + 1;
    return bold ? base + 0.5 : base;
  }

  width(text: string, style: StyleFlags, _fontId?: string): number {
    if (!text) return 0;
    const bold = !!style.bold;
    let sum = 0;
    for (const chStr of text) {
      const cp = chStr.codePointAt(0)!;
      sum += this.getAdvance(cp, bold);
    }
    return Math.ceil(sum);
  }

  /**
   * Draw using unihex bitmaps. Returns true if drew (ready).
   * Never falls back to fillText / system fonts.
   */
  drawText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, sc: number, style: StyleFlags): boolean {
    if (!this.ready || !text) return false;

    const bold = !!style.bold;
    const italic = !!style.italic;
    const fill = ctx.fillStyle;

    ctx.save();
    ctx.imageSmoothingEnabled = false;

    let cur = x;

    for (const chStr of text) {
      const cp = chStr.codePointAt(0)!;
      let g = this.glyphs.get(cp);
      if (!g) g = this.glyphs.get(0xfffd);

      const adv = this.getAdvance(cp, bold);
      if (!g) {
        cur += adv * sc;
        continue;
      }

      const gw = g.right - g.left + 1;
      const ghSrc = 16;

      // dest size in screen px for this glyph at current sc
      const logW = gw / 2;
      const logH = 8;
      const destW = Math.max(1, Math.ceil(logW * sc));
      const destH = Math.max(1, Math.ceil(logH * sc));

      // ensure offscreen
      if (!this.glyphCanvas || this.glyphCanvas.width < destW || this.glyphCanvas.height < destH) {
        this.glyphCanvas = document.createElement('canvas');
        this.glyphCanvas.width = Math.max(this.glyphCanvas.width || 0, destW + 2);
        this.glyphCanvas.height = Math.max(this.glyphCanvas.height || 0, destH + 2);
        this.glyphCtx = this.glyphCanvas.getContext('2d', { willReadFrequently: false });
      }
      const gCanvas = this.glyphCanvas;
      const gg = this.glyphCtx;
      if (!gg) {
        cur += adv * sc;
        continue;
      }

      // raster source bits to a 1:1 source canvas
      const srcC = document.createElement('canvas');
      srcC.width = gw;
      srcC.height = ghSrc;
      const sg = srcC.getContext('2d', { willReadFrequently: false })!;
      sg.imageSmoothingEnabled = false;
      sg.fillStyle = '#fff';
      for (let ry = 0; ry < 16; ry++) {
        const row = g.lines[ry] >>> 0;
        for (let bx = 0; bx < gw; bx++) {
          const bitIdx = g.left + bx;
          const bit = (row >>> (31 - bitIdx)) & 1;
          if (bit) sg.fillRect(bx, ry, 1, 1);
        }
      }

      // draw to offscreen (nearest via smoothing=false)
      gg.clearRect(0, 0, gCanvas.width, gCanvas.height);
      gg.imageSmoothingEnabled = false;
      gg.globalCompositeOperation = 'source-over';
      gg.drawImage(srcC, 0, 0, gw, ghSrc, 0, 0, destW, destH);

      // tint with desired color
      gg.globalCompositeOperation = 'source-in';
      gg.fillStyle = fill as string;
      gg.fillRect(0, 0, destW, destH);
      gg.globalCompositeOperation = 'source-over';

      // draw to main, with italic shear + bold offset
      const drawW = destW;
      const drawH = destH;
      const drawY = y;
      if (italic) {
        ctx.save();
        // match existing McAtlas shear style for visual parity
        ctx.transform(1, 0, -0.18, 1, drawY * 0.18, 0);
        ctx.drawImage(gCanvas, 0, 0, drawW, drawH, cur, drawY, drawW, drawH);
        if (bold) {
          ctx.drawImage(gCanvas, 0, 0, drawW, drawH, cur + 0.5 * sc, drawY, drawW, drawH);
        }
        ctx.restore();
      } else {
        ctx.drawImage(gCanvas, 0, 0, drawW, drawH, cur, drawY, drawW, drawH);
        if (bold) {
          ctx.drawImage(gCanvas, 0, 0, drawW, drawH, cur + 0.5 * sc, drawY, drawW, drawH);
        }
      }

      cur += adv * sc;
    }

    ctx.restore();
    return true;
  }
}
