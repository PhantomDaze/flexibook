/**
 * Pack import smoke test (no Electron UI).
 * Run: npx tsx scripts/test-pack-import.mjs
 */
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');

let failed = 0;
function assert(cond, msg) {
  if (!cond) {
    console.error('FAIL:', msg);
    failed++;
  } else {
    console.log('OK  ', msg);
  }
}

async function loadMods() {
  try {
    const { register } = await import('tsx/esm/api');
    register();
  } catch {
    /* already */
  }
  const exportUrl = pathToFileURL(join(root, 'src/shared/packExport.ts')).href;
  const importUrl = pathToFileURL(join(root, 'src/shared/packImport.ts')).href;
  const [packExport, packImport] = await Promise.all([import(exportUrl), import(importUrl)]);
  return { packExport, packImport };
}

async function main() {
  console.log('=== pack import smoke ===\n');
  const { packExport, packImport } = await loadMods();

  const bookPng = join(root, 'public/assets/textures/gui/book.png');
    assert(existsSync(bookPng), 'book.png exists');
    const bookBytes = readFileSync(bookPng);
  
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
  const fakeTtf = new Uint8Array([0, 1, 0, 0, 0, 10, 0, 80, 0, 3]).buffer;

  const files = await packExport.buildResourcePack({
    namespace: 'myguide',
    themeId: 'main',
    bookId: 'guide',
    includeBook: true,
    packFormat: 34,
    theme,
    content: {
      title: { key: 'myguide.book.guide.title', args: [] },
      elements: [
        { type: 'heading', level: 1, text: { key: 'myguide.h1', args: [] } },
        {
          type: 'paragraph',
          spans: [
            {
              text: 'hi',
              translate: false,
              style: { ...EMPTY_STYLE, font: 'other:title' },
            },
          ],
        },
      ],
      defaultFont: 'other:title',
    },
    customBookPng: bookBytes.buffer.slice(bookBytes.byteOffset, bookBytes.byteOffset + bookBytes.byteLength),
    defaultBookUrl: pathToFileURL(bookPng).href,
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

  // 1) parse file list
  const parsed = packImport.parsePackFiles(files);
  assert(parsed.namespace === 'myguide', 'ns myguide');
  assert(parsed.themeId === 'main', 'themeId main');
  assert(parsed.bookId === 'guide', 'bookId guide');
  assert(!!parsed.theme, 'theme present');
  assert(parsed.theme?.pageContentWidth === 160, 'theme metric');
  assert(!!parsed.content, 'content present');
  assert(parsed.content?.title?.key === 'myguide.book.guide.title', 'content title');
  assert(parsed.langTables.en_us?.['myguide.hello'] === 'Hello', 'en lang');
  assert(parsed.langTables.zh_cn?.['myguide.book.guide.title'] === '指南', 'zh lang');
  assert(parsed.fonts.length === 1, 'one font');
  assert(parsed.fonts[0].id === 'myguide:title', 'font rewritten id');
  assert(parsed.fonts[0].bytes.byteLength === fakeTtf.byteLength, 'font bytes');
  assert(!!parsed.textures.book && parsed.textures.book.byteLength === bookBytes.length, 'book tex');
  assert(parsed.packFormat === 34, 'pack_format');

  // 2) zip roundtrip import
  const zip = packExport.packFilesToZip(files);
  const fromZip = packImport.importPackFromZip(zip);
  assert(fromZip.namespace === 'myguide', 'zip ns');
  assert(fromZip.content?.elements?.length >= 1, 'zip content elements');
  assert(fromZip.fonts[0]?.id === 'myguide:title', 'zip font id');

  // 3) zip-of-folder prefix strip
  const nested = files.map((f) => ({
    path: 'myguide_pack/' + f.path,
    data: f.data,
  }));
  const nestedParsed = packImport.parsePackFiles(nested);
  assert(nestedParsed.namespace === 'myguide', 'nested root strip ns');
  assert(!!nestedParsed.theme, 'nested theme');

  // 4) lang-only partial
  const langOnly = await packExport.buildResourcePack({
    namespace: 'myguide',
    packFormat: 34,
    parts: { meta: true, theme: false, textures: false, content: false, lang: true, fonts: false },
    langTables: { en_us: { a: '1' } },
  });
  const langParsed = packImport.parsePackFiles(langOnly);
  assert(langParsed.langTables.en_us?.a === '1', 'lang-only import');
  assert(!langParsed.theme, 'lang-only no theme');

  // 5) fieldnotes fixture if present
  const fixture = join(root, '..', 'run/resourcepacks/fieldnotes_pack');
  if (existsSync(join(fixture, 'pack.mcmeta'))) {
    const { readdirSync, statSync } = await import('node:fs');
    function walk(dir, base = dir, acc = []) {
      for (const name of readdirSync(dir)) {
        const full = join(dir, name);
        const st = statSync(full);
        if (st.isDirectory()) walk(full, base, acc);
        else {
          const rel = full.slice(base.length + 1).split(/[/\\]/).join('/');
          acc.push({ path: rel, data: new Uint8Array(readFileSync(full)) });
        }
      }
      return acc;
    }
    const fnFiles = walk(fixture);
    const fn = packImport.parsePackFiles(fnFiles);
    assert(fn.namespace === 'fieldnotes', 'fieldnotes ns');
    assert(fn.bookId === 'journal', 'fieldnotes book');
    assert(!!fn.theme, 'fieldnotes theme');
    assert(!!fn.content, 'fieldnotes content');
    assert(Object.keys(fn.langTables).length >= 1, 'fieldnotes lang');
  } else {
    console.log('SKIP fieldnotes fixture (not on disk)');
  }

  console.log('\n' + (failed ? `FAILED ${failed}` : 'ALL PASSED'));
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
