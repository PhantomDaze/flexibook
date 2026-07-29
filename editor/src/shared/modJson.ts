/**
 * Parse FlexiBook mod wire JSON (assets/.../flexibook/books|themes/*.json)
 * into the editor's AdaptiveBookContent / BookTheme models.
 *
 * Element codec shape: { "type": "heading", "data": { ... } }
 * Theme codec shape: snake_case flat object (book_texture, page_content_width, …)
 * Link SIMPLE_CODEC: { "cmd": "id" } | { "url": "https://…" }
 */

import type {
  AdaptiveBookContent,
  BookElement,
  BookTheme,
  ImageFit,
  InlineSpan,
  LinkAction,
  StyleFlags,
  TranslatableText,
} from './types';
import { StyleFlags as SF } from './types';

/* ===================== Theme ===================== */

type ThemeWire = {
  book_texture?: string;
  widgets_texture?: string;
  book_tex_width?: number;
  book_tex_height?: number;
  texture_sheet_size?: number;
  content_left?: number;
  content_top?: number;
  title_offset_y?: number;
  content_offset_y?: number;
  page_label_inset_y?: number;
  page_content_width?: number;
  page_content_height?: number;
  line_height?: number;
  paragraph_gap?: number;
  heading_gap?: number;
  gutter?: number;
  bullet_indent?: number;
  divider_height?: number;
  page_text_color?: number;
  link_color?: number;
  highlight_color?: number;
  divider_color?: number;
  image_fit?: string;
  revision?: number;
};

const THEME_FALLBACK: BookTheme = {
  bookTexture: 'flexibook:textures/gui/book.png',
  widgetsTexture: 'flexibook:textures/gui/book_widgets.png',
  bookTexWidth: 192,
  bookTexHeight: 216,
  textureSheetSize: 256,
  contentLeft: 16,
  contentTop: 10,
  titleOffsetY: 5,
  contentOffsetY: 4,
  pageLabelInsetY: 18,
  pageContentWidth: 160,
  pageContentHeight: 185,
  lineHeight: 9,
  paragraphGap: 3,
  headingGap: 5,
  gutter: 10,
  bulletIndent: 10,
  dividerHeight: 6,
  pageTextColor: 0x3f3f3f,
  linkColor: 0x0000ee,
  highlightColor: 0xffd54f,
  dividerColor: 0x8b7355,
  imageFit: 'stretch',
  revision: 1,
};

function num(v: unknown, fallback: number): number {
  return typeof v === 'number' && Number.isFinite(v) ? v : fallback;
}

function str(v: unknown, fallback: string): string {
  return typeof v === 'string' && v.length > 0 ? v : fallback;
}

function parseImageFit(v: unknown): ImageFit {
  return v === 'contain' ? 'contain' : 'stretch';
}

/** Convert mod theme JSON (snake_case) → editor BookTheme. */
export function parseThemeJson(raw: unknown): BookTheme {
  const w = (raw && typeof raw === 'object' ? raw : {}) as ThemeWire;
  const f = THEME_FALLBACK;
  return {
    bookTexture: str(w.book_texture, f.bookTexture),
    widgetsTexture: str(w.widgets_texture, f.widgetsTexture),
    bookTexWidth: num(w.book_tex_width, f.bookTexWidth),
    bookTexHeight: num(w.book_tex_height, f.bookTexHeight),
    textureSheetSize: num(w.texture_sheet_size, f.textureSheetSize),
    contentLeft: num(w.content_left, f.contentLeft),
    contentTop: num(w.content_top, f.contentTop),
    titleOffsetY: num(w.title_offset_y, f.titleOffsetY),
    contentOffsetY: num(w.content_offset_y, f.contentOffsetY),
    pageLabelInsetY: num(w.page_label_inset_y, f.pageLabelInsetY),
    pageContentWidth: num(w.page_content_width, f.pageContentWidth),
    pageContentHeight: num(w.page_content_height, f.pageContentHeight),
    lineHeight: num(w.line_height, f.lineHeight),
    paragraphGap: num(w.paragraph_gap, f.paragraphGap),
    headingGap: num(w.heading_gap, f.headingGap),
    gutter: num(w.gutter, f.gutter),
    bulletIndent: num(w.bullet_indent, f.bulletIndent),
    dividerHeight: num(w.divider_height, f.dividerHeight),
    pageTextColor: num(w.page_text_color, f.pageTextColor),
    linkColor: num(w.link_color, f.linkColor),
    highlightColor: num(w.highlight_color, f.highlightColor),
    dividerColor: num(w.divider_color, f.dividerColor),
    imageFit: parseImageFit(w.image_fit),
    revision: num(w.revision, f.revision),
  };
}

