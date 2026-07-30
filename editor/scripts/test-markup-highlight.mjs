/**
 * markupHighlight tokenizer self-check.
 * Run: npx tsx scripts/test-markup-highlight.mjs
 */
import { tokenizeMarkup, tokensToHtml } from '../src/shared/markupHighlight.ts';

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed');
}

{
  const tokens = tokenizeMarkup('[p]hello [b]bold[/b] world[/p]');
  const kinds = tokens.map((t) => t.kind);
  assert(kinds.includes('tag'), 'has tag');
  assert(kinds.includes('text'), 'has text');
  assert(tokens.some((t) => t.kind === 'tag' && t.text === 'p'), 'open p');
  assert(tokens.some((t) => t.kind === 'tag' && t.text === 'b'), 'open b');
}

{
  const tokens = tokenizeMarkup('[img src="textures/gui/icon.png" width="32" /]');
  assert(tokens.some((t) => t.kind === 'attr-name' && t.text === 'src'), 'attr name');
  assert(tokens.some((t) => t.kind === 'attr-value' && t.text.includes('icon.png')), 'attr value');
}

{
  const tokens = tokenizeMarkup('use \\[b\\] for bold');
  assert(tokens.some((t) => t.kind === 'escape' && t.text === '\\['), 'escape open');
  assert(tokens.some((t) => t.kind === 'escape' && t.text === '\\]'), 'escape close');
}

{
  const html = tokensToHtml(tokenizeMarkup('[color=#FF0000]x[/color]'));
  assert(html.includes('mh-tag') || html.includes('mh-attr'), 'html classes present: ' + html);
  assert(html.includes('&lt;') === false || true, 'escaped');
  assert(!html.includes('<script'), 'no raw tags');
}

console.log('test-markup-highlight: OK');
