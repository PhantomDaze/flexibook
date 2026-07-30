/**
 * Lightweight FlexiBook tag markup tokenizer for syntax highlighting.
 * Pure: no DOM. Safe to fail — caller falls back to plain text.
 */

export type MarkupTokenKind =
  | 'text'
  | 'tag'
  | 'attr-name'
  | 'attr-value'
  | 'escape'
  | 'punct';

export interface MarkupToken {
  kind: MarkupTokenKind;
  text: string;
}

/**
 * Tokenize FlexiBook markup. Escapes \[ \] first; tags between unescaped [ ].
 */
export function tokenizeMarkup(src: string): MarkupToken[] {
  if (!src) return [];
  const out: MarkupToken[] = [];
  let i = 0;
  let textBuf = '';

  const flushText = () => {
    if (textBuf) {
      out.push({ kind: 'text', text: textBuf });
      textBuf = '';
    }
  };

  while (i < src.length) {
    const ch = src.charAt(i);
    // escape \[ \]
    if (ch === '\\' && i + 1 < src.length) {
      const n = src.charAt(i + 1);
      if (n === '[' || n === ']') {
        flushText();
        out.push({ kind: 'escape', text: ch + n });
        i += 2;
        continue;
      }
    }
    if (ch === '[') {
      const end = src.indexOf(']', i + 1);
      if (end < 0) {
        // unclosed — rest as text
        textBuf += src.slice(i);
        break;
      }
      flushText();
      const inner = src.slice(i + 1, end);
      out.push({ kind: 'punct', text: '[' });
      tokenizeTagInner(inner, out);
      out.push({ kind: 'punct', text: ']' });
      i = end + 1;
      continue;
    }
    textBuf += ch;
    i++;
  }
  flushText();
  return out;
}

function tokenizeTagInner(inner: string, out: MarkupToken[]): void {
  if (!inner) return;
  let s = inner;
  // closing
  if (s.startsWith('/')) {
    out.push({ kind: 'tag', text: '/' });
    s = s.slice(1);
  }
  // self-closing trailing /
  let selfClose = false;
  if (s.endsWith('/')) {
    selfClose = true;
    s = s.slice(0, -1).trimEnd();
  }

  // leading name or name=value compact
  let j = 0;
  while (j < s.length && !/\s/.test(s.charAt(j))) j++;
  const first = s.slice(0, j);
  const rest = s.slice(j);

  if (first.includes('=')) {
    // compact color=#ff or font=ns:path as single tag-ish
    const eq = first.indexOf('=');
    out.push({ kind: 'attr-name', text: first.slice(0, eq) });
    out.push({ kind: 'punct', text: '=' });
    out.push({ kind: 'attr-value', text: first.slice(eq + 1) });
  } else if (first) {
    out.push({ kind: 'tag', text: first });
  }

  // attributes: key="value" | key='value' | key=bare
  let r = rest;
  const attrRe =
    /(\s+)([a-zA-Z_][\w.-]*)(=)(?:"([^"]*)"|'([^']*)'|(\S+))?/g;
  let m: RegExpExecArray | null;
  let last = 0;
  while ((m = attrRe.exec(r)) !== null) {
    if (m.index > last) {
      // unexpected leftover
      const gap = r.slice(last, m.index);
      if (gap.trim()) out.push({ kind: 'text', text: gap });
    }
    out.push({ kind: 'text', text: m[1]! }); // whitespace
    out.push({ kind: 'attr-name', text: m[2]! });
    out.push({ kind: 'punct', text: m[3]! });
    const val = m[4] ?? m[5] ?? m[6] ?? '';
    if (m[4] != null) {
      out.push({ kind: 'punct', text: '"' });
      out.push({ kind: 'attr-value', text: val });
      out.push({ kind: 'punct', text: '"' });
    } else if (m[5] != null) {
      out.push({ kind: 'punct', text: "'" });
      out.push({ kind: 'attr-value', text: val });
      out.push({ kind: 'punct', text: "'" });
    } else {
      out.push({ kind: 'attr-value', text: val });
    }
    last = m.index + m[0].length;
  }
  if (last < r.length) {
    const tail = r.slice(last);
    if (tail) out.push({ kind: 'text', text: tail });
  }

  if (selfClose) {
    out.push({ kind: 'tag', text: '/' });
  }
}

/** Escape for HTML text nodes inside highlight layer. */
export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function tokensToHtml(tokens: MarkupToken[]): string {
  return tokens
    .map((t) => {
      const cls =
        t.kind === 'text'
          ? 'mh-text'
          : t.kind === 'tag'
            ? 'mh-tag'
            : t.kind === 'attr-name'
              ? 'mh-attr-name'
              : t.kind === 'attr-value'
                ? 'mh-attr-value'
                : t.kind === 'escape'
                  ? 'mh-escape'
                  : 'mh-punct';
      return `<span class="${cls}">${escapeHtml(t.text)}</span>`;
    })
    .join('');
}
