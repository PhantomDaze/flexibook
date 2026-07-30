/**
 * Editor-side custom TTF/OTF registry (preview + pack export).
 * Game loads these via vanilla font JSON ttf providers after export.
 */

export type FontFileExt = 'ttf' | 'otf';

export interface CustomFont {
  /** Book/font id, e.g. myguide:title */
  id: string;
  fileName: string;
  ext: FontFileExt;
  bytes: ArrayBuffer;
  /** Unique CSS font-family used by FontFace */
  family: string;
  objectUrl: string;
  status: 'loading' | 'ready' | 'error';
  error?: string;
  /** MC ttf provider knobs (export) */
  size: number;
  oversample: number;
  shiftX: number;
  shiftY: number;
}

export const DEFAULT_TTF_SIZE = 11;
export const DEFAULT_TTF_OVERSAMPLE = 2;

let familySeq = 0;

export function nextFontFamily(): string {
  familySeq += 1;
  return `fb-custom-${familySeq}-${Date.now().toString(36)}`;
}

export function detectFontExt(fileName: string): FontFileExt | null {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.ttf')) return 'ttf';
  if (lower.endsWith('.otf')) return 'otf';
  return null;
}

/** basename without extension, path-safe for MC font file / path segment */
export function fontBaseName(fileName: string): string {
  const base = fileName.replace(/^.*[/\\]/, '').replace(/\.(ttf|otf)$/i, '');
  const cleaned = base
    .toLowerCase()
    .replace(/[^a-z0-9_.-]+/g, '_')
    .replace(/^_+|_+$/g, '');
  return cleaned || 'custom';
}

export function defaultFontId(namespace: string, fileName: string): string {
  const ns = (namespace || 'myguide').toLowerCase().replace(/[^a-z0-9_.-]/g, '') || 'myguide';
  return `${ns}:${fontBaseName(fileName)}`;
}

export function isValidFontId(id: string): boolean {
  if (!id || typeof id !== 'string') return false;
  const idx = id.indexOf(':');
  if (idx <= 0 || idx === id.length - 1) return false;
  const ns = id.slice(0, idx);
  const path = id.slice(idx + 1);
  if (!/^[a-z0-9_.-]+$/.test(ns)) return false;
  if (!/^[a-z0-9_./-]+$/.test(path)) return false;
  if (path.includes('..')) return false;
  return true;
}

export function parseFontId(id: string): { namespace: string; path: string } | null {
  if (!isValidFontId(id)) return null;
  const idx = id.indexOf(':');
  return { namespace: id.slice(0, idx), path: id.slice(idx + 1) };
}

/** Export payload shape for packExport (no live FontFace state). */
export interface CustomFontExport {
  id: string;
  fileName: string;
  ext: FontFileExt;
  bytes: ArrayBuffer;
  size?: number;
  oversample?: number;
  shiftX?: number;
  shiftY?: number;
}

export function toExportFont(f: CustomFont): CustomFontExport {
  return {
    id: f.id,
    fileName: f.fileName,
    ext: f.ext,
    bytes: f.bytes,
    size: f.size,
    oversample: f.oversample,
    shiftX: f.shiftX,
    shiftY: f.shiftY,
  };
}

export function revokeCustomFont(f: CustomFont): void {
  try {
    if (f.objectUrl) URL.revokeObjectURL(f.objectUrl);
  } catch {
    /* ignore */
  }
}

export function revokeAllCustomFonts(fonts: CustomFont[]): void {
  for (const f of fonts) revokeCustomFont(f);
}
