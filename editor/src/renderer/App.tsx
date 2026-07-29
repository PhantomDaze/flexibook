import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { clearLayoutCache, layout } from '../shared/layout';
import { UnihexFont } from '../shared/UnihexFont';
import { JsonTranslationProvider } from '../shared/JsonTranslationProvider';
import type { AdaptiveBookContent, BookTheme, RenderedPage } from '../shared/types';
import { ThemePanel } from './ThemePanel';
import { ContentPanel } from './ContentPanel';
import { PreviewCanvas } from './PreviewCanvas';
import { DEFAULT_THEME, CONTAIN_THEME, DEMO_GUIDE_CONTENT } from './defaults';
import { bookContentToWire, themeToWire } from '../shared/modJson';
import { buildResourcePack, packFilesToZip, validateNamespace } from '../shared/packExport';
import type { CustomTexture, CustomTextures, TextureSlot } from './customTextures';
import {
  EMPTY_CUSTOM_TEXTURES,
  resolveThemeAssetUrl,
  revokeAllCustomTextures,
  revokeCustomTexture,
} from './customTextures';
import './styles.css';

declare global {
  interface Window {
    electronAPI?: {
      openFile?: (options?: any) => Promise<any>;
      saveFile?: (options?: any) => Promise<any>;
      showMessage?: (options: any) => Promise<any>;
      openDirectory?: (options?: any) => Promise<any>;
      writePack?: (payload: { dir: string; files: { path: string; base64: string }[] }) => Promise<{ ok: boolean; root?: string; error?: string }>;
    };
  }
}

