/**
 * One-shot: export fieldnotes sample pack into run/resourcepacks/
 * Run: npx tsx scripts/export-fieldnotes-pack.mjs
 */
import { writeFileSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
try {
  const { register } = await import('tsx/esm/api');
  register();
} catch {
  /* runner is tsx */
}

const { buildResourcePack } = await import(
  pathToFileURL(join(root, 'src/shared/packExport.ts')).href
);
const { parseThemeJson } = await import(
  pathToFileURL(join(root, 'src/shared/modJson.ts')).href
);

const theme = parseThemeJson(
  JSON.parse(readFileSync(join(root, 'assets/themes/default.json'), 'utf8')),
);

const content = {
  title: { key: 'fieldnotes.journal.title', args: [] },
  defaultFont: 'flexibook:default',
  elements: [
    { type: 'heading', level: 1, text: { key: 'fieldnotes.journal.h1', args: [] } },
    {
      type: 'paragraph',
      spans: [
        {
          text: 'fieldnotes.journal.intro',
          translate: true,
          style: { bold: false, italic: false, underline: false },
        },
      ],
    },
    { type: 'divider' },
    { type: 'heading', level: 2, text: { key: 'fieldnotes.journal.notes_h', args: [] } },
    {
      type: 'bullet',
      spans: [
        {
          text: 'fieldnotes.journal.note1',
          translate: true,
          style: { bold: false, italic: false, underline: false },
        },
      ],
    },
    {
      type: 'bullet',
      spans: [
        {
          text: 'fieldnotes.journal.note2',
          translate: true,
          style: { bold: false, italic: false, underline: false },
        },
      ],
    },
    {
      type: 'paragraph',
      spans: [
        {
          text: 'fieldnotes.journal.bold',
          translate: true,
          style: { bold: true, italic: false, underline: false },
        },
        { text: ' ', translate: false, style: { bold: false, italic: false, underline: false } },
        {
          text: 'fieldnotes.journal.plain',
          translate: true,
          style: { bold: false, italic: false, underline: false },
        },
      ],
    },
    {
      type: 'image',
      src: 'flexibook:textures/gui/icon.png',
      width: 48,
      height: 48,
      tooltip: 'fieldnotes.journal.icon_tip',
    },
    {
      type: 'paragraph',
      spans: [
        {
          text: 'fieldnotes.journal.outro',
          translate: true,
          style: { bold: false, italic: false, underline: false },
        },
      ],
    },
  ],
};

const bookPng = join(root, 'public/assets/textures/gui/book.png');
const bookBytes = readFileSync(bookPng);
const ab = (b) => b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);

const files = await buildResourcePack({
  namespace: 'fieldnotes',
  themeId: 'parchment',
  bookId: 'journal',
  includeBook: true,
  packFormat: 34,
  packDescription: 'Field Notes journal — FlexiBook sample pack',
  theme,
  content,
  customBookPng: ab(bookBytes),
  defaultBookUrl: pathToFileURL(bookPng).href,
  langTables: {
    en_us: {
      'fieldnotes.journal.title': 'Field Notes',
      'fieldnotes.journal.h1': 'Traveler Journal',
      'fieldnotes.journal.intro':
        'A resource-pack-only book exported from the FlexiBook editor pipeline.',
      'fieldnotes.journal.notes_h': 'Notes',
      'fieldnotes.journal.note1': 'books/ is an index (content + theme)',
      'fieldnotes.journal.note2': 'contents/ holds translation keys; lang/ holds text',
      'fieldnotes.journal.bold': 'Bold run',
      'fieldnotes.journal.plain': 'and plain text share a paragraph.',
      'fieldnotes.journal.icon_tip': 'Pack icon',
      'fieldnotes.journal.outro':
        'Enable this pack, F3+T, then: /flexibook give fieldnotes:journal',
    },
    zh_cn: {
      'fieldnotes.journal.title': '野外笔记',
      'fieldnotes.journal.h1': '旅人日记',
      'fieldnotes.journal.intro': '这是从 FlexiBook 编辑器导出管道生成的纯资源包书。',
      'fieldnotes.journal.notes_h': '要点',
      'fieldnotes.journal.note1': 'books/ 仅为索引（content + theme）',
      'fieldnotes.journal.note2': 'contents/ 存翻译键；lang/ 存具体文案',
      'fieldnotes.journal.bold': '粗体片段',
      'fieldnotes.journal.plain': '与普通文字同在一段。',
      'fieldnotes.journal.icon_tip': '图标',
      'fieldnotes.journal.outro':
        '启用资源包后 F3+T，再执行：/flexibook give fieldnotes:journal',
    },
  },
});

const out = join(root, '..', 'run', 'resourcepacks', 'fieldnotes_pack');
rmSync(out, { recursive: true, force: true });
for (const f of files) {
  const full = join(out, f.path);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, f.data);
}
console.log('Wrote', files.length, 'files to', out);
console.log(files.map((f) => f.path).sort().join('\n'));
