import { useEffect, useMemo, useState } from 'react';
import { useT } from './UiI18n';
import { MarkupEditor } from './MarkupEditor';
import type { LangCode, LangTables } from '../shared/langTables';
import {
  allKeys,
  ensureKeyInAllLangs,
  ensureLang,
  listLangCodes,
  missingLangsForKey,
  normalizeLangCode,
  removeKeyFromAll,
  setLangValue,
} from '../shared/langTables';

export type { LangCode, LangTables } from '../shared/langTables';

export interface LangPanelProps {
  tables: LangTables;
  onChange: (next: LangTables) => void;
  /** Currently active language (shared with workspace / preview) */
  activeLang: LangCode;
  onActiveLangChange: (lang: LangCode) => void;
  /** Selected translation key for the big value editor */
  selectedKey: string | null;
  onSelectedKeyChange: (key: string | null) => void;
  /** Partial pack: lang/*.json only */
  onExportLangPack?: () => void;
}

export function LangPanel({
  tables,
  onChange,
  activeLang,
  onActiveLangChange,
  selectedKey,
  onSelectedKeyChange,
  onExportLangPack,
}: LangPanelProps) {
  const t = useT();
  const [filter, setFilter] = useState('');
  const [newKeyDraft, setNewKeyDraft] = useState('');
  const [newLangDraft, setNewLangDraft] = useState('');

  const langs = useMemo(() => listLangCodes(tables), [tables]);
  const keys = useMemo(() => allKeys(tables), [tables]);

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return keys;
    return keys.filter((k) => {
      if (k.toLowerCase().includes(q)) return true;
      for (const code of langs) {
        const v = tables[code]?.[k];
        if (v && v.toLowerCase().includes(q)) return true;
      }
      return false;
    });
  }, [keys, filter, tables, langs]);

  function addKey(key: string) {
    const k = key.trim();
    if (!k) return;
    onChange(ensureKeyInAllLangs(tables, k));
    onSelectedKeyChange(k);
    setNewKeyDraft('');
  }

  function removeKey(key: string) {
    onChange(removeKeyFromAll(tables, key));
    if (selectedKey === key) onSelectedKeyChange(null);
  }

  function addLang() {
    const code = normalizeLangCode(newLangDraft);
    if (!code) {
      alert(t('lang.badCode'));
      return;
    }
    if (tables[code]) {
      onActiveLangChange(code);
      setNewLangDraft('');
      return;
    }
    // seed empty table; copy keys from union so rows stay aligned
    let next = ensureLang(tables, code);
    for (const k of allKeys(tables)) {
      next = setLangValue(next, code, k, next[code]?.[k] ?? '');
    }
    onChange(next);
    onActiveLangChange(code);
    setNewLangDraft('');
  }

  function removeLang(code: LangCode) {
    if (langs.length <= 1) {
      alert(t('lang.keepOne'));
      return;
    }
    if (!confirm(t('lang.deleteConfirm', { code }))) return;
    const next = { ...tables };
    delete next[code];
    onChange(next);
    if (activeLang === code) {
      onActiveLangChange(listLangCodes(next)[0] || 'en_us');
    }
  }

  return (
    <div>
      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('lang.section.tables')}</h4>
          <span className="hint">
            {t('lang.stats', { keys: keys.length, langs: langs.length })}
          </span>
        </div>
        <p className="section-hint">
          {t('lang.tablesHelp')}
          {t('lang.exportHelp')}
        </p>
        {onExportLangPack && (
          <div className="toolbar" style={{ marginTop: 8 }}>
            <button type="button" className="primary" onClick={onExportLangPack}>
              {t('lang.exportPack')}
            </button>
          </div>
        )}

        <div className="section-head" style={{ marginTop: 8 }}>
          <h4 className="section-title">{t('lang.section.langs')}</h4>
        </div>
        <div className="lang-chip-row">
          {langs.map((l) => (
            <button
              key={l}
              type="button"
              className={`chip-btn ${activeLang === l ? 'active' : ''}`}
              onClick={() => onActiveLangChange(l)}
            >
              {l}
            </button>
          ))}
        </div>
        <div className="toolbar" style={{ marginTop: 8 }}>
          <input
            type="text"
            className="mono"
            style={{ flex: 1, minWidth: 0 }}
            placeholder={t('lang.addPlaceholder')}
            value={newLangDraft}
            onChange={(e) => setNewLangDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') addLang();
            }}
          />
          <button type="button" onClick={addLang} disabled={!newLangDraft.trim()}>
            {t('lang.addLang')}
          </button>
          {langs.length > 1 && (
            <button type="button" className="danger" onClick={() => removeLang(activeLang)} title={t('lang.removeTitle')}>
              {t('lang.removeLang')}
            </button>
          )}
        </div>
      </div>

      <div className="section">
        <div className="toolbar">
          <input
            type="search"
            className="search"
            style={{ flex: 1, minWidth: 0 }}
            placeholder={t('lang.filterPlaceholder')}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <div className="toolbar">
          <input
            type="text"
            className="mono"
            style={{ flex: 1, minWidth: 0 }}
            placeholder="new.translation.key"
            value={newKeyDraft}
            onChange={(e) => setNewKeyDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') addKey(newKeyDraft);
            }}
          />
          <button type="button" onClick={() => addKey(newKeyDraft)} disabled={!newKeyDraft.trim()}>
            {t('lang.addKey')}
          </button>
        </div>
      </div>

      <div className="section">
        <div className="lang-key-list">
          {filtered.length === 0 && <div className="empty-state">{t('lang.noMatch')}</div>}
          {filtered.map((key) => {
            const val = tables[activeLang]?.[key] ?? '';
            const active = selectedKey === key;
            const miss = missingLangsForKey(tables, key);
            return (
              <div
                key={key}
                className={`lang-key-row ${active ? 'active' : ''}`}
                onClick={() => onSelectedKeyChange(key)}
              >
                <div className="lang-key-meta">
                  <span className="mono lang-key-name">{key}</span>
                  {miss.length > 0 && (
                    <span className="badge warn-badge" title={miss.join(', ')}>
                      {t('lang.missing', { n: miss.length })}
                    </span>
                  )}
                </div>
                <div className="lang-key-val muted" title={val}>
                  {val || <em className="muted">{t('lang.emptyValue')}</em>}
                </div>
                <div className="lang-key-actions" onClick={(e) => e.stopPropagation()}>
                  <button type="button" className="danger" onClick={() => removeKey(key)}>
                    {t('lang.deleteShort')}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/** Compact key picker modal for ContentPanel. */
export function LangKeyPickerModal({
  tables,
  onPick,
  onCreateAndPick,
  onClose,
}: {
  tables: LangTables;
  onPick: (key: string) => void;
  onCreateAndPick: (key: string) => void;
  onClose: () => void;
}) {
  const t = useT();
  const [filter, setFilter] = useState('');
  const [draft, setDraft] = useState('');
  const keys = useMemo(() => {
    const all = allKeys(tables);
    const q = filter.trim().toLowerCase();
    if (!q) return all;
    return all.filter((k) => k.toLowerCase().includes(q));
  }, [tables, filter]);

  const langs = useMemo(() => listLangCodes(tables), [tables]);
  const previewLang = langs[0] || 'en_us';

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal-panel" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <strong>{t('content.pickKey')}</strong>
          <button type="button" onClick={onClose}>
            {t('pack.close')}
          </button>
        </div>
        <input
          type="search"
          placeholder={t('lang.searchPlaceholder')}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          autoFocus
          style={{ width: '100%', marginBottom: 8 }}
        />
        <div className="lang-key-list" style={{ maxHeight: 280 }}>
          {keys.map((k) => (
            <button
              key={k}
              type="button"
              className="lang-pick-row"
              onClick={() => {
                onPick(k);
                onClose();
              }}
            >
              <span className="mono">{k}</span>
              <span className="muted" style={{ fontSize: 11 }}>
                {tables[previewLang]?.[k] || tables[langs[1] || '']?.[k] || ''}
              </span>
            </button>
          ))}
        </div>
        <div className="toolbar" style={{ marginTop: 10 }}>
          <input
            type="text"
            className="mono"
            style={{ flex: 1 }}
            placeholder={t('lang.newKeyPlaceholder')}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
          />
          <button
            type="button"
            className="primary"
            disabled={!draft.trim()}
            onClick={() => {
              onCreateAndPick(draft.trim());
              onClose();
            }}
          >
            {t('lang.createAndUse')}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Large right-pane translation value editor (content-edit workspace mode).
 * Writes through on every keystroke; language switches never drop drafts.
 */
export function TranslationValueWorkspace({
  tables,
  onChange,
  activeLang,
  onActiveLangChange,
  selectedKey,
  onSelectedKeyChange,
}: {
  tables: LangTables;
  onChange: (next: LangTables) => void;
  activeLang: LangCode;
  onActiveLangChange: (lang: LangCode) => void;
  selectedKey: string | null;
  onSelectedKeyChange: (key: string | null) => void;
}) {
  const t = useT();
  const langs = useMemo(() => listLangCodes(tables), [tables]);
  const keys = useMemo(() => allKeys(tables), [tables]);
  const value = selectedKey ? tables[activeLang]?.[selectedKey] ?? '' : '';

  function writeValue(nextVal: string) {
    if (!selectedKey) return;
    onChange(setLangValue(tables, activeLang, selectedKey, nextVal));
  }

  function switchLang(code: LangCode) {
    // tables already hold the latest value (onChange on every keystroke)
    onActiveLangChange(code);
  }

  const miss = selectedKey ? missingLangsForKey(tables, selectedKey) : [];

  return (
    <div className="translate-workspace">
      <div className="translate-workspace-toolbar">
        <div className="translate-workspace-title">
          <span className="label">{t('lang.workspaceTitle')}</span>
          {selectedKey ? (
            <span className="mono translate-key">{selectedKey}</span>
          ) : (
            <span className="muted">{t('lang.workspacePickKey')}</span>
          )}
        </div>
        <div className="lang-chip-row">
          {langs.map((l) => (
            <button
              key={l}
              type="button"
              className={`chip-btn ${activeLang === l ? 'active' : ''}`}
              onClick={() => switchLang(l)}
              title={t('lang.editLangTitle', { lang: l })}
            >
              {l}
              {selectedKey && (tables[l]?.[selectedKey] ?? '') === '' ? ' ·' : ''}
            </button>
          ))}
        </div>
      </div>

      {selectedKey && miss.length > 0 && (
        <div className="banner banner-warn" style={{ margin: '0 0 8px' }}>
          {t('lang.missingInLangs')} <span className="mono">{miss.join(', ')}</span>
        </div>
      )}

      {selectedKey ? (
        <div className="translate-workspace-editor">
          <MarkupEditor
            fill
            autoFocus
            value={value}
            onChange={writeValue}
            placeholder={t('lang.workspacePlaceholder', { lang: activeLang })}
          />
        </div>
      ) : (
        <div className="empty-state translate-workspace-empty">
          {t('lang.workspaceEmpty')}
          <div className="hint" style={{ marginTop: 8 }}>
            {t('lang.workspaceStats', { n: keys.length })} <span className="mono">{activeLang}</span>
          </div>
        </div>
      )}

      {selectedKey && keys.length > 0 && (
        <div className="translate-workspace-footer">
          <button
            type="button"
            disabled={keys.indexOf(selectedKey) <= 0}
            onClick={() => {
              const i = keys.indexOf(selectedKey);
              if (i > 0) onSelectedKeyChange(keys[i - 1]!);
            }}
          >
            {t('lang.prevKey')}
          </button>
          <span className="muted mono" style={{ fontSize: 11 }}>
            {keys.indexOf(selectedKey) + 1} / {keys.length}
          </span>
          <button
            type="button"
            disabled={keys.indexOf(selectedKey) >= keys.length - 1}
            onClick={() => {
              const i = keys.indexOf(selectedKey);
              if (i >= 0 && i < keys.length - 1) onSelectedKeyChange(keys[i + 1]!);
            }}
          >
            {t('lang.nextKey')}
          </button>
        </div>
      )}
    </div>
  );
}
