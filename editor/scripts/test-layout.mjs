/**
 * Deterministic layout tests with a fake measurer (aligns with Java BookLayoutEngineTest).
 * Run: npx tsx scripts/test-layout.mjs
 */
import { clearLayoutCache, layout } from '../src/shared/layout.ts';
import { BookTheme, StyleFlags, InlineSpan, AdaptiveBookContent } from '../src/shared/types.ts';
import { FLEXIBOOK_DEFAULT_FONT } from '../src/shared/UnihexFont.ts';

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed');
}
function assertEq(a, b, msg) {
  if (a !== b) throw new Error(msg || `expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);
}

const THEME = { ...BookTheme.DEFAULT };
const fakeMeas = {
  width(text) {
    return text == null ? 0 : text.length * 6;
  },
};
const tx = { get: (k) => k };

clearLayoutCache();

// deterministic pagination with fixed widths
{
  const content = AdaptiveBookContent.ofElements(
    { key: 'title', args: [] },
    [
      {
        type: 'paragraph',
        spans: [
          InlineSpan.literal('AAAAAAAAAA'),
          InlineSpan.literal(' BBBBBBBBBB'),
        ],
      },
      { type: 'divider' },
      {
        type: 'paragraph',
        spans: [InlineSpan.literal('CCCCCCCCCC')],
      },
    ],
  );
  const narrow = { ...THEME, pageContentWidth: 48, revision: (THEME.revision || 1) + 1 };
  const pages = layout({
    content,
    measurer: fakeMeas,
    translator: tx,
    theme: narrow,
    languageCode: 'en_us',
    guiScaleRef: 2,
    searchQuery: '',
  });
  assert(pages.length >= 1, 'at least one page');
  const textLines = pages[0].elements.filter((e) => e.kind === 'text').length;
  assert(textLines >= 2, `expect multiple lines due to narrow width, got ${textLines}`);
  const hasDivider = pages.some((p) => p.elements.some((e) => e.kind === 'divider'));
  assert(hasDivider, 'divider should be rendered');
}

// empty content
{
  clearLayoutCache();
  const pages = layout({
    content: AdaptiveBookContent.EMPTY,
    measurer: { width: () => 0 },
    translator: tx,
    theme: THEME,
    languageCode: 'en_us',
    guiScaleRef: 2,
  });
  assertEq(pages.length, 1);
  // empty may have empty.body fallback or zero elements depending on engine path
  assert(Array.isArray(pages[0].elements), 'elements array');
}

// default font resolves to flexibook:default
{
  clearLayoutCache();
  const content = AdaptiveBookContent.ofElements(
    { key: 'title', args: [] },
    [{ type: 'paragraph', spans: [InlineSpan.literal('Hello')] }],
  );
  assert(!content.defaultFont, 'no defaultFont');
  const pages = layout({
    content,
    measurer: fakeMeas,
    translator: tx,
    theme: THEME,
    languageCode: 'en_us',
    guiScaleRef: 2,
  });
  assert(pages.length >= 1);
  const lines = pages[0].elements.filter((e) => e.kind === 'text');
  assert(lines.length >= 1, 'should have text lines');
  for (const tl of lines) {
    assertEq(
      tl.style?.font,
      FLEXIBOOK_DEFAULT_FONT,
      'body text without explicit defaultFont must use flexibook:default',
    );
  }
}

// explicit book default font applied
{
  clearLayoutCache();
  const custom = 'mymod:fancy';
  const content = AdaptiveBookContent.ofElements(
    { key: 'title', args: [] },
    [{ type: 'paragraph', spans: [InlineSpan.literal('Hello')] }],
    custom,
  );
  const pages = layout({
    content,
    measurer: fakeMeas,
    translator: tx,
    theme: THEME,
    languageCode: 'en_us',
    guiScaleRef: 2,
  });
  const lines = pages[0].elements.filter((e) => e.kind === 'text');
  assert(lines.length >= 1);
  for (const tl of lines) {
    assertEq(tl.style?.font, custom, 'explicit book font must apply');
  }
}

// heading + bullet get resolved font
{
  clearLayoutCache();
  const content = AdaptiveBookContent.ofElements(
    { key: 'title', args: [] },
    [
      { type: 'heading', level: 1, text: { key: 'H', args: [] } },
      { type: 'bullet', spans: [InlineSpan.literal('item')] },
    ],
  );
  const pages = layout({
    content,
    measurer: fakeMeas,
    translator: tx,
    theme: THEME,
    languageCode: 'en_us',
    guiScaleRef: 2,
  });
  const lines = pages.flatMap((p) => p.elements).filter((e) => e.kind === 'text');
  assert(lines.length >= 2, 'heading + bullet marker/text');
  for (const tl of lines) {
    assertEq(tl.style?.font, FLEXIBOOK_DEFAULT_FONT);
  }
}

// multi-span preserves styles on rendered lines
{
  clearLayoutCache();
  const content = AdaptiveBookContent.ofElements(
    { key: 'title', args: [] },
    [
      {
        type: 'paragraph',
        spans: [
          InlineSpan.literal('plain '),
          InlineSpan.literal('bold', StyleFlags.withBold(StyleFlags.EMPTY, true)),
        ],
      },
    ],
  );
  const pages = layout({
    content,
    measurer: fakeMeas,
    translator: tx,
    theme: { ...THEME, pageContentWidth: 200 },
    languageCode: 'en_us',
    guiScaleRef: 2,
  });
  const lines = pages.flatMap((p) => p.elements).filter((e) => e.kind === 'text');
  assert(
    lines.some((l) => l.style?.bold && l.text.includes('bold')),
    'bold span should appear',
  );
  assert(
    lines.some((l) => !l.style?.bold && l.text.includes('plain')),
    'plain span should appear',
  );
}

console.log('test-layout: OK');
