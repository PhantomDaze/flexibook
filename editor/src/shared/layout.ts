import type {
  AdaptiveBookContent,
  BookElement,
  BookTheme,
  InlineSpan,
  LayoutParams,
  LinkAction,
  RenderedPage,
  StyleFlags,
  TranslatableText,
} from './types';
import { columnWidth as getColumnWidth, StyleFlags as SF } from './types';
import type { TextMeasurer, TranslationProvider } from './providers';
import { resolveBookFont } from './UnihexFont';

/**
 * 简单的布局缓存（LRU 风格，固定容量）
 */
export class LayoutCache {
  private readonly maxSize: number;
  private map = new Map<string, import('./types').RenderedPage[]>();

  constructor(maxSize = 32) {
    this.maxSize = maxSize;
  }

  get(key: string): import('./types').RenderedPage[] | undefined {
    const v = this.map.get(key);
    if (v !== undefined) {
      // 刷新顺序
      this.map.delete(key);
      this.map.set(key, v);
    }
    return v;
  }

  put(key: string, value: import('./types').RenderedPage[]): void {
    if (this.map.has(key)) this.map.delete(key);
    this.map.set(key, value);
    while (this.map.size > this.maxSize) {
      const first = this.map.keys().next().value;
      if (first !== undefined) this.map.delete(first);
    }
  }

  clear(): void {
    this.map.clear();
  }
}

const CACHE = new LayoutCache(32);

export function clearLayoutCache(): void {
  CACHE.clear();
}

export interface LayoutOptions {
  content: AdaptiveBookContent;
  measurer: TextMeasurer;
  translator: TranslationProvider | null;
  theme: BookTheme;
  languageCode: string;
  /** 仅用于缓存键，不参与实际测量（与 MC GUI scale 语义一致） */
  guiScaleRef?: number;
  searchQuery?: string;
  /** 字体图集就绪版本，用于在 atlas 加载后使缓存失效并重新测量 */
  fontAtlasRev?: number;
}

/**
 * 主布局入口 —— 对应 Java 的 BookLayoutEngine.layout(..., providers...)
 * guiScaleRef 仅用于缓存键；测量始终由 measurer 决定（固定参考）。
 */
export function layout(options: LayoutOptions): import('./types').RenderedPage[] {
  const {
    content,
    measurer,
    translator,
    theme,
    languageCode,
    guiScaleRef = 2,
    searchQuery = '',
  } = options;

  const q = (searchQuery || '').trim().toLowerCase();
  const bookFont = resolveBookFont(content.defaultFont);
  const atlasRev = options.fontAtlasRev ?? 0;
  const translatorLang = translator
    ? (translator.getLoadedLang?.() || 'anon')
    : 'null';
  const key = `${contentHash(content)}|${languageCode}|tl:${translatorLang}|${guiScaleRef}|${theme.revision}|${bookFont}|${q}|atlas${atlasRev}`;

  const cached = CACHE.get(key);
  if (cached) return cached;

  const elements = resolveElements(content);
  let startScale = 1.0;
  if (looksMostlyCjk(elements, translator)) {
    startScale = 0.92;
  }

  let params = baseParams(theme);
  params.scale = startScale;
  params.columns = 1;

  let pages = tryLayout(elements, measurer, translator, params, q, bookFont);

  let guard = 0;
  while (guard++ < 12 && (pages.length >= 60 || isOvercrowded(pages, params))) {
    if (params.scale > 0.6 + 1e-3) {
      params = { ...params, scale: Math.max(0.6, params.scale - 0.1) };
    } else if (params.columns < 2) {
      params = { ...params, columns: 2, scale: Math.max(0.85, startScale - 0.05) };
    } else {
      break;
    }
    pages = tryLayout(elements, measurer, translator, params, q, bookFont);
  }

  if (pages.length === 0) {
    const empty = createEmptyPage(translator, bookFont);
    pages = [empty];
  }

  CACHE.put(key, pages);
  return pages;
}

