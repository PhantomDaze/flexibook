/**
 * Approximate MC TTF preview via browser FontFace + canvas measureText/fillText.
 * NOT pixel-parity with Minecraft TrueTypeGlyphProvider — only for authoring feedback.
 */

import type { TextMeasurer } from './providers';
import type { StyleFlags } from './types';
import { MC_CELL } from './UnihexFont';
import type { FontFileExt } from './customFonts';

export class BrowserFont implements TextMeasurer {
  readonly family: string;
  private ready = false;
  private face: FontFace | null = null;
  private measureCanvas: HTMLCanvasElement | null = null;
  private measureCtx: CanvasRenderingContext2D | null = null;

  private constructor(family: string) {
    this.family = family;
  }

  static async loadFromBytes(
    bytes: ArrayBuffer,
    family: string,
    ext: FontFileExt,
  ): Promise<BrowserFont> {
    const font = new BrowserFont(family);
    const format = ext === 'otf' ? 'opentype' : 'truetype';
    // FontFace accepts ArrayBuffer in modern browsers / Electron Chromium
    const face = new FontFace(family, bytes, {
      style: 'normal',
      weight: 'normal',
    });
    // Some engines want format hint in descriptor — set via load of blob url fallback
    try {
      await face.load();
      document.fonts.add(face);
      font.face = face;
      font.ready = true;
    } catch (e1) {
      // Fallback: blob URL
      try {
        const blob = new Blob([bytes], {
          type: ext === 'otf' ? 'font/otf' : 'font/ttf',
        });
        const url = URL.createObjectURL(blob);
        const face2 = new FontFace(family, `url(${url}) format('${format}')`);
        await face2.load();
        document.fonts.add(face2);
        font.face = face2;
        font.ready = true;
        // keep url alive for lifetime of face; caller also holds objectUrl on CustomFont
        void url;
      } catch (e2) {
        throw new Error(
          `FontFace load failed: ${(e2 as Error).message || e1}`,
        );
      }
    }
    return font;
  }

  isReady(): boolean {
    return this.ready;
  }

  unload(): void {
    if (this.face) {
      try {
        document.fonts.delete(this.face);
      } catch {
        /* ignore */
      }
      this.face = null;
    }
    this.ready = false;
  }

  private cssFont(style: StyleFlags, px: number): string {
    const weight = style.bold ? '700' : '400';
    const italic = style.italic ? 'italic' : 'normal';
    return `${italic} ${weight} ${px}px "${this.family}"`;
  }

  private ctx(): CanvasRenderingContext2D {
    if (!this.measureCanvas) {
      this.measureCanvas = document.createElement('canvas');
      this.measureCanvas.width = 4;
      this.measureCanvas.height = 4;
      this.measureCtx = this.measureCanvas.getContext('2d');
    }
    if (!this.measureCtx) {
      throw new Error('2d context unavailable');
    }
    return this.measureCtx;
  }

  width(text: string, style: StyleFlags, _fontId?: string): number {
    if (!text || !this.ready) return 0;
    const c = this.ctx();
    c.font = this.cssFont(style, MC_CELL);
    const m = c.measureText(text);
    // MC Font.width ceils; approximate similarly
    return Math.max(0, Math.ceil(m.width));
  }

  /**
   * Draw with fillText at logical MC_CELL * sc.
   * Returns true if drew.
   */
  drawText(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    sc: number,
    style: StyleFlags,
  ): boolean {
    if (!this.ready || !text) return false;
    const px = MC_CELL * sc;
    ctx.save();
    ctx.font = this.cssFont(style, px);
    ctx.textBaseline = 'top';
    // fillStyle already set by caller
    if (style.italic) {
      // mild shear similar to unihex path for visual consistency
      ctx.transform(1, 0, -0.12, 1, y * 0.12, 0);
    }
    ctx.fillText(text, x, y);
    if (style.bold) {
      // faux-bold second pass (browser bold may already be wider)
      ctx.fillText(text, x + 0.5 * sc, y);
    }
    ctx.restore();
    return true;
  }
}
