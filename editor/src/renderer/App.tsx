import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { clearLayoutCache, layout } from '../shared/layout';
import { FLEXIBOOK_DEFAULT_FONT, UnihexFont } from '../shared/UnihexFont';
import { JsonTranslationProvider } from '../shared/JsonTranslationProvider';
import type { AdaptiveBookContent, BookTheme, RenderedPage } from '../shared/types';
import { ThemePanel } from './ThemePanel';
import { ContentPanel } from './ContentPanel';
import { PreviewCanvas } from './PreviewCanvas';
import { FontPanel } from './FontPanel';
import { LangPanel, TranslationValueWorkspace, type LangCode, type LangTables } from './LangPanel';
import { PackExportModal, type PackExportFormValues, type PackExportMode } from './PackExportForm';
import {
  DEFAULT_LANG_CODES,
  ensureKeyInAllLangs,
  listLangCodes,
  loadLangTablesFromStorage,
  mergeLangTables,
  saveLangTablesToStorage,
} from '../shared/langTables';
import { DEFAULT_THEME, CONTAIN_THEME, DEMO_GUIDE_CONTENT } from './defaults';
import { bookContentToWire, parseBookContentJson, parseThemeJson, themeToWire } from '../shared/modJson';
import {
  buildResourcePack,
  packFilesToZip,
  packPartsSuffix,
  resolvePackParts,
  validateNamespace,
} from '../shared/packExport';
import type { PackParts } from '../shared/packExport';
import {
  importPackFromZip,
  parsePackFiles,
  type PackImportFile,
  type PackImportResult,
} from '../shared/packImport';
import {
  clearWorkspaceDraft,
  createDraftAutosave,
  loadWorkspaceDraft,
  saveWorkspaceDraft,
  type WorkspaceDraftV1,
} from '../shared/workspaceDraft';
import type { CustomTexture, CustomTextures, TextureSlot } from './customTextures';
import {
  EMPTY_CUSTOM_TEXTURES,
  loadImageFromBytes,
  resolveThemeAssetUrl,
  revokeAllCustomTextures,
  revokeCustomTexture,
} from './customTextures';
import type { CustomFont } from '../shared/customFonts';
import {
  DEFAULT_TTF_OVERSAMPLE,
  DEFAULT_TTF_SIZE,
  defaultFontId,
  detectFontExt,
  nextFontFamily,
  revokeAllCustomFonts,
  toExportFont,
  type CustomFontExport,
} from '../shared/customFonts';
import { BrowserFont } from '../shared/BrowserFont';
import { FontRouter } from '../shared/FontRouter';
import './styles.css';

declare global {
  interface Window {
    electronAPI?: {
      openFile?: (options?: any) => Promise<any>;
      saveFile?: (options?: any) => Promise<any>;
      showMessage?: (options: any) => Promise<any>;
      openDirectory?: (options?: any) => Promise<any>;
      writePack?: (payload: {
        dir: string;
        files: { path: string; base64: string }[];
      }) => Promise<{ ok: boolean; root?: string; error?: string }>;
      readPack?: (payload: {
        dir: string;
      }) => Promise<{
        ok: boolean;
        root?: string;
        files?: { path: string; base64: string }[];
        error?: string;
      }>;
      readBinaryFile?: (
        filePath: string,
      ) => Promise<{ ok: boolean; path?: string; base64?: string; size?: number; error?: string }>;
      readTextFile?: (filePath: string) => Promise<{
        ok: boolean;
        path?: string;
        text?: string;
        error?: string;
      }>;
      writeTextFile?: (payload: {
        path: string;
        text: string;
      }) => Promise<{ ok: boolean; path?: string; error?: string }>;
    };
  }
}

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function copyArrayBuffer(src: ArrayBuffer): ArrayBuffer {
  return src.slice(0);
}

function summarizeImport(r: PackImportResult): string {
  const parts = [
    r.namespace && `ns=${r.namespace}`,
    r.theme && `theme=${r.themeId || 'yes'}`,
    r.content && `book=${r.bookId || 'yes'}`,
    Object.keys(r.langTables).length && `lang=${Object.keys(r.langTables).join(',')}`,
    r.fonts.length && `fonts=${r.fonts.length}`,
    r.textures.book && 'textures',
  ].filter(Boolean);
  return parts.length ? `已加载：${parts.join(' · ')}` : '包内未识别到 FlexiBook 数据';
}

async function fetchLangJson(lang: string): Promise<Record<string, string>> {
  const candidates = [
    `/assets/lang/${lang}.json`,
    new URL(`../../assets/lang/${lang}.json`, import.meta.url).toString(),
  ];
  for (const url of candidates) {
    try {
      const res = await fetch(url, { cache: 'no-cache' });
      if (!res.ok) continue;
      const json = await res.json();
      if (json && typeof json === 'object') return { ...json } as Record<string, string>;
    } catch {
      /* try next */
    }
  }
  return {};
}

function translatorFromTable(lang: string, table: Record<string, string>): JsonTranslationProvider {
  const t = new JsonTranslationProvider();
  t.load(lang, table || {});
  return t;
}

function downloadJson(filename: string, data: unknown) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

const JSON_FILTERS = [
  { name: 'JSON', extensions: ['json'] },
  { name: 'All Files', extensions: ['*'] },
];

/** Electron native open → read UTF-8 JSON text; browser falls back to <input type=file>. */
async function pickAndReadJsonText(): Promise<{ path?: string; text: string } | null> {
  const api = window.electronAPI;
  if (api?.openFile && api?.readTextFile) {
    const result = await api.openFile({ filters: JSON_FILTERS, properties: ['openFile'] });
    if (result?.canceled) return null;
    const filePath = result?.filePaths?.[0];
    if (!filePath) return null;
    const read = await api.readTextFile(filePath);
    if (!read?.ok || typeof read.text !== 'string') {
      await api.showMessage?.({
        type: 'error',
        title: '读取失败',
        message: read?.error || '无法读取文件',
      });
      return null;
    }
    return { path: read.path || filePath, text: read.text };
  }
  // Browser fallback
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'application/json,.json';
    input.onchange = async () => {
      const file = input.files?.[0];
      if (!file) {
        resolve(null);
        return;
      }
      try {
        const text = await file.text();
        resolve({ path: file.name, text });
      } catch {
        resolve(null);
      }
    };
    input.click();
  });
}