function contentHash(c: AdaptiveBookContent): string {
  // 简单稳定哈希：用 JSON + 长度
  try {
    return JSON.stringify(c);
  } catch {
    return `${c.title.key}:${(c.elements?.length ?? 0)}:${c.rawMarkup?.length ?? 0}`;
  }
}

function isOvercrowded(pages: import('./types').RenderedPage[], params: LayoutParams): boolean {
  if (pages.length > 40) return true;
  return params.scale > 0.95 && pages.length > 20;
}

function looksMostlyCjk(elements: BookElement[], translator: TranslationProvider | null): boolean {
  let cjk = 0;
  let total = 0;
  for (const el of elements) {
    let s = '';
    switch (el.type) {
      case 'heading':
        s = resolveTranslatablePlain(el.text, translator);
        break;
      case 'paragraph':
        s = joinSpans(el.spans, translator);
        break;
      case 'bullet':
        s = joinSpans(el.spans, translator);
        break;
      default:
        s = '';
    }
    for (let i = 0; i < s.length; i++) {
      const ch = s.charAt(i);
      if (/\s/.test(ch)) continue;
      total++;
      const code = ch.charCodeAt(0);
      // 简单 CJK 范围检测（与 Java UnicodeBlock 近似）
      if (
        isCJKUnified(code) ||
        isHiragana(code) ||
        isKatakana(code) ||
        isHangul(code)
      ) {
        cjk++;
      }
    }
  }
  return total > 0 && cjk / total > 0.3;
}

function isCJKUnified(code: number): boolean {
  return (
    (code >= 0x4e00 && code <= 0x9fff) || // CJK Unified
    (code >= 0x3400 && code <= 0x4dbf)   // Extension A
  );
}
function isHiragana(code: number): boolean {
  return code >= 0x3040 && code <= 0x309f;
}
function isKatakana(code: number): boolean {
  return code >= 0x30a0 && code <= 0x30ff;
}
function isHangul(code: number): boolean {
  return code >= 0xac00 && code <= 0xd7af;
}

function resolveTranslatablePlain(t: TranslatableText, translator: TranslationProvider | null): string {
  if (!translator) return t.key; // 退化
  if (!looksLikeKey(t.key)) return t.key;
  if (t.args.length === 0) return translator.get(t.key);
  return translator.get(t.key, ...t.args);
}

function resolveSpanPlain(span: InlineSpan, translator: TranslationProvider | null): string {
  if (!span.translate) return span.text;
  if (!translator) return span.text;
  if (looksLikeKey(span.text)) {
    return translator.get(span.text);
  }
  return span.text;
}

function joinSpans(spans: InlineSpan[], translator: TranslationProvider | null): string {
  return spans.map(s => resolveSpanPlain(s, translator)).join('');
}

function looksLikeKey(value: string | undefined | null): boolean {
  return !!value && value.indexOf('.') > 0 && value.indexOf(' ') < 0;
}

function resolveElements(content: AdaptiveBookContent): BookElement[] {
  if (content.elements && content.elements.length > 0) {
    return content.elements;
  }
  // 简化：rawMarkup 暂不在这里解析（后续可接入 TagParser 端口）
  return [];
}

/** 从主题生成基础布局参数 */
export function baseParams(theme: BookTheme): LayoutParams {
  return {
    scale: 1.0,
    columns: 1,
    pageContentWidth: theme.pageContentWidth,
    pageContentHeight: theme.pageContentHeight,
    gutter: theme.gutter,
    lineHeight: theme.lineHeight,
    paragraphGap: theme.paragraphGap,
    headingGap: theme.headingGap,
    dividerHeight: theme.dividerHeight,
    bulletIndent: theme.bulletIndent,
  };
}

function createEmptyPage(translator: TranslationProvider | null, bookFont?: string): RenderedPage {
  const page: RenderedPage = { elements: [], clickAreas: [] };
  const style: StyleFlags = bookFont ? { ...SF.EMPTY, font: bookFont } : SF.EMPTY;
  const text = translator ? translator.get('flexibook.book.empty.body') : 'flexibook.book.empty.body';
  page.elements.push({
    kind: 'text',
    x: 0,
    y: 0,
    scale: 1,
    text,
    style,
    link: undefined,
    width: 100,
    height: 9,
    highlight: false,
  });
  return page;
}

