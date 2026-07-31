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
    assert(existsSync(bookPng), 'default book.png exists');
  
  const bookBytes = readFileSync(bookPng);
  
  // minimal theme shape matching BookTheme
  const theme = {
    bookTexture: 'flexibook:textures/gui/book.png',
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

  // tiny fake ttf bytes (not a real font — export only checks packaging)
  const fakeTtf = new Uint8Array([0, 1, 0, 0, 0, 10, 0, 80, 0, 3]).buffer;

  const files = await packMod.buildResourcePack({
    namespace: 'myguide',
    themeId: 'main',
    bookId: 'guide',
    includeBook: true,
    packFormat: 34,
    theme,
    content: {
      ...content,
      defaultFont: 'other:title',
      elements: [
        ...(content.elements || []),
        {
          type: 'paragraph',
          spans: [{ text: 'hi', translate: false, style: { bold: false, italic: false, underline: false, font: 'other:title' } }],
        },
      ],
    },
    customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
    defaultBookUrl: pathToFileURL(bookPng).href,
    customItemPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
    langTables: {
      en_us: { 'myguide.book.guide.title': 'Guide', 'myguide.hello': 'Hello' },
      zh_cn: { 'myguide.book.guide.title': '指南' },
    },
    customFonts: [
      {
        id: 'other:title',
        fileName: 'Title.ttf',
        ext: 'ttf',
        bytes: fakeTtf,
        size: 11,
        oversample: 2,
      },
    ],
    rewriteFontsToPackNs: true,
  });

  const paths = files.map((f) => f.path).sort();
  const expected = [
    'HOW_TO_USE.txt',
    'assets/flexibook/textures/item/flexi_book.png',
    'assets/myguide/flexibook/books/guide.json',
    'assets/myguide/flexibook/contents/guide.json',
    'assets/myguide/flexibook/themes/main.json',
    'assets/myguide/font/title.json',
    'assets/myguide/font/title.ttf',
    'assets/myguide/lang/en_us.json',
    'assets/myguide/lang/zh_cn.json',
    'assets/myguide/textures/gui/book.png',
    'assets/myguide/textures/item/flexi_book.png',
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
  assert(themeJson.book_tex_width === 192, 'theme metrics preserved');
  assert(themeJson.image_fit === 'stretch', 'image_fit preserved');

  // book index (not full body)
  const bookJson = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/flexibook/books/guide.json'].data));
  assert(bookJson.content === 'myguide:guide', 'book index content id');
  assert(bookJson.theme === 'myguide:main', 'book index theme');
  assert(bookJson.font === 'myguide:title', 'book index font rewritten');
  assert(!bookJson.elements, 'book index has no elements');
  assert(!bookJson.title, 'book index has no title');

  // content body
  const contentJson = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/flexibook/contents/guide.json'].data));
  assert(contentJson.title?.key === 'myguide.book.guide.title', 'content title');
  assert(Array.isArray(contentJson.elements), 'content elements array');
  assert(!contentJson.theme, 'content body has no theme field');
  const spanFonts = JSON.stringify(contentJson.elements);
  assert(spanFonts.includes('myguide:title'), 'span font rewritten into pack ns');
  assert(!spanFonts.includes('other:title'), 'old font id not left in content');

  // lang tables
  const enLang = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/lang/en_us.json'].data));
  assert(enLang['myguide.hello'] === 'Hello', 'en lang table exported');
  const zhLang = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/lang/zh_cn.json'].data));
  assert(zhLang['myguide.book.guide.title'] === '指南', 'zh lang table exported');

  // font provider
  const fontJson = JSON.parse(new TextDecoder().decode(byPath['assets/myguide/font/title.json'].data));
  assert(fontJson.providers?.[0]?.type === 'ttf', 'font provider type ttf');
  assert(fontJson.providers?.[0]?.file === 'myguide:title', 'font provider file ref');
  assert(byPath['assets/myguide/font/title.ttf'].data.byteLength === fakeTtf.byteLength, 'ttf bytes packed');

  // png sizes
  assert(byPath['assets/myguide/textures/gui/book.png'].data.byteLength === bookBytes.length, 'book png bytes');

  // HOW_TO_USE contains ns ids
  const how = new TextDecoder().decode(byPath['HOW_TO_USE.txt'].data);
  assert(how.includes('myguide') && how.includes('createBookFromDefinition'), 'HOW_TO_USE content');
  assert(how.includes('lang') && how.includes('font'), 'HOW_TO_USE mentions lang/font');

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
  writeFileSync(join(fixtureDir, 'content.json'), byPath['assets/myguide/flexibook/contents/guide.json'].data);
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
    defaultBookUrl: pathToFileURL(bookPng).href,
  });
  assert(!filesNoBook.some((f) => f.path.includes('/books/')), 'includeBook false omits books');
  assert(!filesNoBook.some((f) => f.path.includes('/contents/')), 'includeBook false omits contents');

  // 7b) partial theme-only pack
  const themeOnly = await packMod.buildResourcePack({
    namespace: 'myguide',
    themeId: 'main',
    packFormat: 34,
    parts: { meta: true, theme: true, textures: false, content: false, lang: false, fonts: false },
    theme,
  });
  const themeOnlyPaths = themeOnly.map((f) => f.path).sort();
  assert(themeOnlyPaths.includes('pack.mcmeta'), 'theme-only has mcmeta');
  assert(themeOnlyPaths.some((p) => p.includes('/themes/')), 'theme-only has theme');
  assert(!themeOnlyPaths.some((p) => p.includes('/textures/')), 'theme-only no textures');
  assert(!themeOnlyPaths.some((p) => p.includes('/contents/')), 'theme-only no contents');
  assert(!themeOnlyPaths.some((p) => p.includes('/lang/')), 'theme-only no lang');

  // 7c) partial lang-only
  const langOnly = await packMod.buildResourcePack({
    namespace: 'myguide',
    packFormat: 34,
    parts: { meta: true, theme: false, textures: false, content: false, lang: true, fonts: false },
    langTables: { en_us: { a: '1' } },
  });
  assert(langOnly.some((f) => f.path.endsWith('lang/en_us.json')), 'lang-only has lang');
  assert(!langOnly.some((f) => f.path.includes('/themes/')), 'lang-only no theme');

  // 7d) partial content-only
  const contentOnly = await packMod.buildResourcePack({
    namespace: 'myguide',
    themeId: 'main',
    bookId: 'guide',
    packFormat: 34,
    parts: { meta: true, theme: false, textures: false, content: true, lang: false, fonts: false },
    content,
  });
  assert(contentOnly.some((f) => f.path.includes('/contents/')), 'content-only has contents');
  assert(contentOnly.some((f) => f.path.includes('/books/')), 'content-only has books index');
  assert(!contentOnly.some((f) => f.path.includes('/themes/')), 'content-only no theme');
  assert(!contentOnly.some((f) => f.path.includes('/textures/')), 'content-only no textures');

  // 7e) partial fonts-only
  const fontsOnly = await packMod.buildResourcePack({
    namespace: 'myguide',
    packFormat: 34,
    parts: { meta: true, theme: false, textures: false, content: false, lang: false, fonts: true },
    customFonts: [
      {
        id: 'other:title',
        fileName: 'Title.ttf',
        ext: 'ttf',
        bytes: fakeTtf,
        size: 11,
        oversample: 2,
      },
    ],
    rewriteFontsToPackNs: true,
  });
  assert(fontsOnly.some((f) => f.path.endsWith('font/title.json')), 'fonts-only has font json');
  assert(fontsOnly.some((f) => f.path.endsWith('font/title.ttf')), 'fonts-only has ttf');
  assert(!fontsOnly.some((f) => f.path.includes('/lang/')), 'fonts-only no lang');

  // 7f) partial textures-only
  const texOnly = await packMod.buildResourcePack({
    namespace: 'myguide',
    packFormat: 34,
    parts: { meta: true, theme: false, textures: true, content: false, lang: false, fonts: false },
    customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
    defaultBookUrl: pathToFileURL(bookPng).href,
    customItemPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
  });
  assert(texOnly.some((f) => f.path.endsWith('textures/gui/book.png')), 'tex-only has book png');
  assert(texOnly.some((f) => f.path === 'assets/myguide/textures/item/flexi_book.png'), 'tex-only has ns item');
  assert(texOnly.some((f) => f.path === 'assets/flexibook/textures/item/flexi_book.png'), 'tex-only has flexibook item override');
  assert(!texOnly.some((f) => f.path.includes('/themes/')), 'tex-only no theme');

  // 7g) textures without custom item → only book.png (no forced default item unless URL set)
  const texBookOnly = await packMod.buildResourcePack({
    namespace: 'myguide',
    packFormat: 34,
    parts: { meta: true, theme: false, textures: true, content: false, lang: false, fonts: false },
    customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
  });
  assert(texBookOnly.some((f) => f.path.endsWith('textures/gui/book.png')), 'book-only has book');
  assert(!texBookOnly.some((f) => f.path.includes('/item/')), 'book-only no item when not provided');

  // 8) bad namespace throws
  let threw = false;
  try {
    await packMod.buildResourcePack({
      namespace: 'Bad NS',
      includeBook: false,
      theme,
      customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
      defaultBookUrl: pathToFileURL(bookPng).href,
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
