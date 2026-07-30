/**
 * TypeScript port of Java `io.github.PhantomDaze.flexibook.parse.TagParser`.
 * Lightweight HTML-subset tag parser for book markup.
 * Supports: h1 h2 p b i u color font br divider img link bullet div, nesting, and \[ \] escapes.
 * Unknown / malformed tags are skipped with a console warning — never throws to callers.
 */

import type { BookElement, InlineSpan, LinkAction, StyleFlags } from './types';
import { StyleFlags as SF, LinkAction as LA, InlineSpan as IS, TranslatableText } from './types';

const MOD_ID = 'flexibook';

interface Tag {
  name: string;
  closing: boolean;
  selfClosing: boolean;
  attrs: Record<string, string>;
  rawInner: string;
}

export function parseMarkup(markup: string | null | undefined): BookElement[] {
  if (markup == null || !markup.trim()) {
    return [];
  }
  try {
    const parser = new Parser(markup);
    return parser.parseBlocks();
  } catch (e) {
    console.warn('[FlexiBook TagParser] failed:', e);
    return [{ type: 'paragraph', spans: [IS.literal(markup)] }];
  }
}

class Parser {
  private readonly src: string;
  private pos = 0;

  constructor(src: string) {
    this.src = src;
  }

  parseBlocks(): BookElement[] {
    const out: BookElement[] = [];
    while (this.pos < this.src.length) {
      this.skipWhitespaceNewlines();
      if (this.pos >= this.src.length) break;

      if (this.peekTag()) {
        const tag = this.readTag();
        if (!tag) continue;
        if (tag.closing) {
          console.warn(`Unexpected closing tag [/${tag.name}] at ${this.pos}`);
          continue;
        }
        switch (tag.name) {
          case 'h1':
            out.push({
              type: 'heading',
              level: 1,
              text: this.readTranslatableUntil('h1'),
              ...fontField(parseFontAttr(tag)),
            });
            break;
          case 'h2':
            out.push({
              type: 'heading',
              level: 2,
              text: this.readTranslatableUntil('h2'),
              ...fontField(parseFontAttr(tag)),
            });
            break;
          case 'p':
            out.push({ type: 'paragraph', spans: this.parseInlines('p') });
            break;
          case 'bullet':
            out.push({ type: 'bullet', spans: this.parseInlines('bullet') });
            break;
          case 'br':
            out.push({ type: 'br' });
            break;
          case 'divider':
            out.push({ type: 'divider' });
            break;
          case 'img':
            out.push(this.parseImage(tag));
            break;
          case 'div': {
            const cls = tag.attrs['class'];
            out.push({
              type: 'box',
              className: cls,
              children: this.parseBlocksUntil('div'),
            });
            break;
          }
          case 'link': {
            const spans = this.parseInlines('link');
            let style: StyleFlags = SF.withColor(SF.EMPTY, parseColor(tag.attrs['color']) ?? 0x00aaff);
            const font = parseFontAttr(tag);
            if (font) style = SF.withFont(style, font);
            const action = linkActionFrom(tag.attrs);
            if (spans.length === 0) {
              out.push({
                type: 'paragraph',
                spans: [IS.literal('', style, action)],
              });
            } else {
              const linked = spans.map((s) => ({
                ...s,
                style: SF.merge(s.style, style),
                link: action,
              }));
              out.push({ type: 'paragraph', spans: linked });
            }
            break;
          }
          default: {
            console.warn(`Unknown block tag [${tag.name}] — treating as paragraph content`);
            if (!tag.selfClosing) {
              this.parseInlines(tag.name);
            }
          }
        }
      } else {
        const loose = this.readUntilTagOrEnd();
        if (loose.trim()) {
          out.push({
            type: 'paragraph',
            spans: [IS.literal(loose.trim())],
          });
        }
      }
    }
    return out;
  }