/* ===================== 核心排版逻辑 ===================== */

function tryLayout(
  elements: BookElement[],
  measurer: TextMeasurer,
  translator: TranslationProvider | null,
  params: LayoutParams,
  searchLower: string,
  bookFont?: string
): import('./types').RenderedPage[] {
  const pages: import('./types').RenderedPage[] = [];
  let page: import('./types').RenderedPage = { elements: [], clickAreas: [] };
  pages.push(page);

  const colY: number[] = new Array(params.columns).fill(0);
  let col = 0;

  for (const element of elements) {
    switch (element.type) {
      case 'heading': {
        const sizeMul = element.level <= 1 ? 1.35 : 1.15;
        const scale = params.scale * sizeMul;
        const text = resolveTranslatablePlain(element.text, translator);
        const hi = matchesSearch(text, searchLower);
        let style: StyleFlags = { ...SF.EMPTY, bold: true };
        if (element.font) style = { ...style, font: element.font };
        else if (bookFont) style = { ...style, font: bookFont };
        col = placeWrappedText(pages, colY, col, params, measurer, text, style, undefined, scale, 0, params.headingGap, hi);
        page = pages[pages.length - 1];
        break;
      }
      case 'paragraph': {
        col = placeInlineSpans(pages, colY, col, params, measurer, translator, element.spans, 0, params.paragraphGap, searchLower, bookFont);
        page = pages[pages.length - 1];
        break;
      }
      case 'bullet': {
        const markerScale = params.scale;
        let x = columnX(col, params);
        if (colY[col] + params.lineHeight * markerScale > params.pageContentHeight) {
          const next = advanceColumn(pages, colY, col, params);
          col = next;
          page = pages[pages.length - 1];
          x = columnX(col, params);
        }
        const markerStyle: StyleFlags = bookFont ? { ...SF.EMPTY, font: bookFont } : SF.EMPTY;
        const markerW = measureWidth(measurer, '•', markerStyle);
        page.elements.push({
          kind: 'text',
          x,
          y: colY[col],
          scale: markerScale,
          text: '•',
          style: markerStyle,
          link: undefined,
          width: markerW,
          height: params.lineHeight,
          highlight: false,
        });
        col = placeInlineSpans(pages, colY, col, params, measurer, translator, element.spans, params.bulletIndent, params.paragraphGap, searchLower, bookFont);
        page = pages[pages.length - 1];
        break;
      }
      case 'br': {
        colY[col] += params.lineHeight * params.scale * 0.5;
        break;
      }
      case 'divider': {
        const h = params.dividerHeight * params.scale;
        if (colY[col] + h > params.pageContentHeight) {
          col = advanceColumn(pages, colY, col, params);
          page = pages[pages.length - 1];
        }
        const x = columnX(col, params);
        const w = getColumnWidth(params);
        page.elements.push({
          kind: 'divider',
          x,
          y: colY[col],
          scale: params.scale,
          width: w,
          height: h,
        });
        colY[col] += h + params.paragraphGap * params.scale;
        break;
      }
      case 'image': {
        let w = element.width * params.scale;
        let h = element.height * params.scale;
        const colW = getColumnWidth(params);
        if (w > colW) {
          const fit = colW / element.width;
          w = element.width * fit;
          h = element.height * fit;
        }
        if (colY[col] + h > params.pageContentHeight) {
          col = advanceColumn(pages, colY, col, params);
          page = pages[pages.length - 1];
        }
        const x = columnX(col, params);
        page.elements.push({
          kind: 'image',
          x,
          y: colY[col],
          scale: 1,
          texture: element.src,
          width: Math.round(w),
          height: Math.round(h),
          tooltipKey: element.tooltip,
        });
        colY[col] += h + params.paragraphGap * params.scale;
        break;
      }
      case 'box': {
        for (const child of element.children) {
          col = layoutOne(child, pages, page, colY, col, params, measurer, translator, searchLower, bookFont);
          page = pages[pages.length - 1];
        }
        break;
      }
    }
  }

  return pages;
}