/** Electron native save → write UTF-8; browser falls back to download. */
async function pickAndWriteJsonText(
  defaultName: string,
  data: unknown,
): Promise<{ ok: boolean; path?: string }> {
  const text = JSON.stringify(data, null, 2);
  const api = window.electronAPI;
  if (api?.saveFile && api?.writeTextFile) {
    const result = await api.saveFile({
      filters: JSON_FILTERS,
      defaultPath: defaultName,
    });
    if (result?.canceled) return { ok: false };
    const filePath = result?.filePath;
    if (!filePath) return { ok: false };
    const write = await api.writeTextFile({ path: filePath, text });
    if (!write?.ok) {
      await api.showMessage?.({
        type: 'error',
        title: '保存失败',
        message: write?.error || '无法写入文件',
      });
      return { ok: false };
    }
    await api.showMessage?.({
      type: 'info',
      title: '已保存',
      message: write.path || filePath,
    });
    return { ok: true, path: write.path || filePath };
  }
  downloadJson(defaultName, data);
  return { ok: true, path: defaultName };
}

/** Font ids referenced by content that are neither default nor registered customs. */
function collectUnregisteredFontIds(
  content: AdaptiveBookContent,
  registered: Set<string>,
): string[] {
  const found = new Set<string>();
  const add = (id?: string | null) => {
    if (!id || id === FLEXIBOOK_DEFAULT_FONT) return;
    if (registered.has(id)) return;
    found.add(id);
  };
  add(content.defaultFont);
  const walk = (els?: import('../shared/types').BookElement[]) => {
    if (!els) return;
    for (const el of els) {
      if (el.type === 'heading') add(el.font);
      if (el.type === 'paragraph' || el.type === 'bullet') {
        for (const s of el.spans || []) add(s.style?.font);
      }
      if (el.type === 'box') walk(el.children);
    }
  };
  walk(content.elements);
  return [...found];
}

/** Convert Uint8Array to base64 string (for IPC writePack). Safe for small-medium binaries like PNGs. */
function uint8ArrayToBase64(u8: Uint8Array): string {
  let binary = '';
  const CHUNK = 16384;
  for (let i = 0; i < u8.length; i += CHUNK) {
    const chunk = u8.subarray(i, i + CHUNK);
    binary += String.fromCharCode.apply(null, chunk as unknown as number[]);
  }
  return btoa(binary);
}

