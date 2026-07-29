/**
 * FlexiBook editor pack export smoke test (no Electron UI).
 * Run: node scripts/test-pack-export.mjs
 */
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { join, dirname, resolve, normalize, sep, isAbsolute } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { unzipSync } from 'fflate';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');
const require = createRequire(import.meta.url);

let failed = 0;
function assert(cond, msg) {
  if (!cond) {
    console.error('FAIL:', msg);
    failed++;
  } else {
    console.log('OK  ', msg);
  }
}

// --- replicate validateNamespace / sanitize from packExport (source of truth via dynamic import) ---
// tsx can import .ts; plain node cannot. Use tsx register if available.
async function loadPackExport() {
  try {
    // Prefer tsx dynamic import of TS source
    const { register } = await import('tsx/esm/api');
    register();
  } catch {
    /* tsx may already be the runner */
  }
  const modUrl = pathToFileURL(join(root, 'src/shared/packExport.ts')).href;
  return import(modUrl);
}

// Fallback pure JS mirror if TS import fails (kept in sync with packExport.ts)
function validateNamespace(ns) {
  if (!ns || typeof ns !== 'string' || ns.length === 0) return 'Namespace is required';
  if (!/^[a-z0-9_.-]+$/.test(ns)) return 'Namespace must match [a-z0-9_.-]+';
  if (ns.startsWith('.') || ns.endsWith('.') || ns.startsWith('-') || ns.startsWith('_')) {
    return 'Namespace cannot start/end with . - _';
  }
  return null;
}

// --- path safety (mirrors main.ts fs:writePack) ---
function safeJoin(rootDir, rel) {
  if (isAbsolute(rel) || rel.includes('..')) throw new Error(`Unsafe path rejected: ${rel}`);
  const fullPath = join(rootDir, rel);
  const normFull = normalize(fullPath);
  const normRoot = normalize(rootDir);
  if (normFull !== normRoot && !normFull.startsWith(normRoot + sep)) {
    throw new Error(`Path escapes pack root: ${rel}`);
  }
  return fullPath;
}