  private parseBlocksUntil(closeName: string): BookElement[] {
    const out: BookElement[] = [];
    while (this.pos < this.src.length) {
      this.skipWhitespaceNewlines();
      if (this.pos >= this.src.length) break;

      if (this.peekTag()) {
        const mark = this.pos;
        const peek = this.readTag();
        if (peek && peek.closing && peek.name === closeName) {
          return out;
        }
        this.pos = mark;
        const before = out.length;
        const next = this.readTag();
        if (!next) continue;
        if (next.closing) {
          if (next.name === closeName) return out;
          continue;
        }
        switch (next.name) {
          case 'h1':
            out.push({
              type: 'heading',
              level: 1,
              text: this.readTranslatableUntil('h1'),
              ...fontField(parseFontAttr(next)),
            });
            break;
          case 'h2':
            out.push({
              type: 'heading',
              level: 2,
              text: this.readTranslatableUntil('h2'),
              ...fontField(parseFontAttr(next)),
            });
            break;
          case 'p':
            out.push({ type: 'paragraph', spans: this.parseInlines('p') });
            break;
          case 'bullet':
            out.push({ type: 'bullet', spans: this.parseInlines('bullet') });
            break;
          case 'br':
            out.push({ type: 'br' });
            break;
          case 'divider':
            out.push({ type: 'divider' });
            break;
          case 'img':
            out.push(this.parseImage(next));
            break;
          case 'div': {
            const cls = next.attrs['class'];
            out.push({
              type: 'box',
              className: cls,
              children: this.parseBlocksUntil('div'),
            });
            break;
          }
          default:
            if (!next.selfClosing) {
              this.parseInlines(next.name);
            }
        }
        if (out.length === before && this.pos === mark) {
          this.pos++;
        }
      } else {
        const loose = this.readUntilTagOrEnd();
        if (loose.trim()) {
          out.push({
            type: 'paragraph',
            spans: [IS.literal(loose.trim())],
          });
        }
      }
    }
    console.warn(`Unclosed [div] / [${closeName}]`);
    return out;
  }

  private parseInlines(closeName: string): InlineSpan[] {
    const spans: InlineSpan[] = [];
    let style: StyleFlags = { ...SF.EMPTY };
    let pendingLink: LinkAction | undefined;
    let buf = '';

    const flush = () => {
      if (!buf) return;
      const text = buf;
      buf = '';
      const translate = text.indexOf('.') > 0 && text.indexOf(' ') < 0;
      if (pendingLink) {
        spans.push(
          translate
            ? IS.key(text, { ...style }, pendingLink)
            : IS.literal(text, { ...style }, pendingLink),
        );
      } else {
        spans.push(translate ? IS.key(text, { ...style }) : IS.literal(text, { ...style }));
      }
    };

    while (this.pos < this.src.length) {
      if (this.src.charAt(this.pos) === '\\' && this.pos + 1 < this.src.length) {
        const n = this.src.charAt(this.pos + 1);
        if (n === '[' || n === ']') {
          buf += n;
          this.pos += 2;
          continue;
        }
      }
      if (this.peekTag()) {
        flush();
        const tag = this.readTag();
        if (!tag) continue;
        if (tag.closing) {
          if (tag.name === closeName) return spans;
          switch (tag.name) {
            case 'b':
              style = SF.withBold(style, false);
              break;
            case 'i':
              style = SF.withItalic(style, false);
              break;
            case 'u':
              style = SF.withUnderline(style, false);
              break;
            case 'color':
              style = SF.withColor(style, undefined);
              break;
            case 'font':
              style = SF.withFont(style, undefined);
              break;
            case 'link':
              pendingLink = undefined;
              break;
          }
          continue;
        }
        switch (tag.name) {
          case 'b':
            style = SF.withBold(style, true);
            break;
          case 'i':
            style = SF.withItalic(style, true);
            break;
          case 'u':
            style = SF.withUnderline(style, true);
            break;
          case 'color': {
            let c = parseColor(tag.attrs['color']);
            if (c == null) c = parseColor(firstAttrValue(tag));
            style = SF.withColor(style, c ?? undefined);
            break;
          }
          case 'font': {
            let f = parseFontAttr(tag);
            if (!f) f = parseFontId(firstAttrValue(tag));
            style = SF.withFont(style, f ?? undefined);
            break;
          }
          case 'link': {
            pendingLink = linkActionFrom(tag.attrs);
            const f = parseFontAttr(tag);
            if (f) style = SF.withFont(style, f);
            break;
          }
          case 'br':
            spans.push(IS.literal('\n', { ...style }));
            break;
        }
        // compact [color=#RRGGBB] / [font=ns:path] when name carries assignment
        if (tag.name.startsWith('color') && tag.name.includes('=')) {
          const parts = tag.name.split('=', 2);
          if (parts.length === 2) {
            style = SF.withColor(style, parseColor(parts[1]) ?? undefined);
          }
        }
        if (tag.name.startsWith('font') && tag.name.includes('=')) {
          const parts = tag.name.split('=', 2);
          if (parts.length === 2) {
            style = SF.withFont(style, parseFontId(parts[1]) ?? undefined);
          }
        }
      } else {
        buf += this.src.charAt(this.pos);
        this.pos++;
      }
    }
    flush();
    if (closeName) {
      console.warn(`Unclosed [${closeName}]`);
    }
    return spans;
  }

