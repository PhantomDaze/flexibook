/**
 * Export real default theme + demo_guide through packExport pipeline.
 * Run: npx tsx scripts/test-pack-demo-export.mjs
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
try {
  const { register } = await import('tsx/esm/api');
  register();
} catch { /* runner is tsx */ }

const { buildResourcePack, packFilesToZip } = await import(pathToFileURL(join(root, 'src/shared/packExport.ts')).href);
const { parseThemeJson, parseBookContentJson } = await import(pathToFileURL(join(root, 'src/shared/modJson.ts')).href);

const theme = parseThemeJson(JSON.parse(readFileSync(join(root, 'assets/themes/default.json'), 'utf8')));
const content = parseBookContentJson(JSON.parse(readFileSync(join(root, 'assets/books/demo_guide.json'), 'utf8')));
const bookPng = join(root, 'public/assets/textures/gui/book.png');
const widgetsPng = join(root, 'public/assets/textures/gui/book_widgets.png');
const bookBytes = readFileSync(bookPng);
const widgetsBytes = readFileSync(widgetsPng);
const ab = (buf) => buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);

const files = await buildResourcePack({
  namespace: 'demopack',
  themeId: 'default',
  bookId: 'demo_guide',
  includeBook: true,
  packFormat: 34,
  theme,
  content,
  customBookPng: ab(bookBytes),
  customWidgetsPng: ab(widgetsBytes),
  defaultBookUrl: pathToFileURL(bookPng).href,
  defaultWidgetsUrl: pathToFileURL(widgetsPng).href,
});

const book = JSON.parse(new TextDecoder().decode(files.find(f => f.path.endsWith('books/demo_guide.json')).data));
const themeOut = JSON.parse(new TextDecoder().decode(files.find(f => f.path.includes('themes/default.json')).data));
console.log('theme textures:', themeOut.book_texture, themeOut.widgets_texture);
console.log('book theme/font:', book.theme, book.font);
console.log('elements:', book.elements?.length, 'types:', book.elements?.map(e => e.type).join(','));
console.log('zip bytes', packFilesToZip(files).byteLength);

const fixture = join(root, '..', 'src/test/resources/pack_export_fixture');
mkdirSync(fixture, { recursive: true });
writeFileSync(join(fixture, 'demo_guide_export.json'), JSON.stringify(book, null, 2));
writeFileSync(join(fixture, 'default_theme_export.json'), JSON.stringify(themeOut, null, 2));
console.log('OK demo export →', fixture);
