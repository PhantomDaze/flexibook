import * as fflate from 'fflate';

import type { BookTheme, AdaptiveBookContent, BookElement, InlineSpan } from './types';
import { themeToWire, bookContentToWire } from './modJson';
import type { CustomFontExport } from './customFonts';
import { parseFontId, fontBaseName } from './customFonts';

/** Which sections to include. Omitted keys default to true (full pack). */
export interface PackParts {
  /** pack.mcmeta + HOW_TO_USE (always written) */
  meta?: boolean;
  /** flexibook/themes/*.json */
  theme?: boolean;
  /** textures/gui book + widgets */
  textures?: boolean;
  /** flexibook/contents + flexibook/books index */
  content?: boolean;
  /** assets/<ns>/lang/*.json */
  lang?: boolean;
  /** assets/<ns>/font/* */
  fonts?: boolean;
}

export const PACK_PARTS_ALL: Required<PackParts> = {
  meta: true,
  theme: true,
  textures: true,
  content: true,
  lang: true,
  fonts: true,
};

export function resolvePackParts(parts?: PackParts | null): Required<PackParts> {
  if (!parts) return { ...PACK_PARTS_ALL };
  return {
    meta: parts.meta !== false,
    theme: !!parts.theme,
    textures: !!parts.textures,
    content: !!parts.content,
    lang: !!parts.lang,
    fonts: !!parts.fonts,
  };
}

/** Short label for zip/folder suffix when exporting a partial pack. */
export function packPartsSuffix(parts: Required<PackParts>): string {
  const flags = [
    parts.theme && 'theme',
    parts.textures && 'tex',
    parts.content && 'content',
    parts.lang && 'lang',
    parts.fonts && 'fonts',
  ].filter(Boolean) as string[];
  if (flags.length === 0 || flags.length >= 5) return 'pack';
  return flags.join('_') + '_pack';
}

export interface PackExportOptions {
  namespace: string; // required, [a-z0-9_.-]+
  themeId?: string; // default 'main', path-safe
  bookId?: string; // default 'guide'
  /** @deprecated use parts.content */
  includeBook?: boolean;
  packFormat?: number; // default 34 (1.21.1)
  packDescription?: string;
  packRootName?: string; // for naming zip/dir, default `${namespace}_pack`
  /** Section filter — omit for full pack */
  parts?: PackParts;
  theme?: BookTheme;
  content?: AdaptiveBookContent;
  /** Custom texture raw bytes (from loadImageFile). If absent, fetch defaults. */
  customBookPng?: ArrayBuffer | null;
  customWidgetsPng?: ArrayBuffer | null;
  /** Vite-fetchable default texture URLs, e.g. '/assets/textures/gui/book.png' */
  defaultBookUrl?: string;
  defaultWidgetsUrl?: string;
  /** Full language tables to embed as assets/<ns>/lang/<lang>.json */
  langTables?: Record<string, Record<string, string>>;
  /** Custom TTF/OTF fonts to embed under assets/<ns>/font/ */
  customFonts?: CustomFontExport[];
  /** Rewrite custom font ids into pack namespace (default true) */
  rewriteFontsToPackNs?: boolean;
}

export interface PackFile {
  /** POSIX-style relative path from pack root (e.g. 'assets/ns/flexibook/themes/main.json') */
  path: string;
  data: Uint8Array;
}

const NS_RE = /^[a-z0-9_.-]+$/;

export function validateNamespace(ns: string): string | null {
  if (!ns || typeof ns !== 'string' || ns.length === 0) {
    return 'Namespace is required';
  }
  if (!NS_RE.test(ns)) {
    return 'Namespace must match [a-z0-9_.-]+';
  }
  if (ns.startsWith('.') || ns.endsWith('.') || ns.startsWith('-') || ns.startsWith('_')) {
    return 'Namespace cannot start/end with . - _';
  }
  return null;
}

