/**
 * TagParser self-check (mirrors Java TagParserTest essentials).
 * Run: npx tsx scripts/test-tagparser.mjs
 */
import { parseMarkup } from '../src/shared/TagParser.ts';

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed');
}

function assertEq(a, b, msg) {
  if (a !== b) throw new Error(msg || `expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);
}

// empty
assertEq(parseMarkup(null).length, 0);
assertEq(parseMarkup('').length, 0);
assertEq(parseMarkup('   \n\t  ').length, 0);

// headings / p / bullet
{
  const markup = `[h1]flexibook.book.demo.h1[/h1]
[h2]flexibook.book.demo.features[/h2]
[p]flexibook.book.demo.intro[/p]
[bullet]flexibook.book.demo.feature.adaptive[/bullet]
`;
  const els = parseMarkup(markup);
  assertEq(els.length, 4);
  assertEq(els[0].type, 'heading');
  assertEq(els[0].level, 1);
  assertEq(els[0].text.key, 'flexibook.book.demo.h1');
  assertEq(els[1].type, 'heading');
  assertEq(els[1].level, 2);
  assertEq(els[2].type, 'paragraph');
  assertEq(els[3].type, 'bullet');
}

// void tags
{
  const els = parseMarkup('[br][divider][img src="textures/gui/icon.png" width="32" height="32" /]');
  assertEq(els.length, 3);
  assertEq(els[0].type, 'br');
  assertEq(els[1].type, 'divider');
  assertEq(els[2].type, 'image');
  assertEq(els[2].width, 32);
  assertEq(els[2].height, 32);
  assertEq(els[2].src, 'flexibook:textures/gui/icon.png');
}

// inline styles
{
  const els = parseMarkup(
    '[p]hello [b]bold[/b] [i]italic[/i] [u]under[/u] [color=#FF0000]red[/color] world[/p]',
  );
  assertEq(els.length, 1);
  const spans = els[0].spans;
  assert(spans.length > 0, 'spans non-empty');
  assert(
    spans.some((s) => s.style?.bold && s.text.includes('bold')),
    'bold',
  );
  assert(
    spans.some((s) => s.style?.italic && s.text.includes('italic')),
    'italic',
  );
  assert(
    spans.some((s) => s.style?.underline && s.text.includes('under')),
    'underline',
  );
  assert(
    spans.some((s) => s.style?.color === 0xff0000 && s.text.includes('red')),
    'color',
  );
}

// links
{
  const cmd = parseMarkup('[p][link cmd="flexibook:say_hi"]click me[/link][/p]');
  assert(
    cmd[0].spans.some((s) => s.link?.type === 'command' && s.link.id === 'flexibook:say_hi'),
    'cmd link',
  );
  const url = parseMarkup('[p][link url="https://neoforged.net/"]site[/link][/p]');
  assert(
    url[0].spans.some((s) => s.link?.type === 'url' && s.link.url.startsWith('https://')),
    'url link',
  );
}

// escapes
{
  const els = parseMarkup('[p]use \\[b\\] for bold[/p]');
  const joined = els[0].spans.map((s) => s.text).join('');
  assert(joined.includes('[b]'), 'escaped brackets become literal: ' + joined);
  assert(!joined.includes('\\['), joined);
}

// div
{
  const els = parseMarkup('[div class="note"][p]flexibook.book.demo.intro[/p][/div]');
  assertEq(els.length, 1);
  assertEq(els[0].type, 'box');
  assertEq(els[0].className, 'note');
  assertEq(els[0].children.length, 1);
  assertEq(els[0].children[0].type, 'paragraph');
}

// loose text
{
  const els = parseMarkup('just some plain text');
  assertEq(els.length, 1);
  assertEq(els[0].type, 'paragraph');
}

// font tags
{
  const els = parseMarkup(
    '[p]plain [font font="minecraft:alt"]fancy[/font] again [font=minecraft:uniform]mono[/font][/p]',
  );
  const spans = els[0].spans;
  assert(
    spans.some((s) => s.style?.font === 'minecraft:alt' && s.text.includes('fancy')),
    'alt font',
  );
  assert(
    spans.some((s) => s.style?.font === 'minecraft:uniform' && s.text.includes('mono')),
    'uniform font',
  );
}

// heading font attr
{
  const els = parseMarkup('[h1 font="minecraft:alt"]demo.h1[/h1]');
  assertEq(els[0].type, 'heading');
  assertEq(els[0].font, 'minecraft:alt');
}

// unknown tags do not throw
{
  parseMarkup('[unknown]stuff[/unknown][p]ok[/p]');
  const els = parseMarkup('[p]still works[/p]');
  assertEq(els.length, 1);
  assertEq(els[0].type, 'paragraph');
}

console.log('test-tagparser: OK');