async function main() {
  console.log('=== pack export smoke ===\n');

  // 1) namespace validation
  assert(validateNamespace('myguide') === null, 'ns myguide ok');
  assert(validateNamespace('') !== null, 'empty ns rejected');
  assert(validateNamespace('MyGuide') !== null, 'uppercase rejected');
  assert(validateNamespace('../x') !== null, 'path traversal ns rejected');
  assert(validateNamespace('a/b') !== null, 'slash in ns rejected');

  // 2) path safety
  const tmpRoot = join('/tmp', 'flexibook_pack_safe_' + process.pid);
  mkdirSync(tmpRoot, { recursive: true });
  try {
    assert(safeJoin(tmpRoot, 'pack.mcmeta') === join(tmpRoot, 'pack.mcmeta'), 'safe relative ok');
    let threw = false;
    try { safeJoin(tmpRoot, '../etc/passwd'); } catch { threw = true; }
    assert(threw, 'rejects ..');
    threw = false;
    try { safeJoin(tmpRoot, '/etc/passwd'); } catch { threw = true; }
    assert(threw, 'rejects absolute');
  } finally {
    rmSync(tmpRoot, { recursive: true, force: true });
  }

  // 3) load packExport via tsx
  let packMod;
  try {
    packMod = await loadPackExport();
    assert(typeof packMod.buildResourcePack === 'function', 'buildResourcePack exported');
    assert(typeof packMod.packFilesToZip === 'function', 'packFilesToZip exported');
    assert(typeof packMod.validateNamespace === 'function', 'validateNamespace exported');
    assert(packMod.validateNamespace('myguide') === null, 'TS validateNamespace myguide');
    assert(packMod.validateNamespace('BAD') !== null, 'TS validateNamespace BAD');
  } catch (e) {
    console.error('TS import failed:', e);
    failed++;
    process.exit(failed ? 1 : 0);
  }

  // 4) build pack with real default PNGs (file:// URLs)
  const bookPng = join(root, 'public/assets/textures/gui/book.png');
  const widgetsPng = join(root, 'public/assets/textures/gui/book_widgets.png');
  assert(existsSync(bookPng), 'default book.png exists');
  assert(existsSync(widgetsPng), 'default widgets.png exists');

  const bookBytes = readFileSync(bookPng);
  const widgetsBytes = readFileSync(widgetsPng);

  // minimal theme shape matching BookTheme
  const theme = {
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

  const EMPTY_STYLE = { bold: false, italic: false, underline: false };
  const content = {
    title: { key: 'myguide.book.guide.title', args: [] },
    elements: [
      { type: 'heading', level: 1, text: { key: 'myguide.h1', args: [] } },
      {
        type: 'paragraph',
        spans: [{ text: 'hello', translate: false, style: EMPTY_STYLE }],
      },
      { type: 'divider' },
      {
        type: 'image',
        src: 'flexibook:textures/gui/icon.png',
        width: 32,
        height: 32,
      },
    ],
    defaultFont: 'flexibook:default',
    themeId: 'flexibook:default',
  };

  const files = await packMod.buildResourcePack({
    namespace: 'myguide',
    themeId: 'main',
    bookId: 'guide',
    includeBook: true,
    packFormat: 34,
    theme,
    content,
    customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
    customWidgetsPng: widgetsBytes.buffer.slice(widgetsBytes.byteOffset, widgetsBytes.byteOffset + widgetsBytes.byteLength),
    defaultBookUrl: pathToFileURL(bookPng).href,
    defaultWidgetsUrl: pathToFileURL(widgetsPng).href,
  });

  const paths = files.map((f) => f.path).sort();
  const expected = [
    'HOW_TO_USE.txt',
    'assets/myguide/flexibook/books/guide.json',
    'assets/myguide/flexibook/themes/main.json',
    'assets/myguide/textures/gui/book.png',
    'assets/myguide/textures/gui/book_widgets.png',
    'pack.mcmeta',
  ].sort();
  assert(JSON.stringify(paths) === JSON.stringify(expected), 'file path set matches expected\n  got: ' + paths.join(', '));

  const byPath = Object.fromEntries(files.map((f) => [f.path, f]));

  // pack.mcmeta
  const mcmeta = JSON.parse(new TextDecoder().decode(byPath['pack.mcmeta'].data));
  assert(mcmeta.pack?.pack_format === 34, 'pack_format 34');
  assert(typeof mcmeta.pack?.description === 'string', 'description string');

  // theme rewrite
  const themeJson = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/flexibook/themes/main.json'].data));
  assert(themeJson.book_texture === 'myguide:textures/gui/book.png', 'book_texture rewritten');
  assert(themeJson.widgets_texture === 'myguide:textures/gui/book_widgets.png', 'widgets_texture rewritten');
  assert(themeJson.book_tex_width === 192, 'theme metrics preserved');
  assert(themeJson.image_fit === 'stretch', 'image_fit preserved');

  // book rewrite
  const bookJson = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/flexibook/books/guide.json'].data));
  assert(bookJson.theme === 'myguide:main', 'book theme forced to pack theme');
  assert(bookJson.title?.key === 'myguide.book.guide.title', 'book title');
  assert(Array.isArray(bookJson.elements), 'book elements array');

  // png sizes
  assert(byPath['assets/myguide/textures/gui/book.png'].data.byteLength === bookBytes.length, 'book png bytes');
  assert(byPath['assets/myguide/textures/gui/book_widgets.png'].data.byteLength === widgetsBytes.length, 'widgets png bytes');

  // HOW_TO_USE contains ns ids
  const how = new TextDecoder().decode(byPath['HOW_TO_USE.txt'].data);
  assert(how.includes('myguide') && how.includes('createBookFromDefinition'), 'HOW_TO_USE content');

  // 5) zip roundtrip
  const zipU8 = packMod.packFilesToZip(files);
  assert(zipU8.byteLength > 100, 'zip non-trivial size: ' + zipU8.byteLength);
  const unzipped = unzipSync(zipU8);
  const zipPaths = Object.keys(unzipped).sort();
  assert(JSON.stringify(zipPaths) === JSON.stringify(expected), 'zip paths match');

  // 6) write to disk for Java codec test
  const outDir = join('/tmp', 'flexibook_export_acceptance');
  rmSync(outDir, { recursive: true, force: true });
  mkdirSync(outDir, { recursive: true });
  for (const f of files) {
    const full = safeJoin(outDir, f.path);
    mkdirSync(dirname(full), { recursive: true });
    writeFileSync(full, f.data);
  }
  // also dump theme/book only for Java test path under repo
  const fixtureDir = join(root, '..', 'src/test/resources/pack_export_fixture');
  mkdirSync(fixtureDir, { recursive: true });
  writeFileSync(join(fixtureDir, 'theme.json'), byPath['assets/myguide/flexibook/themes/main.json'].data);
  writeFileSync(join(fixtureDir, 'book.json'), byPath['assets/myguide/flexibook/books/guide.json'].data);
  writeFileSync(join(fixtureDir, 'pack.mcmeta'), byPath['pack.mcmeta'].data);
  console.log('\nWrote pack to', outDir);
  console.log('Wrote codec fixtures to', fixtureDir);

  // 7) includeBook false omits book
  const filesNoBook = await packMod.buildResourcePack({
    namespace: 'myguide',
    themeId: 'main',
    includeBook: false,
    packFormat: 34,
    theme,
    customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
    customWidgetsPng: widgetsBytes.buffer.slice(widgetsBytes.byteOffset, widgetsBytes.byteOffset + widgetsBytes.byteLength),
    defaultBookUrl: pathToFileURL(bookPng).href,
    defaultWidgetsUrl: pathToFileURL(widgetsPng).href,
  });
  assert(!filesNoBook.some((f) => f.path.includes('/books/')), 'includeBook false omits books');

  // 8) bad namespace throws
  let threw = false;
  try {
    await packMod.buildResourcePack({
      namespace: 'Bad NS',
      includeBook: false,
      theme,
      customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
      customWidgetsPng: widgetsBytes.buffer.slice(widgetsBytes.byteOffset, widgetsBytes.byteOffset + widgetsBytes.byteLength),
      defaultBookUrl: pathToFileURL(bookPng).href,
      defaultWidgetsUrl: pathToFileURL(widgetsPng).href,
    });
  } catch {
    threw = true;
  }
  assert(threw, 'invalid namespace throws');

  console.log('\n' + (failed ? `FAILED ${failed}` : 'ALL PASSED'));
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