/** Editor BookTheme → mod theme JSON (for export). */
export function themeToWire(t: BookTheme): ThemeWire {
  return {
    book_texture: t.bookTexture,
    widgets_texture: t.widgetsTexture,
    book_tex_width: t.bookTexWidth,
    book_tex_height: t.bookTexHeight,
    texture_sheet_size: t.textureSheetSize,
    content_left: t.contentLeft,
    content_top: t.contentTop,
    title_offset_y: t.titleOffsetY,
    content_offset_y: t.contentOffsetY,
    page_label_inset_y: t.pageLabelInsetY,
    page_content_width: t.pageContentWidth,
    page_content_height: t.pageContentHeight,
    line_height: t.lineHeight,
    paragraph_gap: t.paragraphGap,
    heading_gap: t.headingGap,
    gutter: t.gutter,
    bullet_indent: t.bulletIndent,
    divider_height: t.dividerHeight,
    page_text_color: t.pageTextColor,
    link_color: t.linkColor,
    highlight_color: t.highlightColor,
    divider_color: t.dividerColor,
    image_fit: t.imageFit,
    revision: t.revision,
  };
}

/* ===================== Book content ===================== */

type WireTranslatable = { key?: string; args?: unknown[] };
type WireStyle = {
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  color?: number;
  font?: string;
};
type WireLink = { cmd?: string; url?: string; type?: string; id?: string };
type WireSpan = {
  text?: string;
  translate?: boolean;
  style?: WireStyle;
  link?: WireLink;
};
type WireElement = {
  type?: string;
  data?: Record<string, unknown>;
};

function parseTranslatable(raw: unknown, fallbackKey = ''): TranslatableText {
  if (typeof raw === 'string') {
    return { key: raw, args: [] };
  }
  const t = (raw && typeof raw === 'object' ? raw : {}) as WireTranslatable;
  const key = typeof t.key === 'string' ? t.key : fallbackKey;
  const args = Array.isArray(t.args)
    ? t.args.map((a) => (a == null ? '' : String(a)))
    : [];
  return { key, args };
}

function parseStyle(raw: unknown): StyleFlags {
  const s = (raw && typeof raw === 'object' ? raw : {}) as WireStyle;
  const out: StyleFlags = {
    bold: !!s.bold,
    italic: !!s.italic,
    underline: !!s.underline,
  };
  if (typeof s.color === 'number') out.color = s.color;
  if (typeof s.font === 'string' && s.font) out.font = s.font;
  return out;
}

function parseLink(raw: unknown): LinkAction | undefined {
  if (!raw || typeof raw !== 'object') return undefined;
  const l = raw as WireLink;
  // SIMPLE_CODEC: { cmd } | { url }
  if (typeof l.cmd === 'string' && l.cmd) {
    return { type: 'command', id: l.cmd };
  }
  if (typeof l.url === 'string' && l.url) {
    return { type: 'url', url: l.url };
  }
  // Editor / dispatch style fallback
  if (l.type === 'command' && typeof l.id === 'string') {
    return { type: 'command', id: l.id };
  }
  if (l.type === 'url' && typeof l.url === 'string') {
    return { type: 'url', url: l.url };
  }
  return undefined;
}

function parseSpan(raw: unknown): InlineSpan | null {
  if (!raw || typeof raw !== 'object') return null;
  const s = raw as WireSpan;
  if (typeof s.text !== 'string') return null;
  const style = s.style ? parseStyle(s.style) : { ...SF.EMPTY };
  const link = parseLink(s.link);
  return {
    text: s.text,
    translate: s.translate !== false,
    style,
    link,
  };
}

function parseSpans(raw: unknown): InlineSpan[] {
  if (!Array.isArray(raw)) return [];
  const out: InlineSpan[] = [];
  for (const item of raw) {
    const span = parseSpan(item);
    if (span) out.push(span);
  }
  return out;
}

function parseElement(raw: unknown): BookElement | null {
  if (!raw || typeof raw !== 'object') return null;
  const el = raw as WireElement;
  const type = (el.type || '').toLowerCase();
  const data = (el.data && typeof el.data === 'object' ? el.data : {}) as Record<
    string,
    unknown
  >;

  switch (type) {
    case 'heading': {
      const level = num(data.level, 1);
      const text = parseTranslatable(data.text, 'flexibook.book.empty.title');
      const font = typeof data.font === 'string' ? data.font : undefined;
      return font
        ? { type: 'heading', level, text, font }
        : { type: 'heading', level, text };
    }
    case 'paragraph':
      return { type: 'paragraph', spans: parseSpans(data.spans) };
    case 'bullet':
      return { type: 'bullet', spans: parseSpans(data.spans) };
    case 'br':
      return { type: 'br' };
    case 'divider':
      return { type: 'divider' };
    case 'image': {
      const src = str(data.src, 'flexibook:textures/gui/icon.png');
      const width = num(data.width, 16);
      const height = num(data.height, 16);
      const tooltip =
        typeof data.tooltip === 'string' ? data.tooltip : undefined;
      return { type: 'image', src, width, height, tooltip };
    }
    case 'box': {
      const className =
        typeof data.className === 'string'
          ? data.className
          : typeof data.class === 'string'
            ? data.class
            : undefined;
      const childrenRaw = Array.isArray(data.children) ? data.children : [];
      const children: BookElement[] = [];
      for (const c of childrenRaw) {
        const child = parseElement(c);
        if (child) children.push(child);
      }
      return { type: 'box', className, children };
    }
    default:
      return null;
  }
}

