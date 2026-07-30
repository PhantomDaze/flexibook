/**
 * Import a FlexiBook resource pack (folder map or zip) into editor state pieces.
 * Accepts the same 6-part layout produced by packExport.
 */

import * as fflate from 'fflate';

import type { AdaptiveBookContent, BookTheme } from './types';
import { parseBookContentJson, parseThemeJson } from './modJson';
import type { LangTables } from './langTables';
import type { CustomFontExport, FontFileExt } from './customFonts';
import { detectFontExt, isValidFontId, parseFontId } from './customFonts';
import { validateNamespace } from './packExport';

export interface PackImportFile {
  /** POSIX path relative to pack root */
  path: string;
  data: Uint8Array;
}

export interface ImportedFont {
  id: string;
  fileName: string;
  ext: FontFileExt;
  bytes: ArrayBuffer;
  size: number;
  oversample: number;
  shiftX: number;
  shiftY: number;
}

export interface PackImportResult {
  namespace: string | null;
  themeId: string | null;
  bookId: string | null;
  theme: BookTheme | null;
  content: AdaptiveBookContent | null;
  /** book index font field if present */
  bookFont: string | null;
  langTables: LangTables;
  fonts: ImportedFont[];
  textures: {
    book: ArrayBuffer | null;
    widgets: ArrayBuffer | null;
  };
  packFormat: number | null;
  warnings: string[];
  /** Relative paths found */
  files: string[];
}

export interface ApplyPackImportOptions {
  /** merge | replace lang tables (default merge) */
  langMode?: 'merge' | 'replace';
  /** merge | replace custom fonts (default merge by id) */
  fontMode?: 'merge' | 'replace';
}

function normPath(p: string): string {
  return p.replace(/\\/g, '/').replace(/^\/+/, '');
}

/** Strip a single root folder prefix if every entry shares one (zip-of-folder). */
export function stripCommonRoot(paths: string[]): { root: string; map: Map<string, string> } {
  const norms = paths.map(normPath).filter((p) => p && !p.endsWith('/'));
  if (norms.length === 0) return { root: '', map: new Map() };
  const firstSegs = norms.map((p) => p.split('/')[0] || '');
  const candidate = firstSegs[0] || '';
  const allSame =
    !!candidate &&
    firstSegs.every((s) => s === candidate) &&
    norms.every((p) => p.includes('/'));
  // Don't strip if candidate looks like pack root markers
  const looksLikePackRoot =
    norms.some((p) => p === 'pack.mcmeta' || p.startsWith('assets/')) ||
    norms.some((p) => p === candidate + '/pack.mcmeta' || p.startsWith(candidate + '/assets/'));
  if (allSame && looksLikePackRoot && !norms.some((p) => p === 'pack.mcmeta' || p.startsWith('assets/'))) {
    const map = new Map<string, string>();
    const prefix = candidate + '/';
    for (const p of norms) {
      map.set(p, p.startsWith(prefix) ? p.slice(prefix.length) : p);
    }
    return { root: candidate, map };
  }
  const map = new Map<string, string>();
  for (const p of norms) map.set(p, p);
  return { root: '', map };
}

export function unzipPackBytes(bytes: Uint8Array): PackImportFile[] {
  const entries = fflate.unzipSync(bytes);
  const files: PackImportFile[] = [];
  for (const [rawPath, data] of Object.entries(entries)) {
    const p = normPath(rawPath);
    if (!p || p.endsWith('/')) continue;
    files.push({ path: p, data });
  }
  return files;
}

function u8ToArrayBuffer(u8: Uint8Array): ArrayBuffer {
  return u8.buffer.slice(u8.byteOffset, u8.byteOffset + u8.byteLength) as ArrayBuffer;
}

function decodeJson(u8: Uint8Array): unknown {
  const text = new TextDecoder('utf-8').decode(u8);
  return JSON.parse(text);
}