function sanitizeSegment(s: string | undefined, fallback: string): string {
  if (!s) return fallback;
  // allow alnum _ . / - but strip .. and control chars
  let out = s.replace(/[^a-z0-9_./-]/gi, '').replace(/\.\.+/g, '.');
  // no leading/trailing / or .
  out = out.replace(/^[/.\s]+|[/.\s]+$/g, '');
  return out || fallback;
}

async function fetchArrayBuffer(url: string): Promise<ArrayBuffer> {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch ${url}: ${res.status}`);
  }
  return await res.arrayBuffer();
}

function sortedLangJson(table: Record<string, string>): string {
  const keys = Object.keys(table).sort((a, b) => a.localeCompare(b));
  const obj: Record<string, string> = {};
  for (const k of keys) obj[k] = table[k]!;
  return JSON.stringify(obj, null, 2);
}

/**
 * Build font id rewrite map: original custom id → exported pack id.
 */
export function buildFontIdRewriteMap(
  namespace: string,
  fonts: CustomFontExport[] | undefined,
  rewrite: boolean,
): Map<string, string> {
  const map = new Map<string, string>();
  if (!fonts) return map;
  for (const f of fonts) {
    const parsed = parseFontId(f.id);
    const pathPart = parsed?.path || fontBaseName(f.fileName);
    const exportedId = rewrite ? `${namespace}:${pathPart}` : f.id;
    map.set(f.id, exportedId);
  }
  return map;
}

function rewriteFontId(id: string | undefined, map: Map<string, string>): string | undefined {
  if (!id) return id;
  return map.get(id) ?? id;
}

function rewriteSpanFonts(spans: InlineSpan[], map: Map<string, string>): InlineSpan[] {
  return spans.map((s) => {
    if (!s.style?.font) return s;
    const nf = rewriteFontId(s.style.font, map);
    if (nf === s.style.font) return s;
    return { ...s, style: { ...s.style, font: nf } };
  });
}

function rewriteElementFonts(el: BookElement, map: Map<string, string>): BookElement {
  switch (el.type) {
    case 'heading': {
      const nf = rewriteFontId(el.font, map);
      if (nf === el.font) return el;
      const next = { ...el };
      if (nf) next.font = nf;
      else delete next.font;
      return next;
    }
    case 'paragraph':
    case 'bullet':
      return { ...el, spans: rewriteSpanFonts(el.spans || [], map) };
    case 'box':
      return {
        ...el,
        children: (el.children || []).map((c) => rewriteElementFonts(c, map)),
      };
    default:
      return el;
  }
}

/** Deep-clone content with custom font ids rewritten for pack namespace. */
export function rewriteContentFonts(
  content: AdaptiveBookContent,
  map: Map<string, string>,
): AdaptiveBookContent {
  if (map.size === 0) return content;
  const next: AdaptiveBookContent = {
    ...content,
    elements: (content.elements || []).map((e) => rewriteElementFonts(e, map)),
  };
  const df = rewriteFontId(content.defaultFont, map);
  if (df) next.defaultFont = df;
  else delete next.defaultFont;
  return next;
}

/**
 * Build the resource pack file list.
 * Use {@link PackExportOptions.parts} to export only selected sections.
 */
export async function buildResourcePack(opts: PackExportOptions): Promise<PackFile[]> {
  const nsErr = validateNamespace(opts.namespace);
  if (nsErr) throw new Error(nsErr);

  const namespace = opts.namespace;
  const themeId = sanitizeSegment(opts.themeId, 'main');
  const bookId = sanitizeSegment(opts.bookId, 'guide');
  const packFormat = typeof opts.packFormat === 'number' && opts.packFormat > 0 ? opts.packFormat : 34;
  // Backward compat: includeBook false ⇒ drop content if parts not set
  let partsIn = opts.parts;
  if (!partsIn && opts.includeBook === false) {
    partsIn = { ...PACK_PARTS_ALL, content: false };
  }
  const parts = resolvePackParts(partsIn ?? PACK_PARTS_ALL);

  const description =
    opts.packDescription ||
    (parts.theme && parts.content && parts.lang && parts.textures && parts.fonts
      ? `FlexiBook pack for ${namespace}`
      : `FlexiBook partial pack (${packPartsSuffix(parts)}) for ${namespace}`);
  const rewriteFonts = opts.rewriteFontsToPackNs !== false;

  const files: PackFile[] = [];

  // Theme JSON always rewrites texture paths when theme is exported; textures optional unless requested
  const exportTextures = parts.textures;

  let bookPng: ArrayBuffer | null = null;
  let widgetsPng: ArrayBuffer | null = null;
  if (exportTextures) {
    if (opts.customBookPng && opts.customBookPng.byteLength > 0) {
      bookPng = opts.customBookPng;
    } else if (opts.defaultBookUrl) {
      bookPng = await fetchArrayBuffer(opts.defaultBookUrl);
    } else {
      throw new Error('textures export requires defaultBookUrl or customBookPng');
    }
    if (opts.customWidgetsPng && opts.customWidgetsPng.byteLength > 0) {
      widgetsPng = opts.customWidgetsPng;
    } else if (opts.defaultWidgetsUrl) {
      widgetsPng = await fetchArrayBuffer(opts.defaultWidgetsUrl);
    } else {
      throw new Error('textures export requires defaultWidgetsUrl or customWidgetsPng');
    }
  }

  let themeWire: Record<string, unknown> | null = null;
  if (parts.theme) {
    if (!opts.theme) throw new Error('theme export requires theme object');
    themeWire = JSON.parse(JSON.stringify(themeToWire(opts.theme))) as Record<string, unknown>;
    themeWire.book_texture = `${namespace}:textures/gui/book.png`;
    themeWire.widgets_texture = `${namespace}:textures/gui/book_widgets.png`;
  }

  // pack.mcmeta always for a valid resource pack
  files.push({
    path: 'pack.mcmeta',
    data: new TextEncoder().encode(
      JSON.stringify(
        {
          pack: {
            pack_format: packFormat,
            description,
          },
        },
        null,
        2,
      ),
    ),
  });

  const fontRewrite = buildFontIdRewriteMap(
    namespace,
    parts.fonts || parts.content ? opts.customFonts : undefined,
    rewriteFonts,
  );

  if (parts.meta !== false) {
    const fontLines =
      parts.fonts && opts.customFonts && opts.customFonts.length > 0
        ? opts.customFonts
            .map((f) => {
              const exp = fontRewrite.get(f.id) || f.id;
              const parsed = parseFontId(exp);
              const pathPart = parsed?.path || fontBaseName(f.fileName);
              return `   font ${exp} → assets/${namespace}/font/${pathPart}.json + .${f.ext}`;
            })
            .join('\n')
        : parts.fonts
          ? '   (none imported)'
          : '   (not included)';

    const langKeys = parts.lang && opts.langTables ? Object.keys(opts.langTables).sort() : [];
    const langLines =
      langKeys.length > 0
        ? langKeys.map((l) => `   assets/${namespace}/lang/${l}.json`).join('\n')
        : parts.lang
          ? '   (empty)'
          : '   (not included)';

    const included = [
      parts.theme && `themes → assets/${namespace}/flexibook/themes/${themeId}.json`,
      parts.textures && `textures → assets/${namespace}/textures/gui/…`,
      parts.content && `contents → assets/${namespace}/flexibook/contents/${bookId}.json`,
      parts.content && `books → assets/${namespace}/flexibook/books/${bookId}.json`,
      parts.lang && `lang → assets/${namespace}/lang/…`,
      parts.fonts && `fonts → assets/${namespace}/font/…`,
    ]
      .filter(Boolean)
      .join('\n');

    const howTo = `FlexiBook Resource Pack — HOW TO USE

Included sections:
${included || '   (meta only)'}

English:
1. Copy this folder (or .zip) into Minecraft "resourcepacks".
2. Options → Resource Packs → enable.
3. Books need contents+books+theme (+ lang for text). Partial packs merge with other packs of the same namespace.

   createBookFromDefinition("${namespace}:${bookId}")

lang:
${langLines}
fonts:
${fontLines}

中文: 本包为分项/完整导出。可与同 namespace 其它分项包叠加。启用后 F3+T。
`;
    files.push({
      path: 'HOW_TO_USE.txt',
      data: new TextEncoder().encode(howTo),
    });
  }

  if (exportTextures && bookPng && widgetsPng) {
    const texBase = `assets/${namespace}/textures/gui/`;
    files.push({ path: texBase + 'book.png', data: new Uint8Array(bookPng) });
    files.push({ path: texBase + 'book_widgets.png', data: new Uint8Array(widgetsPng) });
  }

  if (parts.theme && themeWire) {
    files.push({
      path: `assets/${namespace}/flexibook/themes/${themeId}.json`,
      data: new TextEncoder().encode(JSON.stringify(themeWire, null, 2)),
    });
  }

  if (parts.lang && opts.langTables) {
    for (const [lang, table] of Object.entries(opts.langTables)) {
      if (!lang || !table || typeof table !== 'object') continue;
      const safeLang = lang.replace(/[^a-z0-9_]/gi, '') || 'en_us';
      files.push({
        path: `assets/${namespace}/lang/${safeLang}.json`,
        data: new TextEncoder().encode(sortedLangJson(table)),
      });
    }
  }

  if (parts.fonts && opts.customFonts) {
    const usedFileBases = new Set<string>();
    for (const f of opts.customFonts) {
      const exportedId = fontRewrite.get(f.id) || f.id;
      const parsed = parseFontId(exportedId);
      const fontPath = sanitizeSegment(parsed?.path, fontBaseName(f.fileName));
      let fileBase = fontBaseName(f.fileName);
      let uniqueBase = fileBase;
      let n = 2;
      while (usedFileBases.has(uniqueBase + '.' + f.ext)) {
        uniqueBase = `${fileBase}_${n++}`;
      }
      usedFileBases.add(uniqueBase + '.' + f.ext);

      files.push({
        path: `assets/${namespace}/font/${uniqueBase}.${f.ext}`,
        data: new Uint8Array(f.bytes),
      });

      const size = typeof f.size === 'number' && f.size > 0 ? f.size : 11;
      const oversample = typeof f.oversample === 'number' && f.oversample > 0 ? f.oversample : 2;
      const shiftX = typeof f.shiftX === 'number' ? f.shiftX : 0;
      const shiftY = typeof f.shiftY === 'number' ? f.shiftY : 0;

      const fontJson = {
        providers: [
          {
            type: 'ttf',
            file: `${namespace}:${uniqueBase}`,
            shift: [shiftX, shiftY],
            size,
            oversample,
          },
        ],
      };
      files.push({
        path: `assets/${namespace}/font/${fontPath}.json`,
        data: new TextEncoder().encode(JSON.stringify(fontJson, null, 2)),
      });
    }
  }

  if (parts.content && opts.content) {
    const rewritten = rewriteContentFonts(opts.content, fontRewrite);
    const bodyWire = bookContentToWire(rewritten);
    delete bodyWire.theme;
    files.push({
      path: `assets/${namespace}/flexibook/contents/${bookId}.json`,
      data: new TextEncoder().encode(JSON.stringify(bodyWire, null, 2)),
    });

    const contentId = `${namespace}:${bookId}`;
    const index: Record<string, unknown> = {
      content: contentId,
      theme: `${namespace}:${themeId}`,
    };
    if (typeof bodyWire.font === 'string' && bodyWire.font) {
      index.font = bodyWire.font;
    }
    files.push({
      path: `assets/${namespace}/flexibook/books/${bookId}.json`,
      data: new TextEncoder().encode(JSON.stringify(index, null, 2)),
    });
  }

  return files;
}

/**
 * Produce a zip (Uint8Array) from the file list using fflate.
 */
export function packFilesToZip(files: PackFile[]): Uint8Array {
  const obj: Record<string, Uint8Array> = {};
  for (const f of files) {
    const p = f.path.replace(/\\/g, '/'); // ensure posix inside zip
    obj[p] = f.data;
  }
  return fflate.zipSync(obj);
}
