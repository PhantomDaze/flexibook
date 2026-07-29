import type { TextMeasurer } from './providers';
import type { StyleFlags } from './types';

/**
 * 基于 HTML5 Canvas 的文本测量实现
 * 参考 AwtTextMeasurer，使用固定参考字号（9px）确保布局稳定
 * GUI 缩放（1/2/3/4）仅影响预览绘制，不影响此处的测量
 */
export class CanvasTextMeasurer implements TextMeasurer {
  private readonly baseFont: string;
  private readonly boldFont: string;
  private readonly italicFont: string;
  private readonly boldItalicFont: string;

  // 复用单个 canvas/context 提高性能
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;

  constructor(baseSizePx = 9) {
    // 使用无衬线字体，尽量接近 MC 默认
    this.baseFont = `${baseSizePx}px sans-serif`;
    this.boldFont = `bold ${baseSizePx}px sans-serif`;
    this.italicFont = `italic ${baseSizePx}px sans-serif`;
    this.boldItalicFont = `bold italic ${baseSizePx}px sans-serif`;

    this.canvas = document.createElement('canvas');
    this.canvas.width = 1;
    this.canvas.height = 1;
    const ctx = this.canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new Error('无法获取 2D 上下文');
    this.ctx = ctx;
  }

  width(text: string, style: StyleFlags, _fontId?: string): number {
    if (!text) return 0;
    this.ctx.font = this.pickFont(style);
    // Canvas measureText 返回的 width 是 CSS 像素，接近布局度量
    const metrics = this.ctx.measureText(text);
    // 下划线不影响 advance，这里直接用 width
    return Math.ceil(metrics.width);
  }

  private pickFont(style: StyleFlags): string {
    const b = !!style.bold;
    const i = !!style.italic;
    if (b && i) return this.boldItalicFont;
    if (b) return this.boldFont;
    if (i) return this.italicFont;
    return this.baseFont;
  }
}