async function createTranslator(lang: string): Promise<JsonTranslationProvider> {
  const t = new JsonTranslationProvider();
  // Prefer public root (stable in Vite dev), then module-relative assets.
  const candidates = [
    `/assets/lang/${lang}.json`,
    new URL(`../../assets/lang/${lang}.json`, import.meta.url).toString(),
  ];
  let lastErr: unknown;
  for (const url of candidates) {
    try {
      await t.loadFromUrl(lang, url);
      return t;
    } catch (e) {
      lastErr = e;
    }
  }
  // Last resort: English so the UI still works
  try {
    await t.loadFromUrl('en_us', '/assets/lang/en_us.json');
  } catch {
    /* ignore */
  }
  console.warn('[FlexiBook] failed to load lang', lang, lastErr);
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
  const [lang, setLang] = useState<'en_us' | 'zh_cn'>('en_us');
  const [search, setSearch] = useState('');
  const [scale, setScale] = useState<1 | 2 | 3 | 4>(2);
  const [theme, setTheme] = useState<BookTheme>(DEFAULT_THEME);
  const [content, setContent] = useState<AdaptiveBookContent>(DEMO_GUIDE_CONTENT);
  const [pages, setPages] = useState<RenderedPage[]>([]);
  const [pageIndex, setPageIndex] = useState(0);
  const [leftTab, setLeftTab] = useState<'theme' | 'content'>('theme');
  const [translator, setTranslator] = useState<JsonTranslationProvider | null>(null);
  const [fontAtlasRev, setFontAtlasRev] = useState(0);
  const [fontReady, setFontReady] = useState(false);
  const [customTextures, setCustomTextures] = useState<CustomTextures>(EMPTY_CUSTOM_TEXTURES);

  const [unihex, setUnihex] = useState<UnihexFont | null>(null);
  const measurer = unihex;

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

  useEffect(() => {
    let cancelled = false;
    setTranslator(null); // drop stale translator so layout does not mix lang code + old strings
    clearLayoutCache();
    createTranslator(lang).then((t) => {
      if (!cancelled) setTranslator(t);
    });
    return () => {
      cancelled = true;
    };
  }, [lang]);

  const laidOut = useMemo(() => {
    if (!translator || !measurer || !measurer.isReady()) return [] as RenderedPage[];
    return layout({
      content,
      measurer,
      translator,
      theme,
      languageCode: lang,
      guiScaleRef: 2,
      searchQuery: search,
      fontAtlasRev,
    });
  }, [content, theme, lang, search, translator, measurer, fontAtlasRev]);

  useEffect(() => {
    setPages(laidOut);
    setPageIndex((i) => Math.min(i, Math.max(0, laidOut.length - 1)));
  }, [laidOut]);

  const widgetsImgRef = useRef<HTMLImageElement | null>(null);
  const [widgetsReady, setWidgetsReady] = useState(false);
  const [widgetsEpoch, setWidgetsEpoch] = useState(0);
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

  // Widgets texture
  useEffect(() => {
    let cancelled = false;
    setWidgetsReady(false);
    const src = customTextures.widgets?.url || resolveThemeAssetUrl(theme.widgetsTexture);
    if (!src) {
      widgetsImgRef.current = null;
      return;
    }
    const img = new Image();
    img.onload = () => {
      if (cancelled) return;
      widgetsImgRef.current = img;
      setWidgetsReady(true);
      setWidgetsEpoch((e) => e + 1);
    };
    img.onerror = () => {
      if (cancelled) return;
      widgetsImgRef.current = null;
      setWidgetsReady(false);
      setWidgetsEpoch((e) => e + 1);
    };
    img.src = src;
    return () => {
      cancelled = true;
    };
  }, [theme.widgetsTexture, customTextures.widgets]);

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

  /** Phase D: full resource pack export */
  const handleExportPack = useCallback(
    async (opts: {
      namespace: string;
      themeId: string;
      bookId: string;
      includeBook: boolean;
      packFormat: number;
    }) => {
      const nsErr = validateNamespace(opts.namespace);
      if (nsErr) {
        alert(nsErr);
        return;
      }
      try {
        const packFiles = await buildResourcePack({
          namespace: opts.namespace,
          themeId: opts.themeId,
          bookId: opts.bookId,
          includeBook: opts.includeBook,
          packFormat: opts.packFormat,
          theme,
          content: opts.includeBook ? content : undefined,
          customBookPng: customTextures.book?.bytes ?? null,
          customWidgetsPng: customTextures.widgets?.bytes ?? null,
          defaultBookUrl: new URL('../../assets/textures/gui/book.png', import.meta.url).toString(),
          defaultWidgetsUrl: new URL('../../assets/textures/gui/book_widgets.png', import.meta.url).toString(),
        });

        const api = window.electronAPI;
        if (api?.openDirectory && api?.writePack) {
          const dirResult = await api.openDirectory({
            properties: ['openDirectory', 'createDirectory'],
          });
          if (dirResult?.canceled) return;
          const parentDir = dirResult?.filePaths?.[0];
          if (!parentDir) return;

          // Nest under {namespace}_pack so choosing Downloads/ doesn't pollute it
          const packRootName = `${opts.namespace}_pack`;
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
              message: `资源包已写入文件夹：\n${res.root}\n\n请查看 HOW_TO_USE.txt`,
            });
          } else {
            alert('写入失败：' + (res?.error || '未知错误'));
          }
        } else {
          // Fallback: browser zip download
          const zipU8 = packFilesToZip(packFiles);
          const blob = new Blob([zipU8.slice()], { type: 'application/zip' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `${opts.namespace}_pack.zip`;
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(url);
          alert('已下载 zip 资源包（解压后放入 resourcepacks 目录）。');
        }
      } catch (e: any) {
        console.error('[FlexiBook] pack export failed', e);
        alert('导出资源包失败：' + (e?.message || e));
      }
    },
    [theme, content, customTextures]
  );

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
          <span className="subtitle">layout parity preview</span>
        </div>

        <button
          type="button"
          className="toggle"
          onClick={() => setLang((l) => (l === 'en_us' ? 'zh_cn' : 'en_us'))}
          title="切换预览语言"
        >
          Lang: {lang}
        </button>

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

        <div className="spacer" />

        <div className="chip" title="当前页码">
          Page <strong>{pageLabel}</strong>
        </div>
      </header>

      <div className="split">
        <aside className="left">
          <div className="tabs" role="tablist">
            <button
              type="button"
              role="tab"
              className={`tab ${leftTab === 'theme' ? 'active' : ''}`}
              aria-selected={leftTab === 'theme'}
              onClick={() => setLeftTab('theme')}
            >
              Theme
            </button>
            <button
              type="button"
              role="tab"
              className={`tab ${leftTab === 'content' ? 'active' : ''}`}
              aria-selected={leftTab === 'content'}
              onClick={() => setLeftTab('content')}
            >
              Content
            </button>
          </div>
          <div className="panel-body">
            {leftTab === 'theme' ? (
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
                onExportPack={handleExportPack}
              />
            ) : (
              <ContentPanel
                content={content}
                onChange={setContent}
                lang={lang}
                onResetDemo={() => setContent(DEMO_GUIDE_CONTENT)}
                onExport={() => downloadJson('demo_guide.json', bookContentToWire(content))}
              />
            )}
          </div>
        </aside>

        <section className="preview">
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

          <div className="preview-area">
            <PreviewCanvas
              key={`book-${bookEpoch}-w-${widgetsEpoch}`}
              pages={pages}
              pageIndex={pageIndex}
              theme={theme}
              scale={scale}
              widgetsImg={widgetsImgRef.current}
              widgetsReady={widgetsReady}
              bookImg={bookImgRef.current}
              bookReady={bookReady}
              atlasMeasurer={unihex}
              fontRev={fontAtlasRev}
              pageLabel={canvasPageLabel}
              titleLabel={canvasTitle}
              onPrev={onPrev}
              onNext={onNext}
            />
          </div>
        </section>
      </div>

      <footer className="status">
        <span className={`dot ${fontReady ? 'ok' : 'warn'}`} />
        <span>Font: {fontReady ? 'flexibook unihex' : 'loading'}</span>
        <span className="sep" />
        <span>Widgets: {widgetsReady ? 'ok' : '…'}</span>
        <span className="sep" />
        <span>
          BG: {customTextures.book ? customTextures.book.fileName : bookReady ? 'theme' : 'missing'}
        </span>
        <span className="sep" />
        <span className="grow">
          搜索影响布局与高亮 · Scale 仅视觉 · 默认字体 flexibook:default（与模组同一 ZIP）
        </span>
      </footer>
    </div>
  );
}