  private readTranslatableUntil(closeName: string): { key: string; args: string[] } {
    const spans = this.parseInlines(closeName);
    if (spans.length === 0) {
      return TranslatableText.of('');
    }
    const first = spans[0]!;
    if (first.translate) {
      return TranslatableText.of(first.text);
    }
    return TranslatableText.of(spans.map((s) => s.text).join(''));
  }

  private parseImage(tag: Tag): BookElement {
    const srcAttr = tag.attrs['src'] ?? `${MOD_ID}:textures/gui/icon.png`;
    let src: string;
    if (srcAttr.includes(':')) {
      src = srcAttr;
    } else {
      src = `${MOD_ID}:${srcAttr}`;
    }
    const w = parseIntAttr(tag.attrs['width'], 48);
    const h = parseIntAttr(tag.attrs['height'], 48);
    const tip = tag.attrs['tooltip'];
    return { type: 'image', src, width: w, height: h, tooltip: tip };
  }

  private peekTag(): boolean {
    return (
      this.pos < this.src.length &&
      this.src.charAt(this.pos) === '[' &&
      (this.pos === 0 || this.src.charAt(this.pos - 1) !== '\\')
    );
  }

  private readTag(): Tag | null {
    if (this.pos >= this.src.length || this.src.charAt(this.pos) !== '[') {
      return null;
    }
    const end = this.src.indexOf(']', this.pos + 1);
    if (end < 0) {
      this.pos = this.src.length;
      return null;
    }
    let inner = this.src.substring(this.pos + 1, end).trim();
    this.pos = end + 1;
    if (!inner) return null;

    let closing = false;
    if (inner.startsWith('/')) {
      closing = true;
      inner = inner.substring(1).trim();
    }
    let selfClosing = false;
    if (inner.endsWith('/')) {
      selfClosing = true;
      inner = inner.substring(0, inner.length - 1).trim();
    }

    const attrs: Record<string, string> = {};
    const sp = indexOfWhitespaceOrEnd(inner);
    const first = inner.substring(0, sp);
    const rest = sp < inner.length ? inner.substring(sp).trim() : '';
    const firstLower = first.toLowerCase();
    let name: string;

    if (firstLower.startsWith('color=') || firstLower === 'color') {
      name = 'color';
      if (first.includes('=')) {
        attrs['color'] = first.substring(first.indexOf('=') + 1).replace(/["']/g, '');
      }
    } else if (firstLower.startsWith('font=') || firstLower === 'font') {
      name = 'font';
      if (first.includes('=')) {
        attrs['font'] = first.substring(first.indexOf('=') + 1).replace(/["']/g, '');
      }
    } else if (first.includes('=')) {
      name = first;
    } else {
      name = firstLower;
    }
    if (rest) parseAttrs(rest, attrs);
    if (name === 'br' || name === 'divider' || name === 'img') {
      selfClosing = true;
    }
    return { name, closing, selfClosing, attrs, rawInner: inner };
  }

  private readUntilTagOrEnd(): string {
    let sb = '';
    while (this.pos < this.src.length) {
      if (this.src.charAt(this.pos) === '\\' && this.pos + 1 < this.src.length) {
        const n = this.src.charAt(this.pos + 1);
        if (n === '[' || n === ']') {
          sb += n;
          this.pos += 2;
          continue;
        }
      }
      if (this.peekTag()) break;
      sb += this.src.charAt(this.pos);
      this.pos++;
    }
    return sb;
  }

  private skipWhitespaceNewlines(): void {
    while (this.pos < this.src.length && /\s/.test(this.src.charAt(this.pos))) {
      this.pos++;
    }
  }
}

function fontField(font: string | undefined): { font?: string } {
  return font ? { font } : {};
}

function parseAttrs(rest: string, attrs: Record<string, string>): void {
  let i = 0;
  while (i < rest.length) {
    while (i < rest.length && /\s/.test(rest.charAt(i))) i++;
    if (i >= rest.length) break;
    const eq = rest.indexOf('=', i);
    if (eq < 0) break;
    const key = rest.substring(i, eq).trim().toLowerCase();
    i = eq + 1;
    if (i >= rest.length) break;
    const q = rest.charAt(i);
    let value: string;
    if (q === '"' || q === "'") {
      const close = rest.indexOf(q, i + 1);
      if (close < 0) {
        value = rest.substring(i + 1);
        i = rest.length;
      } else {
        value = rest.substring(i + 1, close);
        i = close + 1;
      }
    } else {
      let j = i;
      while (j < rest.length && !/\s/.test(rest.charAt(j))) j++;
      value = rest.substring(i, j);
      i = j;
    }
    attrs[key] = value;
  }
}

function indexOfWhitespaceOrEnd(s: string): number {
  for (let i = 0; i < s.length; i++) {
    if (/\s/.test(s.charAt(i))) return i;
  }
  return s.length;
}

function parseColor(raw: string | undefined | null): number | null {
  if (raw == null || !String(raw).trim()) return null;
  let s = String(raw).trim();
  if (s.startsWith('#')) s = s.substring(1);
  if (s.startsWith('0x') || s.startsWith('0X')) s = s.substring(2);
  try {
    if (s.length === 6 || s.length === 8) {
      return parseInt(s, 16);
    }
  } catch {
    /* ignore */
  }
  return null;
}

function parseIntAttr(raw: string | undefined, def: number): number {
  if (raw == null) return def;
  const n = parseInt(String(raw).trim(), 10);
  return Number.isFinite(n) ? n : def;
}

function firstAttrValue(tag: Tag): string | undefined {
  const keys = Object.keys(tag.attrs);
  if (keys.length === 0) return undefined;
  return tag.attrs[keys[0]!];
}

function linkActionFrom(attrs: Record<string, string>): LinkAction {
  if (attrs['cmd']) return LA.command(attrs['cmd']);
  if (attrs['url']) return LA.url(attrs['url']);
  return LA.NONE;
}

function parseFontAttr(tag: Tag): string | undefined {
  if (tag.attrs['font']) return parseFontId(tag.attrs['font']);
  if (tag.name === 'font' && tag.attrs['src']) return parseFontId(tag.attrs['src']);
  return undefined;
}

function parseFontId(raw: string | undefined | null): string | undefined {
  if (raw == null || !String(raw).trim()) return undefined;
  const s = String(raw).trim().replace(/["']/g, '');
  // ResourceLocation-like: ns:path or path
  if (!s) return undefined;
  if (s.includes(':')) {
    const [ns, ...rest] = s.split(':');
    if (!ns || rest.join(':').length === 0) return undefined;
    return s;
  }
  return s;
}
