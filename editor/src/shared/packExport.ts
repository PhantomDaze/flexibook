import * as fflate from 'fflate';

import type { BookTheme, AdaptiveBookContent } from './types';
import { themeToWire, bookContentToWire } from './modJson';

export interface PackExportOptions {
  namespace: string; // required, [a-z0-9_.-]+
  themeId?: string; // default 'main', path-safe
  bookId?: string; // default 'guide'
  includeBook: boolean;
  packFormat?: number; // default 34 (1.21.1)
  packDescription?: string;
  packRootName?: string; // for naming zip/dir, default `${namespace}_pack`
  theme: BookTheme;
  content?: AdaptiveBookContent;
  /** Custom texture raw bytes (from loadImageFile). If absent, fetch defaults. */
  customBookPng?: ArrayBuffer | null;
  customWidgetsPng?: ArrayBuffer | null;
  /** Vite-fetchable default texture URLs, e.g. '/assets/textures/gui/book.png' */
  defaultBookUrl: string;
  defaultWidgetsUrl: string;
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

/**
 * Build the full resource pack file list (pure, no side effects except fetch for defaults).
 */
export async function buildResourcePack(opts: PackExportOptions): Promise<PackFile[]> {
  const nsErr = validateNamespace(opts.namespace);
  if (nsErr) throw new Error(nsErr);

  const namespace = opts.namespace;
  const themeId = sanitizeSegment(opts.themeId, 'main');
  const bookId = sanitizeSegment(opts.bookId, 'guide');
  const packFormat = typeof opts.packFormat === 'number' && opts.packFormat > 0 ? opts.packFormat : 34;
  const description = opts.packDescription || `FlexiBook pack for ${namespace}`;

  const files: PackFile[] = [];

  // Acquire PNG bytes (custom takes precedence)
  let bookPng: ArrayBuffer;
  if (opts.customBookPng && opts.customBookPng.byteLength > 0) {
    bookPng = opts.customBookPng;
  } else {
    bookPng = await fetchArrayBuffer(opts.defaultBookUrl);
  }

  let widgetsPng: ArrayBuffer;
  if (opts.customWidgetsPng && opts.customWidgetsPng.byteLength > 0) {
    widgetsPng = opts.customWidgetsPng;
  } else {
    widgetsPng = await fetchArrayBuffer(opts.defaultWidgetsUrl);
  }

  // Theme wire: deep copy then rewrite texture refs to this namespace
  const themeWire = JSON.parse(JSON.stringify(themeToWire(opts.theme))) as Record<string, unknown>;
  themeWire.book_texture = `${namespace}:textures/gui/book.png`;
  themeWire.widgets_texture = `${namespace}:textures/gui/book_widgets.png`;

  // pack.mcmeta
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
        2
      )
    ),
  });

  // HOW_TO_USE.txt (bilingual)
  const howTo = `FlexiBook Resource Pack — HOW TO USE

English:
1. Copy this entire folder (or the .zip) into your Minecraft "resourcepacks" folder.
2. In-game: Options → Resource Packs → enable the pack.
3. In code (other mods or datapack-driven):
   import net.minecraft.resources.ResourceLocation;
   import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;

   // Create a book item that references the bundled content + theme
   var bookStack = FlexiBookAPI.createBookFromDefinition(
       ResourceLocation.fromNamespaceAndPath("${namespace}", "${bookId}")
   );
   // The exported theme is registered as: ${namespace}:${themeId}

4. The theme JSON lives at: assets/${namespace}/flexibook/themes/${themeId}.json
   The optional book lives at: assets/${namespace}/flexibook/books/${bookId}.json

中文简体:
1. 将整个文件夹（或 .zip）复制到 Minecraft 的 resourcepacks 目录。
2. 游戏内：选项 → 资源包 → 启用该包。
3. 代码中使用（其他模组）：
   FlexiBookAPI.createBookFromDefinition(
       ResourceLocation.fromNamespaceAndPath("${namespace}", "${bookId}")
   );
   主题 ID 为 ${namespace}:${themeId}

注意：资源包会覆盖同名主题/书籍的代码注册。建议使用独立 namespace 避免冲突。
`;
  files.push({
    path: 'HOW_TO_USE.txt',
    data: new TextEncoder().encode(howTo),
  });

  // Textures under assets/<ns>/textures/gui/
  const texBase = `assets/${namespace}/textures/gui/`;
  files.push({
    path: texBase + 'book.png',
    data: new Uint8Array(bookPng),
  });
  files.push({
    path: texBase + 'book_widgets.png',
    data: new Uint8Array(widgetsPng),
  });

  // Theme JSON
  const themeRel = `assets/${namespace}/flexibook/themes/${themeId}.json`;
  files.push({
    path: themeRel,
    data: new TextEncoder().encode(JSON.stringify(themeWire, null, 2)),
  });

  // Optional book content
  if (opts.includeBook && opts.content) {
    const bookWire = bookContentToWire(opts.content);
    // Always point the exported book at this pack's theme (override or set)
    bookWire.theme = `${namespace}:${themeId}`;
    const bookRel = `assets/${namespace}/flexibook/books/${bookId}.json`;
    files.push({
      path: bookRel,
      data: new TextEncoder().encode(JSON.stringify(bookWire, null, 2)),
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
