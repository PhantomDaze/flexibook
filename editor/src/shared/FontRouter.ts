/**
 * Routes width/draw to Unihex (flexibook:default) or registered BrowserFont by style.font.
 * Unregistered external ids fall back to unihex (caller may show a banner).
 */

import type { TextMeasurer } from './providers';
import type { StyleFlags } from './types';
import type { UnihexFont } from './UnihexFont';
import { FLEXIBOOK_DEFAULT_FONT } from './UnihexFont';
import type { BrowserFont } from './BrowserFont';

export interface TextRenderer extends TextMeasurer {
  isReady(): boolean;
  drawText(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    sc: number,
    style: StyleFlags,
  ): boolean;
}

export class FontRouter implements TextRenderer {
  private unihex: UnihexFont;
  private customs: Map<string, BrowserFont>;
  private defaultId: string;

  constructor(
    unihex: UnihexFont,
    customs: Map<string, BrowserFont> = new Map(),
    defaultId: string = FLEXIBOOK_DEFAULT_FONT,
  ) {
    this.unihex = unihex;
    this.customs = customs;
    this.defaultId = defaultId;
  }

  setCustoms(customs: Map<string, BrowserFont>): void {
    this.customs = customs;
  }

  setUnihex(unihex: UnihexFont): void {
    this.unihex = unihex;
  }

  hasCustom(id: string | undefined | null): boolean {
    if (!id) return false;
    return this.customs.has(id);
  }

  registeredIds(): string[] {
    return [...this.customs.keys()];
  }

  isReady(): boolean {
    return this.unihex.isReady();
  }

  private resolveId(style: StyleFlags, fontId?: string): string {
    if (fontId && fontId.length > 0) return fontId;
    if (style.font && style.font.length > 0) return style.font;
    return this.defaultId;
  }

  private pick(style: StyleFlags, fontId?: string): TextRenderer {
    const id = this.resolveId(style, fontId);
    if (id === FLEXIBOOK_DEFAULT_FONT || id === this.defaultId) {
      return this.unihex;
    }
    const custom = this.customs.get(id);
    if (custom && custom.isReady()) return custom;
    // unregistered / not ready → unihex fallback
    return this.unihex;
  }

  width(text: string, style: StyleFlags, fontId?: string): number {
    return this.pick(style, fontId).width(text, style, fontId);
  }

  drawText(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    sc: number,
    style: StyleFlags,
  ): boolean {
    return this.pick(style).drawText(ctx, text, x, y, sc, style);
  }
}