interface FontProviderWire {
  type?: string;
  file?: string;
  size?: number;
  oversample?: number;
  shift?: number[];
}

interface FontJsonWire {
  providers?: FontProviderWire[];
}

/**
 * Parse pack file list into editor-ready pieces.
 * Prefers the first namespace under assets/ that contains flexibook/ or lang/ or font/.
 */
export function parsePackFiles(filesIn: PackImportFile[]): PackImportResult {
  const warnings: string[] = [];
  const paths = filesIn.map((f) => f.path);
  const { map: pathMap } = stripCommonRoot(paths);
  const byPath = new Map<string, Uint8Array>();
  for (const f of filesIn) {
    const stripped = pathMap.get(normPath(f.path)) || normPath(f.path);
    byPath.set(stripped, f.data);
  }
  const files = [...byPath.keys()].sort();

  let packFormat: number | null = null;
  const mcmeta = byPath.get('pack.mcmeta');
  if (mcmeta) {
    try {
      const meta = decodeJson(mcmeta) as { pack?: { pack_format?: number } };
      if (typeof meta?.pack?.pack_format === 'number') packFormat = meta.pack.pack_format;
    } catch {
      warnings.push('pack.mcmeta JSON parse failed');
    }
  }

  // Discover namespaces
  const nsSet = new Set<string>();
  for (const p of files) {
    const m = /^assets\/([^/]+)\//.exec(p);
    if (m?.[1]) nsSet.add(m[1]);
  }
  // Prefer ns that has flexibook data
  let namespace: string | null = null;
  for (const ns of nsSet) {
    if (files.some((p) => p.startsWith(`assets/${ns}/flexibook/`))) {
      namespace = ns;
      break;
    }
  }
  if (!namespace) {
    for (const ns of nsSet) {
      if (
        files.some(
          (p) =>
            p.startsWith(`assets/${ns}/lang/`) ||
            p.startsWith(`assets/${ns}/font/`) ||
            p.startsWith(`assets/${ns}/textures/`),
        )
      ) {
        namespace = ns;
        break;
      }
    }
  }
  if (!namespace && nsSet.size === 1) namespace = [...nsSet][0]!;
  if (namespace) {
    const nsErr = validateNamespace(namespace);
    if (nsErr) {
      warnings.push(`namespace "${namespace}": ${nsErr}`);
    }
  }

  const base = namespace ? `assets/${namespace}/` : null;

  // Themes
  let theme: BookTheme | null = null;
  let themeId: string | null = null;
  if (base) {
    const themePaths = files.filter((p) => p.startsWith(`${base}flexibook/themes/`) && p.endsWith('.json'));
    if (themePaths.length > 1) {
      warnings.push(`multiple themes (${themePaths.length}); using first: ${themePaths[0]}`);
    }
    if (themePaths[0]) {
      try {
        theme = parseThemeJson(decodeJson(byPath.get(themePaths[0]!)!));
        const m = /\/themes\/(.+)\.json$/.exec(themePaths[0]!);
        themeId = m?.[1] || null;
      } catch (e) {
        warnings.push(`theme parse failed: ${(e as Error).message || e}`);
      }
    }
  }

  // Books index + contents
  let content: AdaptiveBookContent | null = null;
  let bookId: string | null = null;
  let bookFont: string | null = null;
  if (base) {
    const bookPaths = files.filter((p) => p.startsWith(`${base}flexibook/books/`) && p.endsWith('.json'));
    const contentPaths = files.filter(
      (p) => p.startsWith(`${base}flexibook/contents/`) && p.endsWith('.json'),
    );

    let contentPath: string | null = null;
    if (bookPaths[0]) {
      try {
        const idx = decodeJson(byPath.get(bookPaths[0]!)!) as {
          content?: string;
          theme?: string;
          font?: string;
        };
        if (typeof idx.font === 'string') bookFont = idx.font;
        if (typeof idx.theme === 'string' && idx.theme.includes(':')) {
          const tid = idx.theme.split(':')[1];
          if (tid && !themeId) themeId = tid;
          // if theme file matches, already loaded; if not yet, try load referenced theme
          if (tid && base) {
            const tp = `${base}flexibook/themes/${tid}.json`;
            if (!theme && byPath.has(tp)) {
              try {
                theme = parseThemeJson(decodeJson(byPath.get(tp)!));
                themeId = tid;
              } catch {
                /* ignore */
              }
            }
          }
        }
        if (typeof idx.content === 'string' && idx.content.includes(':')) {
          const cid = idx.content.split(':')[1];
          if (cid) {
            bookId = cid;
            const cp = `${base}flexibook/contents/${cid}.json`;
            if (byPath.has(cp)) contentPath = cp;
          }
        }
        const bm = /\/books\/(.+)\.json$/.exec(bookPaths[0]!);
        if (!bookId && bm?.[1]) bookId = bm[1];
      } catch (e) {
        warnings.push(`book index parse failed: ${(e as Error).message || e}`);
      }
    }

    if (!contentPath && contentPaths[0]) {
      contentPath = contentPaths[0]!;
      const cm = /\/contents\/(.+)\.json$/.exec(contentPath);
      if (!bookId && cm?.[1]) bookId = cm[1];
      if (contentPaths.length > 1) {
        warnings.push(`multiple contents; using ${contentPath}`);
      }
    }

    if (contentPath && byPath.has(contentPath)) {
      try {
        content = parseBookContentJson(decodeJson(byPath.get(contentPath)!));
        if (bookFont && content && !content.defaultFont) {
          content = { ...content, defaultFont: bookFont };
        } else if (bookFont && content) {
          // index font wins as default if body has no font / different — keep body defaultFont if set
          if (!content.defaultFont) content = { ...content, defaultFont: bookFont };
        }
      } catch (e) {
        warnings.push(`content parse failed: ${(e as Error).message || e}`);
      }
    }
  }

  // Lang
  const langTables: LangTables = {};
  if (base) {
    for (const p of files) {
      if (!p.startsWith(`${base}lang/`) || !p.endsWith('.json')) continue;
      const code = p.slice(`${base}lang/`.length, -'.json'.length);
      if (!code || code.includes('/')) continue;
      try {
        const raw = decodeJson(byPath.get(p)!) as Record<string, unknown>;
        const table: Record<string, string> = {};
        for (const [k, v] of Object.entries(raw || {})) {
          if (typeof k === 'string' && typeof v === 'string') table[k] = v;
        }
        langTables[code.toLowerCase()] = table;
      } catch {
        warnings.push(`lang parse failed: ${p}`);
      }
    }
  }

  // Fonts: scan font/*.json providers
  const fonts: ImportedFont[] = [];
  if (base) {
    const fontJsonPaths = files.filter((p) => p.startsWith(`${base}font/`) && p.endsWith('.json'));
    for (const jp of fontJsonPaths) {
      let wire: FontJsonWire;
      try {
        wire = decodeJson(byPath.get(jp)!) as FontJsonWire;
      } catch {
        warnings.push(`font json parse failed: ${jp}`);
        continue;
      }
      const fontPath = jp.slice(`${base}font/`.length, -'.json'.length);
      const id = namespace ? `${namespace}:${fontPath}` : fontPath;
      const providers = Array.isArray(wire.providers) ? wire.providers : [];
      const ttf = providers.find((pr) => pr && (pr.type === 'ttf' || pr.type === 'otf'));
      if (!ttf) {
        // skip unihex / bitmap only
        if (providers.length) warnings.push(`font ${id}: no ttf provider (skipped)`);
        continue;
      }
      const fileRef = typeof ttf.file === 'string' ? ttf.file : '';
      // MC: "namespace:name" → assets/ns/font/name.ttf
      let fileNs = namespace || '';
      let fileBase = fontPath;
      if (fileRef.includes(':')) {
        const [fns, fpath] = fileRef.split(':');
        fileNs = fns || fileNs;
        fileBase = fpath || fileBase;
      } else if (fileRef) {
        fileBase = fileRef;
      }
      fileBase = fileBase.replace(/\.(ttf|otf)$/i, '');
      let fontBytes: Uint8Array | null = null;
      let ext: FontFileExt = 'ttf';
      for (const e of ['ttf', 'otf'] as FontFileExt[]) {
        const candidate = `assets/${fileNs}/font/${fileBase}.${e}`;
        if (byPath.has(candidate)) {
          fontBytes = byPath.get(candidate)!;
          ext = e;
          break;
        }
      }
      // also try same folder as json
      if (!fontBytes) {
        for (const e of ['ttf', 'otf'] as FontFileExt[]) {
          const candidate = `${base}font/${fileBase}.${e}`;
          if (byPath.has(candidate)) {
            fontBytes = byPath.get(candidate)!;
            ext = e;
            break;
          }
        }
      }
      if (!fontBytes) {
        warnings.push(`font ${id}: missing binary for ${fileRef || fileBase}`);
        continue;
      }
      const shift = Array.isArray(ttf.shift) ? ttf.shift : [0, 0];
      fonts.push({
        id: isValidFontId(id) ? id : `${namespace || 'pack'}:${fontPath.replace(/[^a-z0-9_./-]/gi, '_')}`,
        fileName: `${fileBase}.${ext}`,
        ext,
        bytes: u8ToArrayBuffer(fontBytes),
        size: typeof ttf.size === 'number' && ttf.size > 0 ? ttf.size : 11,
        oversample: typeof ttf.oversample === 'number' && ttf.oversample > 0 ? ttf.oversample : 2,
        shiftX: typeof shift[0] === 'number' ? shift[0] : 0,
        shiftY: typeof shift[1] === 'number' ? shift[1] : 0,
      });
    }
  }

  // Textures
  let bookTex: ArrayBuffer | null = null;
  let widgetsTex: ArrayBuffer | null = null;
  if (base) {
    const bookP = `${base}textures/gui/book.png`;
    const widgetsP = `${base}textures/gui/book_widgets.png`;
    if (byPath.has(bookP)) bookTex = u8ToArrayBuffer(byPath.get(bookP)!);
    if (byPath.has(widgetsP)) widgetsTex = u8ToArrayBuffer(byPath.get(widgetsP)!);
  }
  // fallback: any book.png under textures/gui
  if (!bookTex) {
    const p = files.find((x) => /\/textures\/gui\/book\.png$/i.test(x));
    if (p) bookTex = u8ToArrayBuffer(byPath.get(p)!);
  }
  if (!widgetsTex) {
    const p = files.find((x) => /\/textures\/gui\/book_widgets\.png$/i.test(x));
    if (p) widgetsTex = u8ToArrayBuffer(byPath.get(p)!);
  }

  if (!theme && !content && !Object.keys(langTables).length && !fonts.length && !bookTex) {
    warnings.push('no FlexiBook theme/content/lang/font/texture found in pack');
  }

  return {
    namespace,
    themeId,
    bookId,
    theme,
    content,
    bookFont,
    langTables,
    fonts,
    textures: { book: bookTex, widgets: widgetsTex },
    packFormat,
    warnings,
    files,
  };
}

/** High-level: parse zip bytes */
export function importPackFromZip(bytes: Uint8Array): PackImportResult {
  return parsePackFiles(unzipPackBytes(bytes));
}

/** Detect if path list looks like a resource pack root */
export function isPackRootPaths(paths: string[]): boolean {
  const n = paths.map(normPath);
  if (n.some((p) => p === 'pack.mcmeta' || p.endsWith('/pack.mcmeta'))) return true;
  if (n.some((p) => p.startsWith('assets/') || p.includes('/assets/'))) return true;
  return false;
}

export type { CustomFontExport };
export { detectFontExt, parseFontId };
