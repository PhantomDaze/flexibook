/**
 * 共享类型定义 - 镜像 Java 侧的 record 结构
 * 用于 Electron 编辑器与 mod 保持一致的数据模型
 */

// 简单的 ResourceLocation 表示（字符串形式 "namespace:path"）
export type RL = string;

export interface ResourceLocationLike {
  namespace: string;
  path: string;
}

export function rl(namespace: string, path: string): RL {
  return `${namespace}:${path}`;
}

export function parseRL(rl: RL): ResourceLocationLike {
  const idx = rl.indexOf(':');
  if (idx < 0) return { namespace: 'minecraft', path: rl };
  return { namespace: rl.slice(0, idx), path: rl.slice(idx + 1) };
}

// ===== StyleFlags =====
export interface StyleFlags {
  bold: boolean;
  italic: boolean;
  underline: boolean;
  color?: number; // 0xRRGGBB
  font?: RL;
}

export const StyleFlags = {
  EMPTY: { bold: false, italic: false, underline: false } as StyleFlags,

  withBold(sf: StyleFlags, value: boolean): StyleFlags {
    return { ...sf, bold: value };
  },
  withItalic(sf: StyleFlags, value: boolean): StyleFlags {
    return { ...sf, italic: value };
  },
  withUnderline(sf: StyleFlags, value: boolean): StyleFlags {
    return { ...sf, underline: value };
  },
  withColor(sf: StyleFlags, value?: number): StyleFlags {
    return { ...sf, color: value };
  },
  withFont(sf: StyleFlags, value?: RL): StyleFlags {
    return { ...sf, font: value };
  },
  merge(a: StyleFlags, b: StyleFlags): StyleFlags {
    return {
      bold: a.bold || b.bold,
      italic: a.italic || b.italic,
      underline: a.underline || b.underline,
      color: b.color ?? a.color,
      font: b.font ?? a.font,
    };
  },
};

// ===== LinkAction =====
export type LinkAction =
  | { type: 'none' }
  | { type: 'command'; id: string }
  | { type: 'url'; url: string };

export const LinkAction = {
  NONE: { type: 'none' } as LinkAction,
  command(id: string): LinkAction {
    return { type: 'command', id };
  },
  url(url: string): LinkAction {
    return { type: 'url', url };
  },
};

// ===== InlineSpan =====
export interface InlineSpan {
  text: string;
  translate: boolean;
  style: StyleFlags;
  link?: LinkAction;
}

export const InlineSpan = {
  key(text: string, style: StyleFlags = StyleFlags.EMPTY, link?: LinkAction): InlineSpan {
    return { text, translate: true, style, link };
  },
  literal(text: string, style: StyleFlags = StyleFlags.EMPTY, link?: LinkAction): InlineSpan {
    return { text, translate: false, style, link };
  },
};

// ===== TranslatableText =====
export interface TranslatableText {
  key: string;
  args: string[];
}

export const TranslatableText = {
  of(key: string, ...args: string[]): TranslatableText {
    return { key, args };
  },
};

// ===== BookElement =====
export type BookElement =
  | { type: 'heading'; level: number; text: TranslatableText; font?: RL }
  | { type: 'paragraph'; spans: InlineSpan[] }
  | { type: 'br' }
  | { type: 'divider' }
  | { type: 'image'; src: RL; width: number; height: number; tooltip?: string }
  | { type: 'bullet'; spans: InlineSpan[] }
  | { type: 'box'; className?: string; children: BookElement[] };

export const BookElement = {
  Heading(level: number, text: TranslatableText, font?: RL): BookElement {
    return { type: 'heading', level, text, font };
  },
  Paragraph(spans: InlineSpan[]): BookElement {
    return { type: 'paragraph', spans };
  },
  LineBreak(): BookElement {
    return { type: 'br' };
  },
  Divider(): BookElement {
    return { type: 'divider' };
  },
  Image(src: RL, width = 16, height = 16, tooltip?: string): BookElement {
    return { type: 'image', src, width, height, tooltip };
  },
  Bullet(spans: InlineSpan[]): BookElement {
    return { type: 'bullet', spans };
  },
  Box(className: string | undefined, children: BookElement[]): BookElement {
    return { type: 'box', className, children };
  },
};