function layoutOne(
  element: BookElement,
  pages: import('./types').RenderedPage[],
  _page: import('./types').RenderedPage,
  colY: number[],
  col: number,
  params: LayoutParams,
  measurer: TextMeasurer,
  translator: TranslationProvider | null,
  searchLower: string,
  bookFont?: string
): number {
  switch (element.type) {
    case 'heading': {
      const sizeMul = element.level <= 1 ? 1.35 : 1.15;
      const scale = params.scale * sizeMul;
      const text = resolveTranslatablePlain(element.text, translator);
      const hi = matchesSearch(text, searchLower);
      let style: StyleFlags = { ...SF.EMPTY, bold: true };
      if (element.font) style = { ...style, font: element.font };
      else if (bookFont) style = { ...style, font: bookFont };
      return placeWrappedText(pages, colY, col, params, measurer, text, style, undefined, scale, 0, params.headingGap, hi);
    }
    case 'paragraph':
      return placeInlineSpans(pages, colY, col, params, measurer, translator, element.spans, 0, params.paragraphGap, searchLower, bookFont);
    case 'bullet': {
      let x = columnX(col, params);
      if (colY[col] + params.lineHeight * params.scale > params.pageContentHeight) {
        col = advanceColumn(pages, colY, col, params);
        x = columnX(col, params);
      }
      const markerStyle: StyleFlags = bookFont ? { ...SF.EMPTY, font: bookFont } : SF.EMPTY;
      const markerW = measureWidth(measurer, '•', markerStyle);
      pages[pages.length - 1].elements.push({
        kind: 'text',
        x,
        y: colY[col],
        scale: params.scale,
        text: '•',
        style: markerStyle,
        link: undefined,
        width: markerW,
        height: params.lineHeight,
        highlight: false,
      });
      return placeInlineSpans(pages, colY, col, params, measurer, translator, element.spans, params.bulletIndent, params.paragraphGap, searchLower, bookFont);
    }
    case 'br': {
      colY[col] += params.lineHeight * params.scale * 0.5;
      return col;
    }
    case 'divider': {
      const h = params.dividerHeight * params.scale;
      if (colY[col] + h > params.pageContentHeight) {
        col = advanceColumn(pages, colY, col, params);
      }
      const x = columnX(col, params);
      const w = getColumnWidth(params);
      pages[pages.length - 1].elements.push({
        kind: 'divider',
        x,
        y: colY[col],
        scale: params.scale,
        width: w,
        height: h,
      });
      colY[col] += h + params.paragraphGap * params.scale;
      return col;
    }
    case 'image': {
      let w = element.width * params.scale;
      let h = element.height * params.scale;
      const colW = getColumnWidth(params);
      if (w > colW) {
        const fit = colW / element.width;
        w = element.width * fit;
        h = element.height * fit;
      }
      if (colY[col] + h > params.pageContentHeight) {
        col = advanceColumn(pages, colY, col, params);
      }
      const x = columnX(col, params);
      pages[pages.length - 1].elements.push({
        kind: 'image',
        x,
        y: colY[col],
        scale: 1,
        texture: element.src,
        width: Math.round(w),
        height: Math.round(h),
        tooltipKey: element.tooltip,
      });
      colY[col] += h + params.paragraphGap * params.scale;
      return col;
    }
    case 'box': {
      let c = col;
      for (const child of element.children) {
        c = layoutOne(child, pages, pages[pages.length - 1], colY, c, params, measurer, translator, searchLower, bookFont);
      }
      return c;
    }
  }
}

