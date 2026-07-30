import { useEffect, useMemo, useState } from 'react';
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
      alert('语言代码格式：en_us / zh_cn / ja_jp …');
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
      alert('至少保留一种语言');
      return;
    }
    if (!confirm(`删除语言表 ${code}？此操作可从本地缓存恢复前请先导出。`)) return;
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
          <h4 className="section-title">翻译表</h4>
          <span className="hint">
            {keys.length} 键 · {langs.length} 语言 · 实时缓存
          </span>
        </div>
        <p className="section-hint">
          支持多种语言代码（如 en_us / zh_cn / ja_jp）。右侧「内容编辑」模式用大编辑器写当前键的值；切换语言会先写入缓存，不会丢字。
          「导出翻译资源包」只含 <span className="mono">assets/&lt;ns&gt;/lang/*.json</span>；完整包用顶栏。
        </p>
        {onExportLangPack && (
          <div className="toolbar" style={{ marginTop: 8 }}>
            <button type="button" className="primary" onClick={onExportLangPack}>
              导出翻译资源包…
            </button>
          </div>
        )}

        <div className="section-head" style={{ marginTop: 8 }}>
          <h4 className="section-title">语言</h4>
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
            placeholder="添加语言 ja_jp"
            value={newLangDraft}
            onChange={(e) => setNewLangDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') addLang();
            }}
          />
          <button type="button" onClick={addLang} disabled={!newLangDraft.trim()}>
            添加语言
          </button>
          {langs.length > 1 && (
            <button type="button" className="danger" onClick={() => removeLang(activeLang)} title="删除当前语言表">
              删语言
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
            placeholder="过滤键或译文…"
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
            添加键
          </button>
        </div>
      </div>

      <div className="section">
        <div className="lang-key-list">
          {filtered.length === 0 && <div className="empty-state">无匹配键</div>}
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
                      缺 {miss.length}
                    </span>
                  )}
                </div>
                <div className="lang-key-val muted" title={val}>
                  {val || <em className="muted">（空）</em>}
                </div>
                <div className="lang-key-actions" onClick={(e) => e.stopPropagation()}>
                  <button type="button" className="danger" onClick={() => removeKey(key)}>
                    删
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
          <strong>选择翻译键</strong>
          <button type="button" onClick={onClose}>
            关闭
          </button>
        </div>
        <input
          type="search"
          placeholder="搜索…"
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
            placeholder="新建键…"
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
            新建并选用
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
          <span className="label">内容编辑 · 译文</span>
          {selectedKey ? (
            <span className="mono translate-key">{selectedKey}</span>
          ) : (
            <span className="muted">在左侧 Lang 列表选择一个键</span>
          )}
        </div>
        <div className="lang-chip-row">
          {langs.map((l) => (
            <button
              key={l}
              type="button"
              className={`chip-btn ${activeLang === l ? 'active' : ''}`}
              onClick={() => switchLang(l)}
              title={`编辑 ${l}（当前内容已实时缓存）`}
            >
              {l}
              {selectedKey && (tables[l]?.[selectedKey] ?? '') === '' ? ' ·' : ''}
            </button>
          ))}
        </div>
      </div>

      {selectedKey && miss.length > 0 && (
        <div className="banner banner-warn" style={{ margin: '0 0 8px' }}>
          此键在以下语言为空：<span className="mono">{miss.join(', ')}</span>
        </div>
      )}

      {selectedKey ? (
        <div className="translate-workspace-editor">
          <MarkupEditor
            fill
            autoFocus
            value={value}
            onChange={writeValue}
            placeholder={`在此编写 ${activeLang} 译文…\n可用 [b][i][p] 等标记（语法高亮）`}
          />
        </div>
      ) : (
        <div className="empty-state translate-workspace-empty">
          选择翻译键后在此大编辑器中编写对应语言的值。
          <div className="hint" style={{ marginTop: 8 }}>
            共 {keys.length} 个键 · 当前语言 <span className="mono">{activeLang}</span>
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
            ← 上一键
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
            下一键 →
          </button>
        </div>
      )}
    </div>
  );
}