/** Convert mod AdaptiveBookContent JSON → editor model. */
export function parseBookContentJson(raw: unknown): AdaptiveBookContent {
  const root = (raw && typeof raw === 'object' ? raw : {}) as Record<
    string,
    unknown
  >;
  const title = parseTranslatable(root.title, 'flexibook.book.empty.title');
  const elements: BookElement[] = [];
  if (Array.isArray(root.elements)) {
    for (const item of root.elements) {
      const el = parseElement(item);
      if (el) elements.push(el);
    }
  }
  const content: AdaptiveBookContent = {
    title,
    elements,
  };
  // wire field "raw" (optional markup)
  if (typeof root.raw === 'string' && root.raw) {
    content.rawMarkup = root.raw;
  } else if (typeof root.rawMarkup === 'string' && root.rawMarkup) {
    content.rawMarkup = root.rawMarkup;
  }
  // wire field "font"
  if (typeof root.font === 'string' && root.font) {
    content.defaultFont = root.font;
  } else if (typeof root.defaultFont === 'string' && root.defaultFont) {
    content.defaultFont = root.defaultFont;
  }
  // wire field "theme"
  if (typeof root.theme === 'string' && root.theme) {
    content.themeId = root.theme;
  } else if (typeof root.themeId === 'string' && root.themeId) {
    content.themeId = root.themeId;
  }
  return content;
}

/** Editor content → mod wire JSON (for export / parity). */
export function bookContentToWire(c: AdaptiveBookContent): Record<string, unknown> {
  const out: Record<string, unknown> = {
    title: {
      key: c.title.key,
      ...(c.title.args?.length ? { args: c.title.args } : {}),
    },
  };
  if (c.rawMarkup) out.raw = c.rawMarkup;
  if (c.defaultFont) out.font = c.defaultFont;
  if (c.themeId) out.theme = c.themeId;
  if (c.elements && c.elements.length > 0) {
    out.elements = c.elements.map(elementToWire);
  }
  return out;
}

function styleToWire(s: StyleFlags | undefined | null): WireStyle | undefined {
  if (!s) return undefined;
  const w: WireStyle = {};
  if (s.bold) w.bold = true;
  if (s.italic) w.italic = true;
  if (s.underline) w.underline = true;
  if (s.color != null) w.color = s.color;
  if (s.font) w.font = s.font;
  return Object.keys(w).length ? w : undefined;
}

function linkToWire(link: LinkAction | undefined): WireLink | undefined {
  if (!link || link.type === 'none') return undefined;
  if (link.type === 'command') return { cmd: link.id };
  if (link.type === 'url') return { url: link.url };
  return undefined;
}

function spanToWire(span: InlineSpan): Record<string, unknown> {
  const o: Record<string, unknown> = {
    text: span.text,
    translate: span.translate,
  };
  const style = styleToWire(span.style);
  if (style) o.style = style;
  const link = linkToWire(span.link);
  if (link) o.link = link;
  return o;
}

function elementToWire(el: BookElement): WireElement {
  switch (el.type) {
    case 'heading': {
      const data: Record<string, unknown> = {
        level: el.level,
        text: {
          key: el.text.key,
          ...(el.text.args?.length ? { args: el.text.args } : {}),
        },
      };
      if (el.font) data.font = el.font;
      return { type: 'heading', data };
    }
    case 'paragraph':
      return { type: 'paragraph', data: { spans: el.spans.map(spanToWire) } };
    case 'bullet':
      return { type: 'bullet', data: { spans: el.spans.map(spanToWire) } };
    case 'br':
      return { type: 'br', data: {} };
    case 'divider':
      return { type: 'divider', data: {} };
    case 'image': {
      const data: Record<string, unknown> = {
        src: el.src,
        width: el.width,
        height: el.height,
      };
      if (el.tooltip) data.tooltip = el.tooltip;
      return { type: 'image', data };
    }
    case 'box': {
      const data: Record<string, unknown> = {
        children: el.children.map(elementToWire),
      };
      if (el.className) data.className = el.className;
      return { type: 'box', data };
    }
  }
}
