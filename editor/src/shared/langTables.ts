/**
 * Multi-language translation tables shared by Lang panel / App / pack export.
 * Keys are Minecraft lang codes (en_us, zh_cn, ja_jp, …).
 */

export type LangCode = string;

export type LangTable = Record<string, string>;
export type LangTables = Record<LangCode, LangTable>;

/** Built-in seeds loaded from editor assets */
export const DEFAULT_LANG_CODES: LangCode[] = ['en_us', 'zh_cn'];

export const LANG_CACHE_STORAGE_KEY = 'flexibook.editor.langTables.v1';
export const LANG_META_STORAGE_KEY = 'flexibook.editor.langMeta.v1';

export function emptyLangTables(codes: LangCode[] = DEFAULT_LANG_CODES): LangTables {
  const out: LangTables = {};
  for (const c of codes) out[c] = {};
  return out;
}

export function listLangCodes(tables: LangTables): LangCode[] {
  return Object.keys(tables || {}).sort((a, b) => {
    const ia = DEFAULT_LANG_CODES.indexOf(a);
    const ib = DEFAULT_LANG_CODES.indexOf(b);
    if (ia >= 0 || ib >= 0) {
      if (ia < 0) return 1;
      if (ib < 0) return -1;
      return ia - ib;
    }
    return a.localeCompare(b);
  });
}

export function normalizeLangCode(raw: string): string | null {
  const s = (raw || '').trim().toLowerCase().replace(/-/g, '_');
  if (!s) return null;
  // MC-style: en_us, zh_cn, pt_br, …
  if (!/^[a-z]{2,3}(_[a-z0-9]{2,8})?$/.test(s)) return null;
  return s;
}

export function ensureLang(tables: LangTables, code: LangCode): LangTables {
  if (tables[code]) return tables;
  return { ...tables, [code]: {} };
}

export function ensureKeyInAllLangs(tables: LangTables, key: string): LangTables {
  const k = key.trim();
  if (!k) return tables;
  const next: LangTables = { ...tables };
  for (const code of Object.keys(next)) {
    const t = { ...(next[code] || {}) };
    if (!(k in t)) t[k] = '';
    next[code] = t;
  }
  return next;
}

export function setLangValue(
  tables: LangTables,
  code: LangCode,
  key: string,
  value: string,
): LangTables {
  const base = ensureLang(tables, code);
  return {
    ...base,
    [code]: {
      ...(base[code] || {}),
      [key]: value,
    },
  };
}

export function removeKeyFromAll(tables: LangTables, key: string): LangTables {
  const next: LangTables = {};
  for (const [code, table] of Object.entries(tables)) {
    const t = { ...table };
    delete t[key];
    next[code] = t;
  }
  return next;
}

export function allKeys(tables: LangTables): string[] {
  const set = new Set<string>();
  for (const table of Object.values(tables || {})) {
    for (const k of Object.keys(table || {})) set.add(k);
  }
  return [...set].sort((a, b) => a.localeCompare(b));
}

export function missingLangsForKey(tables: LangTables, key: string): LangCode[] {
  const miss: LangCode[] = [];
  for (const code of listLangCodes(tables)) {
    const v = tables[code]?.[key];
    if (v == null || v === '') miss.push(code);
  }
  return miss;
}

/** Deep clone plain string tables */
export function cloneLangTables(tables: LangTables): LangTables {
  const out: LangTables = {};
  for (const [c, t] of Object.entries(tables || {})) {
    out[c] = { ...(t || {}) };
  }
  return out;
}

export function loadLangTablesFromStorage(): LangTables | null {
  try {
    const raw = localStorage.getItem(LANG_CACHE_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return null;
    const out: LangTables = {};
    for (const [code, table] of Object.entries(parsed as Record<string, unknown>)) {
      if (!code || typeof table !== 'object' || !table) continue;
      const t: LangTable = {};
      for (const [k, v] of Object.entries(table as Record<string, unknown>)) {
        if (typeof k === 'string' && typeof v === 'string') t[k] = v;
      }
      out[code] = t;
    }
    return Object.keys(out).length ? out : null;
  } catch {
    return null;
  }
}

export function saveLangTablesToStorage(tables: LangTables): void {
  try {
    localStorage.setItem(LANG_CACHE_STORAGE_KEY, JSON.stringify(tables));
  } catch {
    /* quota / private mode */
  }
}

/**
 * Merge asset-seeded tables with cached edits.
 * Cache wins on key collision (user edits); missing langs/keys filled from seed.
 */
export function mergeLangTables(seed: LangTables, cached: LangTables | null): LangTables {
  if (!cached) return cloneLangTables(seed);
  const codes = new Set([...Object.keys(seed), ...Object.keys(cached)]);
  const out: LangTables = {};
  for (const code of codes) {
    out[code] = { ...(seed[code] || {}), ...(cached[code] || {}) };
  }
  return out;
}