// ===== AdaptiveBookContent =====
export interface AdaptiveBookContent {
  title: TranslatableText;
  rawMarkup?: string;
  elements?: BookElement[];
  defaultFont?: RL;
  themeId?: RL;
}

export const AdaptiveBookContent = {
  EMPTY: {
    title: TranslatableText.of('flexibook.book.empty.title'),
    elements: [],
  } as AdaptiveBookContent,

  ofElements(
    title: TranslatableText,
    elements: BookElement[],
    defaultFont?: RL,
    themeId?: RL
  ): AdaptiveBookContent {
    return {
      title,
      elements: [...elements],
      defaultFont,
      themeId,
    };
  },

  ofMarkup(
    title: TranslatableText,
    markup: string,
    defaultFont?: RL,
    themeId?: RL
  ): AdaptiveBookContent {
    return {
      title,
      rawMarkup: markup,
      defaultFont,
      themeId,
    };
  },
};

// ===== ImageFit =====
export type ImageFit = 'stretch' | 'contain';

// ===== BookTheme（精简版，足够布局使用） =====
export interface BookTheme {
  bookTexture: RL;
  widgetsTexture: RL;
  bookTexWidth: number;
  bookTexHeight: number;
  textureSheetSize: number;
  contentLeft: number;
  contentTop: number;
  titleOffsetY: number;
  contentOffsetY: number;
  pageLabelInsetY: number;
  pageContentWidth: number;
  pageContentHeight: number;
  lineHeight: number;
  paragraphGap: number;
  headingGap: number;
  gutter: number;
  bulletIndent: number;
  dividerHeight: number;
  pageTextColor: number;
  linkColor: number;
  highlightColor: number;
  dividerColor: number;
  imageFit: ImageFit;
  revision: number;
}

export const BookTheme = {
  DEFAULT: {
    bookTexture: 'flexibook:textures/gui/book.png',
    widgetsTexture: 'flexibook:textures/gui/book_widgets.png',
    bookTexWidth: 192,
    bookTexHeight: 216,
    textureSheetSize: 256,
    contentLeft: 16,
    contentTop: 10,
    titleOffsetY: 0,
    contentOffsetY: 4,
    pageLabelInsetY: 18,
    pageContentWidth: 114,
    pageContentHeight: 160,
    lineHeight: 9,
    paragraphGap: 4,
    headingGap: 6,
    gutter: 8,
    bulletIndent: 8,
    dividerHeight: 6,
    pageTextColor: 0x3F3F3F,
    linkColor: 0x3366CC,
    highlightColor: 0x66FFD54F,
    dividerColor: 0x8B7355,
    imageFit: 'stretch' as ImageFit,
    revision: 1,
  } as BookTheme,
};

// ===== RenderedElement =====
export type RenderedElement =
  | {
      kind: 'text';
      x: number;
      y: number;
      scale: number;
      text: string;
      style: StyleFlags;
      link?: LinkAction;
      width: number;
      height: number;
      highlight: boolean;
    }
  | {
      kind: 'image';
      x: number;
      y: number;
      scale: number;
      texture: RL;
      width: number;
      height: number;
      tooltipKey?: string;
    }
  | {
      kind: 'divider';
      x: number;
      y: number;
      scale: number;
      width: number;
      height: number;
    };

// ===== RenderedPage =====
export interface ClickArea {
  x: number;
  y: number;
  w: number;
  h: number;
  action: LinkAction;
  label: string;
}

export interface RenderedPage {
  elements: RenderedElement[];
  clickAreas: ClickArea[];
}

// ===== LayoutParams（内部使用） =====
export interface LayoutParams {
  scale: number;
  columns: number;
  pageContentWidth: number;
  pageContentHeight: number;
  gutter: number;
  lineHeight: number;
  paragraphGap: number;
  headingGap: number;
  dividerHeight: number;
  bulletIndent: number;
}

export function columnWidth(params: LayoutParams): number {
  if (params.columns <= 1) return params.pageContentWidth;
  return Math.floor((params.pageContentWidth - params.gutter * (params.columns - 1)) / params.columns);
}

export function copyParams(p: LayoutParams): LayoutParams {
  return { ...p };
}
