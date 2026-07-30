/**
 * Persist unexported editor workspace (theme/content/lang/textures/fonts) in IndexedDB.
 * localStorage is too small for PNG/TTF; IDB holds binary drafts across reloads.
 */

import type { AdaptiveBookContent, BookTheme } from './types';
import type { LangTables, LangCode } from './langTables';
import type { CustomFontExport } from './customFonts';

export const WORKSPACE_DB_NAME = 'flexibook-editor';
export const WORKSPACE_DB_VERSION = 1;
export const WORKSPACE_STORE = 'workspace';
export const WORKSPACE_KEY = 'draft';
export const WORKSPACE_META_LS_KEY = 'flexibook.editor.workspaceMeta.v1';

export interface WorkspaceDraftV1 {
  v: 1;
  savedAt: number;
  lang: LangCode;
  leftTab?: 'theme' | 'content' | 'lang' | 'fonts';
  workspaceMode?: 'preview' | 'content-edit';
  selectedLangKey?: string | null;
  packNamespace?: string;
  packThemeId?: string;
  packBookId?: string;
  theme: BookTheme;
  content: AdaptiveBookContent;
  langTables: LangTables;
  /** PNG bytes (optional) */
  textures: {
    book: ArrayBuffer | null;
    widgets: ArrayBuffer | null;
    bookFileName?: string;
    widgetsFileName?: string;
  };
  fonts: CustomFontExport[];
}

export interface WorkspaceMeta {
  savedAt: number;
  namespace?: string;
  bookId?: string;
  themeId?: string;
  hasFonts: boolean;
  hasTextures: boolean;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('indexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(WORKSPACE_DB_NAME, WORKSPACE_DB_VERSION);
    req.onerror = () => reject(req.error || new Error('IDB open failed'));
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(WORKSPACE_STORE)) {
        db.createObjectStore(WORKSPACE_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
  });
}

function idbReq<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error('IDB request failed'));
  });
}

export async function saveWorkspaceDraft(draft: WorkspaceDraftV1): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(WORKSPACE_STORE, 'readwrite');
    const store = tx.objectStore(WORKSPACE_STORE);
    await idbReq(store.put(draft, WORKSPACE_KEY));
    await new Promise<void>((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error('IDB tx failed'));
      tx.onabort = () => reject(tx.error || new Error('IDB tx aborted'));
    });
    writeWorkspaceMeta({
      savedAt: draft.savedAt,
      namespace: draft.packNamespace,
      bookId: draft.packBookId,
      themeId: draft.packThemeId,
      hasFonts: (draft.fonts?.length || 0) > 0,
      hasTextures: !!(draft.textures?.book || draft.textures?.widgets),
    });
  } finally {
    db.close();
  }
}

export async function loadWorkspaceDraft(): Promise<WorkspaceDraftV1 | null> {
  try {
    const db = await openDb();
    try {
      const tx = db.transaction(WORKSPACE_STORE, 'readonly');
      const store = tx.objectStore(WORKSPACE_STORE);
      const val = await idbReq(store.get(WORKSPACE_KEY));
      if (!val || typeof val !== 'object') return null;
      const d = val as WorkspaceDraftV1;
      if (d.v !== 1) return null;
      if (!d.theme || !d.content || !d.langTables) return null;
      return d;
    } finally {
      db.close();
    }
  } catch {
    return null;
  }
}

export async function clearWorkspaceDraft(): Promise<void> {
  try {
    const db = await openDb();
    try {
      const tx = db.transaction(WORKSPACE_STORE, 'readwrite');
      const store = tx.objectStore(WORKSPACE_STORE);
      await idbReq(store.delete(WORKSPACE_KEY));
      await new Promise<void>((resolve, reject) => {
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error || new Error('IDB tx failed'));
      });
    } finally {
      db.close();
    }
  } catch {
    /* ignore */
  }
  try {
    localStorage.removeItem(WORKSPACE_META_LS_KEY);
  } catch {
    /* ignore */
  }
}

export function writeWorkspaceMeta(meta: WorkspaceMeta): void {
  try {
    localStorage.setItem(WORKSPACE_META_LS_KEY, JSON.stringify(meta));
  } catch {
    /* ignore */
  }
}

export function readWorkspaceMeta(): WorkspaceMeta | null {
  try {
    const raw = localStorage.getItem(WORKSPACE_META_LS_KEY);
    if (!raw) return null;
    const m = JSON.parse(raw) as WorkspaceMeta;
    if (!m || typeof m.savedAt !== 'number') return null;
    return m;
  } catch {
    return null;
  }
}

/** Debounced saver */
export function createDraftAutosave(
  save: (draft: WorkspaceDraftV1) => Promise<void>,
  delayMs = 800,
): {
  schedule: (factory: () => WorkspaceDraftV1) => void;
  flush: () => Promise<void>;
  cancel: () => void;
} {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let pending: (() => WorkspaceDraftV1) | null = null;
  let chain: Promise<void> = Promise.resolve();

  const run = () => {
    if (!pending) return;
    const factory = pending;
    pending = null;
    chain = chain
      .then(async () => {
        try {
          await save(factory());
        } catch (e) {
          console.warn('[FlexiBook] workspace draft save failed', e);
        }
      })
      .catch(() => undefined);
  };

  return {
    schedule(factory) {
      pending = factory;
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        timer = null;
        run();
      }, delayMs);
    },
    async flush() {
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      run();
      await chain;
    },
    cancel() {
      if (timer) clearTimeout(timer);
      timer = null;
      pending = null;
    },
  };
}