export default function App() {
  const [lang, setLang] = useState<LangCode>('en_us');
  const [search, setSearch] = useState('');
  const [scale, setScale] = useState<1 | 2 | 3 | 4>(2);
  const [theme, setTheme] = useState<BookTheme>(DEFAULT_THEME);
  const [content, setContent] = useState<AdaptiveBookContent>(DEMO_GUIDE_CONTENT);
  const [pages, setPages] = useState<RenderedPage[]>([]);
  const [pageIndex, setPageIndex] = useState(0);
  const [leftTab, setLeftTab] = useState<'theme' | 'content' | 'lang' | 'fonts'>('theme');
  /** preview = book canvas; content-edit = big translation value editor (no book preview) */
  const [workspaceMode, setWorkspaceMode] = useState<'preview' | 'content-edit'>('preview');
  const [selectedLangKey, setSelectedLangKey] = useState<string | null>(null);
  const [translator, setTranslator] = useState<JsonTranslationProvider | null>(null);
  const [fontAtlasRev, setFontAtlasRev] = useState(0);
  const [fontReady, setFontReady] = useState(false);
  const [customTextures, setCustomTextures] = useState<CustomTextures>(EMPTY_CUSTOM_TEXTURES);
  const [langTables, setLangTables] = useState<LangTables>({ en_us: {}, zh_cn: {} });
  const [langTablesReady, setLangTablesReady] = useState(false);
  const [customFonts, setCustomFonts] = useState<CustomFont[]>([]);
  const [fontImportBusy, setFontImportBusy] = useState(false);
  const browserFontsRef = useRef<Map<string, BrowserFont>>(new Map());
  const [browserFontRev, setBrowserFontRev] = useState(0);
  /** Manual bump to force layout recompute even if inputs unchanged */
  const [layoutForceRev, setLayoutForceRev] = useState(0);
  const [packExportOpen, setPackExportOpen] = useState(false);
  const [packExportMode, setPackExportMode] = useState<PackExportMode>('full');
  /** Last import / draft pack ids for export form defaults */
  const [packNamespace, setPackNamespace] = useState('myguide');
  const [packThemeId, setPackThemeId] = useState('main');
  const [packBookId, setPackBookId] = useState('guide');
  const [packImportBusy, setPackImportBusy] = useState(false);
  const [draftStatus, setDraftStatus] = useState<'loading' | 'ready' | 'saved' | 'error'>('loading');
  const [draftSavedAt, setDraftSavedAt] = useState<number | null>(null);
  const workspaceHydratedRef = useRef(false);
  const draftAutosaveRef = useRef(
    createDraftAutosave(async (d) => {
      await saveWorkspaceDraft(d);
      setDraftSavedAt(d.savedAt);
      setDraftStatus('saved');
    }, 900),
  );
  /** Skip autosave until boot hydrate finishes */
  const allowDraftSaveRef = useRef(false);

  const [unihex, setUnihex] = useState<UnihexFont | null>(null);

  const fontRouter = useMemo(() => {
    if (!unihex) return null;
    const map = new Map<string, BrowserFont>();
    for (const [id, bf] of browserFontsRef.current) {
      if (bf.isReady()) map.set(id, bf);
    }
    // also re-key if customFonts ids changed
    for (const cf of customFonts) {
      const bf = browserFontsRef.current.get(cf.id);
      if (bf && bf.isReady()) map.set(cf.id, bf);
    }
    return new FontRouter(unihex, map, FLEXIBOOK_DEFAULT_FONT);
    // browserFontRev + customFonts force rebuild when loads/renames happen
  }, [unihex, customFonts, browserFontRev]);

  const measurer = fontRouter;

  // Bootstrap lang tables: seed from assets, merge localStorage cache (user edits win)
  // Full workspace draft (IDB) applied after lang seed — see hydrate effect.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const seed: LangTables = {};
      await Promise.all(
        DEFAULT_LANG_CODES.map(async (code) => {
          seed[code] = await fetchLangJson(code);
        }),
      );
      if (cancelled) return;
      const cached = loadLangTablesFromStorage();
      const merged = mergeLangTables(seed, cached);
      setLangTables(merged);
      setLangTablesReady(true);
      const codes = listLangCodes(merged);
      if (codes.length && !codes.includes(lang)) {
        setLang(codes[0]!);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Hydrate workspace draft from IndexedDB once lang seed is ready
  useEffect(() => {
    if (!langTablesReady || workspaceHydratedRef.current) return;
    let cancelled = false;
    (async () => {
      try {
        const draft = await loadWorkspaceDraft();
        if (cancelled) return;
        if (draft) {
          setTheme(draft.theme);
          setContent(draft.content);
          setLangTables(draft.langTables);
          saveLangTablesToStorage(draft.langTables);
          if (draft.lang) setLang(draft.lang);
          if (draft.leftTab) setLeftTab(draft.leftTab);
          if (draft.workspaceMode) setWorkspaceMode(draft.workspaceMode);
          if (draft.selectedLangKey !== undefined) setSelectedLangKey(draft.selectedLangKey);
          if (draft.packNamespace) setPackNamespace(draft.packNamespace);
          if (draft.packThemeId) setPackThemeId(draft.packThemeId);
          if (draft.packBookId) setPackBookId(draft.packBookId);
          setDraftSavedAt(draft.savedAt);

          if (draft.fonts?.length) {
            const ids = new Set<string>();
            const next: CustomFont[] = [];
            for (const exp of draft.fonts) {
              next.push(await buildCustomFontEntry(exp, ids));
            }
            setCustomFonts(next);
          }

          if (draft.textures?.book) {
            try {
              const book = await loadImageFromBytes(
                draft.textures.book,
                draft.textures.bookFileName || 'book.png',
              );
              setCustomTextures({ book });
            } catch {
              /* ignore */
            }
          }
          clearLayoutCache();
          setDraftStatus('saved');
        } else {
          setDraftStatus('ready');
        }
      } catch (e) {
        console.warn('[FlexiBook] draft hydrate failed', e);
        setDraftStatus('error');
      } finally {
        if (!cancelled) {
          workspaceHydratedRef.current = true;
          // allow autosave after a tick so hydrate setStates settle
          setTimeout(() => {
            allowDraftSaveRef.current = true;
          }, 50);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // buildCustomFontEntry is stable
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [langTablesReady]);

  // Debounced workspace draft autosave (theme/content/lang/textures/fonts)
  useEffect(() => {
    if (!allowDraftSaveRef.current || !langTablesReady) return;
    draftAutosaveRef.current.schedule(() => {
      const fonts: CustomFontExport[] = customFonts.map(toExportFont);
      const draft: WorkspaceDraftV1 = {
        v: 1,
        savedAt: Date.now(),
        lang,
        leftTab,
        workspaceMode,
        selectedLangKey,
        packNamespace,
        packThemeId,
        packBookId,
        theme,
        content,
        langTables,
        textures: {
          book: customTextures.book?.bytes ? copyArrayBuffer(customTextures.book.bytes) : null,
          bookFileName: customTextures.book?.fileName,
        },
        fonts,
      };
      return draft;
    });
  }, [
    theme,
    content,
    langTables,
    langTablesReady,
    customTextures,
    customFonts,
    lang,
    leftTab,
    workspaceMode,
    selectedLangKey,
    packNamespace,
    packThemeId,
    packBookId,
  ]);

  // Flush draft on page hide
  useEffect(() => {
    const onHide = () => {
      void draftAutosaveRef.current.flush();
    };
    window.addEventListener('pagehide', onHide);
    return () => {
      window.removeEventListener('pagehide', onHide);
      void draftAutosaveRef.current.flush();
    };
  }, []);

  // Persist lang tables on every change (real-time cache — language switch safe)
  useEffect(() => {
    if (!langTablesReady) return;
    saveLangTablesToStorage(langTables);
  }, [langTables, langTablesReady]);

  // Load Unihex font (same asset as mod) before allowing layout.
  useEffect(() => {
    let cancelled = false;
    const candidates = [
      '/assets/flexibook/font/unifont_all-17.0.05.zip',
      new URL(`../../assets/flexibook/font/unifont_all-17.0.05.zip`, import.meta.url).toString(),
    ];
    (async () => {
      for (const url of candidates) {
        try {
          const f = await UnihexFont.loadFromUrl(url);
          if (cancelled) return;
          setUnihex(f);
          setFontReady(true);
          setFontAtlasRev((v) => v + 1);
          clearLayoutCache();
          return;
        } catch {
          // try next
        }
      }
      if (!cancelled) {
        console.warn('[FlexiBook] failed to load unihex font from any candidate');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Translator from editable lang tables (missing lang falls back to first available)
  useEffect(() => {
    if (!langTablesReady) {
      setTranslator(null);
      return;
    }
    const table = langTables[lang] || langTables[listLangCodes(langTables)[0] || 'en_us'] || {};
    clearLayoutCache();
    setTranslator(translatorFromTable(lang, table));
  }, [lang, langTables, langTablesReady]);

  // Cleanup custom fonts on unmount
  useEffect(() => {
    return () => {
      for (const bf of browserFontsRef.current.values()) {
        try {
          bf.unload();
        } catch {
          /* ignore */
        }
      }
      browserFontsRef.current.clear();
      revokeAllCustomFonts(customFonts);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const laidOut = useMemo(() => {
    if (!translator || !measurer || !measurer.isReady()) return [] as RenderedPage[];
    // layoutForceRev intentionally invalidates even when other deps are equal
    void layoutForceRev;
    return layout({
      content,
      measurer,
      translator,
      theme,
      languageCode: lang,
      guiScaleRef: 2,
      searchQuery: search,
      fontAtlasRev: fontAtlasRev + browserFontRev + layoutForceRev,
    });
  }, [
    content,
    theme,
    lang,
    search,
    translator,
    measurer,
    fontAtlasRev,
    browserFontRev,
    layoutForceRev,
  ]);

  const handleRelayout = useCallback(() => {
    clearLayoutCache();
    setLayoutForceRev((v) => v + 1);
    setPageIndex(0);
  }, []);

  const handleLangTablesChange = useCallback((next: LangTables) => {
    setLangTables(next);
    // real-time persist is handled by effect; also write immediately for crash safety
    saveLangTablesToStorage(next);
  }, []);

  const handleActiveLangChange = useCallback((code: LangCode) => {
    // langTables already hold latest keystrokes — safe to switch without flushing drafts
    setLang(code);
  }, []);

  useEffect(() => {
    setPages(laidOut);
    setPageIndex((i) => Math.min(i, Math.max(0, laidOut.length - 1)));
  }, [laidOut]);

  const bookImgRef = useRef<HTMLImageElement | null>(null);
  const [bookReady, setBookReady] = useState(false);
  const [bookEpoch, setBookEpoch] = useState(0);

  // Book background: prefer local custom file, else theme path under assets/
  useEffect(() => {
    let cancelled = false;
    setBookReady(false);
    const src = customTextures.book?.url || resolveThemeAssetUrl(theme.bookTexture);
    if (!src) {
      bookImgRef.current = null;
      return;
    }
    const img = new Image();
    img.onload = () => {
      if (cancelled) return;
      bookImgRef.current = img;
      setBookReady(true);
      setBookEpoch((e) => e + 1);
    };
    img.onerror = () => {
      if (cancelled) return;
      bookImgRef.current = null;
      setBookReady(false);
      setBookEpoch((e) => e + 1);
    };
    img.src = src;
    return () => {
      cancelled = true;
    };
  }, [theme.bookTexture, customTextures.book]);


  // Revoke object URLs on unmount
  const customTexturesRef = useRef(customTextures);
  customTexturesRef.current = customTextures;
  useEffect(() => {
    return () => revokeAllCustomTextures(customTexturesRef.current);
  }, []);

  const handleCustomTexture = useCallback(
    (slot: TextureSlot, tex: CustomTexture | null, opts?: { syncSize?: boolean }) => {
      setCustomTextures((prev) => {
        const old = prev[slot];
        if (old && old !== tex) revokeCustomTexture(old);
        return { ...prev, [slot]: tex };
      });
      if (slot === 'book' && tex && opts?.syncSize) {
        setTheme((t) => {
          const nw = tex.naturalWidth || t.bookTexWidth;
          const nh = tex.naturalHeight || t.bookTexHeight;
          // Vanilla-style GUI sheets are typically 256² (or larger) with a panel
          // region in the top-left. Keep existing panel size so layout still fits.
          // Full-bleed panel art (≈ content-sized) adopts natural dimensions.
          const looksLikeSheet = nw >= 256 && nh >= 256 && (nw > t.bookTexWidth || nh > t.bookTexHeight);
          if (looksLikeSheet) {
            return {
              ...t,
              textureSheetSize: Math.max(t.textureSheetSize, nw, nh),
              // clamp panel if it somehow exceeds the new sheet
              bookTexWidth: Math.min(t.bookTexWidth, nw),
              bookTexHeight: Math.min(t.bookTexHeight, nh),
              revision: (t.revision || 1) + 1,
            };
          }
          return {
            ...t,
            bookTexWidth: nw,
            bookTexHeight: nh,
            textureSheetSize: Math.max(t.textureSheetSize, nw, nh, 256),
            revision: (t.revision || 1) + 1,
          };
        });
      }
    },
    [],
  );

  const clearCustomTextures = useCallback(() => {
    setCustomTextures((prev) => {
      revokeAllCustomTextures(prev);
      return EMPTY_CUSTOM_TEXTURES;
    });
  }, []);

  const handleLoadTheme = useCallback(async () => {
    try {
      const picked = await pickAndReadJsonText();
      if (!picked) return;
      const json = JSON.parse(picked.text);
      const next = parseThemeJson(json);
      next.revision = (theme.revision || 1) + 1;
      setTheme(next);
      await window.electronAPI?.showMessage?.({
        type: 'info',
        title: '主题已加载',
        message: picked.path || 'theme.json',
      });
    } catch (e: any) {
      console.error('[FlexiBook] load theme failed', e);
      alert('加载主题失败：' + (e?.message || e));
    }
  }, [theme.revision]);

  const handleSaveTheme = useCallback(async () => {
    try {
      await pickAndWriteJsonText('theme.json', themeToWire(theme));
    } catch (e: any) {
      console.error('[FlexiBook] save theme failed', e);
      alert('保存主题失败：' + (e?.message || e));
    }
  }, [theme]);

  const handleLoadContent = useCallback(async () => {
    try {
      const picked = await pickAndReadJsonText();
      if (!picked) return;
      const json = JSON.parse(picked.text);
      const next = parseBookContentJson(json);
      setContent(next);
      setPageIndex(0);
      await window.electronAPI?.showMessage?.({
        type: 'info',
        title: '内容已加载',
        message: picked.path || 'content.json',
      });
    } catch (e: any) {
      console.error('[FlexiBook] load content failed', e);
      alert('加载内容失败：' + (e?.message || e));
    }
  }, []);

  const handleSaveContent = useCallback(async () => {
    try {
      await pickAndWriteJsonText('book.json', bookContentToWire(content));
    } catch (e: any) {
      console.error('[FlexiBook] save content failed', e);
      alert('保存内容失败：' + (e?.message || e));
    }
  }, [content]);

  const registeredFontIds = useMemo(() => new Set(customFonts.map((f) => f.id)), [customFonts]);
  const unregisteredFonts = useMemo(
    () => collectUnregisteredFontIds(content, registeredFontIds),
    [content, registeredFontIds],
  );
  const approxCustomFonts = useMemo(() => {
    const ids: string[] = [];
    if (content.defaultFont && registeredFontIds.has(content.defaultFont)) {
      ids.push(content.defaultFont);
    }
    return ids;
  }, [content.defaultFont, registeredFontIds]);

  const handleCustomFontsChange = useCallback((next: CustomFont[]) => {
    const oldById = browserFontsRef.current;
    const rebuilt = new Map<string, BrowserFont>();
    for (const f of next) {
      const existing = oldById.get(f.id);
      if (existing) {
        rebuilt.set(f.id, existing);
        oldById.delete(f.id);
        continue;
      }
      // rename: same CSS family, new id
      for (const [oldId, bf] of oldById) {
        if (bf.family === f.family) {
          rebuilt.set(f.id, bf);
          oldById.delete(oldId);
          break;
        }
      }
    }
    // unload removed fonts
    for (const bf of oldById.values()) {
      try {
        bf.unload();
      } catch {
        /* ignore */
      }
    }
    browserFontsRef.current = rebuilt;
    setCustomFonts(next);
    setBrowserFontRev((v) => v + 1);
    clearLayoutCache();
  }, []);

  const handleImportFontFiles = useCallback(async (fileList: FileList) => {
    setFontImportBusy(true);
    try {
      const additions: CustomFont[] = [];
      for (const file of Array.from(fileList)) {
        const ext = detectFontExt(file.name);
        if (!ext) {
          alert(`跳过非 TTF/OTF：${file.name}`);
          continue;
        }
        const bytes = await file.arrayBuffer();
        let id = defaultFontId('myguide', file.name);
        const existing = new Set([
          ...customFonts.map((f) => f.id),
          ...additions.map((f) => f.id),
        ]);
        if (existing.has(id)) {
          let n = 2;
          while (existing.has(`${id}_${n}`)) n++;
          id = `${id}_${n}`;
        }
        const family = nextFontFamily();
        const objectUrl = URL.createObjectURL(new Blob([bytes]));
        const entry: CustomFont = {
          id,
          fileName: file.name,
          ext,
          bytes,
          family,
          objectUrl,
          status: 'loading',
          size: DEFAULT_TTF_SIZE,
          oversample: DEFAULT_TTF_OVERSAMPLE,
          shiftX: 0,
          shiftY: 0,
        };
        additions.push(entry);
        void BrowserFont.loadFromBytes(bytes.slice(0), family, ext)
          .then((bf) => {
            browserFontsRef.current.set(id, bf);
            setCustomFonts((prev) =>
              prev.map((f) => (f.id === id ? { ...f, status: 'ready' as const } : f)),
            );
            setBrowserFontRev((v) => v + 1);
            clearLayoutCache();
          })
          .catch((err: Error) => {
            setCustomFonts((prev) =>
              prev.map((f) =>
                f.id === id
                  ? { ...f, status: 'error' as const, error: err?.message || String(err) }
                  : f,
              ),
            );
          });
      }
      if (additions.length) {
        setCustomFonts((prev) => [...prev, ...additions]);
      }
    } finally {
      setFontImportBusy(false);
    }
  }, [customFonts]);

  const ensureLangKey = useCallback((key: string) => {
    const k = key.trim();
    if (!k) return;
    setLangTables((prev) => {
      const next = ensureKeyInAllLangs(prev, k);
      saveLangTablesToStorage(next);
      return next;
    });
    setSelectedLangKey(k);
  }, []);

  const openPackExport = useCallback((mode: PackExportMode) => {
    setPackExportMode(mode);
    setPackExportOpen(true);
  }, []);

  /** Full or partial resource pack export (parts driven by form/mode). */
  const handleExportPack = useCallback(
    async (opts: PackExportFormValues) => {
      const nsErr = validateNamespace(opts.namespace);
      if (nsErr) {
        alert(nsErr);
        return;
      }
      const parts: PackParts = opts.parts ?? {
        meta: true,
        theme: true,
        textures: true,
        content: opts.includeBook !== false,
        lang: true,
        fonts: true,
      };
      const resolved = resolvePackParts(parts);
      try {
        if (resolved.fonts && customFonts.length === 0 && packExportMode === 'fonts') {
          alert('尚未导入自定义字体');
          return;
        }
        const packFiles = await buildResourcePack({
          namespace: opts.namespace,
          themeId: opts.themeId,
          bookId: opts.bookId,
          includeBook: resolved.content,
          packFormat: opts.packFormat,
          parts: resolved,
          theme: resolved.theme ? theme : undefined,
          content: resolved.content ? content : undefined,
          customBookPng: resolved.textures ? customTextures.book?.bytes ?? null : null,
          defaultBookUrl: resolved.textures
            ? new URL('../../assets/textures/gui/book.png', import.meta.url).toString()
            : undefined,
          langTables: resolved.lang ? langTables : undefined,
          customFonts: resolved.fonts ? customFonts.map(toExportFont) : undefined,
          rewriteFontsToPackNs: true,
        });

        const suffix = packPartsSuffix(resolved);
        const packRootName = `${opts.namespace}_${suffix}`;

        const api = window.electronAPI;
        if (api?.openDirectory && api?.writePack) {
          const dirResult = await api.openDirectory({
            properties: ['openDirectory', 'createDirectory'],
          });
          if (dirResult?.canceled) return;
          const parentDir = dirResult?.filePaths?.[0];
          if (!parentDir) return;

          const sep = parentDir.includes('\\') && !parentDir.includes('/') ? '\\' : '/';
          const dir =
            parentDir.endsWith(sep + packRootName) || parentDir.endsWith(packRootName)
              ? parentDir
              : parentDir.replace(/[/\\]+$/, '') + sep + packRootName;

          const payloadFiles = packFiles.map((f) => ({
            path: f.path,
            base64: uint8ArrayToBase64(f.data),
          }));

          const res = await api.writePack({ dir, files: payloadFiles });
          if (res?.ok) {
            await api.showMessage?.({
              type: 'info',
              title: '导出成功',
              message: `资源包已写入：\n${res.root}\n\n共 ${packFiles.length} 个文件`,
            });
          } else {
            alert('写入失败：' + (res?.error || '未知错误'));
          }
        } else {
          const zipU8 = packFilesToZip(packFiles);
          const blob = new Blob([zipU8.slice()], { type: 'application/zip' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `${packRootName}.zip`;
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(url);
          alert(`已下载 ${packRootName}.zip（${packFiles.length} 文件）。`);
        }
      } catch (e: any) {
        console.error('[FlexiBook] pack export failed', e);
        alert('导出资源包失败：' + (e?.message || e));
      }
    },
    [theme, content, customTextures, langTables, customFonts, packExportMode],
  );

  const buildCustomFontEntry = useCallback(
    async (exp: CustomFontExport, existingIds: Set<string>): Promise<CustomFont> => {
      let id = exp.id;
      if (existingIds.has(id)) {
        let n = 2;
        while (existingIds.has(`${id}_${n}`)) n++;
        id = `${id}_${n}`;
      }
      existingIds.add(id);
      const family = nextFontFamily();
      const bytes = copyArrayBuffer(exp.bytes);
      const objectUrl = URL.createObjectURL(new Blob([bytes]));
      const entry: CustomFont = {
        id,
        fileName: exp.fileName,
        ext: exp.ext,
        bytes,
        family,
        objectUrl,
        status: 'loading',
        size: exp.size ?? DEFAULT_TTF_SIZE,
        oversample: exp.oversample ?? DEFAULT_TTF_OVERSAMPLE,
        shiftX: exp.shiftX ?? 0,
        shiftY: exp.shiftY ?? 0,
      };
      void BrowserFont.loadFromBytes(bytes.slice(0), family, exp.ext)
        .then((bf) => {
          browserFontsRef.current.set(id, bf);
          setCustomFonts((prev) =>
            prev.map((f) => (f.id === id ? { ...f, status: 'ready' as const } : f)),
          );
          setBrowserFontRev((v) => v + 1);
          clearLayoutCache();
        })
        .catch((err: Error) => {
          setCustomFonts((prev) =>
            prev.map((f) =>
              f.id === id
                ? { ...f, status: 'error' as const, error: err?.message || String(err) }
                : f,
            ),
          );
        });
      return entry;
    },
    [],
  );

  const applyImportedFonts = useCallback(
    async (exports: CustomFontExport[], mode: 'merge' | 'replace') => {
      if (mode === 'replace') {
        for (const bf of browserFontsRef.current.values()) {
          try {
            bf.unload();
          } catch {
            /* ignore */
          }
        }
        browserFontsRef.current.clear();
        revokeAllCustomFonts(customFonts);
        const ids = new Set<string>();
        const next: CustomFont[] = [];
        for (const exp of exports) {
          next.push(await buildCustomFontEntry(exp, ids));
        }
        setCustomFonts(next);
        setBrowserFontRev((v) => v + 1);
        return;
      }
      // merge by id — overwrite matching ids
      const keep = customFonts.filter((f) => !exports.some((e) => e.id === f.id));
      for (const f of customFonts) {
        if (exports.some((e) => e.id === f.id)) {
          const bf = browserFontsRef.current.get(f.id);
          if (bf) {
            try {
              bf.unload();
            } catch {
              /* ignore */
            }
            browserFontsRef.current.delete(f.id);
          }
          revokeAllCustomFonts([f]);
        }
      }
      const ids = new Set(keep.map((f) => f.id));
      const additions: CustomFont[] = [];
      for (const exp of exports) {
        additions.push(await buildCustomFontEntry(exp, ids));
      }
      setCustomFonts([...keep, ...additions]);
      setBrowserFontRev((v) => v + 1);
    },
    [buildCustomFontEntry, customFonts],
  );

  const applyPackImport = useCallback(
    async (result: PackImportResult) => {
      if (result.namespace) setPackNamespace(result.namespace);
      if (result.themeId) setPackThemeId(result.themeId);
      if (result.bookId) setPackBookId(result.bookId);

      if (result.theme) {
        setTheme({ ...result.theme, revision: (result.theme.revision || 0) + 1 });
      }
      if (result.content) {
        setContent(result.content);
      }

      if (Object.keys(result.langTables).length) {
        setLangTables((prev) => {
          const next = mergeLangTables(prev, result.langTables);
          saveLangTablesToStorage(next);
          return next;
        });
      }

      if (result.fonts.length) {
        await applyImportedFonts(
          result.fonts.map((f) => ({
            id: f.id,
            fileName: f.fileName,
            ext: f.ext,
            bytes: f.bytes,
            size: f.size,
            oversample: f.oversample,
            shiftX: f.shiftX,
            shiftY: f.shiftY,
          })),
          'merge',
        );
      }

      if (result.textures.book) {
        try {
          const book = await loadImageFromBytes(result.textures.book, 'book.png');
          setCustomTextures((prev) => {
            revokeCustomTexture(prev.book);
            return { book };
          });
        } catch (e) {
          console.warn('book texture import failed', e);
        }
      }

      clearLayoutCache();
      setLayoutForceRev((v) => v + 1);
      setPageIndex(0);
    },
    [applyImportedFonts],
  );

  const handleImportPack = useCallback(async () => {
    setPackImportBusy(true);
    try {
      const api = window.electronAPI;
      let files: PackImportFile[] | null = null;
      let sourceLabel = '';

      if (api?.openFile && api?.readBinaryFile && api?.openDirectory && api?.readPack) {
        const choice = await api.showMessage?.({
          type: 'question',
          title: '导入资源包',
          message: '选择导入方式',
          detail: 'ZIP 文件，或资源包根目录（含 pack.mcmeta / assets/）。',
          buttons: ['ZIP 文件…', '文件夹…', '取消'],
          cancelId: 2,
          defaultId: 0,
        });
        const idx = typeof choice?.response === 'number' ? choice.response : 2;
        if (idx === 2) return;
        if (idx === 0) {
          const result = await api.openFile({
            filters: [
              { name: 'Resource pack ZIP', extensions: ['zip'] },
              { name: 'All Files', extensions: ['*'] },
            ],
            properties: ['openFile'],
          });
          if (result?.canceled) return;
          const filePath = result?.filePaths?.[0];
          if (!filePath) return;
          const read = await api.readBinaryFile(filePath);
          if (!read?.ok || !read.base64) {
            alert(read?.error || '读取 ZIP 失败');
            return;
          }
          const u8 = base64ToUint8Array(read.base64);
          const imported = importPackFromZip(u8);
          await applyPackImport(imported);
          sourceLabel = filePath;
          const summary = summarizeImport(imported);
          await api.showMessage?.({
            type: 'info',
            title: '导入完成',
            message: summary + (imported.warnings.length ? `\n\n警告:\n- ${imported.warnings.join('\n- ')}` : ''),
          });
          return;
        }
        // folder
        const dirResult = await api.openDirectory({
          properties: ['openDirectory'],
        });
        if (dirResult?.canceled) return;
        const dir = dirResult?.filePaths?.[0];
        if (!dir) return;
        const pack = await api.readPack({ dir });
        if (!pack?.ok || !pack.files) {
          alert(pack?.error || '读取目录失败');
          return;
        }
        files = pack.files.map((f) => ({
          path: f.path,
          data: base64ToUint8Array(f.base64),
        }));
        sourceLabel = dir;
      } else {
        // Browser: zip only
        const file = await new Promise<File | null>((resolve) => {
          const input = document.createElement('input');
          input.type = 'file';
          input.accept = '.zip,application/zip';
          input.onchange = () => resolve(input.files?.[0] || null);
          input.click();
        });
        if (!file) return;
        const buf = new Uint8Array(await file.arrayBuffer());
        const imported = importPackFromZip(buf);
        await applyPackImport(imported);
        sourceLabel = file.name;
        alert(summarizeImport(imported) + (imported.warnings.length ? `\n\n警告:\n- ${imported.warnings.join('\n- ')}` : ''));
        return;
      }

      if (files) {
        const imported = parsePackFiles(files);
        await applyPackImport(imported);
        const summary = summarizeImport(imported) + `\n来源: ${sourceLabel}`;
        if (api?.showMessage) {
          await api.showMessage({
            type: 'info',
            title: '导入完成',
            message:
              summary + (imported.warnings.length ? `\n\n警告:\n- ${imported.warnings.join('\n- ')}` : ''),
          });
        } else {
          alert(summary);
        }
      }
    } catch (e: any) {
      console.error('[FlexiBook] pack import failed', e);
      alert('导入失败：' + (e?.message || e));
    } finally {
      setPackImportBusy(false);
    }
  }, [applyPackImport]);

  const handleClearDraft = useCallback(async () => {
    if (!confirm('清除本地草稿缓存？（不会删除已导出的资源包）')) return;
    draftAutosaveRef.current.cancel();
    await clearWorkspaceDraft();
    setDraftSavedAt(null);
    setDraftStatus('ready');
    alert('草稿已清除。刷新后将使用默认 demo + 内置语言表。');
  }, []);

  const changePage = useCallback(
    (delta: number) => {
      setPageIndex((i) => Math.max(0, Math.min((pages.length - 1) || 0, i + delta)));
    },
    [pages.length],
  );

  const onPrev = useCallback(() => changePage(-1), [changePage]);
  const onNext = useCallback(() => changePage(1), [changePage]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      if (e.key === 'ArrowLeft' || e.key.toLowerCase() === 'a') changePage(-1);
      if (e.key === 'ArrowRight' || e.key.toLowerCase() === 'd') changePage(1);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [changePage]);

  const pageCount = Math.max(1, pages.length);
  const pageLabel = pages.length === 0 ? '0 / 1' : `${pageIndex + 1} / ${pageCount}`;
  // Mirror AdaptiveBookScreen: Component.translatable("flexibook.screen.page", page + 1, max(1, size)).
  const canvasPageLabel = translator
    ? translator.get('flexibook.screen.page', pageIndex + 1, pageCount)
    : `Page ${pageIndex + 1} / ${pageCount}`;

  const canvasTitle = (() => {
    const t = content?.title;
    if (!t || !t.key) return '';
    if (translator && t.key && t.key.indexOf('.') > 0) {
      try { return translator.get(t.key, ...(t.args || [])); } catch { return t.key; }
    }
    return t.key || '';
  })();

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="brand-mark" aria-hidden>
            F
          </div>
          <div>
            <div className="title">FlexiBook Editor</div>
          </div>
          <span className="subtitle">
            {workspaceMode === 'preview' ? 'layout parity preview' : 'content edit · translations'}
          </span>
        </div>

        <div className="mode-toggle" role="group" aria-label="Workspace mode">
          <button
            type="button"
            className={workspaceMode === 'preview' ? 'active' : ''}
            onClick={() => setWorkspaceMode('preview')}
            title="书籍预览"
          >
            预览
          </button>
          <button
            type="button"
            className={workspaceMode === 'content-edit' ? 'active' : ''}
            onClick={() => {
              setWorkspaceMode('content-edit');
              setLeftTab('lang');
            }}
            title="大编辑器编写翻译键值（不显示书预览）"
          >
            内容编辑
          </button>
        </div>

        <div className="lang-chip-row topbar-langs" title="切换语言（译文已实时缓存，不会因切换丢失）">
          {listLangCodes(langTables).map((code) => (
            <button
              key={code}
              type="button"
              className={`chip-btn ${lang === code ? 'active' : ''}`}
              onClick={() => handleActiveLangChange(code)}
            >
              {code}
            </button>
          ))}
        </div>

        {workspaceMode === 'preview' && (
          <input
            className="search"
            type="search"
            placeholder="搜索内容…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPageIndex(0);
            }}
          />
        )}

        <div className="spacer" />

        <button
          type="button"
          disabled={packImportBusy}
          onClick={() => void handleImportPack()}
          title="从 ZIP 或资源包目录导入主题/正文/翻译/字体/纹理"
        >
          {packImportBusy ? '导入中…' : '导入资源包…'}
        </button>

        <button
          type="button"
          className="primary topbar-pack-export"
          onClick={() => openPackExport('full')}
          title="导出完整 Minecraft 资源包（主题/书/翻译/字体/纹理）"
        >
          导出完整资源包…
        </button>

        <button
          type="button"
          className="ghost"
          onClick={() => void handleClearDraft()}
          title="清除 IndexedDB 工作区草稿"
        >
          清草稿
        </button>

        {workspaceMode === 'preview' && (
          <div className="chip" title="当前页码">
            Page <strong>{pageLabel}</strong>
          </div>
        )}
      </header>

      <div className="split">
        <aside className="left">
          <div className="tabs" role="tablist">
            {(
              [
                ['theme', 'Theme'],
                ['content', 'Content'],
                ['lang', 'Lang'],
                ['fonts', 'Fonts'],
              ] as const
            ).map(([id, label]) => (
              <button
                key={id}
                type="button"
                role="tab"
                className={`tab ${leftTab === id ? 'active' : ''}`}
                aria-selected={leftTab === id}
                onClick={() => setLeftTab(id)}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="panel-body">
            {leftTab === 'theme' && (
              <ThemePanel
                theme={theme}
                onChange={setTheme}
                customTextures={customTextures}
                onCustomTexture={handleCustomTexture}
                onResetDefault={() => {
                  clearCustomTextures();
                  setTheme(DEFAULT_THEME);
                }}
                onLoadContain={() => {
                  clearCustomTextures();
                  setTheme(CONTAIN_THEME);
                }}
                onExport={() => downloadJson('default.json', themeToWire(theme))}
                onLoad={handleLoadTheme}
                onSave={handleSaveTheme}
                onExportThemePack={() => openPackExport('theme')}
                onExportTexturesPack={() => openPackExport('textures')}
              />
            )}
            {leftTab === 'content' && (
              <ContentPanel
                content={content}
                onChange={setContent}
                lang={lang}
                onResetDemo={() => setContent(DEMO_GUIDE_CONTENT)}
                onExport={() => downloadJson('demo_guide.json', bookContentToWire(content))}
                onExportContentPack={() => openPackExport('content')}
                onLoad={handleLoadContent}
                onSave={handleSaveContent}
                fontIds={[FLEXIBOOK_DEFAULT_FONT, ...customFonts.map((f) => f.id)]}
                langTables={langTables}
                onEnsureLangKey={ensureLangKey}
              />
            )}
            {leftTab === 'lang' && (
              <LangPanel
                tables={langTables}
                onChange={handleLangTablesChange}
                activeLang={lang}
                onActiveLangChange={handleActiveLangChange}
                selectedKey={selectedLangKey}
                onSelectedKeyChange={setSelectedLangKey}
                onExportLangPack={() => openPackExport('lang')}
              />
            )}
            {leftTab === 'fonts' && (
              <FontPanel
                fonts={customFonts}
                onChange={handleCustomFontsChange}
                onImportFiles={handleImportFontFiles}
                content={content}
                onContentChange={setContent}
                busy={fontImportBusy}
                onExportFontsPack={() => openPackExport('fonts')}
              />
            )}
          </div>
        </aside>

        <section className={`preview ${workspaceMode === 'content-edit' ? 'workspace-edit' : ''}`}>
          {workspaceMode === 'content-edit' ? (
            <TranslationValueWorkspace
              tables={langTables}
              onChange={handleLangTablesChange}
              activeLang={lang}
              onActiveLangChange={handleActiveLangChange}
              selectedKey={selectedLangKey}
              onSelectedKeyChange={setSelectedLangKey}
            />
          ) : (
            <>
              <div className="preview-header">
                <div className="label">Preview</div>
                <div className="scale" role="group" aria-label="Visual scale">
                  {([1, 2, 3, 4] as const).map((s) => (
                    <button
                      key={s}
                      type="button"
                      className={scale === s ? 'active' : ''}
                      onClick={() => setScale(s)}
                      title={`视觉缩放 ${s}x（不影响布局）`}
                    >
                      {s}x
                    </button>
                  ))}
                </div>
                <button
                  type="button"
                  className="relayout-btn"
                  onClick={handleRelayout}
                  title="清空布局缓存并强制重新布局"
                >
                  重新布局
                </button>
                <div className="page-meta">Page {pageLabel}</div>
                <div className="preview-nav">
                  <button type="button" className="icon" onClick={onPrev} disabled={pageIndex <= 0} title="上一页">
                    ◀
                  </button>
                  <button
                    type="button"
                    className="icon"
                    onClick={onNext}
                    disabled={pageIndex >= pages.length - 1}
                    title="下一页"
                  >
                    ▶
                  </button>
                </div>
              </div>

              {unregisteredFonts.length > 0 && (
                <div className="banner banner-warn preview-font-banner" role="status">
                  外部字体未导入，预览回退 unihex：
                  <span className="mono"> {unregisteredFonts.join(', ')}</span>
                </div>
              )}
              {customFonts.some((f) => f.status === 'ready') && unregisteredFonts.length === 0 && (
                <div className="banner preview-font-banner banner-info" role="status">
                  自定义 TTF 预览为浏览器近似，与游戏 advance 可能不一致
                  {approxCustomFonts.length ? (
                    <span className="mono"> · default {approxCustomFonts.join(', ')}</span>
                  ) : null}
                </div>
              )}

              <div className="preview-area">
                <PreviewCanvas
                  key={`book-${bookEpoch}-r-${layoutForceRev}`}
                  pages={pages}
                  pageIndex={pageIndex}
                  theme={theme}
                  scale={scale}
                  bookImg={bookImgRef.current}
                  bookReady={bookReady}
                  atlasMeasurer={fontRouter}
                  fontRev={fontAtlasRev + browserFontRev + layoutForceRev}
                  pageLabel={canvasPageLabel}
                  titleLabel={canvasTitle}
                  onPrev={onPrev}
                  onNext={onNext}
                />
              </div>
            </>
          )}
        </section>
      </div>

      <PackExportModal
        open={packExportOpen}
        mode={packExportMode}
        initial={{
          namespace: packNamespace,
          themeId: packThemeId,
          bookId: packBookId,
        }}
        onClose={() => setPackExportOpen(false)}
        onExport={(opts) => {
          setPackNamespace(opts.namespace);
          setPackThemeId(opts.themeId);
          setPackBookId(opts.bookId);
          void handleExportPack(opts);
        }}
      />

      <footer className="status">
        <span
          className={`dot ${
            fontReady ? (unregisteredFonts.length ? 'warn' : 'ok') : 'warn'
          }`}
        />
        <span>
          Font:{' '}
          {fontReady
            ? unregisteredFonts.length
              ? `unihex (fallback · ${unregisteredFonts.length} missing)`
              : customFonts.some((f) => f.status === 'ready')
                ? 'unihex + custom TTF (approx)'
                : 'flexibook unihex'
            : 'loading'}
        </span>
        <span className="sep" />
        <span>
          Draft:{' '}
          {draftStatus === 'loading'
            ? '…'
            : draftSavedAt
              ? `saved ${new Date(draftSavedAt).toLocaleTimeString()}`
              : draftStatus === 'error'
                ? 'error'
                : '—'}
        </span>
        <span className="sep" />
        <span className="mono" title="export defaults">
          {packNamespace}:{packBookId}
        </span>
        <span className="sep" />
        <span>Lang: {langTablesReady ? `${Object.keys(langTables).length}L / ${Object.values(langTables).reduce((n, t) => Math.max(n, Object.keys(t || {}).length), 0)} keys` : '…'}</span>
        <span className="sep" />
        <span>Mode: {workspaceMode === 'preview' ? '预览' : '内容编辑'}</span>
        <span className="sep" />
        <span>
          BG: {customTextures.book ? customTextures.book.fileName : bookReady ? 'theme' : 'missing'}
        </span>
        <span className="sep" />
        <span className="grow">
          {workspaceMode === 'content-edit'
            ? '内容编辑：大编辑器写译文 · 切换语言实时缓存 · 不丢数据'
            : '预览：搜索触发布局 · Scale 仅视觉 · 「重新布局」清缓存'}
          {window.electronAPI ? ' · 原生打开/保存' : ' · 浏览器下载'}
        </span>
      </footer>
    </div>
  );
}