function placeInlineSpans(
  pages: import('./types').RenderedPage[],
  colY: number[],
  col: number,
  params: LayoutParams,
  measurer: TextMeasurer,
  translator: TranslationProvider | null,
  spans: InlineSpan[],
  indent: number,
  gapAfter: number,
  searchLower: string,
  bookFont?: string
): number {
  for (const span of spans) {
    const text = resolveSpanPlain(span, translator);
    if (!text) continue;

    let style = span.style;
    if (bookFont && !style.font) {
      style = { ...style, font: bookFont };
    }

    const parts = text.split('\n');
    for (let pi = 0; pi < parts.length; pi++) {
      if (pi > 0) {
        colY[col] += params.lineHeight * params.scale;
      }
      const hi = matchesSearch(parts[pi], searchLower);
      col = placeWrappedText(
        pages,
        colY,
        col,
        params,
        measurer,
        parts[pi],
        style,
        span.link,
        params.scale,
        indent,
        0,
        hi
      );
    }
  }
  colY[col] += gapAfter * params.scale;
  return col;
}

function placeWrappedText(
  pages: import('./types').RenderedPage[],
  colY: number[],
  col: number,
  params: LayoutParams,
  measurer: TextMeasurer,
  text: string,
  style: StyleFlags,
  link: LinkAction | undefined,
  scale: number,
  indent: number,
  gapAfter: number,
  highlight: boolean
): number {
  if (!text) {
    colY[col] += gapAfter * scale;
    return col;
  }

  let colW = getColumnWidth(params) - indent;
  if (colW < 8) colW = 8;

  const maxUnscaled = Math.max(4, Math.floor(colW / scale));
  const measureStyle: StyleFlags = link ? { ...style, underline: true } : style;

  const lines = wrap(measurer, text, maxUnscaled, measureStyle);
  const lineH = params.lineHeight * scale;

  for (const line of lines) {
    if (colY[col] + lineH > params.pageContentHeight) {
      col = advanceColumn(pages, colY, col, params);
    }
    const x = columnX(col, params) + indent;
    const w = measureWidth(measurer, line, measureStyle);
    pages[pages.length - 1].elements.push({
      kind: 'text',
      x,
      y: colY[col],
      scale,
      text: line,
      style,
      link,
      width: w,
      height: params.lineHeight,
      highlight,
    });
    colY[col] += lineH;
  }

  colY[col] += gapAfter * scale;
  return col;
}

function measureWidth(measurer: TextMeasurer, text: string, style: StyleFlags, fontId?: string): number {
  if (!text) return 0;
  return measurer.width(text, style, fontId);
}

function wrap(measurer: TextMeasurer, text: string, maxWidth: number, style: StyleFlags): string[] {
  const lines: string[] = [];
  if (maxWidth <= 0) {
    lines.push(text);
    return lines;
  }
  let start = 0;
  const len = text.length;

  while (start < len) {
    let low = start + 1;
    let high = len;
    let best = start + 1;

    while (low <= high) {
      const mid = (low + high) >>> 1;
      const sub = text.substring(start, mid);
      if (measureWidth(measurer, sub, style) <= maxWidth) {
        best = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }

    let breakAt = best;
    if (best < len) {
      const space = text.lastIndexOf(' ', best - 1);
      if (space >= start + 1) {
        breakAt = space + 1;
      }
    }
    if (breakAt <= start) {
      breakAt = Math.min(start + 1, len);
    }

    lines.push(text.substring(start, breakAt));
    start = breakAt;
    while (start < len && text.charAt(start) === ' ') start++;
  }

  if (lines.length === 0) lines.push('');
  return lines;
}

function advanceColumn(
  pages: import('./types').RenderedPage[],
  colY: number[],
  col: number,
  params: LayoutParams
): number {
  if (col + 1 < params.columns) {
    colY[col + 1] = 0;
    return col + 1;
  }
  // 新页
  pages.push({ elements: [], clickAreas: [] });
  for (let i = 0; i < colY.length; i++) colY[i] = 0;
  return 0;
}

function columnX(col: number, params: LayoutParams): number {
  if (params.columns <= 1) return 0;
  return col * (getColumnWidth(params) + params.gutter);
}

function matchesSearch(text: string | undefined, searchLower: string): boolean {
  if (!searchLower || !text) return false;
  return text.toLowerCase().includes(searchLower);
}

/** 工具：为外部使用暴露 wrap（便于测试） */
export function wrapText(measurer: TextMeasurer, text: string, maxWidth: number, style: StyleFlags): string[] {
  return wrap(measurer, text, maxWidth, style);
}
