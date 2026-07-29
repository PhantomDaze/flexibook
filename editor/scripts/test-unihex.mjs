#!/usr/bin/env node
/**
 * Minimal node test for UnihexFont parse + advance logic.
 * - Works without the real ZIP by using an inline test hex when zip absent.
 * - When zip is present under common locations, also exercises real parse.
 * - Asserts space override =4, ZWNJ=0, formula for advances.
 *
 * Run: npm run test:font
 * (uses plain node + fflate)
 */

import * as fflate from 'fflate';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// ---- Duplicate the pure parse logic so test runs in plain node without ts ----

function ctz32(n) {
  n = n >>> 0;
  if (n === 0) return 32;
  let c = 0;
  if ((n & 0xffff) === 0) { c += 16; n >>>= 16; }
  if ((n & 0xff) === 0) { c += 8; n >>>= 8; }
  if ((n & 0xf) === 0) { c += 4; n >>>= 4; }
  if ((n & 0x3) === 0) { c += 2; n >>>= 2; }
  if ((n & 0x1) === 0) { c += 1; }
  return c;
}

function parseHexLine(line) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('#')) return null;
  const m = trimmed.match(/^([0-9A-Fa-f]{4,6}):(.*)$/);
  if (!m) return null;
  const cp = parseInt(m[1], 16);
  const hex = (m[2] || '').replace(/\s+/g, '').toUpperCase();
  const hexLen = hex.length;
  if (hexLen === 0 || hexLen % 16 !== 0) return null;
  const digitsPerRow = hexLen / 16;
  if (![2, 4, 6, 8].includes(digitsPerRow)) return null;
  const bitWidth = digitsPerRow * 4;

  const lines = new Uint32Array(16);
  let mask = 0;
  for (let r = 0; r < 16; r++) {
    const rowHex = hex.substring(r * digitsPerRow, (r + 1) * digitsPerRow);
    const val = parseInt(rowHex || '0', 16) >>> 0;
    let packed;
    if (bitWidth === 8) packed = (val & 0xff) << 24;
    else if (bitWidth === 16) packed = (val & 0xffff) << 16;
    else if (bitWidth === 24) packed = (val & 0xffffff) << 8;
    else packed = val >>> 0;
    lines[r] = packed >>> 0;
    mask |= lines[r];
  }
  mask >>>= 0;

  let left, right;
  if (mask === 0) {
    left = 0; right = bitWidth;
  } else {
    left = Math.clz32(mask);
    const tz = ctz32(mask);
    right = 32 - tz - 1;
  }
  return { cp, left, right, bitWidth };
}

function parseAllHex(text) {
  const map = new Map();
  for (const ln of text.split(/\r?\n/)) {
    const r = parseHexLine(ln);
    if (r) map.set(r.cp, { left: r.left, right: r.right, bitWidth: r.bitWidth });
  }
  return map;
}

function getAdvance(glyphs, cp, bold = false) {
  if (cp === 0x20) return 4;
  if (cp === 0x200c) return 0;
  let g = glyphs.get(cp);
  if (!g) g = glyphs.get(0xfffd);
  if (!g) return bold ? 0.5 : 0;
  const w = g.right - g.left + 1;
  const base = Math.floor(w / 2) + 1;
  return bold ? base + 0.5 : base;
}

function width(glyphs, text, bold = false) {
  if (!text) return 0;
  let sum = 0;
  for (const ch of text) {
    const cp = ch.codePointAt(0);
    sum += getAdvance(glyphs, cp, bold);
  }
  return Math.ceil(sum);
}

// ---- Test data: minimal valid inline hex (covers narrow + wider) ----
// 8-bit example: 32 hex digits total (2 per row *16). A thin vertical glyph (like '|')
const INLINE_HEX = `
0020:00000000000000000000000000000000
0021:00000000080808080808080808000000
0041:00001818242442427E42424242420000
0049:000000003C18181818181818183C0000
200C:00000000000000000000000000000000
FFFD:000000007E424242424242427E000000
`;

// 16-bit example row would be 64 hex digits; keep simple with 8-bit for test.

function run() {
  console.log('=== Unihex parse/advance test ===');

  // 1) Parse inline
  const glyphs = parseAllHex(INLINE_HEX);

  // Space override
  const sp = getAdvance(glyphs, 0x20);
  console.log('space advance:', sp);
  if (sp !== 4) {
    console.error('FAIL: space must be 4');
    process.exit(1);
  }

  // ZWNJ
  const zwnj = getAdvance(glyphs, 0x200c);
  console.log('ZWNJ advance:', zwnj);
  if (zwnj !== 0) {
    console.error('FAIL: ZWNJ must be 0');
    process.exit(1);
  }

  // Known ascii from inline: '!' (0021) and 'I'(0049) are narrow
  const advI = getAdvance(glyphs, 0x49);
  console.log('I advance (inline):', advI);
  if (advI < 2 || advI > 5) {
    console.error('FAIL: narrow I advance out of expected range');
    process.exit(1);
  }

  // width('I') and bold
  const wI = width(glyphs, 'I');
  const wIb = width(glyphs, 'I', true);
  console.log('width("I")=', wI, ' bold=', wIb);
  if (wIb !== Math.ceil(wI + 0.5)) {
    console.error('FAIL: bold should add +0.5 before ceil');
    process.exit(1);
  }

  // String width formula: ceil(sum)
  const s = 'A!I';
  const w = width(glyphs, s);
  console.log('width("A!I")=', w);
  // A from inline has left/right that should give adv 5 or 6; just sanity >0
  if (w <= 0) {
    console.error('FAIL: width should be positive');
    process.exit(1);
  }

  // 2) If real zip present, parse it too and spot-check
  const zipCandidates = [
    path.resolve(__dirname, '../public/assets/flexibook/font/unifont_all-17.0.05.zip'),
    path.resolve(__dirname, '../assets/flexibook/font/unifont_all-17.0.05.zip'),
    path.resolve(__dirname, '../../src/main/resources/assets/flexibook/font/unifont_all-17.0.05.zip'),
  ];
  let usedZip = null;
  for (const z of zipCandidates) {
    if (fs.existsSync(z)) { usedZip = z; break; }
  }
  if (usedZip) {
    console.log('Found real zip, parsing:', usedZip);
    const buf = fs.readFileSync(usedZip);
    const entries = fflate.unzipSync(new Uint8Array(buf));
    const allGlyphs = new Map();
    for (const [nm, data] of Object.entries(entries)) {
      if (nm.toLowerCase().endsWith('.hex')) {
        parseAllHex(new TextDecoder().decode(data)).forEach((v, k) => allGlyphs.set(k, v));
      }
    }
    console.log('Parsed glyphs from zip:', allGlyphs.size);
    const sp2 = getAdvance(allGlyphs, 0x20);
    if (sp2 !== 4) {
      console.error('FAIL: space from zip must still override to 4');
      process.exit(1);
    }
    // ASCII 'A' should have positive advance and reasonable for 16/32 row data
    const aAdv = getAdvance(allGlyphs, 0x41);
    console.log('A advance from zip:', aAdv);
    if (aAdv < 3 || aAdv > 10) {
      console.error('FAIL: A advance from real data out of plausible range');
      process.exit(1);
    }
    // FFFD must exist in unifont
    if (!allGlyphs.has(0xfffd)) {
      console.warn('WARN: U+FFFD not present in this unihex build');
    }
    console.log('Real zip checks passed.');
  } else {
    console.log('No real zip present — inline hex tests only (ok for bootstrap).');
  }

  console.log('All tests passed.');
}

run();
